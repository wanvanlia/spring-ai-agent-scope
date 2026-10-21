package com.antifraud.agent.service.agent;

import com.antifraud.agent.common.context.AgentContextHolder;
import com.antifraud.agent.common.enums.RiskLevel;
import com.antifraud.agent.common.enums.SessionState;
import com.antifraud.agent.common.util.IdGenerator;
import com.antifraud.agent.entity.RiskCase;
import com.antifraud.agent.entity.ShutdownOrder;
import com.antifraud.agent.mapper.ShutdownOrderMapper;
import com.antifraud.agent.model.dto.ActionDecision;
import com.antifraud.agent.service.session.AgentSessionService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Action Agent(执行智能体): 关停/解封/通知。
 *
 * <p>AgentScope 化后的分工:
 * <ul>
 *   <li>模型在 ReAct 循环内调用 {@code submit_shutdown_request} / {@code notify_user} 工具(模拟运营商接口);</li>
 *   <li>模型最终按 {@link ActionDecision} 结构化回填"是否真的提交/通知";</li>
 *   <li>业务层只负责工单落库与状态流转(单一写入点, 避免双写与单号冲突)。</li>
 * </ul>
 * 高风险操作受 auto-shutdown-high-risk 开关约束, 默认走人机协同(HUMAN_REVIEW)。
 * <p>状态: HUMAN_REVIEW/EXECUTION -> EXECUTION -> END
 */
@Slf4j
@Component
public class ActionAgent {

    private final ReActAgent agent;
    private final ShutdownOrderMapper shutdownOrderMapper;
    private final AgentSessionService sessionService;

    @Value("${antifraud.agent.auto-shutdown-high-risk:false}")
    private boolean autoShutdownHighRisk;

    public ActionAgent(@Qualifier("actionReActAgent") ReActAgent agent,
                       ShutdownOrderMapper shutdownOrderMapper,
                       AgentSessionService sessionService) {
        this.agent = agent;
        this.shutdownOrderMapper = shutdownOrderMapper;
        this.sessionService = sessionService;
    }

    /**
     * 执行关停。
     *
     * @param riskCase 风险案例
     * @param level    风险等级
     * @return 关停工单(可能为 PENDING 待人审)
     */
    public ShutdownOrder executeShutdown(RiskCase riskCase, RiskLevel level) {
        AgentContextHolder.getOrCreate()
                .setSessionId(riskCase.getSessionId())
                .setAgentName("ACTION");
        sessionService.transition(riskCase.getSessionId(), SessionState.EXECUTION,
                Map.of("phone", riskCase.getPhone()));

        // 高风险且未开启自动关停 -> 仅创建待审核工单, 等人工确认(不咨询模型, 安全优先)
        if (level == RiskLevel.HIGH && !autoShutdownHighRisk) {
            log.info("高风险但未启用自动关停, 转人审. caseNo={}", riskCase.getCaseNo());
            ShutdownOrder pending = buildOrder(riskCase, "PENDING", 0);
            shutdownOrderMapper.insert(pending);
            sessionService.transition(riskCase.getSessionId(), SessionState.HUMAN_REVIEW,
                    Map.of("orderNo", pending.getOrderNo()));
            return pending;
        }

        // 交给 AgentScope 智能体: 调用关停/通知工具后回填执行结果
        ActionDecision decision = decide(riskCase, level);
        boolean submitted = decision != null && decision.isShutdown();
        boolean notified = decision != null && decision.isNotifyVictim();

        ShutdownOrder order = buildOrder(riskCase, submitted ? "SUBMITTED" : "FAILED", 1);
        order.setReason(riskCase.getReason());
        order.setNotifyStatus(notified ? "SUCCESS" : "SKIP");
        if (submitted) {
            riskCase.setHandleResult("SHUTDOWN");
            riskCase.setAuditStatus("AUTO");
            sessionService.finish(riskCase.getSessionId());
        } else {
            log.warn("关停未执行或执行失败, 转人工复核. caseNo={} reason={}",
                    riskCase.getCaseNo(), decision == null ? "模型无决策" : decision.getReason());
            // 关停失败需要人工介入, 不能当作流程结束
            sessionService.transition(riskCase.getSessionId(), SessionState.HUMAN_REVIEW,
                    Map.of("orderNo", order.getOrderNo()));
        }
        shutdownOrderMapper.insert(order);
        riskCase.setUpdateTime(LocalDateTime.now());

        log.info("关停执行完成 caseNo={} orderNo={} status={} notify={}",
                riskCase.getCaseNo(), order.getOrderNo(), order.getStatus(), order.getNotifyStatus());
        return order;
    }

    /** 调用 Action 智能体获取处置决策(结构化输出)。 */
    private ActionDecision decide(RiskCase riskCase, RiskLevel level) {
        String task = "风险案例 " + riskCase.getCaseNo() + " 的号码判定为 " + level.name() + " 风险, 理由: "
                + (riskCase.getReason() == null ? "无" : riskCase.getReason())
                + "。请调用关停工具提交关停工单, 并按需通知潜在受害者, 然后回填执行结果。";
        try {
            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(riskCase.getSessionId())
                    .userId(String.valueOf(riskCase.getOperatorId() == null ? 0L : riskCase.getOperatorId()))
                    .build();
            Msg msg = agent.call(List.of(new UserMessage(task)), ActionDecision.class, ctx).block();
            return msg == null ? null : msg.getStructuredData(ActionDecision.class);
        } catch (Exception e) {
            log.error("处置决策失败, caseNo={}", riskCase.getCaseNo(), e);
            return null;
        }
    }

    private ShutdownOrder buildOrder(RiskCase riskCase, String status, int isAuto) {
        ShutdownOrder order = new ShutdownOrder();
        order.setOrderNo(IdGenerator.nextOrderNo());
        order.setCaseId(riskCase.getId());
        order.setPhone(riskCase.getPhone());
        order.setPhoneMasked(riskCase.getPhoneMasked());
        order.setReason(riskCase.getReason());
        order.setStatus(status);
        order.setIsAuto(isAuto);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        return order;
    }
}
