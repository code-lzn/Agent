package com.limou.agent.rag;

import com.limou.agent.mapper.FilmMapper;
import com.limou.agent.model.entity.Film;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 影片 RAG 向量库：启动时把可上映影片（hot/published）的基础信息 embedding 进内存向量库，
 * 聊天时 LLM 通过 {@code QuestionAnswerAdvisor} 检索引用（如"推荐一部吴京主演的电影"）。
 * <p>
 * 与 {@link DocumentRagService}（md 知识库）独立：生命周期/刷新时机不同，
 * 合并会互相稀释 topK，且刷新影片库不应误伤 md 切片。
 */
@Slf4j
@Service
public class FilmRagService {

    private final EmbeddingModel embeddingModel;
    private final FilmMapper filmMapper;

    /** 内存向量库，启动重建；refresh 时原子替换（失败保留旧库） */
    private volatile SimpleVectorStore filmStore;

    public FilmRagService(EmbeddingModel embeddingModel, FilmMapper filmMapper) {
        this.embeddingModel = embeddingModel;
        this.filmMapper = filmMapper;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 重建整个影片向量库（只读 DB，无副作用）。原子替换：新库构建成功才覆盖旧库。
     */
    public synchronized void refresh() {
        try {
            List<Film> films = filmMapper.selectListByQuery(
                    QueryWrapper.create().in(Film::getStatus, List.of("hot", "published")));
            SimpleVectorStore newStore = SimpleVectorStore.builder(embeddingModel).build();
            List<Document> docs = films.stream().map(this::toDocument).toList();
            if (!docs.isEmpty()) {
                newStore.add(docs);
            }
            this.filmStore = newStore;
            log.info("FilmRagService 重建完成: {} 部影片", films.size());
        } catch (Exception e) {
            log.warn("FilmRagService 刷新失败（保留旧库）: {}", e.getMessage());
        }
    }

    /** 影片向量库（可能为 null：embedding 未配置/刷新失败） */
    public VectorStore getVectorStore() {
        return filmStore;
    }

    /**
     * 每条影片一部文档：片名/类型/导演/主演/评分/上映日期/简介。
     * 影片信息短，不必切片（一条一 Document）。
     */
    private Document toDocument(Film f) {
        StringBuilder text = new StringBuilder();
        text.append("电影《").append(f.getName()).append("》");
        if (f.getEnglishName() != null && !f.getEnglishName().isBlank()) {
            text.append("，英文名 ").append(f.getEnglishName());
        }
        if (f.getType() != null && !f.getType().isBlank()) {
            text.append("。类型：").append(f.getType());
        }
        if (f.getDirector() != null && !f.getDirector().isBlank()) {
            text.append("。导演：").append(f.getDirector());
        }
        if (f.getActors() != null && !f.getActors().isBlank()) {
            text.append("。主演：").append(f.getActors());
        }
        if (f.getRating() != null) {
            text.append("。评分：").append(f.getRating());
        }
        if (f.getReleaseDate() != null) {
            text.append("。上映日期：").append(f.getReleaseDate());
        }
        if (f.getDescription() != null && !f.getDescription().isBlank()) {
            text.append("。简介：").append(f.getDescription());
        }
        return new Document(text.toString(), Map.of(
                "filmId", String.valueOf(f.getId()),
                "name", f.getName() == null ? "" : f.getName(),
                "director", f.getDirector() == null ? "" : f.getDirector(),
                "actors", f.getActors() == null ? "" : f.getActors(),
                "type", f.getType() == null ? "" : f.getType(),
                "rating", f.getRating() == null ? "" : f.getRating().toPlainString()));
    }
}
