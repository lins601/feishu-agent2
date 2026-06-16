package com.example.myapp.service;

import com.example.myapp.model.FeedbackRecord;
import com.example.myapp.model.MissRecord;
import com.example.myapp.model.QuestionRecord;
import com.example.myapp.model.ReviewRecord;
import com.example.myapp.model.SyncRecord;
import com.lark.oapi.Client;
import com.lark.oapi.service.bitable.v1.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 飞书多维表格服务 — 写入运营记录。
 * <p>
 * 负责写入 5 张表：
 * qa_record（问答记录）、feedback_record（用户反馈）、
 * miss_question（未命中问题）、knowledge_review（知识审核任务）、
 * sync_record（MinDoc 同步记录）。
 * <p>
 * 异常兜底策略：
 * - 表格写入失败：重试 3 次
 * - 字段不存在：记录日志告警管理员
 * - 权限不足：记录日志告警管理员检查权限
 * - 未命中问题重复：更新 count
 * - 反馈无法关联 qa_id：回复"未找到可反馈的答案"
 * - 表格不可用：不影响用户回复，只记录错误日志
 */
@Service
public class LarkBitableService {

    private static final Logger log = LoggerFactory.getLogger(LarkBitableService.class);

    /** 负反馈触发审核的阈值 */
    private static final int NEGATIVE_FEEDBACK_THRESHOLD = 3;

    /** 写入重试次数 */
    private static final int MAX_RETRIES = 3;

    /** 时间戳格式化（多维表格 created_at 列为文本类型） */
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final Client feishuClient;

    @Value("${feishu.bitable.app-token:}")
    private String appToken;

    @Value("${feishu.bitable.qa-table-id:}")
    private String qaTableId;

    @Value("${feishu.bitable.feedback-table-id:}")
    private String feedbackTableId;

    @Value("${feishu.bitable.miss-table-id:}")
    private String missTableId;

    @Value("${feishu.bitable.review-table-id:}")
    private String reviewTableId;

    @Value("${feishu.bitable.sync-table-id:}")
    private String syncTableId;

    public LarkBitableService(Client feishuClient) {
        this.feishuClient = feishuClient;
    }

    // ─── 问答记录 (qa_record) ─────────────────────────────

