package com.antifraud.agent.service.agent;

import com.antifraud.agent.common.context.AgentContextHolder;
import com.antifraud.agent.common.enums.IntentType;
import com.antifraud.agent.common.enums.SessionState;
import com.antifraud.agent.model.dto.RouterResult;
import com.antifraud.agent.service.session.AgentSessionService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Router Agent(路由智能体): 基于 AgentScope {@link ReActAgent} 的意图识别 + 号码槽位提取。
 *
 * <p>不挂载工具(纯分类任务, 降低延迟与 Token 消耗), 通过结构化输出直接得到 {@link RouterResult}。
 * <p>状态: INIT -> INTENT_RECOGNITION -> (信息缺失) DATA_COLLECTION / (齐备) ANALYSIS
 */
@Slf4j
@Component
public class RouterAgent {

    private final ReActAgent agent;
    private final AgentSessionService sessionService;

    public RouterAgent(@Qualifier("routerReActAgent") ReActAgent agent,
                       AgentSessionService sessionService) {
        this.agent = agent;
        this.sessionService = sessionService;
    }

    /**
     * @param sessionId 会话ID
     * @param userId    用户ID(用于 AgentScope 会话隔离)
     * @param userInput 用户输入
     * @return 路由结果(含意图与号码)
     */
    public RouterResult route(String sessionId, Long userId, String userInput) {
        AgentContextHolder.getOrCreate()
                .setSessionId(sessionId)
                .setAgentName("ROUTER");
        sessionService.transition(sessionId, SessionState.INTENT_RECOGNITION, null);

        RouterResult result;
        try {
            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(sessionId)
                    .userId(String.valueOf(userId))
                    .build();
            Msg msg = agent.call(List.of(new UserMessage(userInput)), RouterResult.class, ctx).block();
            result = msg == null ? null : msg.getStructuredData(RouterResult.class);
        } catch (Exception e) {
            log.error("意图识别失败, 已降级为追问. sessionId={} input={}", sessionId, userInput, e);
            result = null;
        }

        if (result == null || result.getIntent() == null) {
            return fallback(sessionId);
        }

        // 归一化意图
        IntentType intent;
        try {
            intent = IntentType.valueOf(result.getIntent().trim().toUpperCase());
        } catch (Exception e) {
            intent = IntentType.UNKNOWN;
        }
        result.setIntent(intent.name());
        sessionService.updateIntent(sessionId, intent.name());
        log.info("路由完成 sessionId={} intent={} missingInfo={}", sessionId, intent, result.isMissingInfo());

        if (result.isMissingInfo() || result.getPhone() == null || result.getPhone().isBlank()) {
            sessionService.transition(sessionId, SessionState.DATA_COLLECTION, result);
            result.setMissingInfo(true);
        } else {
            sessionService.transition(sessionId, SessionState.ANALYSIS, result);
        }
        return result;
    }

    /** 兜底: 模型不可用/输出不可解析时按"信息缺失 -> 追问"处理, 不阻断流程。 */
    private RouterResult fallback(String sessionId) {
        RouterResult r = new RouterResult();
        r.setIntent(IntentType.UNKNOWN.name());
        r.setMissingInfo(true);
        r.setFollowUp("系统繁忙, 请稍后重试或联系人工。");
        sessionService.updateIntent(sessionId, IntentType.UNKNOWN.name());
        sessionService.transition(sessionId, SessionState.DATA_COLLECTION, r);
        return r;
    }
}
