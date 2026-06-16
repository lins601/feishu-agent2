package com.example.myapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识审核任务 — 对应飞书多维表格 knowledge_review。
 * <p>
 * 同一知识来源累计负反馈达到阈值（默认 3 次）时自动创建，
 * 由知识管理员审核并修正 WISE 知识库内容。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRecord {

    /** 审核任务 ID（本地生成 UUID） */
    private String taskId;

    /** 关联 WISE 知识 ID */
    private String knowledgeId;

    /** 触发原因（如"连续 3 次负反馈"） */
    private String triggerReason;

    /** 关联问题 */
    private String question;

    /** 关联知识来源 */
    private String knowledgeRefs;

    /** 负反馈次数 */
    private int negativeCount;

    /** 状态：待审核 / 已处理 / 忽略 */
    private String status;

    /** 处理人 */
    private String owner;

    /** 创建时间（毫秒时间戳） */
    private long createdAt;
}
