package com.antifraud.agent.common.context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 证据链收集器: 一次 Agent 调用内各工具的返回值汇总。
 *
 * <p>AgentScope 的工具执行发生在 Reactor 线程上, 无法依赖调用方栈传递数据,
 * 因此通过 {@code RuntimeContext} 的"类型化属性"把同一个收集器注入到每个工具:
 * <pre>
 *   RuntimeContext ctx = RuntimeContext.builder().put(EvidenceBundle.class, bundle).build();
 *   agent.call(msg, Report.class, ctx).block();
 *   bundle.getItems(); // 取证结果
 * </pre>
 */
public class EvidenceBundle {

    private final Map<String, Object> items = new LinkedHashMap<>();

    public void add(String key, Object value) {
        items.put(key, value);
    }

    public Map<String, Object> getItems() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
