package com.antifraud.agent.service.log;

import com.antifraud.agent.common.context.AgentContextHolder;
import com.antifraud.agent.entity.ToolCallLog;
import com.antifraud.agent.mapper.ToolCallLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 工具调用审计日志(t_tool_call_log)异步落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolCallLogService {

    private final ToolCallLogMapper toolCallLogMapper;
    private final ObjectMapper objectMapper;

    @Async
    public void record(String toolName, Object arguments, Object result, boolean success, String errorMsg, long durationMs) {
        try {
            AgentContextHolder.AgentContext ctx = AgentContextHolder.get();
            ToolCallLog log = new ToolCallLog();
            log.setSessionId(ctx == null ? null : ctx.getSessionId());
            log.setTraceId(ctx == null ? null : ctx.getTraceId());
            log.setAgentName(ctx == null ? null : ctx.getAgentName());
            log.setToolName(toolName);
            log.setArguments(objectMapper.writeValueAsString(arguments));
            log.setResult(objectMapper.writeValueAsString(result));
            log.setSuccess(success ? 1 : 0);
            log.setErrorMsg(errorMsg);
            log.setDurationMs((int) durationMs);
            log.setCreateTime(LocalDateTime.now());
            toolCallLogMapper.insert(log);
        } catch (Exception e) {
            ToolCallLogService.log.error("工具调用日志落库失败: {}", toolName, e);
        }
    }
}
