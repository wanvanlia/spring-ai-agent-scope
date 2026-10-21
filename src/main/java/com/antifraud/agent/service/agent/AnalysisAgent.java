package com.antifraud.agent.service.agent;

import com.antifraud.agent.common.context.AgentContextHolder;
import com.antifraud.agent.common.context.EvidenceBundle;
import com.antifraud.agent.common.enums.RiskLevel;
import com.antifraud.agent.common.enums.SessionState;
import com.antifraud.agent.common.util.IdGenerator;
import com.antifraud.agent.common.util.MaskingUtil;
import com.antifraud.agent.entity.RiskCase;
import com.antifraud.agent.mapper.RiskCaseMapper;
import com.antifraud.agent.model.dto.RiskAssessmentReport;
import com.antifraud.agent.service.session.AgentSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analysis Agent(分析智能体): 基于 AgentScope {@link ReActAgent} 的 ReAct 循环。
 *
 * <p>与旧实现的本质区别: 取证<b>不再由业务代码写死顺序</b>(校验->画像->黑名单->RAG),
 * 而是把号码画像/黑名单/知识库检索作为工具注册到 {@code Toolkit}, 由模型自主决定调用;
 * 采集完证据后按结构化 schema 输出 {@link RiskAssessmentReport}。
 *
 * <p>状态: ANALYSIS -> (HIGH/MEDIUM) HUMAN_REVIEW / (LOW) END
 */
@Slf4j
@Component
public class AnalysisAgent {

    private final ReActAgent agent;
    private final AgentSessionService sessionService;
    private final RiskCaseMapper riskCaseMapper;
    private final MaskingUtil maskingUtil;
    private final ObjectMapper objectMapper;

