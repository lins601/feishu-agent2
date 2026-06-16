package com.example.myapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户反馈记录 — 对应飞书多维表格 feedback_record。
 * <p>
 * 用户点击"有用"/"没用"按钮后写入，通过 qa_id 关联问答记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRecord {

    /** 反馈记录主键（本地生成 UUID） */
    private String feedbackId;

    /** 关联的问答记录 ID */
    private String qaId;

    /** 反馈用户 open_id */
    private String userId;

    /** 对应问题 */
    private String question;

    /** 对应答案摘要 */
    private String answerSummary;

    /** 知识来源 */
    private String knowledgeRefs;

    /** 反馈值：useful / useless */
    private String feedback;

    /** 创建时间（毫秒时间戳） */
    private long createdAt;
}
