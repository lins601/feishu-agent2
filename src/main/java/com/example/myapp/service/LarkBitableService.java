package com.example.myapp.service;

import com.example.myapp.model.FeedbackRecord;
import com.example.myapp.model.FaqDraftRecord;
import com.example.myapp.model.MissRecord;
import com.example.myapp.model.MindocDocumentMapping;
import com.example.myapp.model.MindocSyncConfig;
import com.example.myapp.model.QuestionRecord;
import com.example.myapp.model.ReviewRecord;
import com.example.myapp.model.SyncRecord;
import com.lark.oapi.Client;
import com.lark.oapi.service.bitable.v1.enums.AppTableFieldTypeEnum;
import com.lark.oapi.service.bitable.v1.enums.AppTableFieldUiTypeEnum;
import com.lark.oapi.service.bitable.v1.enums.AppTableCreateHeaderTypeEnum;
import com.lark.oapi.service.bitable.v1.enums.AppTableCreateHeaderUiTypeEnum;
import com.lark.oapi.service.bitable.v1.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final String WISE_MISS_SYSTEM_PROMPT = "当前知识库中暂未收录该问题。\n\n"
            + "为避免误导产线操作，本次不直接给出处理步骤。\n"
            + "已记录您的问题，并提交管理员补充。";

    private final Client feishuClient;
    private final FeishuAdminNotifier adminNotifier;
    private final Map<String, Integer> negativeFeedbackCountCache = new ConcurrentHashMap<>();

    @Value("${feishu.bitable.app-token:}")
    private String appToken;

    @Value("${feishu.bitable.operation-app-token:${feishu.bitable.app-token:}}")
    private String operationAppToken;

    @Value("${feishu.bitable.miss-app-token:${feishu.bitable.operation-app-token:${feishu.bitable.app-token:}}}")
    private String missAppToken;

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

    @Value("${feishu.bitable.faq-draft-table-id:}")
    private String faqDraftTableId;

    @Value("${feishu.bitable.mindoc-mapping-table-id:}")
    private String mindocMappingTableId;

    @Value("${operation.report.enabled:true}")
    private boolean operationReportEnabled;

    @Value("${operation.report.zone:Asia/Shanghai}")
    private String operationReportZone;

    @Value("${operation.report.archive-enabled:true}")
    private boolean operationReportArchiveEnabled;

    @Value("${operation.report.archive-dir:data/reports}")
    private String operationReportArchiveDir;

    @Value("${operation.report.weekly-cron:0 0 9 ? * MON}")
    private String weeklyReportCron;

    @Value("${operation.report.monthly-cron:0 0 9 1 * ?}")
    private String monthlyReportCron;

    @Value("${operation.miss.top-notify-threshold:3}")
    private int missTopNotifyThreshold;

    @Value("${feishu.feedback.review-reconcile.enabled:true}")
    private boolean reviewReconcileEnabled;

    public LarkBitableService(Client feishuClient) {
        this(feishuClient, null);
    }

    @Autowired
    public LarkBitableService(Client feishuClient, FeishuAdminNotifier adminNotifier) {
        this.feishuClient = feishuClient;
        this.adminNotifier = adminNotifier;
    }

    private record FeedbackAggregate(FeedbackRecord feedback, int count) {
        FeedbackAggregate(FeedbackRecord feedback) {
            this(feedback, 0);
        }

        FeedbackAggregate increment() {
            return new FeedbackAggregate(feedback, count + 1);
        }
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
        // 飞书多维表格的首列为主字段，当前表结构中名称为“多行文本”。
        fields.put("多行文本", record.getQuestion());
        fields.put("问答ID", record.getQaId());
        fields.put("消息ID", record.getMessageId());
        fields.put("群聊ID", record.getChatId());
        fields.put("用户ID", record.getUserId());
        fields.put("问题", record.getQuestion());
        fields.put("问答摘要", record.getAnswerSummary());
        fields.put("会话ID", record.getSessionId());
        fields.put("知识来源", record.getKnowledgeRefs());
        fields.put("是否兜底", record.isFallback());
        fields.put("置信度", record.getConfidence());
        fields.put("响应耗时ms", record.getResponseTimeMs());
        fields.put("创建时间", formatTimestamp(record.getCreatedAt()));

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
            // feedback_record 的主字段为“多行文本”，未传该字段会导致飞书拒绝创建记录。
            fields.put("多行文本", record.getQuestion());
            fields.put("反馈ID", record.getFeedbackId());
            fields.put("问答ID", record.getQaId());
            fields.put("用户ID", record.getUserId());
            fields.put("问题", record.getQuestion());
            fields.put("问答摘要", record.getAnswerSummary());
            fields.put("知识来源", record.getKnowledgeRefs());
            fields.put("反馈", feedbackLabel(record.getFeedback()));
            fields.put("创建时间", formatTimestamp(record.getCreatedAt()));

            createRecord(feedbackTableId, fields, "feedback_record");

            // 3. 负反馈处理：累计次数 → 审核任务
            if ("useless".equals(record.getFeedback())) {
                int negativeCount = effectiveNegativeCount(record, countNegativeFeedback(record));
                log.info("负反馈累计: qa_id={}, group={}, count={}",
                        record.getQaId(), negativeFeedbackGroupKey(record), negativeCount);

                if (negativeCount >= NEGATIVE_FEEDBACK_THRESHOLD) {
                    createReviewTask(record, negativeCount);
                }
                notifyNegativeFeedback(record, negativeCount);
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
        if (!isConfigured(missAppToken, missTableId)) {
            log.warn("miss_question 表未配置，跳过写入");
            return;
        }

        try {
            log.info("准备写入未命中表: appToken={}, tableId={}, question={}",
                    maskToken(missAppToken), missTableId, record.getQuestion());
            AppTableRecord existing = findMissRecord(record.getNormalizedQuestion());
            if (existing != null) {
                Map<String, Object> updateFields = new LinkedHashMap<>();
                int oldCount = readInt(firstFieldValue(existing.getFields(), "出现次数", "count"), 0);
                int nextCount = oldCount + 1;
                updateFields.put("出现次数", String.valueOf(nextCount));
                updateFields.put("最近出现时间", formatTimestamp(record.getUpdatedAt()));

                updateRecord(missAppToken, missTableId, existing.getRecordId(), updateFields, "miss_question");
                log.info("更新未命中问题累计次数: normalized_question={}, count={}",
                        record.getNormalizedQuestion(), nextCount);
                notifyTopMissQuestion(record.getQuestion(), nextCount);
                return;
            }

            Map<String, Object> fields = missRecordFields(record);
            createRecord(missAppToken, missTableId, fields, "miss_question");
            notifyTopMissQuestion(record.getQuestion(), Math.max(1, record.getCount()));
        } catch (Exception e) {
            log.error("处理未命中问题失败: normalized_question={}", record.getNormalizedQuestion(), e);
        }
    }

    public Map<String, Object> debugCreateMissRecord(MissRecord record) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetAppToken", maskToken(missAppToken));
        result.put("targetTableId", missTableId);
        if (!isConfigured(missAppToken, missTableId)) {
            result.put("success", false);
            result.put("error", "miss_question 表未配置");
            return result;
        }

        Map<String, Object> fields = missRecordFields(record);
        result.put("fields", fields);
        try {
            AppTableRecord appTableRecord = AppTableRecord.newBuilder()
                    .fields(fields)
                    .build();
            CreateAppTableRecordReq req = CreateAppTableRecordReq.newBuilder()
                    .appToken(missAppToken)
                    .tableId(missTableId)
                    .appTableRecord(appTableRecord)
                    .build();
            CreateAppTableRecordResp resp = feishuClient.bitable().appTableRecord().create(req);
            int code = resp != null ? resp.getCode() : -1;
            result.put("success", resp != null && code == 0);
            result.put("code", code);
            result.put("msg", resp != null ? resp.getMsg() : "null response");
            if (resp != null && resp.getData() != null && resp.getData().getRecord() != null) {
                result.put("recordId", resp.getData().getRecord().getRecordId());
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("exception", e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("调试写入未命中表异常: fields={}", fields, e);
        }
        return result;
    }

    private Map<String, Object> missRecordFields(MissRecord record) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("问题", record.getQuestion());
        fields.put("标准化问题", record.getNormalizedQuestion());
        fields.put("出现次数", String.valueOf(Math.max(1, record.getCount())));
        fields.put("状态", record.getStatus());
        fields.put("首次提问用户", record.getUserId());
        fields.put("群聊", record.getChatId());
        fields.put("系统提示", WISE_MISS_SYSTEM_PROMPT);
        fields.put("负责人", record.getOwner());
        fields.put("首次出现时间", formatTimestamp(record.getCreatedAt()));
        fields.put("最近出现时间", formatTimestamp(record.getUpdatedAt()));
        return fields;
    }

    private String maskToken(String value) {
        if (value == null || value.isBlank()) {
            return "未配置";
        }
        if (value.length() <= 8) {
            return value.charAt(0) + "***" + value.charAt(value.length() - 1);
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }

    // ─── MinDoc 文档级映射表 (mindoc_document_mapping) ───────────────

    public void upsertMindocDocumentMappings(List<MindocDocumentMapping> mappings) {
        String mappingTableId = resolveMindocMappingTableId(operationAppToken);
        if (!isConfigured(operationAppToken, mappingTableId)) {
            log.warn("mindoc_document_mapping 表未配置，跳过文档映射回写");
            return;
        }
        if (mappings == null || mappings.isEmpty()) {
            return;
        }

        int success = 0;
        for (MindocDocumentMapping mapping : mappings) {
            if (mapping == null || mapping.getDocumentKey() == null || mapping.getDocumentKey().isBlank()) {
                continue;
            }
            try {
                AppTableRecord existing = findMindocMapping(mappingTableId, mapping.getDocumentKey());
                Map<String, Object> fields = mindocMappingFields(mapping);
                if (existing == null) {
                    createRecord(operationAppToken, mappingTableId, fields, "mindoc_document_mapping");
                } else {
                    updateRecord(operationAppToken, mappingTableId,
                            existing.getRecordId(), fields, "mindoc_document_mapping");
                }
                success++;
                if ("failed".equalsIgnoreCase(mapping.getParseStatus())
                        || "parse_failed".equalsIgnoreCase(mapping.getSyncStatus())) {
                    notifyWiseParseFailure(mapping);
                }
            } catch (Exception e) {
                log.warn("回写 MinDoc 文档映射失败: documentKey={}", mapping.getDocumentKey(), e);
            }
        }
        log.info("MinDoc 文档映射回写完成: total={}, success={}", mappings.size(), success);
    }

    private AppTableRecord findMindocMapping(String mappingTableId, String documentKey) {
        try {
            for (AppTableRecord item : searchRecords(operationAppToken, mappingTableId, null, 500)) {
                String existingKey = fieldTextAny(item.getFields(), "文档唯一键", "document_key");
                if (documentKey.equals(existingKey)) {
                    return item;
                }
            }
        } catch (Exception e) {
            log.debug("查询 MinDoc 文档映射失败，改为新增: documentKey={}", documentKey, e);
        }
        return null;
    }

    private Map<String, Object> mindocMappingFields(MindocDocumentMapping m) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("映射ID", firstNonBlank(String.valueOf(m.getMappingId()), m.getDocumentKey()));
        fields.put("项目名称", m.getProjectName());
        fields.put("文档唯一键", m.getDocumentKey());
        fields.put("原始URL", m.getSourceUrl());
        fields.put("规范化URL", m.getNormalizedUrl());
        fields.put("URL哈希", m.getUrlHash());
        fields.put("MinDoc文档ID", m.getDocId());
        fields.put("文档标题", m.getTitle());
        fields.put("Markdown哈希", m.getMdHash());
        fields.put("WISE文件ID", m.getWiseFileId());
        fields.put("旧WISE文件ID", m.getOldWiseFileId());
        fields.put("同步状态", m.getSyncStatus());
        fields.put("解析状态", m.getParseStatus());
        fields.put("最近发现时间", m.getLastSeenTime());
        fields.put("最近同步时间", m.getLastSyncTime());
        fields.put("错误信息", m.getErrorMessage());
        fields.put("是否失效", Boolean.TRUE.equals(m.getExpired()));
        return fields;
    }

    // ─── 运营周报 / 月报 ─────────────────────────────────────

    @Scheduled(cron = "${operation.report.weekly-cron:0 0 9 ? * MON}",
            zone = "${operation.report.zone:Asia/Shanghai}")
    public void sendWeeklyReport() {
        if (!operationReportEnabled || adminNotifier == null) {
            return;
        }
        String report = buildOperationReport(7, "周报");
        archiveOperationReport(report, "weekly");
        adminNotifier.notifyWeeklyReport(report);
    }

    @Scheduled(cron = "${operation.report.monthly-cron:0 0 9 1 * ?}",
            zone = "${operation.report.zone:Asia/Shanghai}")
    public void sendMonthlyReport() {
        if (!operationReportEnabled || adminNotifier == null) {
            return;
        }
        String report = buildOperationReport(30, "月报");
        archiveOperationReport(report, "monthly");
        adminNotifier.notifyMonthlyReport(report);
    }

    String buildOperationReport(int days, String reportType) {
        ZoneId zone = reportZone();
        LocalDate today = LocalDate.now(zone);
        LocalDate start = today.minusDays(Math.max(1, days) - 1L);
        List<AppTableRecord> qaRecords = safeSearch(appToken, qaTableId);
        List<AppTableRecord> feedbackRecords = safeSearch(appToken, feedbackTableId);
        List<AppTableRecord> missRecords = safeSearch(missAppToken, missTableId);
        List<AppTableRecord> reviewRecords = safeSearch(operationAppToken, reviewTableId);
        List<AppTableRecord> syncRecords = safeSearch(operationAppToken, syncTableId);

        int totalQa = countRecordsInRange(qaRecords, start);
        int fallbackQa = countQaFallbackInRange(qaRecords, start);
        int hitQa = Math.max(0, totalQa - fallbackQa);
        int useful = countFeedbackInRange(feedbackRecords, start, false);
        int useless = countFeedbackInRange(feedbackRecords, start, true);
        int pendingReview = countStatus(reviewRecords, "状态", "待审核");
        int syncFailed = countStatus(syncRecords, "同步状态", "failed");
        int syncSuccess = countStatus(syncRecords, "同步状态", "success", "completed");
        List<String> topMiss = topMissQuestions(missRecords, 3);

        int week = today.get(WeekFields.ISO.weekOfWeekBasedYear());
        String title = "周报".equals(reportType)
                ? "制造知识助手周报 · 第 " + week + " 周（" + start + " - " + today + "）"
                : "制造知识助手月报（" + start + " - " + today + "）";

        return title + "\n\n"
                + "本期问答统计：\n"
                + "  · 总提问：" + totalQa + " 次\n"
                + "  · 命中：" + hitQa + " 次（" + percent(hitQa, totalQa) + "）\n"
                + "  · 未命中：" + fallbackQa + " 次（" + percent(fallbackQa, totalQa) + "）\n"
                + "  · 用户反馈：有用 " + useful + " 次 / 没用 " + useless + " 次\n\n"
                + "同步与巡检：\n"
                + "  · 同步成功：" + syncSuccess + " 条\n"
                + "  · 同步失败：" + syncFailed + " 条\n"
                + "  · pending_review 条目：" + pendingReview + " 条\n\n"
                + "待处理：\n"
                + topMissText(topMiss);
    }

    private void archiveOperationReport(String report, String reportKind) {
        if (!operationReportArchiveEnabled || report == null || report.isBlank()) {
            return;
        }
        try {
            Path dir = Path.of(operationReportArchiveDir);
            Files.createDirectories(dir);
            String date = LocalDate.now(reportZone()).toString();
            Path target = dir.resolve(reportKind + "-" + date + ".md");
            Files.writeString(target, report + System.lineSeparator(), StandardCharsets.UTF_8);
            log.info("运营报表已归档: {}", target.toAbsolutePath());
        } catch (Exception e) {
            log.warn("运营报表归档失败: kind={}", reportKind, e);
        }
    }

    private ZoneId reportZone() {
        try {
            return ZoneId.of(firstNonBlank(operationReportZone, "Asia/Shanghai"));
        } catch (Exception e) {
            log.warn("运营报表时区配置无效，使用 Asia/Shanghai: {}", operationReportZone);
            return ZoneId.of("Asia/Shanghai");
        }
    }

    // ─── 知识审核任务 (knowledge_review) ──────────────────

    /**
     * 定时对账反馈表，避免飞书搜索延迟或字段结构差异导致审核任务漏建。
     */
    @Scheduled(initialDelayString = "${feishu.feedback.review-reconcile-initial-delay-ms:10000}",
            fixedDelayString = "${feishu.feedback.review-reconcile-interval-ms:60000}")
    public void reconcileReviewTasks() {
        if (!reviewReconcileEnabled || !isConfigured(appToken, feedbackTableId)
                || !isConfigured(operationAppToken, reviewTableId)) {
            return;
        }
        try {
            Map<String, FeedbackAggregate> groups = new HashMap<>();
            for (AppTableRecord item : searchRecords(feedbackTableId, null, 500)) {
                Map<String, Object> fields = item.getFields();
                if (fields == null || !isNegativeFeedback(firstFieldValue(fields, "反馈", "feedback"))) {
                    continue;
                }

                FeedbackRecord feedback = feedbackFromFields(fields);
                String groupKey = negativeFeedbackGroupKey(feedback);
                if (groupKey.isBlank() || "question:".equals(groupKey)) {
                    continue;
                }

                groups.compute(groupKey, (ignored, existing) ->
                        existing == null ? new FeedbackAggregate(feedback).increment()
                                : existing.increment());
            }

            for (FeedbackAggregate aggregate : groups.values()) {
                if (aggregate.count() >= NEGATIVE_FEEDBACK_THRESHOLD) {
                    negativeFeedbackCountCache.put(negativeFeedbackGroupKey(aggregate.feedback()),
                            aggregate.count());
                    createReviewTask(aggregate.feedback(), aggregate.count());
                }
            }
        } catch (Exception e) {
            log.warn("负反馈审核任务对账失败", e);
        }
    }

    /**
     * 创建审核任务（由 handleFeedback 内部调用，负反馈达到阈值时触发）。
     */
    private void createReviewTask(FeedbackRecord feedback, int negativeCount) {
        if (!isConfigured(operationAppToken, reviewTableId)) {
            log.warn("knowledge_review 表未配置，跳过审核任务创建");
            return;
        }
        if (hasExistingReviewTask(feedback)) {
            log.info("已存在待处理知识审核任务，跳过创建: group={}", negativeFeedbackGroupKey(feedback));
            return;
        }

        ReviewRecord review = ReviewRecord.builder()
                .taskId(generateId("review"))
                .knowledgeId("")
                .triggerReason(reviewTriggerReason(feedback, negativeCount))
                .question(feedback.getQuestion())
                .knowledgeRefs(feedback.getKnowledgeRefs())
                .negativeCount(negativeCount)
                .status("待审核")
                .owner("")
                .createdAt(Instant.now().toEpochMilli())
                .build();

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("任务ID", review.getTaskId());
        fields.put("知识ID", review.getKnowledgeId());
        fields.put("触发原因", review.getTriggerReason());
        fields.put("问题", review.getQuestion());
        fields.put("知识来源", review.getKnowledgeRefs());
        fields.put("负反馈次数", String.valueOf(review.getNegativeCount()));
        fields.put("状态", review.getStatus());
        fields.put("负责人", review.getOwner());
        fields.put("创建时间", formatTimestamp(review.getCreatedAt()));

        createRecord(operationAppToken, reviewTableId, fields, "knowledge_review");
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
        if (!isConfigured(operationAppToken, syncTableId)) {
            log.warn("sync_record 表未配置，跳过写入");
            return;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("文档ID", record.getDocId());
        fields.put("原始链接", record.getUrl());
        fields.put("标题", record.getTitle());
        fields.put("Markdown Hash", record.getMdHash());
        fields.put("WISE文件ID", record.getWiseFileId());
        fields.put("同步状态", record.getSyncStatus());
        fields.put("解析状态", record.getParseStatus());
        fields.put("更新时间", formatTimestamp(record.getUpdateTime()));
        fields.put("错误信息", record.getErrorMessage());

        createRecord(operationAppToken, syncTableId, fields, "sync_record");
    }

    /**
     * 从 sync_record 表读取管理员维护的 MinDoc 同步任务。
     */
    public List<MindocSyncConfig> listMindocSyncConfigs(String defaultKnowledgeBaseId) {
        if (!isConfigured(operationAppToken, syncTableId)) {
            log.warn("sync_record 表未配置，跳过读取 MinDoc 同步任务");
            return List.of();
        }

        try {
            List<MindocSyncConfig> configs = new ArrayList<>();
            for (AppTableRecord item : searchRecords(operationAppToken, syncTableId, null, 500)) {
                MindocSyncConfig config = mindocConfigFromFields(
                        item.getRecordId(), item.getFields(), defaultKnowledgeBaseId);
                if (config.getSourceUrl() == null || config.getSourceUrl().isBlank()) {
                    continue;
                }
                configs.add(config);
            }
            return configs;
        } catch (Exception e) {
            log.warn("读取 MinDoc 同步任务失败", e);
            return List.of();
        }
    }

    /**
     * 自动补齐 MinDoc 同步所需的飞书多维表格字段。
     * <p>
     * 不会删除或重命名已有字段，只创建缺失字段。管理员操作端表仍使用 {@code sync_record}，
     * 文档级映射写入 {@code mindoc_document_mapping}。
     */
    public Map<String, Object> ensureMindocSyncSchema() {
        Map<String, Object> result = new LinkedHashMap<>();
        String resolvedMappingTableId = resolveMindocMappingTableId(operationAppToken);
        result.put("syncTableId", syncTableId);
        result.put("mappingTableId", resolvedMappingTableId);
        result.put("syncRecord", ensureFields(operationAppToken, syncTableId, mindocSyncFields(), "sync_record"));
        result.put("mindocDocumentMapping", ensureFields(operationAppToken, resolvedMappingTableId,
                mindocMappingSchemaFields(), "mindoc_document_mapping"));
        return result;
    }

    /**
     * 回写 sync_record 行的运行状态。字段不存在或权限不足时只记录日志，不影响调度继续处理其他行。
     */
    public void updateMindocSyncStatus(String recordId, String syncStatus,
                                       String parseStatus, String errorMessage) {
        if (!isConfigured(operationAppToken, syncTableId)) {
            log.warn("sync_record 表未配置，跳过状态回写");
            return;
        }
        if (recordId == null || recordId.isBlank()) {
            log.warn("sync_record recordId 为空，跳过状态回写");
            return;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("同步状态", syncStatus);
        fields.put("解析状态", parseStatus);
        fields.put("更新时间", formatTimestamp(Instant.now().toEpochMilli()));
        fields.put("错误信息", errorMessage == null ? "" : errorMessage);

        updateRecord(operationAppToken, syncTableId, recordId, fields, "sync_record");
    }

    private Map<String, Object> ensureFields(String targetAppToken, String tableId,
                                             Map<String, AppTableFieldTypeEnum> expected,
                                             String tableName) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> created = new ArrayList<>();
        List<String> existing = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        result.put("created", created);
        result.put("existing", existing);
        result.put("failed", failed);

        if (!isConfigured(targetAppToken, tableId)) {
            result.put("status", "not_configured");
            return result;
        }

        try {
            Map<String, AppTableFieldForList> current = listFields(targetAppToken, tableId);
            for (Map.Entry<String, AppTableFieldTypeEnum> entry : expected.entrySet()) {
                String fieldName = entry.getKey();
                if (current.containsKey(fieldName)) {
                    existing.add(fieldName);
                    continue;
                }
                try {
                    createField(targetAppToken, tableId, fieldName, entry.getValue());
                    created.add(fieldName);
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("FieldNameDuplicated")) {
                        existing.add(fieldName);
                        continue;
                    }
                    failed.add(fieldName + ": " + e.getMessage());
                    log.warn("创建多维表字段失败: table={}, field={}", tableName, fieldName, e);
                }
            }
            result.put("status", failed.isEmpty() ? "success" : "partial_success");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
            log.warn("检查多维表字段失败: table={}", tableName, e);
        }
        return result;
    }

    private Map<String, AppTableFieldForList> listFields(String targetAppToken, String tableId) throws Exception {
        Map<String, AppTableFieldForList> fields = new LinkedHashMap<>();
        String pageToken = null;
        do {
            ListAppTableFieldReq req = ListAppTableFieldReq.newBuilder()
                    .appToken(targetAppToken)
                    .tableId(tableId)
                    .pageSize(100)
                    .pageToken(pageToken)
                    .build();
            ListAppTableFieldResp resp = feishuClient.bitable().appTableField().list(req);
            if (resp == null || resp.getCode() != 0) {
                throw new IllegalStateException("list fields failed: code="
                        + (resp != null ? resp.getCode() : "null") + ", msg="
                        + (resp != null ? resp.getMsg() : "null"));
            }
            if (resp.getData() != null && resp.getData().getItems() != null) {
                for (AppTableFieldForList item : resp.getData().getItems()) {
                    if (item.getFieldName() != null) {
                        fields.put(item.getFieldName(), item);
                    }
                }
                pageToken = Boolean.TRUE.equals(resp.getData().getHasMore())
                        ? resp.getData().getPageToken()
                        : null;
            } else {
                pageToken = null;
            }
        } while (pageToken != null && !pageToken.isBlank());
        return fields;
    }

    private void createField(String targetAppToken, String tableId, String fieldName,
                             AppTableFieldTypeEnum type) throws Exception {
        AppTableField.Builder field = AppTableField.newBuilder()
                .fieldName(fieldName)
                .type(type)
                .uiType(uiTypeFor(type));
        CreateAppTableFieldReq req = CreateAppTableFieldReq.newBuilder()
                .appToken(targetAppToken)
                .tableId(tableId)
                .appTableField(field.build())
                .build();
        CreateAppTableFieldResp resp = feishuClient.bitable().appTableField().create(req);
        if (resp == null || resp.getCode() != 0) {
            throw new IllegalStateException("create field failed: code="
                    + (resp != null ? resp.getCode() : "null") + ", msg="
                    + (resp != null ? resp.getMsg() : "null"));
        }
    }

    private AppTableFieldUiTypeEnum uiTypeFor(AppTableFieldTypeEnum type) {
        if (type == AppTableFieldTypeEnum.CHECKBOX) {
            return AppTableFieldUiTypeEnum.CHECKBOX;
        }
        return AppTableFieldUiTypeEnum.TEXT;
    }

    private String resolveMindocMappingTableId(String targetAppToken) {
        if (mindocMappingTableId != null && !mindocMappingTableId.isBlank()) {
            return mindocMappingTableId;
        }
        if (!isConfigured(targetAppToken)) {
            return "";
        }
        try {
            String existing = findTableIdByName(targetAppToken, "文档映射表");
            if (!existing.isBlank()) {
                mindocMappingTableId = existing;
                return existing;
            }
            String created = createTable(targetAppToken, "文档映射表");
            mindocMappingTableId = created;
            return created;
        } catch (Exception e) {
            log.warn("查找或创建文档映射表失败", e);
            return "";
        }
    }

    private String findTableIdByName(String targetAppToken, String tableName) throws Exception {
        String pageToken = null;
        do {
            ListAppTableReq req = ListAppTableReq.newBuilder()
                    .appToken(targetAppToken)
                    .pageSize(100)
                    .pageToken(pageToken)
                    .build();
            ListAppTableResp resp = feishuClient.bitable().appTable().list(req);
            if (resp == null || resp.getCode() != 0) {
                throw new IllegalStateException("list tables failed: code="
                        + (resp != null ? resp.getCode() : "null") + ", msg="
                        + (resp != null ? resp.getMsg() : "null"));
            }
            if (resp.getData() != null && resp.getData().getItems() != null) {
                for (AppTable table : resp.getData().getItems()) {
                    if (tableName.equals(table.getName()) && table.getTableId() != null) {
                        return table.getTableId();
                    }
                }
                pageToken = Boolean.TRUE.equals(resp.getData().getHasMore())
                        ? resp.getData().getPageToken()
                        : null;
            } else {
                pageToken = null;
            }
        } while (pageToken != null && !pageToken.isBlank());
        return "";
    }

    private String createTable(String targetAppToken, String tableName) throws Exception {
        CreateAppTableReq req = CreateAppTableReq.newBuilder()
                .appToken(targetAppToken)
                .createAppTableReqBody(CreateAppTableReqBody.newBuilder()
                        .table(ReqTable.newBuilder()
                                .name(tableName)
                                .defaultViewName("默认视图")
                                .fields(new AppTableCreateHeader[]{
                                        AppTableCreateHeader.newBuilder()
                                                .fieldName("映射ID")
                                                .type(AppTableCreateHeaderTypeEnum.TEXT)
                                                .uiType(AppTableCreateHeaderUiTypeEnum.TEXT)
                                                .build()
                                })
                                .build())
                        .build())
                .build();
        CreateAppTableResp resp = feishuClient.bitable().appTable().create(req);
        if (resp == null || resp.getCode() != 0 || resp.getData() == null
                || resp.getData().getTableId() == null || resp.getData().getTableId().isBlank()) {
            throw new IllegalStateException("create table failed: code="
                    + (resp != null ? resp.getCode() : "null") + ", msg="
                    + (resp != null ? resp.getMsg() : "null"));
        }
        return resp.getData().getTableId();
    }

    private Map<String, AppTableFieldTypeEnum> mindocSyncFields() {
        Map<String, AppTableFieldTypeEnum> fields = new LinkedHashMap<>();
        for (String name : List.of(
                "配置ID", "项目名称", "URL地址", "入口链接", "根链接", "包含路径", "排除路径",
                "认证方式", "认证值", "访问方式", "同步频率", "同步状态", "启停状态",
                "目标知识库ID", "Markdown Hash", "WISE文件ID", "文档数量", "上次同步时间",
                "解析状态", "更新时间", "错误信息")) {
            fields.put(name, AppTableFieldTypeEnum.TEXT);
        }
        return fields;
    }

    private Map<String, AppTableFieldTypeEnum> mindocMappingSchemaFields() {
        Map<String, AppTableFieldTypeEnum> fields = new LinkedHashMap<>();
        for (String name : List.of(
                "映射ID", "项目名称", "文档唯一键", "原始URL", "规范化URL", "URL哈希",
                "MinDoc文档ID", "文档标题", "Markdown哈希", "WISE文件ID", "旧WISE文件ID",
                "同步状态", "解析状态", "最近发现时间", "最近同步时间", "错误信息")) {
            fields.put(name, AppTableFieldTypeEnum.TEXT);
        }
        fields.put("是否失效", AppTableFieldTypeEnum.CHECKBOX);
        return fields;
    }

    /**
     * 回写管理员操作端 MinDoc 同步行的本轮入库摘要。
     * <p>
     * 详细文档级映射仍写入 {@code mindoc_document_mapping}，这里保留摘要方便管理员
     * 在操作端确认该 URL 已完成爬取、入库并具备后续增量更新所需的 md_hash。
     */
    public void updateMindocSyncMappingSummary(String recordId, List<MindocDocumentMapping> mappings) {
        if (!isConfigured(operationAppToken, syncTableId)) {
            log.warn("sync_record 表未配置，跳过映射摘要回写");
            return;
        }
        if (recordId == null || recordId.isBlank() || mappings == null || mappings.isEmpty()) {
            return;
        }

        String mdHashes = mappings.stream()
                .map(MindocDocumentMapping::getMdHash)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(50)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        String wiseFileIds = mappings.stream()
                .map(MindocDocumentMapping::getWiseFileId)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(50)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Markdown Hash", mdHashes);
        fields.put("WISE文件ID", wiseFileIds);
        fields.put("文档数量", String.valueOf(mappings.size()));
        fields.put("上次同步时间", formatTimestamp(Instant.now().toEpochMilli()));
        updateRecord(operationAppToken, syncTableId, recordId, fields, "sync_record");
    }

    // ─── FAQ 草稿 (faq_draft) ─────────────────────────

    /**
     * 从未命中问题生成 FAQ 草稿。
     * <p>
     * 只生成问题结构，不自动编写答案；管理员确认并补全答案后再入库 WISE FAQ。
     */
    public List<FaqDraftRecord> createFaqDraftsFromMissQuestions(int missThreshold) {
        if (!isConfigured(missAppToken, missTableId)
                || !isConfigured(operationAppToken, faqDraftTableId)) {
            log.debug("miss_question 或 faq_draft 表未配置，跳过未命中 FAQ 草稿生成");
            return List.of();
        }

        List<FaqDraftRecord> created = new ArrayList<>();
        try {
            for (AppTableRecord item : searchRecords(missAppToken, missTableId, null, 500)) {
                Map<String, Object> fields = item.getFields();
                int count = readInt(firstFieldValue(fields, "出现次数", "count"), 0);
                String status = fieldTextAny(fields, "状态", "status");
                if (count < missThreshold || isClosedSourceStatus(status)) {
                    continue;
                }

                String question = firstNonBlank(
                        fieldTextAny(fields, "问题", "question"),
                        fieldTextAny(fields, "标准化问题", "normalized_question")
                );
                if (question.isBlank()
                        || hasExistingFaqDraft("未命中", item.getRecordId(), question)) {
                    continue;
                }

                long now = Instant.now().toEpochMilli();
                FaqDraftRecord draft = FaqDraftRecord.builder()
                        .draftId(generateId("faq"))
                        .sourceType("未命中")
                        .sourceId(item.getRecordId())
                        .standardQuestion(question)
                        .similarQuestions(question)
                        .answer("")
                        .knowledgeRefs("")
                        .confidence(0.0)
                        .status("待确认")
                        .owner(fieldTextAny(fields, "负责人", "owner"))
                        .createdAt(now)
                        .updatedAt(now)
                        .wiseFaqId("")
                        .errorMessage("")
                        .build();
                createFaqDraftRecord(draft);
                created.add(draft);
            }
        } catch (Exception e) {
            log.warn("从未命中问题生成 FAQ 草稿失败", e);
        }
        return created;
    }

    /**
     * 从知识审核任务生成 FAQ 草稿。
     */
    public List<FaqDraftRecord> createFaqDraftsFromReviewTasks() {
        if (!isConfigured(operationAppToken, reviewTableId)
                || !isConfigured(operationAppToken, faqDraftTableId)) {
            log.debug("knowledge_review 或 faq_draft 表未配置，跳过负反馈 FAQ 草稿生成");
            return List.of();
        }

        List<FaqDraftRecord> created = new ArrayList<>();
        try {
            for (AppTableRecord item : searchRecords(operationAppToken, reviewTableId, null, 500)) {
                Map<String, Object> fields = item.getFields();
                String status = fieldTextAny(fields, "状态", "status");
                if (!status.isBlank() && !status.contains("待审核")) {
                    continue;
                }

                String question = fieldTextAny(fields, "问题", "question");
                String sourceId = firstNonBlank(fieldTextAny(fields, "任务ID", "task_id"), item.getRecordId());
                if (question.isBlank()
                        || hasExistingFaqDraft("负反馈", sourceId, question)) {
                    continue;
                }

                long now = Instant.now().toEpochMilli();
                FaqDraftRecord draft = FaqDraftRecord.builder()
                        .draftId(generateId("faq"))
                        .sourceType("负反馈")
                        .sourceId(sourceId)
                        .standardQuestion(question)
                        .similarQuestions(question)
                        .answer("")
                        .knowledgeRefs(fieldTextAny(fields, "知识来源", "knowledge_refs"))
                        .confidence(0.0)
                        .status("待确认")
                        .owner(fieldTextAny(fields, "负责人", "owner"))
                        .createdAt(now)
                        .updatedAt(now)
                        .wiseFaqId("")
                        .errorMessage("")
                        .build();
                createFaqDraftRecord(draft);
                created.add(draft);
            }
        } catch (Exception e) {
            log.warn("从知识审核任务生成 FAQ 草稿失败", e);
        }
        return created;
    }

    /**
     * 读取已由管理员确认、等待入库的 FAQ 草稿。
     */
    public List<FaqDraftRecord> listFaqDraftsReadyForIngestion() {
        if (!isConfigured(operationAppToken, faqDraftTableId)) {
            log.debug("faq_draft 表未配置，跳过 FAQ 待入库扫描");
            return List.of();
        }

        List<FaqDraftRecord> drafts = new ArrayList<>();
        try {
            for (AppTableRecord item : searchRecords(operationAppToken, faqDraftTableId, null, 500)) {
                FaqDraftRecord draft = faqDraftFromFields(item.getRecordId(), item.getFields());
                if (isReadyFaqDraft(draft)) {
                    drafts.add(draft);
                }
            }
        } catch (Exception e) {
            log.warn("读取待入库 FAQ 草稿失败", e);
        }
        return drafts;
    }

    /**
     * 回写 FAQ 草稿入库状态。
     */
    public void updateFaqDraftIngestionResult(String recordId, String status,
                                              String wiseFaqId, String errorMessage) {
        if (!isConfigured(operationAppToken, faqDraftTableId)) {
            log.warn("faq_draft 表未配置，跳过 FAQ 草稿状态回写");
            return;
        }
        if (recordId == null || recordId.isBlank()) {
            log.warn("FAQ 草稿 recordId 为空，跳过状态回写");
            return;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("状态", status);
        fields.put("WISE FAQ ID", wiseFaqId == null ? "" : wiseFaqId);
        fields.put("错误信息", errorMessage == null ? "" : errorMessage);
        fields.put("更新时间", formatTimestamp(Instant.now().toEpochMilli()));
        updateRecord(operationAppToken, faqDraftTableId, recordId, fields, "faq_draft");
    }

    // ─── 内部方法 ──────────────────────────────────────

    MindocSyncConfig mindocConfigFromFields(String recordId, Map<String, Object> fields,
                                            String defaultKnowledgeBaseId) {
        String configId = firstNonBlank(
                fieldTextAny(fields, "配置ID", "config_id"),
                fieldTextAny(fields, "文档ID", "doc_id"),
                recordId
        );
        String projectName = firstNonBlank(
                fieldTextAny(fields, "项目名称", "project_name"),
                fieldTextAny(fields, "标题", "title"),
                configId,
                "MINDOC"
        );
        String sourceUrl = firstNonBlank(
                fieldTextAny(fields, "入口链接", "source_url"),
                fieldTextAny(fields, "URL地址", "url_address"),
                fieldTextAny(fields, "MinDoc URL", "mindoc_url"),
                fieldTextAny(fields, "原始链接", "url")
        );
        String wiseKbId = firstNonBlank(
                fieldTextAny(fields, "目标知识库ID", "wise_kb_id"),
                fieldTextAny(fields, "knowledge_base_id"),
                fieldTextAny(fields, "target_knowledge_base_id"),
                defaultKnowledgeBaseId
        );

        return MindocSyncConfig.builder()
                .recordId(recordId)
                .configId(configId)
                .projectName(projectName)
                .sourceUrl(sourceUrl)
                .rootUrl(fieldTextAny(fields, "根链接", "root_url", "同步根URL"))
                .includePath(fieldTextAny(fields, "包含路径", "include_path", "同步范围"))
                .excludePath(fieldTextAny(fields, "排除路径", "exclude_path"))
                .authType(normalizeMindocAuthType(firstNonBlank(
                        fieldTextAny(fields, "认证方式", "auth_type", "访问方式"),
                        "none")))
                .authValue(fieldTextAny(fields, "认证值", "auth_value", "Cookie", "Token"))
                .syncCron(fieldTextAny(fields, "同步频率", "sync_cron"))
                .status(firstNonBlank(
                        fieldTextAny(fields, "同步状态", "sync_status"),
                        fieldTextAny(fields, "启停状态", "status"),
                        fieldTextAny(fields, "是否启用", "enabled")))
                .wiseKbId(wiseKbId)
                .build();
    }

    private String normalizeMindocAuthType(String authType) {
        String value = firstNonBlank(authType, "none").trim().toLowerCase(Locale.ROOT);
        if (value.contains("cookie")) {
            return "cookie";
        }
        if (value.contains("bearer")) {
            return "bearer";
        }
        if (value.contains("token")) {
            return "token";
        }
        if (value.contains("内网") || value.contains("免登录") || value.contains("无需")
                || value.contains("不需要") || value.contains("none")) {
            return "none";
        }
        return value;
    }

    FaqDraftRecord faqDraftFromFields(String recordId, Map<String, Object> fields) {
        return FaqDraftRecord.builder()
                .recordId(recordId)
                .draftId(fieldTextAny(fields, "草稿ID", "draft_id"))
                .sourceType(fieldTextAny(fields, "来源类型", "source_type"))
                .sourceId(fieldTextAny(fields, "来源记录ID", "source_id"))
                .standardQuestion(fieldTextAny(fields, "标准问", "standard_question"))
                .similarQuestions(fieldTextAny(fields, "相似问", "similar_questions"))
                .answer(fieldTextAny(fields, "答案", "answer"))
                .knowledgeRefs(fieldTextAny(fields, "知识来源", "knowledge_refs"))
                .confidence(readDouble(firstFieldValue(fields, "置信度", "confidence"), 0.0))
                .status(fieldTextAny(fields, "状态", "status"))
                .owner(fieldTextAny(fields, "负责人", "owner"))
                .createdAt(Instant.now().toEpochMilli())
                .updatedAt(Instant.now().toEpochMilli())
                .wiseFaqId(fieldTextAny(fields, "WISE FAQ ID", "wise_faq_id"))
                .errorMessage(fieldTextAny(fields, "错误信息", "error_message"))
                .build();
    }

    private void createFaqDraftRecord(FaqDraftRecord draft) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("草稿ID", draft.getDraftId());
        fields.put("来源类型", draft.getSourceType());
        fields.put("来源记录ID", draft.getSourceId());
        fields.put("标准问", draft.getStandardQuestion());
        fields.put("相似问", draft.getSimilarQuestions());
        putIfNotBlank(fields, "答案", draft.getAnswer());
        putIfNotBlank(fields, "知识来源", draft.getKnowledgeRefs());
        fields.put("置信度", String.valueOf(draft.getConfidence()));
        fields.put("状态", draft.getStatus());
        putIfNotBlank(fields, "负责人", draft.getOwner());
        fields.put("创建时间", formatTimestamp(draft.getCreatedAt()));
        fields.put("更新时间", formatTimestamp(draft.getUpdatedAt()));
        putIfNotBlank(fields, "WISE FAQ ID", draft.getWiseFaqId());
        putIfNotBlank(fields, "错误信息", draft.getErrorMessage());
        createRecord(operationAppToken, faqDraftTableId, fields, "faq_draft");
    }

    private void putIfNotBlank(Map<String, Object> fields, String fieldName, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(fieldName, value);
        }
    }

    private boolean hasExistingFaqDraft(String sourceType, String sourceId,
                                        String standardQuestion) throws Exception {
        String normalizedQuestion = normalizeQuestion(standardQuestion);
        for (AppTableRecord item : searchRecords(operationAppToken, faqDraftTableId, null, 500)) {
            Map<String, Object> fields = item.getFields();
            String existingSourceType = fieldTextAny(fields, "来源类型", "source_type");
            String existingSourceId = fieldTextAny(fields, "来源记录ID", "source_id");
            String existingQuestion = fieldTextAny(fields, "标准问", "standard_question");
            String existingStatus = fieldTextAny(fields, "状态", "status");

            if (sourceType.equals(existingSourceType) && sourceId.equals(existingSourceId)) {
                return true;
            }
            if (!isClosedFaqDraftStatus(existingStatus)
                    && normalizedQuestion.equals(normalizeQuestion(existingQuestion))) {
                return true;
            }
        }
        return false;
    }

    private boolean isReadyFaqDraft(FaqDraftRecord draft) {
        if (draft == null || draft.getRecordId() == null || draft.getRecordId().isBlank()) {
            return false;
        }
        String status = firstNonBlank(draft.getStatus());
        return (status.contains("待入库") || status.contains("已确认"))
                && (draft.getWiseFaqId() == null || draft.getWiseFaqId().isBlank())
                && draft.getStandardQuestion() != null && !draft.getStandardQuestion().isBlank()
                && draft.getAnswer() != null && !draft.getAnswer().isBlank();
    }

    private boolean isClosedSourceStatus(String status) {
        String text = firstNonBlank(status);
        return text.contains("已补充") || text.contains("忽略");
    }

    private boolean isClosedFaqDraftStatus(String status) {
        String text = firstNonBlank(status);
        return text.contains("已入库") || text.contains("退回") || text.contains("忽略");
    }

    String negativeFeedbackGroupKey(FeedbackRecord record) {
        if (hasKnowledgeRefs(record)) {
            return "knowledge_refs:" + record.getKnowledgeRefs().trim();
        }
        return "question:" + normalizeQuestion(record != null ? record.getQuestion() : "");
    }

    String reviewTriggerReason(FeedbackRecord feedback, int negativeCount) {
        if (hasKnowledgeRefs(feedback)) {
            return "同一知识来源连续 " + negativeCount + " 次负反馈";
        }
        return "同问题 " + negativeCount + " 次负反馈（未提取到知识来源）";
    }

    String normalizeQuestion(String question) {
        if (question == null) {
            return "";
        }
        return question.toLowerCase(Locale.ROOT)
                .replaceAll("@_user_\\d+", "")
                .replaceAll("@_all", "")
                .replaceAll("[\\p{Punct}\\s，。！？、；：“”‘’（）【】《》〈〉…—-]+", "")
                .trim();
    }

    private boolean hasKnowledgeRefs(FeedbackRecord record) {
        return record != null && record.getKnowledgeRefs() != null && !record.getKnowledgeRefs().isBlank();
    }

    FeedbackRecord feedbackFromFields(Map<String, Object> fields) {
        return FeedbackRecord.builder()
                .feedbackId(fieldTextAny(fields, "反馈ID", "feedback_id"))
                .qaId(fieldTextAny(fields, "问答ID", "qa_id"))
                .userId(fieldTextAny(fields, "用户ID", "user_id"))
                .question(fieldTextAny(fields, "问题", "question"))
                .answerSummary(fieldTextAny(fields, "问答摘要", "答案摘要", "answer_summary"))
                .knowledgeRefs(fieldTextAny(fields, "知识来源", "knowledge_refs"))
                .feedback(isNegativeFeedback(firstFieldValue(fields, "反馈", "feedback")) ? "useless" : "useful")
                .createdAt(Instant.now().toEpochMilli())
                .build();
    }

    private int effectiveNegativeCount(FeedbackRecord record, int persistedCount) {
        String groupKey = negativeFeedbackGroupKey(record);
        int inMemoryCount = negativeFeedbackCountCache.merge(groupKey, 1, Integer::sum);
        int effectiveCount = Math.max(persistedCount, inMemoryCount);
        negativeFeedbackCountCache.put(groupKey, effectiveCount);
        return effectiveCount;
    }

    private boolean isClosedReview(AppTableRecord item) {
        if (item == null || item.getFields() == null) {
            return false;
        }
        String status = fieldTextAny(item.getFields(), "状态", "status");
        return status.contains("已处理") || status.contains("忽略");
    }

    private String fieldText(Map<String, Object> fields, String fieldName) {
        return fields == null ? "" : fieldValueText(fields.get(fieldName));
    }

    private String fieldTextAny(Map<String, Object> fields, String... fieldNames) {
        return fieldValueText(firstFieldValue(fields, fieldNames));
    }

    private Object firstFieldValue(Map<String, Object> fields, String... fieldNames) {
        if (fields == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            if (fieldName != null && fields.containsKey(fieldName)) {
                Object value = fields.get(fieldName);
                if (!fieldValueText(value).isBlank()) {
                    return value;
                }
            }
        }
        for (String fieldName : fieldNames) {
            if (fieldName != null && fields.containsKey(fieldName)) {
                return fields.get(fieldName);
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    String fieldValueText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value).trim();
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("text", "name", "value", "content", "title")) {
                if (map.containsKey(key)) {
                    String preferred = fieldValueText(map.get(key));
                    if (!preferred.isBlank()) {
                        return preferred;
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            for (Object item : map.values()) {
                appendFieldText(sb, item);
            }
            return sb.toString().trim();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder sb = new StringBuilder();
            for (Object item : iterable) {
                appendFieldText(sb, item);
            }
            return sb.toString().trim();
        }
        if (value.getClass().isArray()) {
            StringBuilder sb = new StringBuilder();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                appendFieldText(sb, Array.get(value, i));
            }
            return sb.toString().trim();
        }
        return String.valueOf(value).trim();
    }

    private void appendFieldText(StringBuilder sb, Object value) {
        String text = fieldValueText(value);
        if (text.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(text);
    }

    private FilterInfo filter(String fieldName, String fieldValue) {
        return FilterInfo.newBuilder()
                .conjunction("and")
                .conditions(new Condition[]{
                        Condition.newBuilder()
                                .fieldName(fieldName)
                                .operator("is")
                                .value(new String[]{fieldValue})
                                .build()
                })
                .build();
    }

    private List<AppTableRecord> searchRecords(String tableId, FilterInfo filter,
                                               int pageSize) throws Exception {
        return searchRecords(appToken, tableId, filter, pageSize);
    }

    private List<AppTableRecord> searchRecords(String targetAppToken, String tableId,
                                               FilterInfo filter, int pageSize) throws Exception {
        List<AppTableRecord> records = new ArrayList<>();
        String pageToken = null;
        do {
            SearchAppTableRecordReqBody.Builder bodyBuilder = SearchAppTableRecordReqBody.newBuilder();
            if (filter != null) {
                bodyBuilder.filter(filter);
            }

            SearchAppTableRecordReq.Builder reqBuilder = SearchAppTableRecordReq.newBuilder()
                    .appToken(targetAppToken)
                    .tableId(tableId)
                    .pageSize(pageSize)
                    .searchAppTableRecordReqBody(bodyBuilder.build());
            if (pageToken != null && !pageToken.isBlank()) {
                reqBuilder.pageToken(pageToken);
            }

            SearchAppTableRecordResp resp = feishuClient.bitable().appTableRecord()
                    .search(reqBuilder.build());
            if (resp == null || resp.getCode() != 0 || resp.getData() == null) {
                throw new IllegalStateException("search bitable failed: code="
                        + (resp != null ? resp.getCode() : "null") + ", msg="
                        + (resp != null ? resp.getMsg() : "null"));
            }
            if (resp.getData().getItems() != null) {
                for (AppTableRecord item : resp.getData().getItems()) {
                    records.add(item);
                }
            }
            pageToken = Boolean.TRUE.equals(resp.getData().getHasMore())
                    ? resp.getData().getPageToken() : null;
        } while (pageToken != null && !pageToken.isBlank());
        return records;
    }

    /**
     * 检查用户是否已对某问答提交过反馈。
     */
    private boolean hasExistingFeedback(String qaId, String userId) {
        if ((qaId == null || qaId.isBlank()) || (userId == null || userId.isBlank())) {
            return false;
        }
        try {
            for (AppTableRecord item : searchRecords(feedbackTableId, null, 500)) {
                Map<String, Object> fields = item.getFields();
                if (fields == null) {
                    continue;
                }
                String existingQaId = fieldTextAny(fields, "问答ID", "qa_id");
                String existingUserId = fieldTextAny(fields, "用户ID", "user_id");
                if (qaId.equals(existingQaId) && userId.equals(existingUserId)) {
                    return true;
                }
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
            String normalizedQuestion = normalizeQuestion(record.getQuestion());
            int count = 0;
            for (AppTableRecord item : searchRecords(feedbackTableId, null, 500)) {
                Map<String, Object> fields = item.getFields();
                if (fields == null) {
                    continue;
                }
                if (!isNegativeFeedback(firstFieldValue(fields, "反馈", "feedback"))) {
                    continue;
                }
                if (isSameNegativeFeedbackGroup(record, fields, normalizedQuestion)) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            log.warn("查询负反馈次数失败: qa_id={}, group={}",
                    record.getQaId(), negativeFeedbackGroupKey(record), e);
        }
        return 0;
    }

    private boolean isSameNegativeFeedbackGroup(FeedbackRecord record, Map<String, Object> fields,
                                                String normalizedQuestion) {
        if (hasKnowledgeRefs(record)) {
            return record.getKnowledgeRefs().trim()
                    .equals(fieldTextAny(fields, "知识来源", "knowledge_refs").trim());
        }
        if (!normalizedQuestion.isBlank()) {
            return normalizedQuestion.equals(normalizeQuestion(fieldTextAny(fields, "问题", "question")));
        }
        return record != null && record.getQaId() != null && !record.getQaId().isBlank()
                && record.getQaId().equals(fieldTextAny(fields, "问答ID", "qa_id"));
    }

    private boolean hasExistingReviewTask(FeedbackRecord feedback) {
        try {
            if (hasKnowledgeRefs(feedback)) {
                for (AppTableRecord item : searchRecords(operationAppToken, reviewTableId,
                        filter("知识来源", feedback.getKnowledgeRefs().trim()), 100)) {
                    if (!isClosedReview(item)) {
                        return true;
                    }
                }
                return false;
            }

            String normalizedQuestion = normalizeQuestion(feedback.getQuestion());
            if (normalizedQuestion.isBlank()) {
                return false;
            }
            for (AppTableRecord item : searchRecords(operationAppToken, reviewTableId, null, 500)) {
                Map<String, Object> fields = item.getFields();
                if (fields == null || isClosedReview(item)) {
                    continue;
                }
                if (normalizedQuestion.equals(normalizeQuestion(fieldTextAny(fields, "问题", "question")))) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("查询已有审核任务失败，默认允许创建: group={}",
                    negativeFeedbackGroupKey(feedback), e);
        }
        return false;
    }

    private AppTableRecord findMissRecord(String normalizedQuestion) {
        try {
            for (AppTableRecord item : searchRecords(missAppToken, missTableId,
                    filter("标准化问题", normalizedQuestion), 1)) {
                return item;
            }
        } catch (Exception e) {
            log.warn("查询未命中问题失败，改为新增: normalized_question={}", normalizedQuestion, e);
        }
        return null;
    }

    private List<AppTableRecord> safeSearch(String targetAppToken, String tableId) {
        if (!isConfigured(targetAppToken, tableId) || feishuClient == null) {
            return List.of();
        }
        try {
            return searchRecords(targetAppToken, tableId, null, 500);
        } catch (Exception e) {
            log.warn("读取运营报表数据失败: tableId={}", tableId, e);
            return List.of();
        }
    }

    private int countRecordsInRange(List<AppTableRecord> records, LocalDate start) {
        int count = 0;
        for (AppTableRecord record : records) {
            if (isRecordInRange(record, start)) {
                count++;
            }
        }
        return count;
    }

    private int countQaFallbackInRange(List<AppTableRecord> records, LocalDate start) {
        int count = 0;
        for (AppTableRecord record : records) {
            if (!isRecordInRange(record, start) || record.getFields() == null) {
                continue;
            }
            String fallback = fieldTextAny(record.getFields(), "is_fallback", "是否兜底");
            if ("true".equalsIgnoreCase(fallback) || fallback.contains("是")) {
                count++;
            }
        }
        return count;
    }

    private int countFeedbackInRange(List<AppTableRecord> records, LocalDate start, boolean negative) {
        int count = 0;
        for (AppTableRecord record : records) {
            if (!isRecordInRange(record, start) || record.getFields() == null) {
                continue;
            }
            boolean isNegative = isNegativeFeedback(firstFieldValue(record.getFields(), "反馈", "feedback"));
            if (negative == isNegative) {
                count++;
            }
        }
        return count;
    }

    private int countStatus(List<AppTableRecord> records, String fieldName, String... expected) {
        int count = 0;
        for (AppTableRecord record : records) {
            if (record.getFields() == null) {
                continue;
            }
            String status = fieldTextAny(record.getFields(), fieldName, fieldName.toLowerCase(Locale.ROOT));
            for (String value : expected) {
                if (!status.isBlank() && status.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private List<String> topMissQuestions(List<AppTableRecord> records, int limit) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (AppTableRecord record : records) {
            if (record.getFields() == null) {
                continue;
            }
            String question = firstNonBlank(
                    fieldTextAny(record.getFields(), "问题", "question"),
                    fieldTextAny(record.getFields(), "标准化问题", "normalized_question"));
            int count = readInt(firstFieldValue(record.getFields(), "出现次数", "count"), 0);
            if (!question.isBlank()) {
                entries.add(Map.entry(question, count));
            }
        }
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> top = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            top.add((i + 1) + ". " + entries.get(i).getKey() + " - 出现 "
                    + entries.get(i).getValue() + " 次");
        }
        return top;
    }

    private String topMissText(List<String> topMiss) {
        if (topMiss.isEmpty()) {
            return "  · 高频未命中问题 Top3：暂无";
        }
        return "  · 高频未命中问题 Top3：\n    " + String.join("\n    ", topMiss);
    }

    private boolean isRecordInRange(AppTableRecord record, LocalDate start) {
        if (record == null || record.getFields() == null) {
            return false;
        }
        String timeText = firstNonBlank(
                fieldTextAny(record.getFields(), "created_at", "创建时间", "首次出现时间", "更新时间"),
                fieldTextAny(record.getFields(), "最近出现时间"));
        if (timeText.isBlank()) {
            return true;
        }
        try {
            LocalDate date = LocalDate.parse(timeText.substring(0, Math.min(10, timeText.length())));
            return !date.isBefore(start);
        } catch (Exception ignored) {
            return true;
        }
    }

    private String percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return "0%";
        }
        return Math.round(numerator * 1000.0 / denominator) / 10.0 + "%";
    }

    private void notifyNegativeFeedback(FeedbackRecord record, int negativeCount) {
        if (adminNotifier != null) {
            adminNotifier.notifyNegativeFeedback(record.getQuestion(), record.getKnowledgeRefs(), negativeCount);
        }
    }

    private void notifyTopMissQuestion(String question, int count) {
        if (adminNotifier != null && count >= Math.max(1, missTopNotifyThreshold)) {
            adminNotifier.notifyTopMissQuestion(question, count);
        }
    }

    private void notifyWiseParseFailure(MindocDocumentMapping mapping) {
        if (adminNotifier != null) {
            adminNotifier.notifyWiseParseFailure(mapping.getProjectName(), mapping.getTitle(), mapping.getErrorMessage());
        }
    }

    /**
     * 通用：创建多维表格记录，失败时重试 3 次。
     * <p>
     * 表格不可用时只记录错误日志，不影响用户问答体验。
     */
    private void createRecord(String tableId, Map<String, Object> fields, String tableName) {
        createRecord(appToken, tableId, fields, tableName);
    }

    private void createRecord(String targetAppToken, String tableId,
                              Map<String, Object> fields, String tableName) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                AppTableRecord record = AppTableRecord.newBuilder()
                        .fields(fields)
                        .build();

                CreateAppTableRecordReq req = CreateAppTableRecordReq.newBuilder()
                        .appToken(targetAppToken)
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
        updateRecord(appToken, tableId, recordId, fields, tableName);
    }

    private void updateRecord(String targetAppToken, String tableId, String recordId,
                              Map<String, Object> fields, String tableName) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                AppTableRecord record = AppTableRecord.newBuilder()
                        .fields(fields)
                        .build();

                UpdateAppTableRecordReq req = UpdateAppTableRecordReq.newBuilder()
                        .appToken(targetAppToken)
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
        return isConfigured(appToken, tableId);
    }

    private boolean isConfigured(String targetAppToken, String tableId) {
        return targetAppToken != null && !targetAppToken.isEmpty()
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

    boolean isNegativeFeedback(Object value) {
        String text = fieldValueText(value);
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

    private double readDouble(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).replaceAll("\\s+", ""));
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
