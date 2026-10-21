package com.antifraud.agent.service.tool;

import com.antifraud.agent.service.log.ToolCallLogService;
import com.antifraud.agent.service.rag.RagRetrievalService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库检索工具(RAG): 由 Analysis Agent 在需要比对诈骗话术/相似案例时调用。
 *
 * <p>底层仍复用 Spring AI 的 Milvus {@code VectorStore}(Embedding + 检索),
 * 对 AgentScope 而言它就是一个普通工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeTool {

    private static final String AGENT_NAME = "ANALYSIS";

    private final RagRetrievalService ragRetrievalService;
    private final ToolCallLogService toolCallLogService;

    @Tool(name = "search_knowledge_base",
            description = "检索反诈知识库中与关键词相似的诈骗案例/话术特征。返回命中案例列表。",
            readOnly = true, concurrencySafe = true)
    public Map<String, Object> searchKnowledgeBase(
            @ToolParam(name = "keyword", description = "关键词/通话摘要") String keyword,
            RuntimeContext ctx) {
        ToolRuntime.bind(ctx, AGENT_NAME);
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        try {
            List<Document> docs = ragRetrievalService.retrieve(keyword);
            List<String> contents = docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.toList());
            result.put("count", docs.size());
            result.put("cases", contents);
            toolCallLogService.record("search_knowledge_base",
                    Map.of("keyword", keyword), result, true, null, System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 向量库不可用时不能让整个 ReAct 循环失败, 返回空结果让模型基于其它证据继续研判
            log.warn("知识库检索失败, 返回空结果. keyword={}", keyword, e);
            result.put("count", 0);
            result.put("cases", List.of());
            result.put("error", "知识库不可用");
            toolCallLogService.record("search_knowledge_base",
                    Map.of("keyword", keyword), result, false, e.getMessage(), System.currentTimeMillis() - start);
        }
        ToolRuntime.evidence(ctx, "ragContext", result);
        return result;
    }
}
