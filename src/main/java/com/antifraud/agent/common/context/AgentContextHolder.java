package com.antifraud.agent.common.context;

/**
 * Agent 调用上下文(ThreadLocal): 在工具执行链路中透传 sessionId / traceId / agentName,
 * 供工具调用日志(t_tool_call_log)审计。
 */
public final class AgentContextHolder {

    private static final ThreadLocal<AgentContext> HOLDER = new ThreadLocal<>();

    private AgentContextHolder() {
    }

    public static void set(AgentContext context) {
        HOLDER.set(context);
    }

    public static AgentContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static AgentContext getOrCreate() {
        AgentContext ctx = HOLDER.get();
        if (ctx == null) {
            ctx = new AgentContext();
            HOLDER.set(ctx);
        }
        return ctx;
    }

    public static class AgentContext {
        private String sessionId;
        private String traceId;
        private String agentName;

        public String getSessionId() { return sessionId; }
        public AgentContext setSessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public String getTraceId() { return traceId; }
        public AgentContext setTraceId(String traceId) { this.traceId = traceId; return this; }
        public String getAgentName() { return agentName; }
        public AgentContext setAgentName(String agentName) { this.agentName = agentName; return this; }
    }
}
