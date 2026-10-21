package com.antifraud.agent.service.session;

import com.antifraud.agent.common.enums.SessionState;
import com.antifraud.agent.entity.AgentSession;
import com.antifraud.agent.mapper.AgentSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agent 会话管理: 创建/查询/状态流转(t_agent_session)。
 * <p>业务会话状态由 MySQL 承载; 对话历史(模型上下文)由 AgentScope 的 AgentStateStore
 * 按 (userId, sessionId) 持久化, 因此跨轮次调用需复用同一 sessionId。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSessionService {

    private final AgentSessionMapper agentSessionMapper;
    private final ObjectMapper objectMapper;

    /** 创建新会话 */
    public AgentSession createSession(Long userId, String traceId) {
        AgentSession session = new AgentSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setConversationId(session.getSessionId());
        session.setUserId(userId);
        session.setTraceId(traceId);
        session.setCurrentState(SessionState.INIT.name());
        session.setStatus(1);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        agentSessionMapper.insert(session);
        return session;
    }

    /**
     * 查询该用户最近一次会话。
     * <p>多轮对话需要稳定的 sessionId: AgentScope 的 AgentState 以 {@code (userId, sessionId)}
     * 为键持久化, 会话复用后上下文(历史消息/工具结果)才能连续。
     */
    public AgentSession findLatestSession(Long userId) {
        return agentSessionMapper.selectOne(
                new LambdaQueryWrapper<AgentSession>()
                        .eq(AgentSession::getUserId, userId)
                        .orderByDesc(AgentSession::getId)
                        .last("LIMIT 1"));
    }

    public AgentSession findBySessionId(String sessionId) {
        return agentSessionMapper.selectOne(
                new LambdaQueryWrapper<AgentSession>().eq(AgentSession::getSessionId, sessionId));
    }

    /** 状态流转 + 上下文更新 */
    public void transition(String sessionId, SessionState state, Object context) {
        AgentSession session = findBySessionId(sessionId);
        if (session == null) {
            log.warn("会话不存在: {}", sessionId);
            return;
        }
        session.setCurrentState(state.name());
        try {
            session.setContextJson(context == null ? null : objectMapper.writeValueAsString(context));
        } catch (Exception e) {
            log.warn("上下文序列化失败", e);
        }
        session.setUpdateTime(LocalDateTime.now());
        agentSessionMapper.updateById(session);
    }

    public void updateIntent(String sessionId, String intent) {
        AgentSession session = findBySessionId(sessionId);
        if (session != null) {
            session.setIntent(intent);
            session.setUpdateTime(LocalDateTime.now());
            agentSessionMapper.updateById(session);
        }
    }

    public void finish(String sessionId) {
        transition(sessionId, SessionState.END, null);
    }
}
