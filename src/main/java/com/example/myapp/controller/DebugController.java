package com.example.myapp.controller;

import com.example.myapp.service.FeishuMessageService;
import com.example.myapp.service.LarkBitableService;
import com.example.myapp.service.MessagePollingService;
import com.example.myapp.service.MindocSyncScheduler;
import com.example.myapp.service.WiseAgentService;
import com.example.myapp.model.MissRecord;
import com.example.myapp.model.QuestionRecord;
import com.example.myapp.dto.AgentResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.enums.CreateMessageReceiveIdTypeEnum;
import com.lark.oapi.service.im.v1.model.*;
import com.lark.oapi.service.bitable.v1.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

/**
 * 调试测试接口 — 用于排查卡片按钮和多维表格写入问题。
 */
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private static final Logger log = LoggerFactory.getLogger(DebugController.class);

    private final Client feishuClient;
    private final LarkBitableService bitableService;
    private final FeishuMessageService messageService;
    private final MessagePollingService messagePollingService;
    private final MindocSyncScheduler mindocSyncScheduler;
    private final WiseAgentService wiseAgentService;
    private final Environment env;

    public DebugController(Client feishuClient, LarkBitableService bitableService,
                           FeishuMessageService messageService,
                           MessagePollingService messagePollingService,
                           MindocSyncScheduler mindocSyncScheduler, WiseAgentService wiseAgentService,
                           Environment env) {
        this.feishuClient = feishuClient;
        this.bitableService = bitableService;
        this.messageService = messageService;
        this.messagePollingService = messagePollingService;
        this.mindocSyncScheduler = mindocSyncScheduler;
        this.wiseAgentService = wiseAgentService;
        this.env = env;
    }

    /** 直接验证当前配置能否调用 WISE Agent；仅用于本地排障。 */
    @GetMapping("/test-wise-agent")
    public Map<String, Object> testWiseAgent(@RequestParam(defaultValue = "请用一句话回答：测试连接是否成功？") String question) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", wiseAgentService.isConfigured());
        String answer = wiseAgentService.chatForDiagnosis(question);
        result.put("answer", answer);
        result.put("answerLength", answer != null ? answer.length() : 0);
        return result;
    }

    /**
     * 测试 1：检查卡片 JSON 结构是否正确
     */
    @GetMapping("/card-json")
    public Map<String, Object> testCardJson() {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // 通过反射调用 buildResponseCard（private 方法）
            AgentResponse mockResponse = AgentResponse.success("这是一条测试回答，用于验证卡片 JSON 结构是否正确。");
            Method method = FeishuMessageService.class.getDeclaredMethod(
                    "buildResponseCard", AgentResponse.class, String.class, String.class);
            method.setAccessible(true);
            String cardJson = (String) method.invoke(messageService, mockResponse, "测试问题", "qa_test_001");

            result.put("status", "success");
            result.put("cardJson", cardJson);
            result.put("cardJsonLength", cardJson.length());

            // 解析检查
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> parsed = mapper.readValue(cardJson, Map.class);
            result.put("schema", parsed.get("schema"));
            result.put("hasHeader", parsed.containsKey("header"));
            result.put("hasBody", parsed.containsKey("body"));

            // 检查 body.elements 中是否有 action 元素
            if (parsed.containsKey("body")) {
                Map<String, Object> body = (Map<String, Object>) parsed.get("body");
                List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");
                result.put("elementCount", elements.size());

                boolean hasAction = false;
                int actionButtonCount = 0;
                for (Map<String, Object> elem : elements) {
                    if ("action".equals(elem.get("tag"))) {
                        hasAction = true;
                        List<Map<String, Object>> actions = (List<Map<String, Object>>) elem.get("actions");
                        if (actions != null) {
                            actionButtonCount = actions.size();
                        }
                    }
                }
                result.put("hasActionElement", hasAction);
                result.put("actionButtonCount", actionButtonCount);
            }

            // 检查是否同时有 elements（schema 1.0 格式）
            result.put("hasTopLevelElements", parsed.containsKey("elements"));

        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.toString());
            log.error("生成卡片 JSON 失败", e);
        }

        return result;
    }

    /**
     * 测试 2：直接发送一张测试卡片到指定用户（验证按钮是否显示）
     */
    @PostMapping("/send-test-card")
    public Map<String, Object> sendTestCard(@RequestParam String openId) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // 构建测试卡片 JSON
            String cardJson = buildTestCardJson();
            result.put("cardJson", cardJson);

            // 发送
            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType(CreateMessageReceiveIdTypeEnum.OPEN_ID)
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(openId)
                            .msgType("interactive")
                            .content(cardJson)
                            .build())
                    .build();

            CreateMessageResp resp = feishuClient.im().message().create(req);
            result.put("apiCode", resp.getCode());
            result.put("apiMsg", resp.getMsg());

            if (resp.getCode() == 0 && resp.getData() != null) {
                result.put("status", "success");
                result.put("messageId", resp.getData().getMessageId());
            } else {
                result.put("status", "failed");
                result.put("fullResponse", resp.toString());
            }

        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.toString());
            log.error("发送测试卡片失败", e);
        }

        return result;
    }

    /**
     * 测试 3：测试多维表格写入
     */
    @PostMapping("/test-bitable")
    public Map<String, Object> testBitableWrite() {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // 写入一条测试记录到 qa_record
            String qaId = "qa_debug_" + System.currentTimeMillis();
            QuestionRecord record = QuestionRecord.builder()
                    .qaId(qaId)
                    .messageId("msg_debug_test")
                    .chatId("chat_debug_test")
                    .userId("user_debug_test")
                    .question("调试测试问题")
                    .answerSummary("调试测试回答")
                    .sessionId("session_debug_test")
                    .knowledgeRefs("")
                    .fallback(false)
                    .confidence(0.8)
                    .responseTimeMs(100)
                    .createdAt(Instant.now().toEpochMilli())
                    .build();

            String appToken = env.getProperty("feishu.bitable.app-token");
            String qaTableId = env.getProperty("feishu.bitable.qa-table-id");

            result.put("appToken", appToken);
            result.put("qaTableId", qaTableId);
            result.put("qaId", qaId);

            if (appToken == null || appToken.isEmpty()) {
                result.put("status", "config_error");
                result.put("error", "appToken 未配置");
                return result;
            }

            // 直接调用飞书 API 写入
            Map<String, Object> fields = new LinkedHashMap<>();
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
            fields.put("创建时间", java.time.Instant.ofEpochMilli(record.getCreatedAt())
                    .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            AppTableRecord tableRecord = AppTableRecord.newBuilder()
                    .fields(fields)
                    .build();

            CreateAppTableRecordReq req = CreateAppTableRecordReq.newBuilder()
                    .appToken(appToken)
                    .tableId(qaTableId)
                    .appTableRecord(tableRecord)
                    .build();

            CreateAppTableRecordResp resp = feishuClient.bitable().appTableRecord().create(req);
            result.put("apiCode", resp.getCode());
            result.put("apiMsg", resp.getMsg());

            if (resp.getCode() == 0) {
                result.put("status", "success");
                if (resp.getData() != null && resp.getData().getRecord() != null) {
                    result.put("recordId", resp.getData().getRecord().getRecordId());
                }
            } else {
                result.put("status", "failed");
                result.put("detail", "API 返回错误码: " + resp.getCode() + ", 消息: " + resp.getMsg());
            }

        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("多维表格写入测试失败", e);
        }

        return result;
    }

    @RequestMapping(value = "/mindoc-sync/run", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> runMindocSync() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            result.put("schema", bitableService.ensureMindocSyncSchema());
            MindocSyncScheduler.SyncSummary summary = mindocSyncScheduler.runSyncOnce();
            result.put("status", "success");
            result.put("total", summary.total());
            result.put("success", summary.success());
            result.put("failed", summary.failed());
            result.put("skipped", summary.skipped());
            result.put("mappingTable", "mindoc_document_mapping");
            result.put("note", "同步完成后检查多维表字段 normalized_url / url_hash / md_hash / wise_file_id / old_wise_file_id / expired");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.toString());
            log.error("手动触发 MinDoc 同步失败", e);
        }
        return result;
    }

    @RequestMapping(value = "/mindoc-sync/bootstrap", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> bootstrapMindocSyncTables() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            result.put("status", "success");
            result.put("schema", bitableService.ensureMindocSyncSchema());
            result.put("note", "只补齐 sync_record 和 mindoc_document_mapping 缺失字段，不触发爬虫。");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.toString());
            log.error("初始化 MinDoc 同步表结构失败", e);
        }
        return result;
    }

    /**
     * 测试 4：查询多维表格中的记录（验证是否能读取）
     */
    @GetMapping("/test-bitable-read")
    public Map<String, Object> testBitableRead() {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            String appToken = env.getProperty("feishu.bitable.app-token");
            String qaTableId = env.getProperty("feishu.bitable.qa-table-id");

            result.put("appToken", appToken);
            result.put("qaTableId", qaTableId);

            SearchAppTableRecordReq req = SearchAppTableRecordReq.newBuilder()
                    .appToken(appToken)
                    .tableId(qaTableId)
                    .pageSize(5)
                    .build();

            SearchAppTableRecordResp resp = feishuClient.bitable().appTableRecord().search(req);
            result.put("apiCode", resp.getCode());
            result.put("apiMsg", resp.getMsg());

            if (resp.getCode() == 0 && resp.getData() != null) {
                result.put("status", "success");
                result.put("total", resp.getData().getTotal());
                if (resp.getData().getItems() != null) {
                    result.put("returnedCount", resp.getData().getItems().length);
                    List<Map<String, Object>> records = new ArrayList<>();
                    for (AppTableRecord record : resp.getData().getItems()) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("recordId", record.getRecordId());
                        r.put("fields", record.getFields());
                        records.add(r);
                    }
                    result.put("records", records);
                }
            } else {
                result.put("status", "failed");
            }

        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("多维表格读取测试失败", e);
        }

        return result;
    }

    /**
     * 测试 6：直接调用 LarkBitableService.saveQuestionRecord（验证修复后的完整流程）
     */
    @PostMapping("/test-save-question")
    public Map<String, Object> testSaveQuestion() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String qaId = "qa_e2e_" + System.currentTimeMillis();
            QuestionRecord record = QuestionRecord.builder()
                    .qaId(qaId)
                    .messageId("msg_e2e_test")
                    .chatId("chat_e2e_test")
                    .userId("user_e2e_test")
                    .question("端到端测试问题")
                    .answerSummary("端到端测试回答")
                    .sessionId("session_e2e_test")
                    .knowledgeRefs("")
                    .fallback(false)
                    .confidence(0.8)
                    .responseTimeMs(123)
                    .createdAt(Instant.now().toEpochMilli())
                    .build();

            // 直接调用 service 方法（@Async 会异步执行，这里同步等待结果）
            bitableService.saveQuestionRecord(record);

            // 等 2 秒让异步完成
            java.lang.Thread.sleep(2000);

            result.put("status", "called");
            result.put("qaId", qaId);
            result.put("note", "检查应用日志确认是否写入成功（搜索 '写入 qa_record'）");

        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/test-save-miss")
    public Map<String, Object> testSaveMiss() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            long now = Instant.now().toEpochMilli();
            String question = "调试未命中问题_" + now;
            MissRecord record = MissRecord.builder()
                    .question(question)
                    .normalizedQuestion(question)
                    .count(1)
                    .status("pending")
                    .userId("ou_debug")
                    .chatId("oc_debug")
                    .owner("")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            Map<String, Object> writeResult = bitableService.debugCreateMissRecord(record);
            result.put("status", Boolean.TRUE.equals(writeResult.get("success")) ? "success" : "failed");
            result.put("question", question);
            result.put("missTableId", env.getProperty("feishu.bitable.miss-table-id"));
            result.put("missAppToken", env.getProperty("feishu.bitable.miss-app-token"));
            result.put("operationAppToken", env.getProperty("feishu.bitable.operation-app-token"));
            result.put("writeResult", writeResult);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/test-save-miss-service")
    public Map<String, Object> testSaveMissService() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            long now = Instant.now().toEpochMilli();
            String question = "调试真实路径未命中问题_" + now;
            MissRecord record = MissRecord.builder()
                    .question(question)
                    .normalizedQuestion(question)
                    .count(1)
                    .status("pending")
                    .userId("ou_debug")
                    .chatId("oc_debug")
                    .owner("")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            bitableService.saveMissRecord(record);
            result.put("status", "submitted");
            result.put("question", question);
            result.put("missTableId", env.getProperty("feishu.bitable.miss-table-id"));
            result.put("missAppToken", mask(env.getProperty("feishu.bitable.miss-app-token")));
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/test-ocr-image")
    public Map<String, Object> testOcrImage(@RequestParam String imageKey,
                                            @RequestParam(defaultValue = "") String messageId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String text = messagePollingService.debugOcrImageKey(messageId, imageKey);
            result.put("status", text.isBlank() ? "empty" : "success");
            result.put("messageId", messageId);
            result.put("imageKey", imageKey);
            result.put("text", text);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/feedback-monitor-chats")
    public Map<String, Object> feedbackMonitorChats() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> chatIds = messagePollingService.monitorChatIdsSnapshot();
        result.put("count", chatIds.size());
        result.put("chatIds", chatIds);
        result.put("cardActionsEnabled", env.getProperty("feishu.feedback.card-actions-enabled"));
        result.put("websocketEnabled", env.getProperty("feishu.websocket.enabled"));
        return result;
    }

    @PostMapping("/register-feedback-chat")
    public Map<String, Object> registerFeedbackChat(@RequestParam String chatId,
                                                    @RequestParam(defaultValue = "debug") String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        messagePollingService.registerChatForFeedback(chatId, reason);
        result.put("status", "registered");
        result.put("chatId", chatId);
        result.put("chatIds", messagePollingService.monitorChatIdsSnapshot());
        return result;
    }

    @GetMapping("/list-chat-messages")
    public Map<String, Object> listChatMessages(@RequestParam String chatId,
                                                @RequestParam(defaultValue = "600") long seconds,
                                                @RequestParam(defaultValue = "20") int pageSize,
                                                @RequestParam(defaultValue = "false") boolean includeRaw) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            long startSeconds = Math.max(0, Instant.now().getEpochSecond() - Math.max(1, seconds));
            int safePageSize = Math.max(1, Math.min(50, pageSize));
            ListMessageReq req = ListMessageReq.newBuilder()
                    .containerIdType("chat")
                    .containerId(chatId)
                    .startTime(String.valueOf(startSeconds))
                    .sortType("ByCreateTimeDesc")
                    .pageSize(safePageSize)
                    .build();

            ListMessageResp resp = feishuClient.im().message().list(req);
            result.put("apiCode", resp != null ? resp.getCode() : null);
            result.put("apiMsg", resp != null ? resp.getMsg() : "null");
            result.put("chatId", chatId);

            List<Map<String, Object>> messages = new ArrayList<>();
            if (resp != null && resp.getData() != null && resp.getData().getItems() != null) {
                for (Message message : resp.getData().getItems()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("messageId", message.getMessageId());
                    item.put("msgType", message.getMsgType());
                    item.put("senderType", message.getSender() != null ? message.getSender().getSenderType() : "");
                    item.put("createTime", message.getCreateTime());
                    item.put("updateTime", message.getUpdateTime());
                    item.put("parentId", message.getParentId());
                    item.put("rootId", message.getRootId());
                    String content = message.getBody() != null ? message.getBody().getContent() : "";
                    item.put("text", extractMessageText(content));
                    item.put("allText", extractAllText(content));
                    if (includeRaw) {
                        item.put("content", content);
                    }
                    messages.add(item);
                }
            }
            result.put("messages", messages);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return result;
    }

    // --- 工具方法 ---

    /**
     * 测试 5：检查配置注入情况
     */
    @GetMapping("/check-config")
    public Map<String, Object> checkConfig() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 从 Spring Environment 读取（这一定能读到）
        Map<String, String> envValues = new LinkedHashMap<>();
        envValues.put("feishu.bitable.app-token", env.getProperty("feishu.bitable.app-token"));
        envValues.put("feishu.bitable.qa-table-id", env.getProperty("feishu.bitable.qa-table-id"));
        envValues.put("feishu.bitable.feedback-table-id", env.getProperty("feishu.bitable.feedback-table-id"));
        envValues.put("feishu.bitable.miss-table-id", env.getProperty("feishu.bitable.miss-table-id"));
        envValues.put("feishu.bitable.operation-app-token", mask(env.getProperty("feishu.bitable.operation-app-token")));
        envValues.put("feishu.bitable.miss-app-token", mask(env.getProperty("feishu.bitable.miss-app-token")));
        envValues.put("feishu.app-id", mask(env.getProperty("feishu.app-id")));
        envValues.put("feishu.app-secret", configuredLabel(env.getProperty("feishu.app-secret")));
        result.put("from_environment", envValues);

        // 2. 通过反射从 LarkBitableService 读取 @Value 字段
        Map<String, String> fieldValues = new LinkedHashMap<>();
        String[] fieldNames = {"appToken", "operationAppToken", "missAppToken",
                "qaTableId", "feedbackTableId", "missTableId", "reviewTableId", "syncTableId"};
        for (String fieldName : fieldNames) {
            try {
                // 尝试从原始类获取（不是代理类）
                Class<?> targetClass = bitableService.getClass();
                // 如果是 CGLIB 代理，获取父类
                if (targetClass.getName().contains("$$")) {
                    targetClass = targetClass.getSuperclass();
                }
                Field field = targetClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(bitableService);
                fieldValues.put(fieldName, value != null ? value.toString() : "NULL");
            } catch (Exception e) {
                fieldValues.put(fieldName, "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        result.put("from_bitableService_fields", fieldValues);

        // 3. 检查 bitableService 的实际类（是否是代理）
        result.put("bitableService_class", bitableService.getClass().getName());
        result.put("bitableService_isProxy", bitableService.getClass().getName().contains("$$"));
        if (bitableService.getClass().getName().contains("$$")) {
            result.put("bitableService_superClass", bitableService.getClass().getSuperclass().getName());
        }

        // 4. 直接用 Environment 的值尝试写入多维表格
        String appToken = env.getProperty("feishu.bitable.app-token");
        String qaTableId = env.getProperty("feishu.bitable.qa-table-id");

        if (appToken != null && !appToken.isEmpty() && qaTableId != null && !qaTableId.isEmpty()) {
            result.put("write_test", testDirectWrite(appToken, qaTableId));
        } else {
            result.put("write_test", "跳过 - Environment 中也读不到配置");
        }

        return result;
    }

    private String configuredLabel(String value) {
        return value == null || value.isBlank() ? "未配置" : "已配置";
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "未配置";
        }
        if (value.length() <= 8) {
            return value.charAt(0) + "***" + value.charAt(value.length() - 1);
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }

    private String extractMessageText(String contentJson) {
        try {
            if (contentJson == null || contentJson.isBlank()) {
                return "";
            }
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(contentJson);
            return node.path("text").asText("");
        } catch (Exception e) {
            return contentJson;
        }
    }

    private String extractAllText(String contentJson) {
        try {
            if (contentJson == null || contentJson.isBlank()) {
                return "";
            }
            JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(contentJson);
            List<String> texts = new ArrayList<>();
            collectTextNodes(node, texts);
            return String.join("\n", texts);
        } catch (Exception e) {
            return contentJson;
        }
    }

    private void collectTextNodes(JsonNode node, List<String> texts) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String text = node.asText("");
            if (!text.isBlank()) {
                texts.add(text);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectTextNodes(item, texts);
            }
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectTextNodes(entry.getValue(), texts));
        }
    }

    private Map<String, Object> testDirectWrite(String appToken, String qaTableId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // 逐组字段测试，定位哪个字段类型不匹配
            Map<String, Object> testGroups = new LinkedHashMap<>();

            // 组 1：只发文本字段
            Map<String, Object> textOnly = new LinkedHashMap<>();
            textOnly.put("qa_id", "qa_debug_" + System.currentTimeMillis());
            textOnly.put("message_id", "msg_debug");
            textOnly.put("chat_id", "chat_debug");
            textOnly.put("user_id", "user_debug");
            textOnly.put("question", "调试测试问题");
            textOnly.put("answer_summary", "调试回答");
            textOnly.put("session_id", "session_debug");
            textOnly.put("knowledge_refs", "");
            testGroups.put("text_fields_only", textOnly);

            // 组 2：加 is_fallback（布尔值）
            Map<String, Object> withBool = new LinkedHashMap<>(textOnly);
            withBool.put("is_fallback", false);
            testGroups.put("with_is_fallback", withBool);

            // 组 3：加数字字段
            Map<String, Object> withNumbers = new LinkedHashMap<>(withBool);
            withNumbers.put("confidence", 0.8);
            withNumbers.put("response_time_ms", 100);
            testGroups.put("with_numbers", withNumbers);

            // 组 4：加 created_at（毫秒时间戳 — 旧方式，预期失败）
            Map<String, Object> withTimestamp = new LinkedHashMap<>(withNumbers);
            withTimestamp.put("created_at", Instant.now().toEpochMilli());
            testGroups.put("with_created_at_number_OLD", withTimestamp);

            // 组 5：created_at 用格式化字符串（修复后方式）
            Map<String, Object> withTimestampStr = new LinkedHashMap<>(withNumbers);
            withTimestampStr.put("created_at", java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.of("Asia/Shanghai"))
                    .format(Instant.now()));
            testGroups.put("with_created_at_string_FIXED", withTimestampStr);

            // 逐组测试
            for (Map.Entry<String, Object> entry : testGroups.entrySet()) {
                String groupName = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> fields = (Map<String, Object>) entry.getValue();

                try {
                    AppTableRecord record = AppTableRecord.newBuilder()
                            .fields(fields)
                            .build();

                    CreateAppTableRecordReq req = CreateAppTableRecordReq.newBuilder()
                            .appToken(appToken)
                            .tableId(qaTableId)
                            .appTableRecord(record)
                            .build();

                    CreateAppTableRecordResp resp = feishuClient.bitable().appTableRecord().create(req);
                    Map<String, Object> groupResult = new LinkedHashMap<>();
                    groupResult.put("apiCode", resp.getCode());
                    groupResult.put("apiMsg", resp.getMsg());
                    groupResult.put("success", resp.getCode() == 0);
                    if (resp.getData() != null && resp.getData().getRecord() != null) {
                        groupResult.put("recordId", resp.getData().getRecord().getRecordId());
                    }
                    result.put(groupName, groupResult);

                    // 记录首次失败位置，但继续测试后续组
                    if (resp.getCode() != 0 && !result.containsKey("first_failure_at")) {
                        result.put("first_failure_at", groupName);
                    }
                } catch (Exception e) {
                    Map<String, Object> errResult = new LinkedHashMap<>();
                    errResult.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                    result.put(groupName, errResult);
                }
            }

        } catch (Exception e) {
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return result;
    }

    private String buildTestCardJson() {
        var elements = new ArrayList<Map<String, Object>>();

        var contentElement = new LinkedHashMap<String, Object>();
        contentElement.put("tag", "markdown");
        contentElement.put("content", "这是一条**调试测试消息**。\n\n如果你能看到下方的两个按钮，说明卡片格式正确。");
        elements.add(contentElement);

        // schema 1.0：按钮用 action 包裹
        var action = new LinkedHashMap<String, Object>();
        action.put("tag", "action");
        var actions = new ArrayList<Map<String, Object>>();

        var usefulBtn = new LinkedHashMap<String, Object>();
        usefulBtn.put("tag", "button");
        var usefulText = new LinkedHashMap<String, Object>();
        usefulText.put("tag", "plain_text");
        usefulText.put("content", "👍 有用");
        usefulBtn.put("text", usefulText);
        usefulBtn.put("type", "primary");
        var usefulValue = new LinkedHashMap<String, Object>();
        usefulValue.put("action", "feedback");
        usefulValue.put("qa_id", "qa_debug_v3");
        usefulValue.put("feedback", "useful");
        usefulBtn.put("value", usefulValue);
        actions.add(usefulBtn);

        var uselessBtn = new LinkedHashMap<String, Object>();
        uselessBtn.put("tag", "button");
        var uselessText = new LinkedHashMap<String, Object>();
        uselessText.put("tag", "plain_text");
        uselessText.put("content", "👎 没用");
        uselessBtn.put("text", uselessText);
        uselessBtn.put("type", "danger");
        var uselessValue = new LinkedHashMap<String, Object>();
        uselessValue.put("action", "feedback");
        uselessValue.put("qa_id", "qa_debug_v3");
        uselessValue.put("feedback", "useless");
        uselessBtn.put("value", uselessValue);
        actions.add(uselessBtn);

        action.put("actions", actions);
        elements.add(action);

        var card = new LinkedHashMap<String, Object>();
        // schema 1.0：不用 schema 字段，不用 body 包裹

        var header = new LinkedHashMap<String, Object>();
        var titleObj = new LinkedHashMap<String, Object>();
        titleObj.put("tag", "plain_text");
        titleObj.put("content", "🧪 调试测试卡片 v3");
        header.put("title", titleObj);
        header.put("template", "blue");
        card.put("header", header);

        card.put("elements", elements);

        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(card);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String getFieldValue(String fieldName) {
        try {
            java.lang.reflect.Field field = LarkBitableService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(bitableService);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
