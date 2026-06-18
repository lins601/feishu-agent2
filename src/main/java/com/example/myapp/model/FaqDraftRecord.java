package com.example.myapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FAQ 草稿记录 — 对应飞书多维表格 faq_draft。
 * <p>
 * 系统只生成草稿，管理员确认并补全答案后再入库 WISE FAQ。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaqDraftRecord {

    /** 飞书多维表格记录 ID，用于状态回写 */
    private String recordId;

    /** FAQ 草稿主键 */
    private String draftId;

    /** 来源类型：未命中 / 负反馈 / 高频问题 */
    private String sourceType;

    /** 来源记录 ID：miss_question recordId 或 knowledge_review task_id */
    private String sourceId;

    /** 标准问 */
    private String standardQuestion;

    /** 相似问，按换行或分号分隔 */
    private String similarQuestions;

    /** 管理员确认后的正式答案 */
    private String answer;

    /** 关联知识来源 */
    private String knowledgeRefs;

    /** 草稿置信度 */
    private double confidence;

    /** 待确认 / 待入库 / 已入库 / 入库失败 / 退回 / 忽略 */
    private String status;

    /** 负责人 */
    private String owner;

    /** 创建时间（毫秒时间戳） */
    private long createdAt;

    /** 更新时间（毫秒时间戳） */
    private long updatedAt;

    /** WISE FAQ 入库后的 ID */
    private String wiseFaqId;

    /** 失败原因 */
    private String errorMessage;
}
