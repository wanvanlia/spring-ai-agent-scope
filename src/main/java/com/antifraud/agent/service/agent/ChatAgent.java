package com.antifraud.agent.service.agent;

import com.antifraud.agent.common.context.AgentContextHolder;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用问答智能体: 承接意图识别为 UNKNOWN 的兜底对话。
 *
 * <p>多轮记忆不再依赖自研 Redis ChatMemory, 而是由 AgentScope 的 {@code AgentState}
 * (经 {@code AgentStateStore} 持久化)按 {@code (userId, sessionId)} 自动恢复上下文。
 */
@Slf4j
@Component
public class ChatAgent {

    private final ReActAgent agent;

    public ChatAgent(@Qualifier("chatReActAgent") ReActAgent agent) {
        this.agent = agent;
    }

    /**
     * @param sessionId 会话ID(同一会话复用即可获得连续记忆)
     * @param userId    用户ID
     * @param text      用户输入
     */
    public String chat(String sessionId, Long userId, String text) {
        AgentContextHolder.getOrCreate()
                .setSessionId(sessionId)
                .setAgentName("CHAT");
        try {
            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(sessionId)
                    .userId(String.valueOf(userId))
                    .build();
            Msg msg = agent.call(List.of(new UserMessage(text)), ctx).block();
            String reply = msg == null ? null : msg.getTextContent();
            if (reply == null || reply.isBlank()) {
                return "系统当前繁忙, 已为您转接人工审核员, 请稍候。";
            }
            return reply;
        } catch (Exception e) {
            log.error("通用对话失败, sessionId={}", sessionId, e);
            return "系统当前繁忙, 已为您转接人工审核员, 请稍候。(fallback)";
        }
    }
}
