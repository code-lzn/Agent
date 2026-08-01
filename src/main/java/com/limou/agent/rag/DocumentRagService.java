package com.limou.agent.rag;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
//@Service
@Getter
public class DocumentRagService {

    /**
     * 嵌入模型     --dashScope 模型
     */
    private final EmbeddingModel embeddingModel;
    private final ResourcePatternResolver resourcePatternResolver;
    private VectorStore vectorStore;

    public DocumentRagService(EmbeddingModel embeddingModel,
                              ResourcePatternResolver resourcePatternResolver) {
        this.embeddingModel = embeddingModel;
        this.resourcePatternResolver = resourcePatternResolver;
    }

//    @PostConstruct
    public void init() {
        try {
            vectorStore = SimpleVectorStore.builder(embeddingModel).build();
            loadDocuments();
        } catch (Exception e) {
            log.warn("RAG 初始化失败（可能是 API key 未配置或网络异常），文档检索功能暂不可用: {}", e.getMessage());
            vectorStore = null;
        }
    }

    /**
     * 加载 classpath:document/ 下的所有 .md 文件，切片并存入向量库
     */
    public void loadDocuments() {
        List<Document> allDocs = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath*:document/*.md");
            if (resources.length == 0) {
                log.warn("未找到文档，路径: classpath*:document/*.md");
                return;
            }
            for (Resource resource : resources) {
                log.info("解析 Markdown: {}", resource.getFilename());
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(true)
                        .withIncludeBlockquote(true)
                        .withAdditionalMetadata("filename", resource.getFilename())
                        .build();
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                allDocs.addAll(reader.get());
            }
        } catch (IOException e) {
            log.error("加载文档失败", e);
            return;
        }

        TokenTextSplitter splitter = new TokenTextSplitter(500, 100, 5, 1000, true);
        List<Document> chunks = splitter.apply(allDocs);
        vectorStore.add(chunks);
        log.info("文档加载完成，共 {} 个切片存入向量库", chunks.size());
    }

    /**
     * 语义检索
     */
    public List<Document> search(String query, int topK) {
        if (vectorStore == null) {
            log.warn("向量库未初始化，返回空结果");
            return List.of();
        }
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build());
    }

}