    /**
     * 写入问答记录到 qa_record 表。
     */
    @Async
    public void saveQuestionRecord(QuestionRecord record) {
        if (!isConfigured(qaTableId)) {
            log.warn("qa_record 表未配置，跳过写入");
            return;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("qa_id", record.getQaId());
        fields.put("message_id", record.getMessageId());
        fields.put("chat_id", record.getChatId());
        fields.put("user_id", record.getUserId());
        fields.put("question", record.getQuestion());
        fields.put("answer_summary", record.getAnswerSummary());
        fields.put("session_id", record.getSessionId());
        fields.put("knowledge_refs", record.getKnowledgeRefs());
        fields.put("is_fallback", record.isFallback());
        fields.put("confidence", record.getConfidence());
        fields.put("response_time_ms", record.getResponseTimeMs());
        fields.put("created_at", formatTimestamp(record.getCreatedAt()));

        createRecord(qaTableId, fields, "qa_record");
    }

    // ─── 反馈记录 (feedback_record) ────────────────────────

    /**
     * 处理用户反馈。
     * <p>
     * 1. 检查是否已反馈（防重复）
     * 2. 写入 feedback_record 表
     * 3. 若为负反馈，查询累计负反馈次数
     * 4. 达到阈值则写入 knowledge_review 表
     */
    @Async
    public void handleFeedback(FeedbackRecord record) {
        if (!isConfigured(feedbackTableId)) {
            log.warn("feedback_record 表未配置，跳过写入");
            return;
        }

        try {
            // 1. 检查是否已反馈（同一 qa_id + user_id 只记录一次）
            if (hasExistingFeedback(record.getQaId(), record.getUserId())) {
                log.info("用户已反馈过该问答，跳过: qa_id={}, user={}", record.getQaId(), record.getUserId());
                return;
            }

            // 2. 写入 feedback_record
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("feedback_id", record.getFeedbackId());
            fields.put("qa_id", record.getQaId());
            fields.put("user_id", record.getUserId());
            fields.put("question", record.getQuestion());
            fields.put("answer_summary", record.getAnswerSummary());
            fields.put("knowledge_refs", record.getKnowledgeRefs());
            fields.put("feedback", feedbackLabel(record.getFeedback()));
            fields.put("created_at", formatTimestamp(record.getCreatedAt()));

            createRecord(feedbackTableId, fields, "feedback_record");

            // 3. 负反馈处理：累计次数 → 审核任务
            if ("useless".equals(record.getFeedback())) {
                int negativeCount = countNegativeFeedback(record);
                log.info("负反馈累计: qa_id={}, knowledge_refs={}, count={}",
                        record.getQaId(), record.getKnowledgeRefs(), negativeCount);

                if (negativeCount == NEGATIVE_FEEDBACK_THRESHOLD) {
                    createReviewTask(record, negativeCount);
                }
            }
        } catch (Exception e) {
            log.error("处理反馈失败: qa_id={}, feedback={}", record.getQaId(), record.getFeedback(), e);
        }
    }

    // ─── 未命中问题 (miss_question) ────────────────────────

    /**
     * 写入未命中问题到 miss_question 表。
     * <p>
     * 如果相同问题已存在，则更新 count + 1 和 updated_at。
     */
    @Async
    public void saveMissRecord(MissRecord record) {
        if (!isConfigured(missTableId)) {
            log.warn("miss_question 表未配置，跳过写入");
            return;
        }

        try {
            AppTableRecord existing = findMissRecord(record.getNormalizedQuestion());
            if (existing != null) {
                int nextCount = readInt(existing.getFields().get("count"), 0) + 1;
                Map<String, Object> updateFields = new LinkedHashMap<>();
                updateFields.put("count", nextCount);
                updateFields.put("updated_at", formatTimestamp(record.getUpdatedAt()));

                updateRecord(missTableId, existing.getRecordId(), updateFields, "miss_question");
                log.info("更新未命中问题累计次数: normalized_question={}, count={}",
                        record.getNormalizedQuestion(), nextCount);
                return;
            }

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("question", record.getQuestion());
            fields.put("normalized_question", record.getNormalizedQuestion());
            fields.put("count", Math.max(1, record.getCount()));
            fields.put("status", record.getStatus());
            fields.put("user_id", record.getUserId());
            fields.put("chat_id", record.getChatId());
            fields.put("owner", record.getOwner());
            fields.put("created_at", formatTimestamp(record.getCreatedAt()));
            fields.put("updated_at", formatTimestamp(record.getUpdatedAt()));

            createRecord(missTableId, fields, "miss_question");
        } catch (Exception e) {
            log.error("处理未命中问题失败: normalized_question={}", record.getNormalizedQuestion(), e);
        }
    }

    // ─── 知识审核任务 (knowledge_review) ──────────────────

    /**
     * 创建审核任务（由 handleFeedback 内部调用，负反馈达到阈值时触发）。
     */
    private void createReviewTask(FeedbackRecord feedback, int negativeCount) {
        if (!isConfigured(reviewTableId)) {
            log.warn("knowledge_review 表未配置，跳过审核任务创建");
            return;
        }

        ReviewRecord review = ReviewRecord.builder()
                .taskId(generateId("review"))
                .knowledgeId("")
                .triggerReason("连续 " + negativeCount + " 次负反馈")
                .question(feedback.getQuestion())
                .knowledgeRefs(feedback.getKnowledgeRefs())
                .negativeCount(negativeCount)
                .status("待审核")
                .owner("")
                .createdAt(Instant.now().toEpochMilli())
                .build();

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("task_id", review.getTaskId());
        fields.put("knowledge_id", review.getKnowledgeId());
        fields.put("trigger_reason", review.getTriggerReason());
        fields.put("question", review.getQuestion());
        fields.put("knowledge_refs", review.getKnowledgeRefs());
        fields.put("negative_count", review.getNegativeCount());
        fields.put("status", review.getStatus());
        fields.put("owner", review.getOwner());
        fields.put("created_at", formatTimestamp(review.getCreatedAt()));

        createRecord(reviewTableId, fields, "knowledge_review");
        log.info("创建知识审核任务: task_id={}, negative_count={}", review.getTaskId(), negativeCount);
    }

    // ─── MinDoc 同步记录 (sync_record) ───────────────────

    /**
     * 写入 MinDoc 同步记录到 sync_record 表。
     * <p>
     * 由 KnowledgeSyncService 在定时爬取任务中调用，
     * 记录每篇文档的爬取、转换、上传 WISE 的全链路状态。
     */
    @Async
    public void saveSyncRecord(SyncRecord record) {
        if (!isConfigured(syncTableId)) {
            log.warn("sync_record 表未配置，跳过写入");
            return;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("doc_id", record.getDocId());
        fields.put("url", record.getUrl());
        fields.put("title", record.getTitle());
        fields.put("md_hash", record.getMdHash());
        fields.put("wise_file_id", record.getWiseFileId());
        fields.put("sync_status", record.getSyncStatus());
        fields.put("parse_status", record.getParseStatus());
        fields.put("update_time", formatTimestamp(record.getUpdateTime()));
        fields.put("error_message", record.getErrorMessage());

        createRecord(syncTableId, fields, "sync_record");
    }

    // ─── 内部方法 ──────────────────────────────────────

    /**
     * 检查用户是否已对某问答提交过反馈。
     */
    private boolean hasExistingFeedback(String qaId, String userId) {
        try {
            SearchAppTableRecordReq req = SearchAppTableRecordReq.newBuilder()
                    .appToken(appToken)
                    .tableId(feedbackTableId)
                    .pageSize(1)
                    .searchAppTableRecordReqBody(SearchAppTableRecordReqBody.newBuilder()
                            .filter(FilterInfo.newBuilder()
                                    .conjunction("and")
                                    .conditions(new Condition[]{
                                            Condition.newBuilder()
                                                    .fieldName("qa_id")
                                                    .operator("is")
                                                    .value(new String[]{qaId})
                                                    .build(),
                                            Condition.newBuilder()
                                                    .fieldName("user_id")
                                                    .operator("is")
                                                    .value(new String[]{userId})
                                                    .build()
                                    })
                                    .build())
                            .build())
                    .build();

            SearchAppTableRecordResp resp = feishuClient.bitable().appTableRecord().search(req);
            if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
                return resp.getData().getTotal() != null && resp.getData().getTotal() > 0;
            }
        } catch (Exception e) {
            log.warn("查询已有反馈失败，默认放行: qa_id={}, user={}", qaId, userId, e);
        }
        return false;
    }

    /**
     * 查询某问答的累计负反馈次数。
     */
    private int countNegativeFeedback(FeedbackRecord record) {
        try {
            boolean countByKnowledge = record.getKnowledgeRefs() != null && !record.getKnowledgeRefs().isBlank();
            String fieldName = countByKnowledge ? "knowledge_refs" : "qa_id";
            String fieldValue = countByKnowledge ? record.getKnowledgeRefs() : record.getQaId();

            SearchAppTableRecordReq req = SearchAppTableRecordReq.newBuilder()
                    .appToken(appToken)
                    .tableId(feedbackTableId)
                    .pageSize(200)
                    .searchAppTableRecordReqBody(SearchAppTableRecordReqBody.newBuilder()
                            .filter(FilterInfo.newBuilder()
                                    .conjunction("and")
                                    .conditions(new Condition[]{
                                            Condition.newBuilder()
                                                    .fieldName(fieldName)
                                                    .operator("is")
                                                    .value(new String[]{fieldValue})
                                                    .build()
                                    })
                                    .build())
                            .build())
                    .build();

            SearchAppTableRecordResp resp = feishuClient.bitable().appTableRecord().search(req);
            if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
                int count = 0;
                AppTableRecord[] items = resp.getData().getItems();
                if (items != null) {
                    for (AppTableRecord item : items) {
                        Object feedback = item.getFields() != null ? item.getFields().get("feedback") : null;
                        if (isNegativeFeedback(feedback)) {
                            count++;
                        }
                    }
                }
                return count;
            }
        } catch (Exception e) {
            log.warn("查询负反馈次数失败: qa_id={}, knowledge_refs={}",
                    record.getQaId(), record.getKnowledgeRefs(), e);
        }
        return 0;
    }

