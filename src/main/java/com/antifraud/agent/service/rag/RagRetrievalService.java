package com.antifraud.agent.service.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 混合检索: 向量语义检索 + 关键词(metadata.tag)检索, 简单融合排序取 Top-K。
 * <p>对应文档 3.2.2: 混合检索 + 重排序。重排序可后续引入 Cross-Encoder, 此处用分数融合兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final VectorStore vectorStore;

    @Value("${antifraud.rag.top-k:5}")
    private int topK;

    @Value("${antifraud.rag.similarity-threshold:0.75}")
    private double similarityThreshold;

    private static final List<String> HIGH_RISK_KEYWORDS = List.of(
            "安全账户", "验证码", "屏幕共享", "公安局", "转账", "公检法", "洗钱", "保证金"
    );

    /**
     * 检索与输入文本最相关的反诈知识片段。
     *
     * @param query 用户输入/通话摘要
     * @return 命中片段列表
     */
    public List<Document> retrieve(String query) {
        // 1. 向量语义检索
        List<Document> semantic = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build());

        // 2. 关键词维度评分(针对特定诈骗术语命中加权)
        List<Document> merged = new ArrayList<>(semantic);
        for (Document doc : merged) {
            double boost = HIGH_RISK_KEYWORDS.stream()
                    .filter(k -> doc.getText() != null && doc.getText().contains(k))
                    .count() * 0.05;
            doc.getMetadata().put("boostedScore",
                    doc.getScore() == null ? boost : doc.getScore() + boost);
        }

        // 3. 融合排序取 Top-K
        return merged.stream()
                .sorted(Comparator.comparing((Document d) ->
                        ((Number) d.getMetadata().getOrDefault("boostedScore", 0.0)).doubleValue())
                        .reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 拼接检索结果为上下文文本。
     */
    public String retrieveAsContext(String query) {
        List<Document> docs = retrieve(query);
        if (docs.isEmpty()) {
            return "无匹配案例。";
        }
        return docs.stream()
                .map(d -> "- " + d.getText())
                .collect(Collectors.joining("\n"));
    }
}
