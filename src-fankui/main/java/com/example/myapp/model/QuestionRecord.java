package com.example.myapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 问答记录 — 对应飞书多维表格 qa_record。
 * <p>
 * 每次机器人回复答案后写入，用于追踪问答历史、关联用户反馈和未命中统计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionRecord {

    /** 问答记录主键（本地生成 UUID） */
    private String qaId;

    /** 飞书消息 ID */
    private String messageId;

    /** 群聊 ID */
    private String chatId;

    /** 用户 open_id */
    private String userId;

    /** 原始问题 */
    private String question;

    /** 答案摘要（截断前 200 字） */
    private String answerSummary;

    /** WISE 会话 ID */
    private String sessionId;

    /** 知识来源（WISE 文档标题 / FAQ 标题） */
    private String knowledgeRefs;

    /** 是否兜底回复 */
    private boolean fallback;

    /** 置信度（0-1） */
    private double confidence;

    /** 响应耗时（毫秒） */
    private long responseTimeMs;

    /** 创建时间（毫秒时间戳） */
    private long createdAt;
}
