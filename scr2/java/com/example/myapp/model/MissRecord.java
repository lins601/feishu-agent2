package com.example.myapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 未命中问题记录 — 对应飞书多维表格 miss_question。
 * <p>
 * 当机器人无法返回有效答案时写入，用于后续知识补充和 FAQ 沉淀。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissRecord {

    /** 原始问题 */
    private String question;

    /** 标准化后的问题（去语气词、统一表述） */
    private String normalizedQuestion;

    /** 出现次数 */
    private int count;

    /** 状态：待补充 / 已补充 / 忽略 */
    private String status;

    /** 首次提问用户 open_id */
    private String userId;

    /** 群聊 ID */
    private String chatId;

    /** 负责人 */
    private String owner;

    /** 首次出现时间（毫秒时间戳） */
    private long createdAt;

    /** 最近出现时间（毫秒时间戳） */
    private long updatedAt;
}