    private AppTableRecord findMissRecord(String normalizedQuestion) {
        try {
            SearchAppTableRecordReq req = SearchAppTableRecordReq.newBuilder()
                    .appToken(appToken)
                    .tableId(missTableId)
                    .pageSize(1)
                    .searchAppTableRecordReqBody(SearchAppTableRecordReqBody.newBuilder()
                            .filter(FilterInfo.newBuilder()
                                    .conjunction("and")
                                    .conditions(new Condition[]{
                                            Condition.newBuilder()
                                                    .fieldName("normalized_question")
                                                    .operator("is")
                                                    .value(new String[]{normalizedQuestion})
                                                    .build()
                                    })
                                    .build())
                            .build())
                    .build();

            SearchAppTableRecordResp resp = feishuClient.bitable().appTableRecord().search(req);
            if (resp != null && resp.getCode() == 0 && resp.getData() != null
                    && resp.getData().getItems() != null && resp.getData().getItems().length > 0) {
                return resp.getData().getItems()[0];
            }
        } catch (Exception e) {
            log.warn("查询未命中问题失败，改为新增: normalized_question={}", normalizedQuestion, e);
        }
        return null;
    }

    /**
     * 通用：创建多维表格记录，失败时重试 3 次。
     * <p>
     * 表格不可用时只记录错误日志，不影响用户问答体验。
     */
    private void createRecord(String tableId, Map<String, Object> fields, String tableName) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                AppTableRecord record = AppTableRecord.newBuilder()
                        .fields(fields)
                        .build();

