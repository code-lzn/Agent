package com.limou.agent.task;

import com.limou.agent.ai.movie.fuzzy.FilmPinyinIndex;
import com.limou.agent.rag.FilmRagService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 影片缓存统一刷新入口：影片新增/编辑/删除/状态变更后调用，让模糊匹配索引与 RAG 向量库
 * 立即反映 DB 变化，无需重启。集中刷新避免各调用方重复依赖多个 bean。
 */
@Slf4j
@Component
public class FilmCacheRefresher {

    @Resource
    private FilmPinyinIndex filmPinyinIndex;

    @Resource
    private FilmRagService filmRagService;

    /** 重建影片拼音索引 + RAG 向量库（影片量小，同步调用即可） */
    public void refreshAll() {
        filmPinyinIndex.refresh();
        filmRagService.refresh();
    }
}
