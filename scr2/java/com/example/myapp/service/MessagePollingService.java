package com.example.myapp.service;

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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Client feishuClient;
    private final TaskExecutor feedbackTaskExecutor;
    private final ObjectMapper objectMapper;
    private final Map<String, Long> processedMessageIds = new ConcurrentHashMap<>();
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

    public MessagePollingService(Client feishuClient,
                                 @Qualifier("feedbackTaskExecutor") TaskExecutor feedbackTaskExecutor,
                                 ObjectMapper objectMapper) {
        this.feishuClient = feishuClient;
        this.feedbackTaskExecutor = feedbackTaskExecutor;
        this.objectMapper = objectMapper;
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
                if (!isNewUserQuestion(message)) {
                    continue;
                }

                String messageId = message.getMessageId();
                String question = extractText(message.getBody() != null
                        ? message.getBody().getContent() : "");
                if (question.isBlank() || FEEDBACK_WORDS.contains(question)) {
                    continue;
                }

                if (processedMessageIds.putIfAbsent(messageId, System.currentTimeMillis()) == null) {
                    log.info("检测到新问题，提交反馈监控任务: messageId={}, question={}", messageId, question);
                    feedbackTaskExecutor.execute(() -> monitorReplyAndSendCard(
                            monitorChatId, messageId, createTime(message), question));
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
                                      String question, String createTime) {
        if (!enabled || eventChatId == null || eventChatId.isBlank()
                || userMessageId == null || userMessageId.isBlank()
                || question == null || question.isBlank()
                || FEEDBACK_WORDS.contains(question.trim())) {
            return;
        }

        if (processedMessageIds.putIfAbsent(userMessageId, System.currentTimeMillis()) != null) {
            log.debug("反馈监控任务已存在，跳过: messageId={}", userMessageId);
            return;
        }

        long questionCreateTime = parseTimestamp(createTime);
        log.info("提交事件驱动反馈监控任务: chatId={}, messageId={}, question={}",
                eventChatId, userMessageId, question);
        try {
            feedbackTaskExecutor.execute(() -> monitorReplyAndSendCard(
                    eventChatId, userMessageId, questionCreateTime, question));
        } catch (TaskRejectedException e) {
            processedMessageIds.remove(userMessageId);
            log.warn("反馈监控任务提交失败，线程池可能正在关闭: messageId={}", userMessageId, e);
        }
    }

    /**
     * 等待关联的机器人回复出现并停止更新。只要检测到 WISE 开始回复，最终都尝试发送反馈卡片。
     */
    private void monitorReplyAndSendCard(String monitorChatId, String userMessageId,
                                         long questionCreateTime, String question) {
        long deadline = System.currentTimeMillis() + Math.max(replyTimeoutMs, replyPollIntervalMs);
        String replyMessageId = null;
        String lastUpdateTime = null;
        String answerSummary = "";
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
                    stableSince = System.currentTimeMillis();
                    log.debug("检测到机器人回复更新: questionId={}, replyId={}, updateTime={}",
                            userMessageId, replyMessageId, currentUpdateTime);
                } else if (System.currentTimeMillis() - stableSince >= replyStableMs) {
                    log.info("机器人回复已完成: questionId={}, replyId={}", userMessageId, replyMessageId);
                    return;
                }

                sleep(replyPollIntervalMs);
            }

            log.warn("等待机器人回复完成超时，仍将发送反馈卡片: chatId={}, questionId={}",
                    monitorChatId, userMessageId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("反馈监控任务被中断，仍将发送反馈卡片: questionId={}", userMessageId);
        } catch (Exception e) {
            log.warn("反馈监控异常，仍将发送反馈卡片: questionId={}", userMessageId, e);
        } finally {
            if (replyMessageId != null || sendOnTimeout) {
                sendFeedbackCard(userMessageId, question, answerSummary);
            } else {
                processedMessageIds.remove(userMessageId);
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

        Message latest = null;
        Message latestRelated = null;
        for (Message message : messages) {
            if (!isAppMessage(message) || Boolean.TRUE.equals(message.getDeleted())
                    || isTransientReply(message)
                    || isFeedbackCard(message)
                    || createTime(message) <= questionCreateTime) {
                continue;
            }
            if (!userMessageId.equals(message.getParentId())
                    && !userMessageId.equals(message.getRootId())) {
                if (latest == null || updateTime(message) > updateTime(latest)) {
                    latest = message;
                }
            } else if (latestRelated == null || updateTime(message) > updateTime(latestRelated)) {
                latestRelated = message;
            }
        }
        return latestRelated != null ? latestRelated : latest;
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

    private void sendFeedbackCard(String userMessageId, String question, String answerSummary) {
        String qaId = LarkBitableService.generateId("qa");
        for (int attempt = 1; attempt <= SEND_MAX_ATTEMPTS; attempt++) {
            try {
                ReplyMessageReq req = ReplyMessageReq.newBuilder()
                        .messageId(userMessageId)
                        .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                                .msgType("interactive")
                                .content(buildFeedbackCard(qaId, question, answerSummary))
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

    private String buildFeedbackCard(String qaId, String question, String answerSummary) throws Exception {
        var elements = new ArrayList<Map<String, Object>>();

        var textElement = new LinkedHashMap<String, Object>();
        textElement.put("tag", "markdown");
        textElement.put("content", "**这个回答对您有帮助吗？**");
        elements.add(textElement);

        var actions = new ArrayList<Map<String, Object>>();
        actions.add(buildFeedbackButton("👍 有用", "primary", qaId, question, answerSummary, "useful"));
        actions.add(buildFeedbackButton("👎 没用", "danger", qaId, question, answerSummary, "useless"));

        var action = new LinkedHashMap<String, Object>();
        action.put("tag", "action");
        action.put("actions", actions);
        elements.add(action);

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
                                                     String feedback) {
        var text = new LinkedHashMap<String, Object>();
        text.put("tag", "plain_text");
        text.put("content", label);

        var value = new LinkedHashMap<String, Object>();
        value.put("action", "feedback");
        value.put("qa_id", qaId);
        value.put("feedback", feedback);
        value.put("question", question);
        value.put("answer_summary", answerSummary);

        var button = new LinkedHashMap<String, Object>();
        button.put("tag", "button");
        button.put("text", text);
        button.put("type", type);
        button.put("value", value);
        return button;
    }

    private boolean isNewUserQuestion(Message message) {
        return message != null
                && message.getMessageId() != null
                && createTime(message) >= serviceStartedAt - 5000
                && "text".equals(message.getMsgType())
                && message.getSender() != null
                && "user".equals(message.getSender().getSenderType());
    }

    private boolean isAppMessage(Message message) {
        return message.getSender() != null && "app".equals(message.getSender().getSenderType());
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
            collectText(node, sb);
            return truncate(sb.toString().replaceAll("\\s+", " ").trim(), 300);
        } catch (Exception e) {
            return truncate(content.replaceAll("\\s+", " ").trim(), 300);
        }
    }

    private void collectText(JsonNode node, StringBuilder sb) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String value = node.asText("");
            if (!value.isBlank() && !"回答反馈".equals(value)
                    && !value.contains("这个回答对您有帮助吗")) {
                sb.append(value).append(' ');
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectText(child, sb));
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectText(entry.getValue(), sb));
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

    private void sleep(long millis) throws InterruptedException {
        Thread.sleep(Math.max(100, millis));
    }
}
