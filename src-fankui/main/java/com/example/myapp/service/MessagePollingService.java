package com.example.myapp.service;

import com.example.myapp.model.FeedbackRecord;
import com.example.myapp.model.MissRecord;
import com.example.myapp.model.QuestionRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.ListMessageReq;
import com.lark.oapi.service.im.v1.model.ListMessageResp;
import com.lark.oapi.service.im.v1.model.Message;
import com.lark.oapi.service.im.v1.model.ReplyMessageReq;
import com.lark.oapi.service.im.v1.model.ReplyMessageReqBody;
import com.lark.oapi.service.im.v1.model.ReplyMessageResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轮询 WISE 绑定的飞书会话，并在机器人回答结束后异步补发反馈卡片。
 */
@Service
public class MessagePollingService {

    private static final Logger log = LoggerFactory.getLogger(MessagePollingService.class);
    private static final Set<String> FEEDBACK_WORDS = Set.of("有用", "没用", "👍", "👎");
    private static final int PAGE_SIZE = 50;
    private static final int SEND_MAX_ATTEMPTS = 3;
    private static final long PROCESSED_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final Pattern KNOWLEDGE_FILE_PATTERN =
            Pattern.compile("[A-Za-z0-9_./-]+\\.(?:md|pdf|docx?|xlsx?)");

    private final Client feishuClient;
    private final TaskExecutor feedbackTaskExecutor;
    private final ObjectMapper objectMapper;
    private final LarkBitableService bitableService;
    private final Map<String, Long> processedMessageIds = new ConcurrentHashMap<>();
    private final Map<String, List<QaContext>> recentQaByUser = new ConcurrentHashMap<>();
    private final Map<String, QaContext> qaByReplyMessageId = new ConcurrentHashMap<>();
    private final Map<String, QaContext> qaByUserMessageId = new ConcurrentHashMap<>();
    private final long serviceStartedAt = System.currentTimeMillis();

    @Value("${feishu.feedback.enabled:true}")
    private boolean enabled;

    @Value("${feishu.feedback.chat-id:}")
    private String chatIds;

    @Value("${feishu.feedback.reply-timeout-ms:120000}")
    private long replyTimeoutMs;

    @Value("${feishu.feedback.reply-poll-interval-ms:2000}")
    private long replyPollIntervalMs;

    @Value("${feishu.feedback.reply-stable-ms:5000}")
    private long replyStableMs;

    @Value("${feishu.feedback.send-on-timeout:false}")
    private boolean sendOnTimeout;

    @Value("${feishu.feedback.miss-notice.enabled:true}")
    private boolean missNoticeEnabled;

    @Value("${feishu.feedback.timeout-notice.enabled:true}")
    private boolean timeoutNoticeEnabled;

    @Value("${feishu.feedback.max-question-length:500}")
    private int maxQuestionLength;

    @Value("${feishu.feedback.context-ttl-ms:300000}")
    private long qaContextTtlMs;

    @Value("${feishu.feedback.card-actions-enabled:true}")
    private boolean cardActionsEnabled;

    public MessagePollingService(Client feishuClient,
                                 @Qualifier("feedbackTaskExecutor") TaskExecutor feedbackTaskExecutor,
                                 ObjectMapper objectMapper,
                                 LarkBitableService bitableService) {
        this.feishuClient = feishuClient;
        this.feedbackTaskExecutor = feedbackTaskExecutor;
        this.objectMapper = objectMapper;
        this.bitableService = bitableService;
    }

    private record QaContext(String qaId, String question, String answerSummary, String knowledgeRefs,
                             String userId, String chatId, String userMessageId,
                             String replyMessageId, long timestamp) {
    }

    private record FeedbackMatch(QaContext context, boolean ambiguous) {
    }

    /**
     * 发现新问题后仅提交异步任务，不阻塞 Spring 的调度线程。
     */
    @Scheduled(fixedDelayString = "${feishu.feedback.message-poll-interval-ms:5000}")
    public void pollMessages() {
        if (!enabled) {
            return;
        }

        List<String> monitorChatIds = configuredChatIds();
        if (monitorChatIds.isEmpty()) {
            return;
        }

        for (String monitorChatId : monitorChatIds) {
            pollChatMessages(monitorChatId);
        }

        cleanProcessedMessages();
    }

