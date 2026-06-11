package com.example.myapp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识审核记录 POJO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRecord {

    private String knowledgeSource;
    /** negative_feedback / expired / content_error */
    private String reviewReason;
    /** pending / approved / rejected */
    private String reviewStatus = "pending";
    private String assignee;
    private LocalDateTime createdAt = LocalDateTime.now();
}
