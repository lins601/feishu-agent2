package com.example.myapp.service;

import com.example.myapp.agent.KnowledgeAssistantAgent;
import com.example.myapp.dto.AgentResponse;
import com.example.myapp.model.FeedbackRecord;
import com.example.myapp.model.MissRecord;
import com.example.myapp.model.QuestionRecord;
import com.lark.oapi.Client;
import com.lark.oapi.event.cardcallback.model.*;
import com.lark.oapi.service.im.v1.enums.CreateMessageReceiveIdTypeEnum;
import com.lark.oapi.service.im.v1.model.*;
import com.lark.oapi.core.response.BaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 飞书消息服务 — 接收消息 → 调用 Agent → 回复飞书 → 记录运营数据。
 * <p>
 * 全链路：用户消息 → Agent 处理 → 飞书卡片回复（含反馈按钮） → 异步写入多维表格
 * <p>
 * 反馈机制支持两种方式：
 * 1. 卡片按钮点击（通过 WebSocket 或 HTTP card.action.trigger 回调）
 * 2. 文本反馈（"有用"/"没用"/"👍"/"👎"，通过消息接收识别）
 * <p>
 * 反馈关联优先级（PDR 10.6）：
 * 1. 用户引用机器人答案 → 通过 bot_reply_message_id 反查 qa_id
 * 2. parent_message_id → 通过父消息 ID 反查
 * 3. 最近 5 分钟唯一 SUCCESS 任务 → 按 chat_id + user_id 查找
 * 4. 多候选或无候选 → 提示用户引用具体答案
 */
@Service
public class FeishuMessageService {

    private static final Logger log = LoggerFactory.getLogger(FeishuMessageService.class);

    /** 反馈文本匹配关键词 */
    private static final Set<String> FEEDBACK_POSITIVE = Set.of("有用", "👍");
    private static final Set<String> FEEDBACK_NEGATIVE = Set.of("没用", "👎");

