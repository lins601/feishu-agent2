package com.example.myapp.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * WISE 检索结果 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WiseSearchResult {

    /** 知识片段列表 */
    private List<Chunk> chunks;

    /** 是否有结果 */
    public boolean hasResults() {
        return chunks != null && !chunks.isEmpty();
    }

    /** 最高分 */
    public double getTopScore() {
        if (!hasResults()) return 0.0;
        return chunks.stream().mapToDouble(Chunk::getScore).max().orElse(0.0);
    }

    /** 单个知识片段 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Chunk {

        /** 片段内容 */
        private String content;

        /** 相关性分数 */
        private double score;

        /** 所属文档标题 */
        private String documentTitle;

        /** 文档来源 URL */
        private String sourceUrl;

        /** 标签列表 */
        private List<String> tags;

        /** 所属系统: WMS / QMS / MES */
        private String system;

        /** 片段 ID */
        private String chunkId;
    }
}
