package com.example.myapp.service;

import com.example.myapp.model.FeedbackRecord;
import com.example.myapp.model.MissRecord;
import com.example.myapp.model.QuestionRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.ListMessageReq;
import com.lark.oapi.service.im.v1.model.ListMessageResp;
import com.lark.oapi.service.im.v1.model.ListChat;
import com.lark.oapi.service.im.v1.model.ListChatReq;
import com.lark.oapi.service.im.v1.model.ListChatResp;
import com.lark.oapi.service.im.v1.model.GetMessageResourceReq;
import com.lark.oapi.service.im.v1.model.GetMessageResourceResp;
import com.lark.oapi.service.im.v1.model.Message;
import com.lark.oapi.service.im.v1.model.ReplyMessageReq;
import com.lark.oapi.service.im.v1.model.ReplyMessageReqBody;
import com.lark.oapi.service.im.v1.model.ReplyMessageResp;
import com.lark.oapi.service.optical_char_recognition.v1.model.BasicRecognizeImageReq;
import com.lark.oapi.service.optical_char_recognition.v1.model.BasicRecognizeImageReqBody;
import com.lark.oapi.service.optical_char_recognition.v1.model.BasicRecognizeImageResp;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
    private static final int MAX_ANSWER_RECORD_LENGTH = 5000;
    private static final Pattern KNOWLEDGE_FILE_PATTERN =
            Pattern.compile("[A-Za-z0-9_./-]+\\.(?:md|pdf|docx?|xlsx?)");
    private static final Pattern KNOWLEDGE_SOURCE_LINE_PATTERN =
            Pattern.compile("(?:知识来源|参考文档|引用文档|来源)\\s*[:：]\\s*([^\\n\\r。；;]+)");

    private final Client feishuClient;
    private final TaskExecutor feedbackTaskExecutor;
    private final ObjectMapper objectMapper;
    private final LarkBitableService bitableService;
    private final Map<String, Long> processedMessageIds = new ConcurrentHashMap<>();
    private final Map<String, List<QaContext>> recentQaByUser = new ConcurrentHashMap<>();
    private final Map<String, QaContext> qaByQaId = new ConcurrentHashMap<>();
    private final Map<String, QaContext> qaByReplyMessageId = new ConcurrentHashMap<>();
    private final Map<String, QaContext> qaByUserMessageId = new ConcurrentHashMap<>();
    private final Set<String> autoRegisteredChatIds = ConcurrentHashMap.newKeySet();
    private final Set<String> groupChatIds = ConcurrentHashMap.newKeySet();
    private final Set<String> messageReadForbiddenChatIds = ConcurrentHashMap.newKeySet();
    private final long serviceStartedAt = System.currentTimeMillis();
    private volatile boolean autoRegisteredChatsLoaded = false;

    @Value("${feishu.feedback.enabled:true}")
    private boolean enabled;

    @Value("${feishu.feedback.chat-id:}")
    private String chatIds;

    @Value("${feishu.feedback.auto-register.enabled:true}")
    private boolean autoRegisterEnabled;

    @Value("${feishu.feedback.auto-register.persist-path:data/feishu-feedback-chats.txt}")
    private String autoRegisterPersistPath;

    @Value("${feishu.feedback.auto-register.load-existing-chats:true}")
    private boolean loadExistingChatsOnStartup = true;

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
    private long qaContextTtlMs = 300000L;

    @Value("${feishu.feedback.card-actions-enabled:true}")
    private boolean cardActionsEnabled;

    @Value("${feishu.feedback.card-action-mode:callback}")
    private String cardActionMode;

    @Value("${feishu.feedback.action-url-base:}")
    private String actionUrlBase;

    @Value("${feishu.feedback.require-mention-in-group:true}")
    private boolean requireMentionInGroup;

    @Value("${feishu.feedback.permission-fallback.enabled:true}")
    private boolean permissionFallbackEnabled;

    @Value("${feishu.feedback.permission-fallback.delay-ms:12000}")
    private long permissionFallbackDelayMs;

    @Value("${feishu.feedback.record-opaque-wise-as-miss:false}")
    private boolean recordOpaqueWiseAsMiss;

    @Value("${feishu.feedback.wise-ocr-enabled:true}")
    private boolean wiseOcrEnabled;

    public MessagePollingService(Client feishuClient,
                                 @Qualifier("feedbackTaskExecutor") TaskExecutor feedbackTaskExecutor,
                                 ObjectMapper objectMapper,
                                 LarkBitableService bitableService) {
        this.feishuClient = feishuClient;
        this.feedbackTaskExecutor = feedbackTaskExecutor;
        this.objectMapper = objectMapper;
        this.bitableService = bitableService;
    }

    record QaContext(String qaId, String question, String answerSummary, String knowledgeRefs,
                     String userId, String chatId, String userMessageId,
                     String replyMessageId, long timestamp) {
    }

    record FeedbackMatch(QaContext context, boolean ambiguous) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadExistingChatsOnStartup() {
        if (!enabled || !autoRegisterEnabled || !loadExistingChatsOnStartup || feishuClient == null) {
            return;
        }
        try {
            String pageToken = null;
            int count = 0;
            do {
                ListChatReq.Builder reqBuilder = ListChatReq.newBuilder()
                        .pageSize(100)
                        .sortType("ByCreateTimeAsc");
                if (pageToken != null && !pageToken.isBlank()) {
                    reqBuilder.pageToken(pageToken);
                }
                ListChatResp resp = feishuClient.im().chat().list(reqBuilder.build());
                if (resp == null || resp.getCode() != 0 || resp.getData() == null) {
                    log.warn("自动加载机器人已加入群聊失败: code={}, msg={}",
                            resp != null ? resp.getCode() : "null",
                            resp != null ? resp.getMsg() : "null");
                    return;
                }
                if (resp.getData().getItems() != null) {
                    for (ListChat chat : resp.getData().getItems()) {
                        if (chat != null && chat.getChatId() != null && !chat.getChatId().isBlank()) {
                            registerGroupChatForFeedback(chat.getChatId(), "startup_chat_list:" + chat.getName());
                            count++;
                        }
                    }
                }
                pageToken = Boolean.TRUE.equals(resp.getData().getHasMore())
                        ? resp.getData().getPageToken() : null;
            } while (pageToken != null && !pageToken.isBlank());
            log.info("自动加载机器人已加入群聊完成: count={}", count);
        } catch (Exception e) {
            log.warn("自动加载机器人已加入群聊异常，保留事件自动登记和配置兜底", e);
        }
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

    public void registerChatForFeedback(String chatId, String reason) {
        if (!autoRegisterEnabled || chatId == null || chatId.isBlank()) {
            return;
        }
        loadAutoRegisteredChatsIfNecessary();
        String normalized = chatId.trim();
        if (autoRegisteredChatIds.add(normalized)) {
            persistAutoRegisteredChats();
            log.info("自动登记反馈监听群聊: chatId={}, reason={}", normalized, reason);
        }
    }

    public void registerGroupChatForFeedback(String chatId, String reason) {
        registerChatForFeedback(chatId, reason);
        if (chatId != null && !chatId.isBlank()) {
            groupChatIds.add(chatId.trim());
        }
    }

    public void unregisterChatForFeedback(String chatId, String reason) {
        if (!autoRegisterEnabled || chatId == null || chatId.isBlank()) {
            return;
        }
        loadAutoRegisteredChatsIfNecessary();
        String normalized = chatId.trim();
        if (autoRegisteredChatIds.remove(normalized)) {
            persistAutoRegisteredChats();
            log.info("移除自动登记反馈监听群聊: chatId={}, reason={}", normalized, reason);
        }
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

                if (requireMentionInGroup && groupChatIds.contains(monitorChatId) && !hasMention(message)) {
                    processedMessageIds.putIfAbsent(messageId, System.currentTimeMillis());
                    log.debug("群聊消息未 @ 机器人，跳过反馈监控: chatId={}, messageId={}",
                            monitorChatId, messageId);
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
        loadAutoRegisteredChatsIfNecessary();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (chatIds != null && !chatIds.isBlank()) {
            for (String id : chatIds.split("[,，\\s]+")) {
                if (!id.isBlank()) {
                    ids.add(id.trim());
                }
            }
        }
        ids.addAll(autoRegisteredChatIds);
        return new ArrayList<>(ids);
    }

    List<String> monitorChatIdsForTest() {
        return configuredChatIds();
    }

    public List<String> monitorChatIdsSnapshot() {
        return configuredChatIds();
    }

    private void loadAutoRegisteredChatsIfNecessary() {
        if (autoRegisteredChatsLoaded || !autoRegisterEnabled) {
            return;
        }
        synchronized (autoRegisteredChatIds) {
            if (autoRegisteredChatsLoaded) {
                return;
            }
            if (!loadExistingChatsOnStartup) {
                autoRegisteredChatsLoaded = true;
                log.info("已按配置跳过加载历史自动登记反馈群聊: path={}", autoRegisterPersistPath);
                return;
            }
            Path path = Path.of(autoRegisterPersistPath);
            if (Files.exists(path)) {
                try {
                    for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                        String chatId = line.trim();
                        if (!chatId.isBlank() && !chatId.startsWith("#")) {
                            autoRegisteredChatIds.add(chatId);
                        }
                    }
                    log.info("已加载自动登记反馈群聊: count={}, path={}",
                            autoRegisteredChatIds.size(), path);
                } catch (IOException e) {
                    log.warn("读取自动登记反馈群聊失败: path={}", path, e);
                }
            }
            autoRegisteredChatsLoaded = true;
        }
    }

    private void persistAutoRegisteredChats() {
        Path path = Path.of(autoRegisterPersistPath);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, new TreeSet<>(autoRegisteredChatIds), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("持久化自动登记反馈群聊失败: path={}", path, e);
        }
    }

    /**
     * 长连接收到用户提问时调用。这里使用事件中的真实 chat_id，避免固定会话 ID 不匹配。
     */
    public void scheduleFeedbackCard(String eventChatId, String userMessageId,
                                      String question, String createTime, String userId, String parentId) {
        registerChatForFeedback(eventChatId, "message_event");
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
     * 等待关联的机器人回复出现并停止更新，避免在 WISE 流式回答中途发送反馈卡片。
     */
    private void monitorReplyAndSendCard(String monitorChatId, String userMessageId,
                                         long questionCreateTime, String question, String userId) {
        long deadline = System.currentTimeMillis() + Math.max(replyTimeoutMs, replyPollIntervalMs);
        String replyMessageId = null;
        String lastUpdateTime = null;
        String answerSummary = "";
        String answerRawContent = "";
        String knowledgeRefs = "";
        long stableSince = 0;

        try {
            while (System.currentTimeMillis() < deadline) {
                Message reply = findBotReply(monitorChatId, userMessageId, questionCreateTime);
                if (permissionFallbackEnabled && messageReadForbiddenChatIds.contains(monitorChatId)) {
                    sendPermissionFallbackFeedbackCard(monitorChatId, userMessageId, userId,
                            question, questionCreateTime);
                    return;
                }
                if (reply == null) {
                    sleep(replyPollIntervalMs);
                    continue;
                }

                String currentUpdateTime = normalizedUpdateTime(reply);
                if (!reply.getMessageId().equals(replyMessageId)
                        || !currentUpdateTime.equals(lastUpdateTime)) {
                    replyMessageId = reply.getMessageId();
                    lastUpdateTime = currentUpdateTime;
                    answerRawContent = reply.getBody() != null ? reply.getBody().getContent() : "";
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
                String answerOcrText = "";
                String answerDetectionText = answerSummary + " " + answerRawContent;
                if (wiseOcrEnabled
                        && !isWiseMissAnswerTemplate(answerDetectionText)
                        && containsImageKey(answerRawContent)) {
                    answerOcrText = extractOcrTextFromImages(replyMessageId, answerRawContent);
                    if (!answerOcrText.isBlank()) {
                        answerDetectionText = answerDetectionText + " " + answerOcrText;
                        if (answerSummary == null || answerSummary.isBlank()) {
                            answerSummary = truncate(answerOcrText.replaceAll("\\s+", " ").trim(),
                                    MAX_ANSWER_RECORD_LENGTH);
                        }
                    }
                }
                boolean wiseMissAnswer = isWiseMissAnswerTemplate(answerDetectionText);
                if (!wiseMissAnswer
                        && recordOpaqueWiseAsMiss
                        && answerOcrText.isBlank()
                        && isOpaqueWiseReply(answerRawContent)
                        && (answerSummary == null || answerSummary.isBlank())) {
                    wiseMissAnswer = true;
                    answerSummary = "WISE 回复正文未通过飞书消息 API 暴露，已按未命中兜底记录。";
                    log.info("WISE 回复为不可读卡片，按配置写入未命中表: questionId={}, question={}",
                            userMessageId, question);
                }
                boolean fallback = isFallbackAnswer(answerSummary + " " + answerOcrText);
                String recordAnswerSummary = answerSummaryForRecord(answerSummary);
                long completedAt = Instant.now().toEpochMilli();
                saveQuestionRecord(qaId, userMessageId, monitorChatId, userId, question,
                        recordAnswerSummary, knowledgeRefs, fallback, questionCreateTime, completedAt);
                QaContext context = cacheQaContext(monitorChatId, userId, qaId, question, recordAnswerSummary,
                        knowledgeRefs, userMessageId, replyMessageId);

                if (wiseMissAnswer) {
                    log.info("检测到 WISE 知识库未命中固定话术，写入未命中表: questionId={}, question={}",
                            userMessageId, question);
                    saveMissRecord(question, userId, monitorChatId, completedAt);
                } else if (fallback) {
                    log.info("检测到兜底回答但未匹配 WISE 未命中固定话术，不写未命中表: questionId={}, answerSummary={}",
                            userMessageId, truncate(answerSummary, 180));
                }

                String feedbackCardMessageId = sendFeedbackCard(userMessageId, qaId, question,
                        recordAnswerSummary, knowledgeRefs);
                cacheReplyAlias(feedbackCardMessageId, context);
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
        Message selected = latestRelated != null ? latestRelated : latestUnthreaded;
        if (selected == null && log.isDebugEnabled()) {
            log.debug("未匹配到 WISE 回复候选: questionId={}, recentMessages={}",
                    userMessageId, summarizeRecentMessages(messages, questionCreateTime));
        }
        return selected;
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
            int code = resp != null ? resp.getCode() : -1;
            String msg = resp != null ? resp.getMsg() : "null";
            if (code == 230027 && msg.contains("im:message.group_msg")) {
                messageReadForbiddenChatIds.add(monitorChatId);
            }
            log.warn("读取飞书消息失败: chatId={}, code={}, msg={}", monitorChatId,
                    code, msg);
            return null;
        }
        messageReadForbiddenChatIds.remove(monitorChatId);
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
                .status("pending")
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

    private void sendPermissionFallbackFeedbackCard(String monitorChatId, String userMessageId,
                                                    String userId, String question,
                                                    long questionCreateTime) throws InterruptedException {
        long delay = Math.max(0, permissionFallbackDelayMs);
        if (delay > 0) {
            sleep(delay);
        }
        String qaId = LarkBitableService.generateId("qa");
        long completedAt = Instant.now().toEpochMilli();
        String answerSummary = "群聊未授权读取 WISE 回复正文，按用户提问延迟发送反馈卡片。";
        saveQuestionRecord(qaId, userMessageId, monitorChatId, userId, question, answerSummary,
                "", false, questionCreateTime, completedAt);
        QaContext context = cacheQaContext(monitorChatId, userId, qaId, question, answerSummary,
                "", userMessageId, null);
        String feedbackCardMessageId = sendFeedbackCard(userMessageId, qaId, question, answerSummary, "");
        cacheReplyAlias(feedbackCardMessageId, context);
        log.info("群聊无读取权限，已按延迟兜底发送反馈卡片: chatId={}, questionId={}",
                monitorChatId, userMessageId);
    }

    QaContext cacheQaContext(String monitorChatId, String userId, String qaId,
                             String question, String answerSummary, String knowledgeRefs,
                             String userMessageId, String replyMessageId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        QaContext context = new QaContext(qaId, question, answerSummary, knowledgeRefs,
                userId, monitorChatId, userMessageId, replyMessageId, System.currentTimeMillis());
        String key = qaContextKey(monitorChatId, userId);
        recentQaByUser.computeIfAbsent(key, ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(context);
        qaByQaId.put(qaId, context);
        if (replyMessageId != null && !replyMessageId.isBlank()) {
            qaByReplyMessageId.put(replyMessageId, context);
        }
        if (userMessageId != null && !userMessageId.isBlank()) {
            qaByUserMessageId.put(userMessageId, context);
        }
        cleanRecentQaContexts();
        return context;
    }

    void cacheReplyAlias(String messageId, QaContext context) {
        if (messageId == null || messageId.isBlank() || context == null) {
            return;
        }
        qaByReplyMessageId.put(messageId, context);
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

    public boolean recordFeedbackByQaId(String qaId, String feedback) {
        if (qaId == null || qaId.isBlank() || feedback == null || feedback.isBlank()) {
            return false;
        }
        String normalizedFeedback = feedback.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("useful", "useless").contains(normalizedFeedback)) {
            return false;
        }
        QaContext context = qaByQaId.get(qaId.trim());
        if (context == null || isExpired(context.timestamp())) {
            log.info("URL 按钮反馈未找到可关联答案: qaId={}", qaId);
            return false;
        }
        FeedbackRecord record = FeedbackRecord.builder()
                .feedbackId(LarkBitableService.generateId("fb"))
                .qaId(context.qaId())
                .userId(context.userId())
                .question(context.question())
                .answerSummary(context.answerSummary())
                .knowledgeRefs(context.knowledgeRefs())
                .feedback(normalizedFeedback)
                .createdAt(Instant.now().toEpochMilli())
                .build();
        bitableService.handleFeedback(record);
        log.info("URL 按钮反馈已记录: qaId={}, feedback={}", qaId, normalizedFeedback);
        return true;
    }

    FeedbackMatch matchFeedbackContext(String monitorChatId, String userId,
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

    private String sendFeedbackCard(String userMessageId, String qaId, String question,
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
                    String feedbackCardMessageId = resp.getData() != null ? resp.getData().getMessageId() : "";
                    log.info("反馈卡片发送成功: qaId={}, userMessageId={}, feedbackCardMessageId={}",
                            qaId, userMessageId, feedbackCardMessageId);
                    return feedbackCardMessageId;
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
        return "";
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
        if ("url".equalsIgnoreCase(cardActionMode) && actionUrlBase != null && !actionUrlBase.isBlank()) {
            button.put("url", feedbackActionUrl(qaId, feedback));
        } else {
            button.put("value", value);
        }
        return button;
    }

    private String feedbackActionUrl(String qaId, String feedback) {
        String base = actionUrlBase.strip();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/feedback/record?qaId="
                + URLEncoder.encode(qaId, StandardCharsets.UTF_8)
                + "&feedback=" + URLEncoder.encode(feedback, StandardCharsets.UTF_8);
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
        if (message == null || message.getSender() == null) {
            return false;
        }
        String senderType = message.getSender().getSenderType();
        return "app".equals(senderType) || "bot".equals(senderType);
    }

    private String summarizeRecentMessages(Message[] messages, long questionCreateTime) {
        if (messages == null || messages.length == 0) {
            return "[]";
        }
        List<String> summaries = new ArrayList<>();
        for (Message message : messages) {
            if (message == null || createTime(message) <= questionCreateTime) {
                continue;
            }
            String senderType = message.getSender() != null ? message.getSender().getSenderType() : "";
            String summary = extractAnswerSummary(message);
            String parent = message.getParentId();
            if (parent == null || parent.isBlank()) {
                parent = message.getRootId();
            }
            summaries.add("{id=" + message.getMessageId()
                    + ",sender=" + senderType
                    + ",type=" + message.getMsgType()
                    + ",parent=" + (parent == null ? "" : parent)
                    + ",text=" + truncate(summary, 80)
                    + "}");
            if (summaries.size() >= 8) {
                break;
            }
        }
        return summaries.toString();
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
        String text = (answerSummary == null ? "" : answerSummary) + "\n" + raw;
        Matcher sourceMatcher = KNOWLEDGE_SOURCE_LINE_PATTERN.matcher(text);
        while (sourceMatcher.find() && refs.size() < 10) {
            addSplitRefs(refs, sourceMatcher.group(1));
        }
        Matcher matcher = KNOWLEDGE_FILE_PATTERN.matcher(text);
        while (matcher.find() && refs.size() < 10) {
            refs.add(matcher.group());
        }
        return String.join(", ", refs);
    }

    private void addSplitRefs(LinkedHashSet<String> refs, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String item : value.split("[,，、|/]+")) {
            String normalized = item.trim()
                    .replaceAll("^[\\[【(（]+", "")
                    .replaceAll("[\\]】)）.。]+$", "");
            if (!normalized.isBlank()) {
                refs.add(normalized);
            }
            if (refs.size() >= 10) {
                return;
            }
        }
    }

    public String debugOcrImageKey(String messageId, String imageKey) {
        return ocrImageKey(messageId, imageKey);
    }

    private boolean containsImageKey(String contentJson) {
        return contentJson != null && (contentJson.contains("\"image_key\"")
                || contentJson.contains("\"img_key\""));
    }

    private boolean isOpaqueWiseReply(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return false;
        }
        return contentJson.contains("\"title\":\"WISE\"")
                && (containsImageKey(contentJson)
                || contentJson.contains("请升级至最新版本客户端"));
    }

    private String extractOcrTextFromImages(String messageId, String contentJson) {
        List<String> imageKeys = extractImageKeys(contentJson);
        if (imageKeys.isEmpty()) {
            return "";
        }

        List<String> texts = new ArrayList<>();
        for (String imageKey : imageKeys.subList(0, Math.min(imageKeys.size(), 3))) {
            String text = ocrImageKey(messageId, imageKey);
            if (!text.isBlank()) {
                texts.add(text);
            }
        }
        return String.join("\n", texts);
    }

    private String ocrImageKey(String messageId, String imageKey) {
        if (imageKey == null || imageKey.isBlank() || feishuClient == null) {
            return "";
        }
        try {
            GetMessageResourceResp imageResp = feishuClient.im().messageResource().get(GetMessageResourceReq.newBuilder()
                    .messageId(messageId)
                    .fileKey(imageKey)
                    .type("image")
                    .build());
            if (imageResp == null || imageResp.getData() == null) {
                log.warn("下载 WISE 消息图片失败: messageId={}, imageKey={}, code={}, msg={}",
                        messageId, imageKey, imageResp != null ? imageResp.getCode() : null,
                        imageResp != null ? imageResp.getMsg() : "null response");
                return "";
            }

            String base64 = Base64.getEncoder().encodeToString(imageResp.getData().toByteArray());
            BasicRecognizeImageResp ocrResp = feishuClient.opticalCharRecognition().v1().image()
                    .basicRecognize(BasicRecognizeImageReq.newBuilder()
                            .basicRecognizeImageReqBody(BasicRecognizeImageReqBody.newBuilder()
                                    .image(base64)
                                    .build())
                            .build());
            if (ocrResp == null || !ocrResp.success() || ocrResp.getData() == null
                    || ocrResp.getData().getTextList() == null) {
                log.warn("WISE 图片 OCR 失败: imageKey={}, code={}, msg={}",
                        imageKey, ocrResp != null ? ocrResp.getCode() : null,
                        ocrResp != null ? ocrResp.getMsg() : "null response");
                return "";
            }

            String text = String.join("\n", ocrResp.getData().getTextList()).trim();
            log.info("WISE 图片 OCR 完成: imageKey={}, textLength={}", imageKey, text.length());
            return text;
        } catch (Exception e) {
            log.warn("WISE 图片 OCR 异常: imageKey={}", imageKey, e);
            return "";
        }
    }

    private List<String> extractImageKeys(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(contentJson);
            LinkedHashSet<String> imageKeys = new LinkedHashSet<>();
            collectImageKeys(node, "", imageKeys);
            return new ArrayList<>(imageKeys);
        } catch (Exception e) {
            log.debug("解析 WISE 图片 key 失败: content={}", contentJson, e);
            return List.of();
        }
    }

    private void collectImageKeys(JsonNode node, String fieldName, Set<String> imageKeys) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String value = node.asText("");
            if (("image_key".equals(fieldName) || "img_key".equals(fieldName))
                    && value.startsWith("img_")) {
                imageKeys.add(value);
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectImageKeys(child, fieldName, imageKeys));
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectImageKeys(entry.getValue(), entry.getKey(), imageKeys));
        }
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

    String detectFeedback(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.equals("有用") || normalized.equals("👍")) {
            return "useful";
        }
        if (normalized.equals("没用") || normalized.equals("👎")) {
            return "useless";
        }
        return null;
    }

    private boolean hasMention(Message message) {
        if (message == null) {
            return false;
        }
        if (message.getMentions() != null && message.getMentions().length > 0) {
            return true;
        }
        String content = message.getBody() != null ? message.getBody().getContent() : "";
        return content != null && (content.contains("@_user_") || content.contains("@_all"));
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

    boolean isFallbackAnswer(String answerSummary) {
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

    boolean isWiseMissAnswerTemplate(String answerSummary) {
        if (answerSummary == null || answerSummary.isBlank()) {
            return false;
        }
        String normalized = answerSummary.replaceAll("\\s+", "");
        return normalized.contains("当前知识库中暂未收录该问题")
                && normalized.contains("为避免误导产线操作")
                && normalized.contains("本次不直接给出处理步骤")
                && normalized.contains("已记录您的问题")
                && normalized.contains("提交管理员补充");
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
