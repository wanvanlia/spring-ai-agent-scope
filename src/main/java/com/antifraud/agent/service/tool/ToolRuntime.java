package com.antifraud.agent.service.tool;

import com.antifraud.agent.common.context.AgentContextHolder;
import com.antifraud.agent.common.context.EvidenceBundle;
import io.agentscope.core.agent.RuntimeContext;

/**
 * AgentScope 工具的公共运行时支持。
 *
 * <p>要点: 工具由 AgentScope 在 Reactor 线程上回调, 请求线程的 ThreadLocal 已不可见,
 * 因此工具内部需要两件事:
 * <ol>
 *   <li>把 {@code RuntimeContext} 里的 sessionId 回填到 {@link AgentContextHolder},
 *       保证 t_tool_call_log 的审计链路(会话/Trace)不丢;</li>
 *   <li>把工具结果写入 {@link EvidenceBundle}, 供研判落库时生成证据链。</li>
 * </ol>
 * 所有方法都对入参做空值防御: 上下文缺失时不影响工具主流程。
 */
public final class ToolRuntime {

    private ToolRuntime() {
    }

    /** 绑定审计上下文(会话/智能体名)。 */
    public static void bind(RuntimeContext ctx, String agentName) {
        if (ctx == null) {
            return;
        }
        Object traceId = ctx.get("traceId");
        AgentContextHolder.set(new AgentContextHolder.AgentContext()
                .setSessionId(ctx.getSessionId())
                .setTraceId(traceId == null ? null : String.valueOf(traceId))
                .setAgentName(agentName));
    }

    /** 追加一条取证结果到证据链。 */
    public static void evidence(RuntimeContext ctx, String key, Object value) {
        if (ctx == null) {
            return;
        }
        EvidenceBundle bundle = ctx.get(EvidenceBundle.class);
        if (bundle != null) {
            bundle.add(key, value);
        }
    }
}
