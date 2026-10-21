package com.antifraud.agent.service.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 知识库摄入(Ingestion): 文本 -> TokenTextSplitter 分片 -> EmbeddingModel -> 向量库。
 * <p>对应文档 3.2.1: 定期导入最新诈骗案例/黑产话术/法律法规。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestionService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter tokenTextSplitter;

    /**
     * 摄入单条知识。
     *
     * @param content  原文
     * @param metadata 元数据(source/tag/updated)
     */
    public void ingest(String content, Map<String, Object> metadata) {
        ingest(List.of(new Document(content, metadata)));
    }

    /**
     * 批量摄入并分片。
     */
    public void ingest(List<Document> documents) {
        List<Document> chunks = tokenTextSplitter.apply(documents);
        vectorStore.add(chunks);
        log.info("知识摄入完成: 原始 {} 篇 -> 分片 {} 片", documents.size(), chunks.size());
    }
}