    private void pollChatMessages(String monitorChatId) {
        try {
            long startSeconds = Math.max(0, serviceStartedAt / 1000 - 5);
            Message[] items = listMessages(monitorChatId, startSeconds);
            if (items == null) {
                return;
            }

            ArrayList<Message> orderedMessages = new ArrayList<>();
            for (Message item : items) {
                orderedMessages.add(item);
            }
            orderedMessages.sort(Comparator.comparingLong(this::createTime));

            for (Message message : orderedMessages) {
                if (!isNewUserMessage(message)) {
                    continue;
                }

                String messageId = message.getMessageId();
                if (!"text".equals(message.getMsgType())) {
                    if (processedMessageIds.putIfAbsent(messageId, System.currentTimeMillis()) == null) {
                        replyText(messageId, "暂仅支持文字提问，请用文字描述您的问题。");
                    }
                    continue;
                }

                String question = extractText(message.getBody() != null
                        ? message.getBody().getContent() : "");
                if (question.isBlank()) {
                    continue;
                }

                String feedbackType = detectFeedback(question);
                if (feedbackType != null) {
                    if (processedMessageIds.putIfAbsent(messageId, System.currentTimeMillis()) == null) {
                        handleTextFeedback(monitorChatId, senderId(message), messageId,
                                feedbackType, referencedMessageId(message));
                    }
                    continue;
                }

                if (isIgnorablePureEmoji(question)) {
                    processedMessageIds.putIfAbsent(messageId, System.currentTimeMillis());
                    continue;
                }

                String normalizedQuestion = normalizeQuestionLength(messageId, question);
                if (processedMessageIds.putIfAbsent(messageId, System.currentTimeMillis()) == null) {
                    log.info("检测到新问题，提交反馈监控任务: messageId={}, question={}", messageId, normalizedQuestion);
                    feedbackTaskExecutor.execute(() -> monitorReplyAndSendCard(
                            monitorChatId, messageId, createTime(message), normalizedQuestion, senderId(message)));
                }
            }
        } catch (Exception e) {
            log.warn("轮询飞书消息失败: chatId={}", monitorChatId, e);
        }
    }

    private List<String> configuredChatIds() {
        if (chatIds == null || chatIds.isBlank()) {
            return List.of();
        }
        ArrayList<String> ids = new ArrayList<>();
        for (String id : chatIds.split("[,，\\s]+")) {
            if (!id.isBlank()) {
                ids.add(id.trim());
            }
        }
        return ids;
    }

    /**
     * 长连接收到用户提问时调用。这里使用事件中的真实 chat_id，避免固定会话 ID 不匹配。
     */
    public void scheduleFeedbackCard(String eventChatId, String userMessageId,
                                      String question, String createTime, String userId, String parentId) {
        if (!enabled || eventChatId == null || eventChatId.isBlank()
                || userMessageId == null || userMessageId.isBlank()
                || question == null || question.isBlank()
                || isIgnorablePureEmoji(question)) {
            return;
        }

        if (processedMessageIds.putIfAbsent(userMessageId, System.currentTimeMillis()) != null) {
            log.debug("反馈监控任务已存在，跳过: messageId={}", userMessageId);
            return;
        }

        String feedbackType = detectFeedback(question);
        if (feedbackType != null) {
            handleTextFeedback(eventChatId, userId, userMessageId, feedbackType, parentId);
            return;
        }

        String normalizedQuestion = normalizeQuestionLength(userMessageId, question);
        long questionCreateTime = parseTimestamp(createTime);
        log.info("提交事件驱动反馈监控任务: chatId={}, messageId={}, question={}",
                eventChatId, userMessageId, normalizedQuestion);
        try {
            feedbackTaskExecutor.execute(() -> monitorReplyAndSendCard(
                    eventChatId, userMessageId, questionCreateTime, normalizedQuestion, userId));
        } catch (TaskRejectedException e) {
            processedMessageIds.remove(userMessageId);
            log.warn("反馈监控任务提交失败，线程池可能正在关闭: messageId={}", userMessageId, e);
        }
    }

    public void scheduleFeedbackCard(String eventChatId, String userMessageId,
                                      String question, String createTime, String userId) {
        scheduleFeedbackCard(eventChatId, userMessageId, question, createTime, userId, null);
    }

    public void scheduleFeedbackCard(String eventChatId, String userMessageId,
                                      String question, String createTime) {
        scheduleFeedbackCard(eventChatId, userMessageId, question, createTime, "", null);
    }

