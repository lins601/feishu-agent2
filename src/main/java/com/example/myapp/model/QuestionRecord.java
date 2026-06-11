package com.example.myapp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 问答记录 POJO（写入飞书多维表格）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionRecord {

    /** 提问用户 ID */
    private String userId;

    /** 用户姓名 */
    private String userName;

    /** 原始问题 */
    private String question;

    /** 回答摘要 */
    private String answerSummary;

    /** 知识来源（WISE 文档标题/URL） */
    private String knowledgeSource;

    /** 匹配置信度 */
    private double confidence;

    /** 对话时间 */
    private LocalDateTime createdAt = LocalDateTime.now();
}
