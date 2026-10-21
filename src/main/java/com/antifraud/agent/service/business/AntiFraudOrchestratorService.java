package com.antifraud.agent.service.business;

import com.antifraud.agent.common.context.AgentContextHolder;
import com.antifraud.agent.common.enums.IntentType;
import com.antifraud.agent.common.enums.RiskLevel;
import com.antifraud.agent.common.enums.SessionState;
import com.antifraud.agent.common.guard.PromptGuard;
import com.antifraud.agent.entity.AgentSession;
import com.antifraud.agent.entity.RiskCase;
import com.antifraud.agent.entity.ShutdownOrder;
import com.antifraud.agent.model.dto.RouterResult;
import com.antifraud.agent.model.vo.ChatResponse;
import com.antifraud.agent.model.vo.FraudAnalysisVO;
import com.antifraud.agent.service.agent.ActionAgent;
import com.antifraud.agent.service.agent.AnalysisAgent;
import com.antifraud.agent.service.agent.ChatAgent;
import com.antifraud.agent.service.agent.RouterAgent;
import com.antifraud.agent.service.session.AgentSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * 反诈业务编排服务: 贯通 Router -> Analysis -> Action 的状态机闭环。
 *
 * <p>智能体由 AgentScope Java 2.x 驱动(见 {@code config/AgentScopeAgentConfig}),
 * 本服务只负责: 会话与状态机、安全护栏、业务落库与响应组装。
 * <p>对应文档 3.4 电话诈骗业务流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AntiFraudOrchestratorService {

    private final RouterAgent routerAgent;
    private final AnalysisAgent analysisAgent;
    private final ActionAgent actionAgent;
    private final ChatAgent chatAgent;
    private final AgentSessionService sessionService;
    private final ObjectMapper objectMapper;
    private final PromptGuard promptGuard;

    /**
     * 多轮对话入口(人工审核员与 Agent 交互)。
     * <p>若信息缺失 -> 追问; 若意图为查询/举报且含号码 -> 触发研判。
     */
    public ChatResponse chat(Long userId, String userInput) {
        // Prompt 注入防御(输入层拦截)
        promptGuard.check(userInput);
        String traceId = UUID.randomUUID().toString().replace("-", "");
        AgentSession session = findOrCreateSession(userId, traceId);
        bindContext(session, traceId);

        ChatResponse resp = new ChatResponse();
        resp.setSessionId(session.getSessionId());
        resp.setConversationId(session.getConversationId());

        RouterResult router = routerAgent.route(session.getSessionId(), userId, userInput);
        if (router.isMissingInfo() || router.getPhone() == null) {
            resp.setReply(router.getFollowUp() != null ? router.getFollowUp()
                    : "请提供待核查的手机号, 例如: 帮我核查 13800001111 是否有诈骗风险。");
            resp.setNeedFollowUp(true);
            return resp;
        }

        // 号码齐备 -> 进入研判
        if (router.getIntent() == null || IntentType.UNKNOWN.name().equals(router.getIntent())
                || IntentType.QUERY.name().equals(router.getIntent())
                || IntentType.REPORT.name().equals(router.getIntent())
                || IntentType.SHUTDOWN.name().equals(router.getIntent())) {
            FraudAnalysisVO vo = analyzeAndAct(session.getSessionId(), router.getPhone(), userId);
            resp.setReply(buildReply(vo));
            resp.setNeedFollowUp(vo.isNeedHumanReview());
        } else {
            // 兜底走通用聊天(由 AgentScope chatAgent 承载, 具备多轮记忆)
            resp.setReply(chatAgent.chat(session.getSessionId(), userId, userInput));
        }
        return resp;
    }

    /**
     * 直接以号码触发研判(供 API 直调, 跳过意图识别)。
     */
    public FraudAnalysisVO analyzePhone(Long operatorId, String phone) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        AgentSession session = sessionService.createSession(operatorId, traceId);
        bindContext(session, traceId);
        sessionService.updateIntent(session.getSessionId(), IntentType.QUERY.name());
        sessionService.transition(session.getSessionId(), SessionState.ANALYSIS, Map.of("phone", phone));
        FraudAnalysisVO vo = analyzeAndAct(session.getSessionId(), phone, operatorId);
        vo.setSessionId(session.getSessionId());
        return vo;
    }

    /**
     * 以已有会话触发研判(状态机 ANALYSIS -> EXECUTION/HUMAN_REVIEW)。
     */
    public FraudAnalysisVO analyzeAndAct(String sessionId, String phone, Long operatorId) {
        RiskCase riskCase = analysisAgent.analyze(sessionId, operatorId, phone);
        RiskLevel level = normalize(riskCase.getRiskLevel());

        FraudAnalysisVO vo = toVO(riskCase);
        vo.setRiskLevel(level.name());

        if (level == RiskLevel.HIGH || level == RiskLevel.MEDIUM) {
            ShutdownOrder order = actionAgent.executeShutdown(riskCase, level);
            vo.setOrderNo(order.getOrderNo());
            vo.setHandleResult(order.getStatus());
            vo.setNeedHumanReview("PENDING".equals(order.getStatus()));
        } else {
            // 低风险: 继续观察, 不关停
            riskCase.setHandleResult("CONTINUE_OBSERVE");
            riskCase.setAuditStatus("AUTO");
            sessionService.transition(sessionId, SessionState.END, Map.of("riskLevel", level));
            vo.setHandleResult("CONTINUE_OBSERVE");
            vo.setNeedHumanReview(false);
        }
        return vo;
    }

    /**
     * 复用用户最近一次未结束的会话, 保证 AgentScope 的会话上下文(历史消息)可跨轮次延续。
     */
    private AgentSession findOrCreateSession(Long userId, String traceId) {
        AgentSession latest = sessionService.findLatestSession(userId);
        if (latest != null && !SessionState.END.name().equals(latest.getCurrentState())) {
            return latest;
        }
        return sessionService.createSession(userId, traceId);
    }

    private void bindContext(AgentSession session, String traceId) {
        AgentContextHolder.getOrCreate()
                .setSessionId(session.getSessionId())
                .setTraceId(traceId);
    }

    private RiskLevel normalize(String raw) {
        if (raw == null) {
            return RiskLevel.MEDIUM;
        }
        try {
            return RiskLevel.valueOf(raw.toUpperCase());
        } catch (Exception e) {
            return RiskLevel.MEDIUM;
        }
    }

    private FraudAnalysisVO toVO(RiskCase rc) {
        FraudAnalysisVO vo = new FraudAnalysisVO();
        vo.setSessionId(rc.getSessionId());
        vo.setCaseNo(rc.getCaseNo());
        vo.setPhoneMasked(rc.getPhoneMasked());
        vo.setRiskLevel(rc.getRiskLevel());
        vo.setConfidence(rc.getConfidence() == null ? null : rc.getConfidence().doubleValue());
        vo.setFraudType(rc.getFraudType());
        vo.setReason(rc.getReason());
        vo.setHandleResult(rc.getHandleResult());
        return vo;
    }

    private String buildReply(FraudAnalysisVO vo) {
        StringBuilder sb = new StringBuilder();
        sb.append("研判完成。号码 ").append(vo.getPhoneMasked());
        sb.append(" 风险等级: ").append(vo.getRiskLevel());
        if (vo.getConfidence() != null) {
            sb.append("(置信度 ").append(vo.getConfidence()).append(")");
        }
        if (vo.getFraudType() != null) {
            sb.append(", 类型: ").append(vo.getFraudType());
        }
        if (vo.getReason() != null) {
            sb.append("。理由: ").append(vo.getReason());
        }
        if (vo.getOrderNo() != null) {
            sb.append("。已生成工单: ").append(vo.getOrderNo());
        }
        if (vo.isNeedHumanReview()) {
            sb.append("。已转人工审核, 请在控制台确认是否关停。");
        }
        return sb.toString();
    }
}