    /**
     * 等待关联的机器人回复出现并停止更新。只要检测到 WISE 开始回复，最终都尝试发送反馈卡片。
     */
    private void monitorReplyAndSendCard(String monitorChatId, String userMessageId,
                                         long questionCreateTime, String question, String userId) {
        long deadline = System.currentTimeMillis() + Math.max(replyTimeoutMs, replyPollIntervalMs);
        String replyMessageId = null;
        String lastUpdateTime = null;
        String answerSummary = "";
        String knowledgeRefs = "";
        long stableSince = 0;

        try {
            while (System.currentTimeMillis() < deadline) {
                Message reply = findBotReply(monitorChatId, userMessageId, questionCreateTime);
                if (reply == null) {
                    sleep(replyPollIntervalMs);
                    continue;
                }

                String currentUpdateTime = normalizedUpdateTime(reply);
                if (!reply.getMessageId().equals(replyMessageId)
                        || !currentUpdateTime.equals(lastUpdateTime)) {
                    replyMessageId = reply.getMessageId();
                    lastUpdateTime = currentUpdateTime;
                    answerSummary = extractAnswerSummary(reply);
                    knowledgeRefs = extractKnowledgeRefs(reply, answerSummary);
                    stableSince = System.currentTimeMillis();
                    log.debug("检测到机器人回复更新: questionId={}, replyId={}, updateTime={}",
                            userMessageId, replyMessageId, currentUpdateTime);
                } else if (System.currentTimeMillis() - stableSince >= replyStableMs) {
                    log.info("机器人回复已完成: questionId={}, replyId={}", userMessageId, replyMessageId);
                    return;
                }

                sleep(replyPollIntervalMs);
            }

            log.warn("等待机器人回复完成超时，进入兜底流程: chatId={}, questionId={}",
                    monitorChatId, userMessageId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("反馈监控任务被中断，进入兜底流程: questionId={}", userMessageId);
        } catch (Exception e) {
            log.warn("反馈监控异常，进入兜底流程: questionId={}", userMessageId, e);
        } finally {
            if (replyMessageId != null) {
                String qaId = LarkBitableService.generateId("qa");
                boolean fallback = isFallbackAnswer(answerSummary);
                String recordAnswerSummary = answerSummaryForRecord(answerSummary);
                long completedAt = Instant.now().toEpochMilli();
                saveQuestionRecord(qaId, userMessageId, monitorChatId, userId, question,
                        recordAnswerSummary, knowledgeRefs, fallback, questionCreateTime, completedAt);
                cacheQaContext(monitorChatId, userId, qaId, question, recordAnswerSummary,
                        knowledgeRefs, userMessageId, replyMessageId);

                if (fallback) {
                    saveMissRecord(question, userId, monitorChatId, completedAt);
                    sendMissNoticeIfNeeded(userMessageId, answerSummary);
                }

                sendFeedbackCard(userMessageId, qaId, question, recordAnswerSummary, knowledgeRefs);
            } else if (sendOnTimeout) {
                String qaId = handleTimeoutFallback(monitorChatId, userMessageId, userId,
                        question, questionCreateTime);
                sendFeedbackCard(userMessageId, qaId, question, timeoutFallbackText(), "");
            } else {
                handleTimeoutFallback(monitorChatId, userMessageId, userId, question, questionCreateTime);
                log.warn("未检测到 WISE 回复，不发送反馈卡片: questionId={}", userMessageId);
            }
        }
    }

    private Message findBotReply(String monitorChatId, String userMessageId,
                                 long questionCreateTime) throws Exception {
        Message[] messages = listMessages(monitorChatId, Math.max(0, questionCreateTime / 1000 - 2));
        if (messages == null) {
            return null;
        }

        return selectBotReplyCandidate(messages, userMessageId, questionCreateTime);
    }

    Message selectBotReplyCandidate(Message[] messages, String userMessageId,
                                    long questionCreateTime) {
        Message latestUnthreaded = null;
        Message latestRelated = null;
        for (Message message : messages) {
            if (!isAppMessage(message) || Boolean.TRUE.equals(message.getDeleted())
                    || isTransientReply(message)
                    || isFeedbackCard(message)
                    || isSystemManagedReply(message)
                    || createTime(message) <= questionCreateTime) {
                continue;
            }

            boolean relatedToQuestion = userMessageId.equals(message.getParentId())
                    || userMessageId.equals(message.getRootId());
            if (relatedToQuestion) {
                if (latestRelated == null || updateTime(message) > updateTime(latestRelated)) {
                    latestRelated = message;
                }
                continue;
            }

            if (hasThreadReference(message)
                    || hasNewerUserQuestionBefore(messages, questionCreateTime, createTime(message), userMessageId)) {
                continue;
            }

            if (latestUnthreaded == null || updateTime(message) > updateTime(latestUnthreaded)) {
                latestUnthreaded = message;
            }
        }
        return latestRelated != null ? latestRelated : latestUnthreaded;
    }

    private boolean hasThreadReference(Message message) {
        return message != null && ((message.getParentId() != null && !message.getParentId().isBlank())
                || (message.getRootId() != null && !message.getRootId().isBlank()));
    }

    private boolean hasNewerUserQuestionBefore(Message[] messages, long questionCreateTime,
                                               long candidateCreateTime, String userMessageId) {
        for (Message message : messages) {
            if (message == null || !isUserMessage(message)
                    || userMessageId.equals(message.getMessageId())) {
                continue;
            }
            long messageCreateTime = createTime(message);
            if (messageCreateTime <= questionCreateTime || messageCreateTime >= candidateCreateTime) {
                continue;
            }
            if (!"text".equals(message.getMsgType())) {
                continue;
            }
            String text = extractText(message.getBody() != null ? message.getBody().getContent() : "");
            if (!text.isBlank() && detectFeedback(text) == null && !isIgnorablePureEmoji(text)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSystemManagedReply(Message message) {
        if (message == null || message.getBody() == null) {
            return false;
        }
        String rawContent = message.getBody().getContent();
        String answerText = extractAnswerSummary(message);
        String text = (rawContent == null ? "" : rawContent) + " " + answerText;
        return text.contains("抱歉，当前系统繁忙")
                || text.contains("暂时没有检测到知识库回复")
                || text.contains("您的问题已记录，请稍后重试")
                || text.contains("未找到可反馈的答案")
                || text.contains("感谢您的反馈");
    }

    private Message[] listMessages(String monitorChatId, long startSeconds) throws Exception {
        ListMessageReq req = ListMessageReq.newBuilder()
                .containerIdType("chat")
                .containerId(monitorChatId)
                .startTime(String.valueOf(startSeconds))
                .sortType("ByCreateTimeDesc")
                .pageSize(PAGE_SIZE)
                .build();

        ListMessageResp resp = feishuClient.im().message().list(req);
        if (resp == null || resp.getCode() != 0 || resp.getData() == null) {
            log.warn("读取飞书消息失败: chatId={}, code={}, msg={}", monitorChatId,
                    resp != null ? resp.getCode() : "null",
                    resp != null ? resp.getMsg() : "null");
            return null;
        }
        return resp.getData().getItems();
    }

    private void saveQuestionRecord(String qaId, String userMessageId, String monitorChatId,
                                    String userId, String question, String answerSummary,
                                    String knowledgeRefs, boolean fallback,
                                    long questionCreateTime, long completedAt) {
        QuestionRecord record = QuestionRecord.builder()
                .qaId(qaId)
                .messageId(userMessageId)
                .chatId(monitorChatId)
                .userId(userId)
                .question(question)
                .answerSummary(truncate(answerSummary, 300))
                .sessionId(monitorChatId + "_" + userMessageId)
                .knowledgeRefs(knowledgeRefs)
                .fallback(fallback)
                .confidence(fallback ? 0.0 : 0.8)
                .responseTimeMs(Math.max(0, completedAt - questionCreateTime))
                .createdAt(completedAt)
                .build();
        bitableService.saveQuestionRecord(record);
    }

    private void saveMissRecord(String question, String userId, String monitorChatId, long now) {
        String normalized = normalizeQuestion(question);
        MissRecord record = MissRecord.builder()
                .question(question)
                .normalizedQuestion(normalized)
                .count(1)
                .status("待补充")
                .userId(userId)
                .chatId(monitorChatId)
                .owner("")
                .createdAt(now)
                .updatedAt(now)
                .build();
        bitableService.saveMissRecord(record);
    }

    private String handleTimeoutFallback(String monitorChatId, String userMessageId, String userId,
                                         String question, long questionCreateTime) {
        String qaId = LarkBitableService.generateId("qa");
        long completedAt = Instant.now().toEpochMilli();
        String answerSummary = timeoutFallbackText();
        saveQuestionRecord(qaId, userMessageId, monitorChatId, userId, question, answerSummary,
                "", true, questionCreateTime, completedAt);
        cacheQaContext(monitorChatId, userId, qaId, question, answerSummary,
                "", userMessageId, null);

        if (timeoutNoticeEnabled) {
            replyText(userMessageId, answerSummary);
        }
        return qaId;
    }

    private void cacheQaContext(String monitorChatId, String userId, String qaId,
                                String question, String answerSummary, String knowledgeRefs,
                                String userMessageId, String replyMessageId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        QaContext context = new QaContext(qaId, question, answerSummary, knowledgeRefs,
                userId, monitorChatId, userMessageId, replyMessageId, System.currentTimeMillis());
        String key = qaContextKey(monitorChatId, userId);
        recentQaByUser.computeIfAbsent(key, ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(context);
        if (replyMessageId != null && !replyMessageId.isBlank()) {
            qaByReplyMessageId.put(replyMessageId, context);
        }
        if (userMessageId != null && !userMessageId.isBlank()) {
            qaByUserMessageId.put(userMessageId, context);
        }
        cleanRecentQaContexts();
    }

    private void handleTextFeedback(String monitorChatId, String userId,
                                    String feedbackMessageId, String feedbackType,
                                    String referencedMessageId) {
        if (userId == null || userId.isBlank()) {
            log.info("文本反馈缺少用户 ID，跳过: messageId={}", feedbackMessageId);
            return;
        }

        FeedbackMatch match = matchFeedbackContext(monitorChatId, userId, referencedMessageId);
        if (match.ambiguous()) {
            log.info("文本反馈存在多个候选，要求用户引用具体答案: user={}, chatId={}", userId, monitorChatId);
            replyText(feedbackMessageId, "您最近提了多个问题，请引用具体答案后再反馈，避免关联错误。");
            return;
        }

        QaContext context = match.context();
        if (context == null) {
            log.info("未找到可关联的文本反馈答案: user={}, chatId={}", userId, monitorChatId);
            replyText(feedbackMessageId, "未找到可反馈的答案。请先提问，收到回复后再反馈「有用」或「没用」。");
            return;
        }

        FeedbackRecord record = FeedbackRecord.builder()
                .feedbackId(LarkBitableService.generateId("fb"))
                .qaId(context.qaId())
                .userId(userId)
                .question(context.question())
                .answerSummary(context.answerSummary())
                .knowledgeRefs(context.knowledgeRefs())
                .feedback(feedbackType)
                .createdAt(Instant.now().toEpochMilli())
                .build();
        bitableService.handleFeedback(record);

        String text = "useful".equals(feedbackType)
                ? "感谢您的反馈！已记录为「有用」。"
                : "感谢您的反馈！已记录为「没用」，我们会持续优化知识库。";
        replyText(feedbackMessageId, text);
    }

    private FeedbackMatch matchFeedbackContext(String monitorChatId, String userId,
                                               String referencedMessageId) {
        if (referencedMessageId != null && !referencedMessageId.isBlank()) {
            QaContext context = qaByReplyMessageId.get(referencedMessageId);
            if (context == null) {
                context = qaByUserMessageId.get(referencedMessageId);
            }
            if (context != null && sameUserChat(context, monitorChatId, userId)
                    && !isExpired(context.timestamp())) {
                return new FeedbackMatch(context, false);
            }
        }

        List<QaContext> contexts = recentQaByUser.get(qaContextKey(monitorChatId, userId));
        if (contexts == null || contexts.isEmpty()) {
            return new FeedbackMatch(null, false);
        }
        long now = System.currentTimeMillis();
        List<QaContext> validContexts = new ArrayList<>();
        synchronized (contexts) {
            for (QaContext context : contexts) {
                if (now - context.timestamp() > qaContextTtlMs) {
                    continue;
                }
                validContexts.add(context);
            }
        }
        if (validContexts.isEmpty()) {
            return new FeedbackMatch(null, false);
        }
        if (validContexts.size() > 1) {
            return new FeedbackMatch(null, true);
        }
        return new FeedbackMatch(validContexts.get(0), false);
    }

    private boolean sameUserChat(QaContext context, String monitorChatId, String userId) {
        return monitorChatId.equals(context.chatId()) && userId.equals(context.userId());
    }

    private void sendMissNoticeIfNeeded(String userMessageId, String answerSummary) {
        if (!missNoticeEnabled || containsMissNotice(answerSummary)) {
            return;
        }
        replyText(userMessageId, "当前知识库中暂未收录该问题。\n\n"
                + "为避免误导产线操作，本次不直接给出处理步骤。\n"
                + "已记录您的问题，并提交管理员补充。");
    }

    private void sendFeedbackCard(String userMessageId, String qaId, String question,
                                  String answerSummary, String knowledgeRefs) {
        for (int attempt = 1; attempt <= SEND_MAX_ATTEMPTS; attempt++) {
            try {
                ReplyMessageReq req = ReplyMessageReq.newBuilder()
                        .messageId(userMessageId)
                        .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                                .msgType("interactive")
                                .content(buildFeedbackCard(qaId, question, answerSummary, knowledgeRefs))
                                .build())
                        .build();

                ReplyMessageResp resp = feishuClient.im().message().reply(req);
                if (resp != null && resp.getCode() == 0) {
                    log.info("反馈卡片发送成功: qaId={}, userMessageId={}", qaId, userMessageId);
                    return;
                }

                log.warn("反馈卡片发送失败（第 {}/{} 次）: userMessageId={}, code={}, msg={}",
                        attempt, SEND_MAX_ATTEMPTS, userMessageId,
                        resp != null ? resp.getCode() : "null",
                        resp != null ? resp.getMsg() : "null");
            } catch (Exception e) {
                log.warn("发送反馈卡片异常（第 {}/{} 次）: userMessageId={}",
                        attempt, SEND_MAX_ATTEMPTS, userMessageId, e);
            }

            if (attempt < SEND_MAX_ATTEMPTS) {
                try {
                    sleep(1000L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.error("反馈卡片最终发送失败: userMessageId={}", userMessageId);
    }

    private String buildFeedbackCard(String qaId, String question, String answerSummary,
                                     String knowledgeRefs) throws Exception {
        var elements = new ArrayList<Map<String, Object>>();

        var textElement = new LinkedHashMap<String, Object>();
        textElement.put("tag", "markdown");
        String prompt = "**这个回答对您有帮助吗？**";
        if (!cardActionsEnabled) {
            prompt += "\n\n请直接回复「有用」或「没用」，也可以回复 👍 / 👎，"
                    + "系统会自动关联您最近 5 分钟内的答案。";
        }
        textElement.put("content", prompt);
        elements.add(textElement);

        if (cardActionsEnabled) {
            var actions = new ArrayList<Map<String, Object>>();
            actions.add(buildFeedbackButton("👍 有用", "primary", qaId, question,
                    answerSummary, knowledgeRefs, "useful"));
            actions.add(buildFeedbackButton("👎 没用", "danger", qaId, question,
                    answerSummary, knowledgeRefs, "useless"));

            var action = new LinkedHashMap<String, Object>();
            action.put("tag", "action");
            action.put("actions", actions);
            elements.add(action);
        }

        var title = new LinkedHashMap<String, Object>();
        title.put("tag", "plain_text");
        title.put("content", "回答反馈");

        var header = new LinkedHashMap<String, Object>();
        header.put("title", title);
        header.put("template", "grey");

        var card = new LinkedHashMap<String, Object>();
        card.put("header", header);
        card.put("elements", elements);
        return objectMapper.writeValueAsString(card);
    }

    private Map<String, Object> buildFeedbackButton(String label, String type, String qaId,
                                                     String question, String answerSummary,
                                                     String knowledgeRefs, String feedback) {
        var text = new LinkedHashMap<String, Object>();
        text.put("tag", "plain_text");
        text.put("content", label);

        var value = new LinkedHashMap<String, Object>();
        value.put("action", "feedback");
        value.put("qa_id", qaId);
        value.put("feedback", feedback);
        value.put("question", question);
        value.put("answer_summary", answerSummary);
        value.put("knowledge_refs", knowledgeRefs);

        var button = new LinkedHashMap<String, Object>();
        button.put("tag", "button");
        button.put("text", text);
        button.put("type", type);
        button.put("value", value);
        return button;
    }

    private boolean isNewUserMessage(Message message) {
        return message != null
                && message.getMessageId() != null
                && createTime(message) >= serviceStartedAt - 5000
                && isUserMessage(message);
    }

    private boolean isUserMessage(Message message) {
        return message.getSender() != null && "user".equals(message.getSender().getSenderType());
    }

    private boolean isAppMessage(Message message) {
        return message.getSender() != null && "app".equals(message.getSender().getSenderType());
    }

    private String senderId(Message message) {
        if (message == null || message.getSender() == null) {
            return "";
        }
        return message.getSender().getId() != null ? message.getSender().getId() : "";
    }

    private String referencedMessageId(Message message) {
        if (message == null) {
            return "";
        }
        if (message.getParentId() != null && !message.getParentId().isBlank()) {
            return message.getParentId();
        }
        return message.getRootId() != null ? message.getRootId() : "";
    }

    private boolean isTransientReply(Message message) {
        if (!"text".equals(message.getMsgType()) || message.getBody() == null) {
            return false;
        }
        String text = extractText(message.getBody().getContent());
        return text.equals("正在查询中…") || text.equals("正在查询中...") || text.equals("正在查询中");
    }

    private boolean isFeedbackCard(Message message) {
        if (message.getBody() == null) {
            return false;
        }
        String content = message.getBody().getContent();
        return content != null && (content.contains("回答反馈")
                || content.contains("这个回答对您有帮助吗")
                || content.contains("\"action\":\"feedback\""));
    }

    private String extractAnswerSummary(Message message) {
        if (message == null || message.getBody() == null) {
            return "";
        }
        String content = message.getBody().getContent();
        if (content == null || content.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(content);
            StringBuilder sb = new StringBuilder();
            collectText(node, sb, "");
            return truncate(sb.toString().replaceAll("\\s+", " ").trim(), 300);
        } catch (Exception e) {
            return truncate(content.replaceAll("\\s+", " ").trim(), 300);
        }
    }

    private String extractKnowledgeRefs(Message message, String answerSummary) {
        String raw = "";
        if (message != null && message.getBody() != null && message.getBody().getContent() != null) {
            raw = message.getBody().getContent();
        }
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        Matcher matcher = KNOWLEDGE_FILE_PATTERN.matcher((answerSummary == null ? "" : answerSummary) + " " + raw);
        while (matcher.find() && refs.size() < 10) {
            refs.add(matcher.group());
        }
        return String.join(", ", refs);
    }

    private void collectText(JsonNode node, StringBuilder sb, String fieldName) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String value = node.asText("");
            if (isReadableTextField(fieldName, value)) {
                sb.append(value).append(' ');
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectText(child, sb, fieldName));
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectText(entry.getValue(), sb, entry.getKey()));
        }
    }

    private String extractText(String contentJson) {
        try {
            if (contentJson == null || contentJson.isBlank()) {
                return "";
            }
            JsonNode node = objectMapper.readTree(contentJson);
            return node.path("text").asText("")
                    .replaceAll("@_user_\\d+", "")
                    .replaceAll("@_all", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        } catch (Exception e) {
            log.debug("解析消息文本失败: content={}", contentJson, e);
            return "";
        }
    }

    private void cleanProcessedMessages() {
        long expireBefore = System.currentTimeMillis() - PROCESSED_TTL_MS;
        processedMessageIds.entrySet().removeIf(entry -> entry.getValue() < expireBefore);
        cleanRecentQaContexts();
    }

    private void cleanRecentQaContexts() {
        long expireBefore = System.currentTimeMillis() - qaContextTtlMs;
        recentQaByUser.forEach((key, contexts) -> {
            synchronized (contexts) {
                contexts.removeIf(context -> context.timestamp() < expireBefore);
            }
            if (contexts.isEmpty()) {
                recentQaByUser.remove(key);
            }
        });
        qaByReplyMessageId.entrySet().removeIf(entry -> entry.getValue().timestamp() < expireBefore);
        qaByUserMessageId.entrySet().removeIf(entry -> entry.getValue().timestamp() < expireBefore);
    }

    private String normalizedUpdateTime(Message message) {
        String updateTime = message.getUpdateTime();
        return updateTime == null || updateTime.isBlank() ? message.getCreateTime() : updateTime;
    }

    private long createTime(Message message) {
        return parseTimestamp(message != null ? message.getCreateTime() : null);
    }

    private long updateTime(Message message) {
        return parseTimestamp(normalizedUpdateTime(message));
    }

    private long parseTimestamp(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return Instant.now().toEpochMilli();
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    private String normalizeQuestionLength(String messageId, String question) {
        String trimmed = question == null ? "" : question.trim();
        if (trimmed.length() <= maxQuestionLength) {
            return trimmed;
        }
        replyText(messageId, "您的问题较长，我会先按前 " + maxQuestionLength
                + " 个字处理；建议补充时尽量保留报错码、系统名和操作页面。");
        return trimmed.substring(0, maxQuestionLength);
    }

    private String detectFeedback(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.equals("有用") || normalized.equals("👍")) {
            return "useful";
        }
        if (normalized.equals("没用") || normalized.equals("👎")) {
            return "useless";
        }
        return null;
    }

    private boolean isIgnorablePureEmoji(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim();
        if (normalized.isEmpty() || FEEDBACK_WORDS.contains(normalized)) {
            return false;
        }
        String withoutEmojiAndSymbols = normalized
                .replaceAll("[\\p{So}\\p{Sk}\\p{Cn}\\uFE0F\\u200D\\s]+", "");
        return withoutEmojiAndSymbols.isEmpty() && normalized.codePointCount(0, normalized.length()) <= 6;
    }

    private boolean isReadableTextField(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        String key = fieldName == null ? "" : fieldName;
        if (Set.of("tag", "type", "template", "img_key", "image_key", "action",
                "feedback", "qa_id", "open_id", "union_id").contains(key)) {
            return false;
        }
        if (Set.of("WISE", "text", "img", "markdown", "plain_text", "zh_cn").contains(trimmed)) {
            return false;
        }
        if (trimmed.startsWith("img_") || trimmed.startsWith("om_") || trimmed.startsWith("qa_")) {
            return false;
        }
        if (trimmed.contains("请升级至最新版本客户端")
                || trimmed.contains("这个回答对您有帮助吗")
                || "回答反馈".equals(trimmed)) {
            return false;
        }
        return true;
    }

    private boolean isFallbackAnswer(String answerSummary) {
        if (answerSummary == null || answerSummary.isBlank()) {
            return false;
        }
        String text = answerSummary.toLowerCase(Locale.ROOT);
        return text.contains("暂未收录")
                || text.contains("未收录该问题")
                || text.contains("知识库中暂未")
                || text.contains("知识库中没有")
                || text.contains("没有找到")
                || text.contains("未找到")
                || text.contains("暂无相关")
                || text.contains("没有相关")
                || text.contains("无法回答")
                || text.contains("无法提供")
                || text.contains("没有明确答案")
                || text.contains("为避免误导")
                || text.contains("不直接给出处理步骤");
    }

    private String answerSummaryForRecord(String answerSummary) {
        if (answerSummary == null || answerSummary.isBlank()) {
            return "WISE 已返回答案（富文本内容暂无法提取纯文本摘要）";
        }
        return answerSummary;
    }

    private boolean containsMissNotice(String answerSummary) {
        if (answerSummary == null) {
            return false;
        }
        return answerSummary.contains("当前知识库中暂未收录该问题")
                && answerSummary.contains("已记录您的问题");
    }

    private String normalizeQuestion(String question) {
        if (question == null) {
            return "";
        }
        return question.toLowerCase(Locale.ROOT)
                .replaceAll("@_user_\\d+", "")
                .replaceAll("@_all", "")
                .replaceAll("[\\p{Punct}\\s，。！？、；：“”‘’（）【】《》〈〉…—-]+", "")
                .trim();
    }

    private String qaContextKey(String monitorChatId, String userId) {
        return monitorChatId + "_" + userId;
    }

    private boolean isExpired(long timestamp) {
        return System.currentTimeMillis() - timestamp > qaContextTtlMs;
    }

    private String timeoutFallbackText() {
        return "抱歉，当前系统繁忙，暂时没有检测到知识库回复。\n\n"
                + "您的问题已记录，请稍后重试；如果影响产线操作，请先联系对应系统管理员确认。";
    }

    private void replyText(String messageId, String text) {
        try {
            ReplyMessageReq req = ReplyMessageReq.newBuilder()
                    .messageId(messageId)
                    .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                            .msgType("text")
                            .content("{\"text\":\"" + escapeJson(text) + "\"}")
                            .build())
                    .build();

            ReplyMessageResp resp = feishuClient.im().message().reply(req);
            if (resp == null || resp.getCode() != 0) {
                log.warn("回复文本失败: messageId={}, code={}, msg={}", messageId,
                        resp != null ? resp.getCode() : "null",
                        resp != null ? resp.getMsg() : "null");
            }
        } catch (Exception e) {
            log.warn("回复文本异常: messageId={}", messageId, e);
        }
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void sleep(long millis) throws InterruptedException {
        Thread.sleep(Math.max(100, millis));
    }
}