                CreateAppTableRecordReq req = CreateAppTableRecordReq.newBuilder()
                        .appToken(appToken)
                        .tableId(tableId)
                        .appTableRecord(record)
                        .build();

                CreateAppTableRecordResp resp = feishuClient.bitable().appTableRecord().create(req);
                if (resp != null && resp.getCode() == 0) {
                    log.info("写入 {} 成功", tableName);
                    return;
                }

                // 飞书 API 返回错误码
                int code = resp != null ? resp.getCode() : -1;
                String msg = resp != null ? resp.getMsg() : "null";

                // 字段不存在 / 权限不足 / 字段类型不匹配 → 不重试，直接告警
                if (code == 1254043 || code == 1254040 || code == 99991663 || code == 1254060) {
                    log.error("写入 {} 失败（字段或权限问题，不重试）: code={}, msg={}, table={}, fields={}",
                            tableName, code, msg, tableId, fields);
                    return;
                }

                log.warn("写入 {} 失败（第 {}/{} 次）: code={}, msg={}",
                        tableName, attempt, MAX_RETRIES, code, msg);

            } catch (Exception e) {
                log.warn("写入 {} 异常（第 {}/{} 次）", tableName, attempt, MAX_RETRIES, e);
            }

            // 重试间隔：指数退避
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        log.error("写入 {} 最终失败（已重试 {} 次），表格可能不可用", tableName, MAX_RETRIES);
    }

    private void updateRecord(String tableId, String recordId, Map<String, Object> fields, String tableName) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                AppTableRecord record = AppTableRecord.newBuilder()
                        .fields(fields)
                        .build();

                UpdateAppTableRecordReq req = UpdateAppTableRecordReq.newBuilder()
                        .appToken(appToken)
                        .tableId(tableId)
                        .recordId(recordId)
                        .appTableRecord(record)
                        .build();

                UpdateAppTableRecordResp resp = feishuClient.bitable().appTableRecord().update(req);
                if (resp != null && resp.getCode() == 0) {
                    log.info("更新 {} 成功: recordId={}", tableName, recordId);
                    return;
                }

                int code = resp != null ? resp.getCode() : -1;
                String msg = resp != null ? resp.getMsg() : "null";
                if (code == 1254043 || code == 1254040 || code == 99991663 || code == 1254060) {
                    log.error("更新 {} 失败（字段或权限问题，不重试）: code={}, msg={}, table={}, recordId={}, fields={}",
                            tableName, code, msg, tableId, recordId, fields);
                    return;
                }

                log.warn("更新 {} 失败（第 {}/{} 次）: code={}, msg={}",
                        tableName, attempt, MAX_RETRIES, code, msg);
            } catch (Exception e) {
                log.warn("更新 {} 异常（第 {}/{} 次）", tableName, attempt, MAX_RETRIES, e);
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        log.error("更新 {} 最终失败（已重试 {} 次），表格可能不可用", tableName, MAX_RETRIES);
    }

    private boolean isConfigured(String tableId) {
        return appToken != null && !appToken.isEmpty()
                && tableId != null && !tableId.isEmpty();
    }

    private String feedbackLabel(String feedback) {
        if ("useful".equals(feedback) || "有用".equals(feedback) || "👍".equals(feedback)) {
            return "有用";
        }
        if ("useless".equals(feedback) || "没用".equals(feedback) || "👎".equals(feedback)) {
            return "没用";
        }
        return feedback;
    }

    private boolean isNegativeFeedback(Object value) {
        String text = String.valueOf(value);
        return text.contains("useless") || text.contains("没用") || text.contains("👎");
    }

    private int readInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).replaceAll("\\.0$", ""));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    /**
     * 生成本地唯一 ID。
     */
    public static String generateId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
    }

    /**
     * 将毫秒时间戳格式化为可读字符串（多维表格 created_at 列为文本类型）。
     */
    private static String formatTimestamp(long epochMilli) {
        return TIMESTAMP_FMT.format(Instant.ofEpochMilli(epochMilli));
    }
}
