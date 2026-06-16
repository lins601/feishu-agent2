package com.example.myapp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识入库状态跟踪 POJO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestionStatus {

    private String documentUrl;
    private String sourceName;
    /** WISE 返回的 knowledge_id */
    private String knowledgeId;
    /** processing / completed / failed / auth_failed / expired / skipped */
    private String parseStatus;
    private String failureReason;
    private LocalDateTime ingestedAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
}