    /** QA 上下文过期时间：5 分钟（毫秒） */
    private static final long QA_CONTEXT_TTL_MS = 5 * 60 * 1000L;
    /** WISE 答案内的 Markdown 图片占位。图片需在同一位置替换成飞书 img 元素。 */
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)");
    /** WISE 明确表示当前知识库未覆盖时的常见措辞，仅用于展示“确认未命中”按钮。 */
    private static final Pattern LIKELY_KNOWLEDGE_MISS = Pattern.compile(
            "(?is)(知识库.{0,20}(没有|未收录|不存在|不包含|无相关)|超出.{0,12}知识库.{0,12}(范围|覆盖)|无法提供.{0,16}(可靠|可验证).{0,12}(解答|答案)|暂未.{0,12}(收录|覆盖|找到))");

    private final Client feishuClient;
    private final KnowledgeAssistantAgent agent;
    private final LarkBitableService bitableService;

    // ─── QA 上下文缓存（用于文本反馈关联）─────────────
    // bot_reply_message_id → QA 上下文（优先级 1、2 使用）
    private final ConcurrentHashMap<String, QaContext> replyMessageContext = new ConcurrentHashMap<>();
    // "chatId_userId" → 该用户最近的 QA 上下文列表（优先级 3 使用）
    private final ConcurrentHashMap<String, List<QaContext>> userRecentQa = new ConcurrentHashMap<>();
    // 防止同一反馈按钮被连点时并发写入多维表格。
    private final Set<String> feedbackInFlight = ConcurrentHashMap.newKeySet();

    public FeishuMessageService(Client feishuClient, KnowledgeAssistantAgent agent,
                                 LarkBitableService bitableService) {
        this.feishuClient = feishuClient;
        this.agent = agent;
        this.bitableService = bitableService;
    }

    /**
     * QA 上下文 — 记录每次成功回复的问答信息，用于后续文本反馈关联。
     */
    private static class QaContext {
        final String qaId;
        final String question;
        final String answerSummary;
        final String knowledgeRefs;
        final String userId;
        final String chatId;
        final long timestamp;

        QaContext(String qaId, String question, String answerSummary, String knowledgeRefs,
                  String userId, String chatId, long timestamp) {
            this.qaId = qaId;
            this.question = question;
            this.answerSummary = answerSummary;
            this.knowledgeRefs = knowledgeRefs;
            this.userId = userId;
            this.chatId = chatId;
            this.timestamp = timestamp;
        }
    }

    // ═══════════════════════════════════════════════════
    //  消息处理入口
    // ═══════════════════════════════════════════════════

    /**
     * 处理用户消息（异步，避免飞书 3 秒超时）。
     *
     * @param parentId 用户消息的 parent_id（引用回复时被引用消息的 ID），可为空
     */
    @Async
    public void handleUserMessage(String openId, String chatId, String chatType,
                                   String messageId, String textContent, String parentId) {
        log.info("开始处理用户消息: chatType={}, text={}, parentId={}", chatType, textContent, parentId);

        // ─── 判断是否为文本反馈 ──────────────────────
        String feedbackType = detectFeedback(textContent);
        if (feedbackType != null) {
            processTextFeedback(openId, chatId, chatType, messageId, parentId, feedbackType);
            return;
        }

        // ─── 正常问答流程 ────────────────────────────
        long start = System.currentTimeMillis();
        String qaId = LarkBitableService.generateId("qa");

        try {
            // 1. 先回复"正在查询中…"
            try {
                // 统一用 reply 方式发送占位消息
                replyTextMessage(messageId, "正在查询中…");
            } catch (Exception e) {
                log.warn("发送占位消息失败，继续处理", e);
            }

            // 2. 调用 Agent 处理
            AgentResponse response = agent.process(textContent);

            // 3. 构建飞书回复（含反馈按钮）
            String cardJson = buildResponseCard(response, textContent, qaId);

            // 4. 回复飞书，并捕获 bot_reply_message_id
            String botReplyMessageId = sendAndCaptureReply(chatType, openId, messageId, cardJson);

            long elapsed = System.currentTimeMillis() - start;

            // 5. 缓存 QA 上下文（用于后续文本反馈关联）
            // 表格保留较完整的 WISE 最终回答，便于后续检索与追溯。
            String answerSummary = truncate(response.getAnswerText(), 5000);
            String knowledgeRefs = "";
            if (botReplyMessageId != null) {
                QaContext ctx = new QaContext(qaId, textContent, answerSummary, knowledgeRefs,
                        openId, chatId, System.currentTimeMillis());
                replyMessageContext.put(botReplyMessageId, ctx);
            }
            // 同时按 user 维度缓存
            String userKey = chatId + "_" + openId;
            userRecentQa.computeIfAbsent(userKey, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new QaContext(qaId, textContent, answerSummary, knowledgeRefs,
                            openId, chatId, System.currentTimeMillis()));

            // 6. 写入问答记录到多维表格
            String sessionId = chatId + "_" + openId;
            QuestionRecord qaRecord = QuestionRecord.builder()
                    .qaId(qaId)
                    .messageId(messageId)
                    .chatId(chatId)
                    .userId(openId)
                    .question(textContent)
                    .answerSummary(answerSummary)
                    .sessionId(sessionId)
                    .knowledgeRefs(knowledgeRefs)
                    .fallback(!response.isSuccess())
                    .confidence(response.isSuccess() ? 0.8 : 0.0)
                    .responseTimeMs(elapsed)
                    .createdAt(Instant.now().toEpochMilli())
                    .build();

            bitableService.saveQuestionRecord(qaRecord);

            // 7. 未命中问题写入 miss_question
            if (!response.isSuccess() && !response.isDegraded()) {
                MissRecord missRecord = MissRecord.builder()
                        .question(textContent)
                        .normalizedQuestion(textContent)
                        .count(1)
                        .status("pending")
                        .userId(openId)
                        .chatId(chatId)
                        .owner("")
                        .createdAt(Instant.now().toEpochMilli())
                        .updatedAt(Instant.now().toEpochMilli())
                        .build();
                bitableService.saveMissRecord(missRecord);
            }

            // 8. 清理过期缓存
            cleanExpiredContext();

            log.info("消息处理完成: chatType={}, qaId={}, botReplyId={}, success={}, 耗时={}ms",
                    chatType, qaId, botReplyMessageId, response.isSuccess(), elapsed);

        } catch (Exception e) {
            log.error("处理用户消息异常: text={}", textContent, e);
            try {
                String fallback = "⚠️ 处理消息时出现异常，请稍后重试。";
                if ("p2p".equals(chatType)) {
                    sendTextMessage(openId, CreateMessageReceiveIdTypeEnum.OPEN_ID, fallback);
                } else {
                    replyTextMessage(messageId, fallback);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 兼容旧签名（无 parentId 时调用）。
     */
    @Async
    public void handleUserMessage(String openId, String chatId, String chatType,
                                   String messageId, String textContent) {
        handleUserMessage(openId, chatId, chatType, messageId, textContent, null);
    }

    // ═══════════════════════════════════════════════════
    //  文本反馈处理（PDR 10.6 完整优先级链）
    // ═══════════════════════════════════════════════════

    /**
     * 处理文本反馈。
     * <p>
     * 反馈关联优先级：
     * 1. 用户引用了机器人答案 → 通过被引用消息 ID 反查 qa_id
     * 2. parent_message_id → 通过父消息 ID 反查
     * 3. 最近 5 分钟唯一 SUCCESS 任务 → 按 chat_id + user_id 查找
     * 4. 多候选或无候选 → 提示引用
     */
    private void processTextFeedback(String openId, String chatId, String chatType,
                                      String messageId, String parentId, String feedbackType) {
        log.info("检测到文本反馈: user={}, feedback={}, parentId={}", openId, feedbackType, parentId);
        QaContext matched = null;

        // ─── 优先级 1：用户引用了机器人答案 ──────────
        // 飞书引用回复时，被引用消息的 ID 会出现在 parent_id 中
        if (parentId != null && !parentId.isEmpty()) {
            matched = replyMessageContext.get(parentId);
            if (matched != null) {
                log.info("反馈关联（优先级 1 - 引用回复）: qa_id={}", matched.qaId);
            }
        }

        // ─── 优先级 2：parent_message_id ─────────────
        if (matched == null && parentId != null && !parentId.isEmpty()) {
            // parent_id 可能指向用户自己的提问消息，尝试通过 userKey + 时间窗口查找
            // 此处优先级 2 和优先级 3 共用 userRecentQa 缓存
        }

        // ─── 优先级 3：最近 5 分钟唯一 SUCCESS 任务 ──
        if (matched == null) {
            String userKey = chatId + "_" + openId;
            List<QaContext> recentList = userRecentQa.get(userKey);
            if (recentList != null) {
                long now = System.currentTimeMillis();
                List<QaContext> validContexts = recentList.stream()
                        .filter(ctx -> (now - ctx.timestamp) <= QA_CONTEXT_TTL_MS)
                        .collect(Collectors.toList());

                if (validContexts.size() == 1) {
                    matched = validContexts.get(0);
                    log.info("反馈关联（优先级 3 - 最近唯一任务）: qa_id={}", matched.qaId);
                } else if (validContexts.size() > 1) {
                    // ─── 优先级 4：多候选 → 提示引用 ──
                    log.info("反馈关联（优先级 4 - 多候选）: userKey={}, count={}", userKey, validContexts.size());
                    try {
                        String hint = "您最近提了多个问题，请引用具体答案后再反馈（长按答案 → 引用 → 输入\"有用\"或\"没用\"），避免关联错误。";
                        if ("p2p".equals(chatType)) {
                            sendTextMessage(openId, CreateMessageReceiveIdTypeEnum.OPEN_ID, hint);
                        } else {
                            replyTextMessage(messageId, hint);
                        }
                    } catch (Exception e) {
                        log.warn("发送多候选提示失败", e);
                    }
                    return;
                }
            }
        }

        // ─── 优先级 4：无候选 ────────────────────────
        if (matched == null) {
            log.info("反馈关联（优先级 4 - 无候选）: user={}, chatId={}", openId, chatId);
            try {
                String hint = "未找到可反馈的答案。请先提问，收到回复后再反馈\"有用\"或\"没用\"。";
                if ("p2p".equals(chatType)) {
                    sendTextMessage(openId, CreateMessageReceiveIdTypeEnum.OPEN_ID, hint);
                } else {
                    replyTextMessage(messageId, hint);
                }
            } catch (Exception e) {
                log.warn("发送无候选提示失败", e);
            }
            return;
        }

        // ─── 成功关联，写入反馈记录 ─────────────────
        saveFeedback(matched, openId, feedbackType, chatType, messageId);
    }

    /**
     * 写入反馈记录并回复用户确认。
     */
    private void saveFeedback(QaContext ctx, String openId, String feedbackType,
                               String chatType, String messageId) {
        FeedbackRecord record = FeedbackRecord.builder()
                .feedbackId(LarkBitableService.generateId("fb"))
                .qaId(ctx.qaId)
                .userId(openId)
                .question(ctx.question)
                .answerSummary(ctx.answerSummary)
                .knowledgeRefs(ctx.knowledgeRefs)
                .feedback(feedbackType)
                .createdAt(Instant.now().toEpochMilli())
                .build();

        bitableService.handleFeedback(record);

        // 回复确认
        try {
            String reply = "useful".equals(feedbackType)
                    ? "✅ 感谢您的反馈！已记录为「有用」👍"
                    : "📝 感谢您的反馈！已记录为「没用」，我们会持续优化知识库。";
            if ("p2p".equals(chatType)) {
                sendTextMessage(openId, CreateMessageReceiveIdTypeEnum.OPEN_ID, reply);
            } else {
                replyTextMessage(messageId, reply);
            }
        } catch (Exception e) {
            log.warn("发送反馈确认失败", e);
        }
    }

    // ═══════════════════════════════════════════════════
    //  卡片交互回调处理（按钮点击）
    // ═══════════════════════════════════════════════════

    /**
     * 处理用户点击反馈按钮的回调。
     * <p>
     * 由 FeishuConfig 中的 onP2CardActionTrigger handler 调用。
     */
    public P2CardActionTriggerResponse handleFeedbackAction(P2CardActionTrigger event) {
        P2CardActionTriggerData data = event.getEvent();
        CallBackOperator operator = data.getOperator();
        CallBackAction action = data.getAction();
        return handleFeedbackAction(operator != null ? operator.getOpenId() : "",
                action != null ? action.getValue() : Map.of());
    }

    public P2CardActionTriggerResponse handleFeedbackAction(String openId, Map<String, Object> actionValue) {
        String qaId = stringValue(actionValue.get("qa_id"));
        String feedbackType = stringValue(actionValue.get("feedback"));
        String question = stringValue(actionValue.getOrDefault("question", ""));
        String answerSummary = stringValue(actionValue.getOrDefault("answer_summary", ""));
        String knowledgeRefs = stringValue(actionValue.getOrDefault("knowledge_refs", ""));

        if (qaId.isBlank() || feedbackType.isBlank()) {
            log.warn("卡片按钮反馈缺少必要字段: user={}, value={}", openId, actionValue);
            return feedbackToast("warning", "反馈信息不完整，请直接回复「有用」或「没用」。");
        }

        log.info("卡片按钮反馈: qa_id={}, user={}, feedback={}", qaId, openId, feedbackType);

        String feedbackKey = qaId + "_" + openId;
        if (!feedbackInFlight.add(feedbackKey)) {
            return feedbackToast("info", "反馈正在记录，请勿重复点击。");
        }

        // “该知识暂不存在”由用户确认后才写未命中表，避免模型自身误判直接污染运营数据。
        if ("knowledge_missing".equals(feedbackType)) {
            long now = Instant.now().toEpochMilli();
            MissRecord missRecord = MissRecord.builder()
                    .question(question)
                    .normalizedQuestion(question)
                    .count(1)
                    .status("pending")
                    .userId(openId)
                    .chatId("")
                    .owner("")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            bitableService.saveMissRecord(missRecord);
            CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS)
                    .execute(() -> feedbackInFlight.remove(feedbackKey));
            return feedbackToast("success", "已记录为未命中问题，管理员会补充知识库。📝");
        }

        // 写入反馈记录（异步）
        FeedbackRecord record = FeedbackRecord.builder()
                .feedbackId(LarkBitableService.generateId("fb"))
                .qaId(qaId)
                .userId(openId)
                .question(question)
                .answerSummary(answerSummary)
                .knowledgeRefs(knowledgeRefs)
                .feedback(feedbackType)
                .createdAt(Instant.now().toEpochMilli())
                .build();

        bitableService.handleFeedback(record);
        // 多维表格写入会在后台完成；短暂防抖可避免飞书重复回调或用户连点造成并发写入。
        CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS)
                .execute(() -> feedbackInFlight.remove(feedbackKey));
        return feedbackToast("success",
                "useful".equals(feedbackType) ? "感谢您的反馈！👍" : "感谢您的反馈，我们会持续优化知识库 📝");
    }

    private P2CardActionTriggerResponse feedbackToast(String type, String content) {
        P2CardActionTriggerResponse response = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();
        toast.setType(type);
        toast.setContent(content);
        response.setToast(toast);
        return response;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    // ═══════════════════════════════════════════════════
    //  反馈检测
    // ═══════════════════════════════════════════════════

    /**
     * 检测文本是否为反馈。
     *
     * @return "useful" / "useless" / null（非反馈）
     */
    private String detectFeedback(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();

        if (FEEDBACK_POSITIVE.contains(trimmed)) {
            return "useful";
        }
        if (FEEDBACK_NEGATIVE.contains(trimmed)) {
            return "useless";
        }
        return null;
    }

    // ═══════════════════════════════════════════════════
    //  消息发送（含 bot_reply_message_id 捕获）
    // ═══════════════════════════════════════════════════

    /**
     * 发送卡片回复并捕获 bot_reply_message_id。
     *
     * @return 机器人回复消息的 message_id，失败返回 null
     */
    private String sendAndCaptureReply(String chatType, String openId, String userMessageId,
                                        String cardJson) {
        try {
            // 统一使用 reply 方式回复（p2p 和群聊都用回复，卡片紧跟在问题下方）
            return replyCardMessageCaptureId(userMessageId, cardJson);
        } catch (Exception e) {
            log.error("发送卡片并捕获 ID 失败", e);
            return null;
        }
    }

    private String sendCardMessageCaptureId(String openId, String cardJson) throws Exception {
        CreateMessageReq req = CreateMessageReq.newBuilder()
                .receiveIdType(CreateMessageReceiveIdTypeEnum.OPEN_ID)
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId(openId)
                        .msgType("interactive")
                        .content(cardJson)
                        .build())
                .build();

        CreateMessageResp resp = feishuClient.im().message().create(req);
        checkResponse("发送卡片消息", openId, resp);
        if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
            return resp.getData().getMessageId();
        }
        return null;
    }

    private String replyCardMessageCaptureId(String messageId, String cardJson) throws Exception {
        ReplyMessageReq req = ReplyMessageReq.newBuilder()
                .messageId(messageId)
                .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                        .msgType("interactive")
                        .content(cardJson)
                        .build())
                .build();

        ReplyMessageResp resp = feishuClient.im().message().reply(req);
        checkResponse("回复卡片消息", messageId, resp);
        if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
            return resp.getData().getMessageId();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════
    //  缓存清理
    // ═══════════════════════════════════════════════════

    /**
     * 清理过期的 QA 上下文（超过 5 分钟）。
     */
    private void cleanExpiredContext() {
        long now = System.currentTimeMillis();

        // 清理 replyMessageContext
        replyMessageContext.entrySet().removeIf(entry ->
                (now - entry.getValue().timestamp) > QA_CONTEXT_TTL_MS);

        // 清理 userRecentQa
        userRecentQa.forEach((key, list) -> {
            list.removeIf(ctx -> (now - ctx.timestamp) > QA_CONTEXT_TTL_MS);
            if (list.isEmpty()) {
                userRecentQa.remove(key);
            }
        });
    }

    // ═══════════════════════════════════════════════════
    //  欢迎消息
    // ═══════════════════════════════════════════════════

    public void sendWelcomeMessage(String openId, String chatId) {
        String cardJson = buildWelcomeCard();
        try {
            sendCardMessage(openId, CreateMessageReceiveIdTypeEnum.OPEN_ID, cardJson);
        } catch (Exception e) {
            log.error("发送欢迎消息失败: openId={}", openId, e);
        }
    }

    // ═══════════════════════════════════════════════════
    //  卡片构建
    // ═══════════════════════════════════════════════════

    private String buildResponseCard(AgentResponse response, String question, String qaId) {
        var elements = new ArrayList<Map<String, Object>>();

        var contentElement = new LinkedHashMap<String, Object>();
        contentElement.put("tag", "markdown");
        String rawAnswer = response.getAnswerText() == null ? "" : response.getAnswerText();
        int expectedImageCount = countMarkdownImages(rawAnswer);
        boolean answerReferencesImages = expectedImageCount > 0;
        List<String> imageKeys = new ArrayList<>();
        if (response.getImageDataUris() != null) {
            for (String imageDataUri : response.getImageDataUris()) {
                String imageKey = uploadBase64Image(imageDataUri);
                if (!imageKey.isBlank()) {
                    imageKeys.add(imageKey);
                }
            }
        }

        // 正确性优先：WISE 回答明确含图而图片未同步成功时，绝不能发一份缺图的“完整答案”。
        if (answerReferencesImages && imageKeys.size() < expectedImageCount) {
            contentElement.put("content", "⚠️ 本答案引用了知识库图片，但图片同步到飞书失败。为避免缺图导致操作错误，正文未发布；请稍后重试。管理员可在问答记录中查看原始答案并排查图片同步日志。");
            elements.add(contentElement);
            return buildCard("⚠️ 制造知识助手（图片同步失败）", "orange", elements);
        }

        // 逐个用飞书 img_key 替换 WISE 的 Markdown 图片占位，保持“文字解释 → 图片”的原始顺序。
        appendAnswerElements(elements, rawAnswer, imageKeys);
        boolean likelyKnowledgeMiss = response.isSuccess() && isLikelyKnowledgeMiss(rawAnswer);
        elements.add(buildFeedbackAction(qaId, question, truncate(response.getAnswerText(), 5000), likelyKnowledgeMiss));

        String headerTitle;
        String template;
        if (response.isDegraded()) {
            headerTitle = "⚠️ 制造知识助手（降级模式）";
            template = "red";
        } else if (response.isSuccess()) {
            headerTitle = "✅ 制造知识助手";
            template = "green";
        } else {
            headerTitle = "📋 制造知识助手";
            template = "blue";
        }
        return buildCard(headerTitle, template, elements);
    }

    private String buildCard(String headerTitle, String template, List<Map<String, Object>> elements) {
        var card = new LinkedHashMap<String, Object>();
        var header = new LinkedHashMap<String, Object>();
        var titleObj = new LinkedHashMap<String, Object>();
        titleObj.put("tag", "plain_text");
        titleObj.put("content", headerTitle);
        header.put("title", titleObj);
        header.put("template", template);
        card.put("header", header);
        card.put("elements", elements);

        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(card);
        } catch (Exception e) {
            return buildFallbackTextCard("卡片构建失败，请稍后重试。");
        }
    }

    private int countMarkdownImages(String markdown) {
        if (markdown == null) return 0;
        int count = 0;
        Matcher matcher = MARKDOWN_IMAGE.matcher(markdown);
        while (matcher.find()) count++;
        return count;
    }

    private void appendAnswerElements(List<Map<String, Object>> elements, String rawAnswer, List<String> imageKeys) {
        String answerForCard = truncate(rawAnswer, 5000);
        Matcher matcher = MARKDOWN_IMAGE.matcher(answerForCard);
        int textStart = 0;
        int imageIndex = 0;
        while (matcher.find()) {
            addMarkdownElement(elements, answerForCard.substring(textStart, matcher.start()));
            String alt = matcher.group(1).isBlank() ? "知识库图片" : matcher.group(1);
            addImageElement(elements, imageKeys.get(imageIndex++), alt);
            textStart = matcher.end();
        }
        String tail = answerForCard.substring(textStart);
        if (rawAnswer.length() > answerForCard.length()) {
            tail += "\n\n> 回答较长，完整内容已存入多维表格。";
        }
        addMarkdownElement(elements, tail);
    }

    private void addMarkdownElement(List<Map<String, Object>> elements, String content) {
        if (content == null || content.isBlank()) return;
        var markdown = new LinkedHashMap<String, Object>();
        markdown.put("tag", "markdown");
        markdown.put("content", normalizeMarkdownForFeishu(content));
        elements.add(markdown);
    }

    /** 飞书卡片对 ATX 标题兼容性不一致，统一呈现为加粗标题，避免用户看到 #。 */
    private String normalizeMarkdownForFeishu(String content) {
        Matcher heading = Pattern.compile("(?m)^#{1,6}\\s+(.+?)\\s*$").matcher(content.trim());
        StringBuffer formatted = new StringBuffer();
        while (heading.find()) {
            heading.appendReplacement(formatted, Matcher.quoteReplacement("**" + heading.group(1).trim() + "**"));
        }
        heading.appendTail(formatted);
        return formatted.toString();
    }

    private boolean isLikelyKnowledgeMiss(String answer) {
        return answer != null && LIKELY_KNOWLEDGE_MISS.matcher(answer).find();
    }

    private void addImageElement(List<Map<String, Object>> elements, String imageKey, String alt) {
        var image = new LinkedHashMap<String, Object>();
        image.put("tag", "img");
        image.put("img_key", imageKey);
        image.put("alt", Map.of("tag", "plain_text", "content", alt));
        elements.add(image);
    }

    /**
     * 构建反馈按钮区域（schema 1.0 格式：action 包裹按钮）。
     */
    private Map<String, Object> buildFeedbackAction(String qaId, String question, String answerSummary,
                                                     boolean likelyKnowledgeMiss) {
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
        usefulValue.put("qa_id", qaId);
        usefulValue.put("feedback", "useful");
        usefulValue.put("question", question);
        usefulValue.put("answer_summary", answerSummary);
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
        uselessValue.put("qa_id", qaId);
        uselessValue.put("feedback", "useless");
        uselessValue.put("question", question);
        uselessValue.put("answer_summary", answerSummary);
        uselessBtn.put("value", uselessValue);
        actions.add(uselessBtn);

        if (likelyKnowledgeMiss) {
            var missingBtn = new LinkedHashMap<String, Object>();
            missingBtn.put("tag", "button");
            missingBtn.put("text", Map.of("tag", "plain_text", "content", "📝 该知识暂不存在"));
            var missingValue = new LinkedHashMap<String, Object>();
            missingValue.put("action", "feedback");
            missingValue.put("qa_id", qaId);
            missingValue.put("feedback", "knowledge_missing");
            missingValue.put("question", question);
            missingValue.put("answer_summary", answerSummary);
            missingBtn.put("value", missingValue);
            actions.add(missingBtn);
        }

        action.put("actions", actions);
        return action;
    }

    private String buildFallbackTextCard(String text) {
        return "{\"header\":{\"title\":{\"tag\":\"plain_text\",\"content\":\"制造知识助手\"},\"template\":\"blue\"},\"elements\":[{\"tag\":\"markdown\",\"content\":\"" +
                escapeJson(text) + "\"}]}";
    }

    private String uploadBase64Image(String dataUri) {
        try {
            return CompletableFuture.supplyAsync(() -> uploadBase64ImageBlocking(dataUri))
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("上传知识库图片超时或失败，跳过图片以保证正文及时回复", e);
            return "";
        }
    }

    private String uploadBase64ImageBlocking(String dataUri) {
        try {
            int comma = dataUri.indexOf(',');
            if (comma < 0) return "";
            String mediaType = dataUri.substring(5, dataUri.indexOf(';')).toLowerCase(Locale.ROOT);
            String suffix = mediaType.contains("jpeg") || mediaType.contains("jpg") ? ".jpg"
                    : mediaType.contains("gif") ? ".gif" : ".png";
            Path file = Files.createTempFile("wise-image-", suffix);
            Files.write(file, Base64.getMimeDecoder().decode(dataUri.substring(comma + 1)));
            try {
                var req = CreateImageReq.newBuilder().createImageReqBody(CreateImageReqBody.newBuilder()
                        .imageType("message").image(file.toFile()).build()).build();
                var resp = feishuClient.im().image().create(req);
                return resp != null && resp.getCode() == 0 && resp.getData() != null
                        ? resp.getData().getImageKey() : "";
            } finally { Files.deleteIfExists(file); }
        } catch (Exception e) {
            log.warn("上传知识库图片到飞书失败", e);
            return "";
        }
    }

    private String buildWelcomeCard() {
        var elements = new ArrayList<Map<String, Object>>();

        var e1 = new LinkedHashMap<String, Object>();
        e1.put("tag", "markdown");
        e1.put("content", "我可以帮你解答 **WMS（仓储）**、**QMS（品质）**、**MES（制造执行）** 相关的常见问题。\n\n直接输入你的问题即可，例如：");
        elements.add(e1);

        var e2 = new LinkedHashMap<String, Object>();
        e2.put("tag", "markdown");
        e2.put("content", "- IQC检验单提交失败怎么办？\n- 如何创建盘点单？\n- 工单报工状态不允许怎么处理？");
        elements.add(e2);

        var card = new LinkedHashMap<String, Object>();

        var header = new LinkedHashMap<String, Object>();
        var titleObj = new LinkedHashMap<String, Object>();
        titleObj.put("tag", "plain_text");
        titleObj.put("content", "👋 制造知识助手");
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

    // ═══════════════════════════════════════════════════
    //  消息发送（公开方法，供外部调用）
    // ═══════════════════════════════════════════════════

    public void sendCardMessage(String receiveId, CreateMessageReceiveIdTypeEnum idType,
                                 String cardJson) throws Exception {
        CreateMessageReq req = CreateMessageReq.newBuilder()
                .receiveIdType(idType)
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId(receiveId)
                        .msgType("interactive")
                        .content(cardJson)
                        .build())
                .build();

        CreateMessageResp resp = feishuClient.im().message().create(req);
        checkResponse("发送卡片消息", receiveId, resp);
    }

    public void replyCardMessage(String messageId, String cardJson) throws Exception {
        ReplyMessageReq req = ReplyMessageReq.newBuilder()
                .messageId(messageId)
                .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                        .msgType("interactive")
                        .content(cardJson)
                        .build())
                .build();

        ReplyMessageResp resp = feishuClient.im().message().reply(req);
        checkResponse("回复卡片消息", messageId, resp);
    }

    public void sendTextMessage(String receiveId, CreateMessageReceiveIdTypeEnum idType,
                                 String text) throws Exception {
        CreateMessageReq req = CreateMessageReq.newBuilder()
                .receiveIdType(idType)
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId(receiveId)
                        .msgType("text")
                        .content("{\"text\":\"" + escapeJson(text) + "\"}")
                        .build())
                .build();

        CreateMessageResp resp = feishuClient.im().message().create(req);
        checkResponse("发送文本消息", receiveId, resp);
    }

    public void replyTextMessage(String messageId, String text) throws Exception {
        ReplyMessageReq req = ReplyMessageReq.newBuilder()
                .messageId(messageId)
                .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                        .msgType("text")
                        .content("{\"text\":\"" + escapeJson(text) + "\"}")
                        .build())
                .build();

        ReplyMessageResp resp = feishuClient.im().message().reply(req);
        checkResponse("回复文本消息", messageId, resp);
    }

    // ═══════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════

    private void checkResponse(String action, String id, BaseResponse<?> resp) {
        if (resp == null || resp.getCode() != 0) {
            log.error("{}失败: id={}, code={}, msg={}", action, id,
                    resp != null ? resp.getCode() : "null",
                    resp != null ? resp.getMsg() : "null");
        } else {
            log.debug("{}成功: id={}", action, id);
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }
}