    public AnalysisAgent(@Qualifier("analysisReActAgent") ReActAgent agent,
                         AgentSessionService sessionService,
                         RiskCaseMapper riskCaseMapper,
                         MaskingUtil maskingUtil,
                         ObjectMapper objectMapper) {
        this.agent = agent;
        this.sessionService = sessionService;
        this.riskCaseMapper = riskCaseMapper;
        this.maskingUtil = maskingUtil;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行风险研判。
     *
     * @param sessionId 会话ID
     * @param userId    用户ID(用于 AgentScope 会话隔离)
     * @param phone     涉案号码
     */
    public RiskCase analyze(String sessionId, Long userId, String phone) {
        AgentContextHolder.getOrCreate()
                .setSessionId(sessionId)
                .setAgentName("ANALYSIS");
        sessionService.transition(sessionId, SessionState.ANALYSIS, Map.of("phone", phone));

        EvidenceBundle evidence = new EvidenceBundle();
        RiskAssessmentReport report = null;
        String finalText = null;
        try {
            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(sessionId)
                    .userId(String.valueOf(userId))
                    .put(EvidenceBundle.class, evidence)
                    .build();
            AgentContextHolder.AgentContext holder = AgentContextHolder.get();
            if (holder != null && holder.getTraceId() != null) {
                ctx.put("traceId", holder.getTraceId());
            }
            Msg msg = agent.call(
                    List.of(new UserMessage(buildTask(phone))), RiskAssessmentReport.class, ctx).block();
            if (msg != null) {
                finalText = msg.getTextContent();
                report = msg.getStructuredData(RiskAssessmentReport.class);
            }
        } catch (Exception e) {
            // 循环超时/模型异常/结构化解析失败都不应让接口 500, 走"中风险 + 转人工"的保守降级
            log.error("风险研判失败, 已降级处理. sessionId={} phone={}", sessionId, phone, e);
        }

        boolean degraded = report == null || report.getRiskLevel() == null;
        if (degraded) {
            log.warn("未拿到有效结构化研判结果, 按中风险兜底并转人工复核. sessionId={} phone={}", sessionId, phone);
            if (report == null) {
                report = new RiskAssessmentReport();
                report.setPhone(phone);
            }
            report.setRiskLevel(RiskLevel.MEDIUM.name());
            if (report.getConfidence() == null) {
                report.setConfidence(0.5);
            }
            if (report.getReason() == null || report.getReason().isBlank()) {
                report.setReason("LLM 未返回有效研判结果, 走兜底中风险研判, 转人工复核。");
            }
            report.setSuggestion("HUMAN_REVIEW");
        }

        Map<String, Object> evidenceChain = new LinkedHashMap<>(evidence.getItems());
        evidenceChain.put("finalText", finalText);

        // 落库风险案例 + 证据链
        RiskCase riskCase = persistRiskCase(sessionId, phone, report, evidenceChain, degraded);

        // 状态流转
        RiskLevel level = normalizeRiskLevel(report.getRiskLevel());
        if (level.isNeedHumanReview()) {
            sessionService.transition(sessionId, SessionState.HUMAN_REVIEW, Map.of("riskLevel", level));
        } else {
            sessionService.transition(sessionId, SessionState.EXECUTION, Map.of("riskLevel", level));
        }
        log.info("研判完成 sessionId={} phone={} riskLevel={} 取证项={}",
                sessionId, phone, level, evidence.getItems().keySet());
        return riskCase;
    }

    private String buildTask(String phone) {
        return "请研判手机号 " + phone + " 的电信网络诈骗风险: "
                + "先用工具采集证据(号码格式校验 / 通话画像 / 黑名单 / 反诈知识库), "
                + "再综合给出风险等级、置信度、诈骗类型与处置建议。";
    }

    private RiskCase persistRiskCase(String sessionId, String phone, RiskAssessmentReport report,
                                     Map<String, Object> evidence, boolean degraded) {
        // 兜底: LLM 未返回有效风险等级(如返回空 JSON / 解析失败)时按中风险处理。
        // 原因: risk_level 是 NOT NULL 列, 而 MyBatis-Plus 默认忽略 null 字段,
        //      直接 setRiskLevel(null) 会让 INSERT 不含该列 ->
        //      "Field 'risk_level' doesn't have a default value"。
        RiskLevel level = normalizeRiskLevel(report.getRiskLevel());
        if (degraded) {
            log.warn("LLM 未返回有效风险等级, 按 {} 兜底并转人工复核. sessionId={} phone={}", level, sessionId, phone);
        }

        RiskCase rc = new RiskCase();
        rc.setCaseNo(IdGenerator.nextCaseNo());
        rc.setSessionId(sessionId);
        rc.setPhone(phone);
        rc.setPhoneMasked(maskingUtil.maskPhone(phone));
        rc.setRiskLevel(level.name());
        rc.setConfidence(report.getConfidence() == null ? null : BigDecimal.valueOf(report.getConfidence()));
        rc.setFraudType(report.getFraudType());
        rc.setReason(report.getReason());
        rc.setHandleResult("PENDING");
        // 处置建议归一化: 模型可能拼错(如 AUTO_SHUTOFF)或给出未知值, 未知一律按转人工处理
        String suggestion = normalizeSuggestion(report.getSuggestion());
        if (!suggestion.equals(report.getSuggestion())) {
            log.warn("处置建议非标准值, 已归一化: {} -> {} (caseNo={})", report.getSuggestion(), suggestion, rc.getCaseNo());
        }
        // 结构化结果缺失时一律转人工, 避免在无有效研判数据的情况下走自动处置
        rc.setAuditStatus(degraded || "HUMAN_REVIEW".equals(suggestion) ? "HUMAN_REVIEW" : "AUTO");
        try {
            rc.setEvidenceJson(objectMapper.writeValueAsString(evidence));
            rc.setAnalysisReport(objectMapper.writeValueAsString(report));
        } catch (Exception e) {
            log.warn("证据链/报告序列化失败", e);
        }
        rc.setCreateTime(LocalDateTime.now());
        rc.setUpdateTime(LocalDateTime.now());
        riskCaseMapper.insert(rc);
        return rc;
    }

    /**
     * 归一化处置建议: AUTO_SHUTDOWN / HUMAN_REVIEW / CONTINUE_OBSERVE。
     * 无法识别(含模型拼错、返回 schema 占位符等)时保守地按 HUMAN_REVIEW 处理。
     */
    private String normalizeSuggestion(String raw) {
        if (raw == null || raw.isBlank()) {
            return "HUMAN_REVIEW";
        }
        return switch (raw.trim().toUpperCase()) {
            case "AUTO_SHUTDOWN", "AUTO_SHUTOFF", "SHUTDOWN" -> "AUTO_SHUTDOWN";
            case "CONTINUE_OBSERVE", "OBSERVE" -> "CONTINUE_OBSERVE";
            case "HUMAN_REVIEW" -> "HUMAN_REVIEW";
            default -> "HUMAN_REVIEW";
        };
    }

    private RiskLevel normalizeRiskLevel(String raw) {
        if (raw == null) {
            return RiskLevel.MEDIUM;
        }
        try {
            return RiskLevel.valueOf(raw.toUpperCase());
        } catch (Exception e) {
            return RiskLevel.MEDIUM;
        }
    }
}
