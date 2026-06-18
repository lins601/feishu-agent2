package com.example.myapp.controller;

import com.example.myapp.config.FeishuConfig;
import com.example.myapp.service.FeishuMessageService;
import com.example.myapp.service.MessagePollingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 飞书事件回调入口。
 * <p>
 * 支持 POST 和 GET 两种请求方式：
 * - POST：飞书推送事件（消息接收等）
 * - GET：飞书配置事件订阅时的 URL 验证（部分飞书版本用 GET）
 * <p>
 * challenge 验证同时支持 POST 和 GET 请求。
 */
@RestController
@RequestMapping("/api/feishu")
public class FeishuEventController {

    private static final Logger log = LoggerFactory.getLogger(FeishuEventController.class);

    private final FeishuConfig feishuConfig;
    private final FeishuMessageService feishuMessageService;
    private final MessagePollingService messagePollingService;
    private final ObjectMapper objectMapper;

    @Value("${feishu.feedback.wise-only:true}")
    private boolean wiseOnlyFeedbackMode;

    @Value("${feishu.feedback.require-mention-in-group:true}")
    private boolean requireMentionInGroup;

    public FeishuEventController(FeishuConfig feishuConfig,
                                  FeishuMessageService feishuMessageService,
                                  MessagePollingService messagePollingService,
                                  ObjectMapper objectMapper) {
        this.feishuConfig = feishuConfig;
        this.feishuMessageService = feishuMessageService;
        this.messagePollingService = messagePollingService;
        this.objectMapper = objectMapper;
    }

    // ─── GET：飞书 URL 验证（部分版本用 GET 发 challenge）──────────

    @GetMapping("/event")
    public ResponseEntity<?> handleGetEvent(
            @RequestParam(required = false) String challenge,
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String type) {

        log.info("收到 GET 请求: challenge={}, token={}, type={}", challenge, token, type);

        // challenge 验证
        if (challenge != null && !challenge.isEmpty()) {
            if (token != null && !feishuConfig.getVerificationToken().equals(token)) {
                log.warn("GET challenge token 校验失败: token={}", token);
                return ResponseEntity.status(403).body(Map.of("error", "token mismatch"));
            }
            log.info("GET 验证成功, challenge={}", challenge);
            return ResponseEntity.ok(Map.of("challenge", challenge));
        }

        // 非 challenge 的 GET 请求，返回 200
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ─── POST：飞书事件推送 + challenge 验证 ──────────────────────

    @PostMapping("/event")
    public ResponseEntity<?> handlePostEvent(@RequestBody String body,
                                              @RequestHeader(value = "X-Lark-Signature", required = false) String signature,
                                              @RequestHeader(value = "X-Lark-Timestamp", required = false) String timestamp) {
        log.info("收到 POST 事件回调, body length={}", body != null ? body.length() : 0);

        try {
            JsonNode root = objectMapper.readTree(body);

            // ─── challenge 验证 ────────────────────────────────
            if (root.has("challenge")) {
                String challenge = root.path("challenge").asText();
                String token = root.path("token").asText();

                if (!feishuConfig.getVerificationToken().equals(token)) {
                    log.warn("POST challenge token 校验失败: token={}", token);
                    return ResponseEntity.status(403).body(Map.of("error", "token mismatch"));
                }

                log.info("POST 验证成功, challenge={}", challenge);
                return ResponseEntity.ok(Map.of("challenge", challenge));
            }

            String directType = root.path("type").asText("");
            if ("card.action.trigger".equals(directType)) {
                return ResponseEntity.ok(handleCardAction(root));
            }

            // ─── 解析事件 ──────────────────────────────────────
            String eventType = root.path("header").path("event_type").asText("");
            String eventId = root.path("header").path("event_id").asText("");

            log.info("收到飞书事件: type={}, id={}", eventType, eventId);

            if ("card.action.trigger".equals(eventType) || "card.action.trigger_v1".equals(eventType)) {
                return ResponseEntity.ok(handleCardAction(root));
            }

            switch (eventType) {
                case "im.message.receive_v1" -> handleReceiveMessage(root);
                case "im.chat.access_event.bot_p2p_chat_entered_v1" -> handleBotP2pChatEntered(root);
                case "im.chat.member.bot.added_v1" -> handleBotAdded(root);
                case "im.chat.member.bot.deleted_v1" -> handleBotDeleted(root);
                default -> log.info("忽略未处理的事件类型: {}", eventType);
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("处理飞书事件异常: body={}", body != null ? body.substring(0, Math.min(body.length(), 200)) : "null", e);
            return ResponseEntity.ok().build();
        }
    }

    @SuppressWarnings("unchecked")
    private P2CardActionTriggerResponse handleCardAction(JsonNode root) {
        JsonNode event = root.has("event") ? root.path("event") : root;
        String openId = event.path("operator").path("open_id").asText("");
        if (openId.isBlank()) {
            openId = event.path("operator").path("user_id").asText("");
        }
        Map<String, Object> value = objectMapper.convertValue(
                event.path("action").path("value"), Map.class);
        log.info("HTTP 卡片按钮回调: openId={}, value={}", openId, value);
        return feishuMessageService.handleFeedbackAction(openId, value != null ? value : Map.of());
    }

    // ─── 事件处理 ──────────────────────────────────────────────

    private void handleReceiveMessage(JsonNode root) {
        try {
            JsonNode event = root.path("event");

            String chatType = event.path("chat_type").asText("");
            String messageId = event.path("message").path("message_id").asText("");
            String msgType = event.path("message").path("msg_type").asText("");
            String chatId = event.path("message").path("chat_id").asText("");
            String createTime = event.path("message").path("create_time").asText("");
            String parentId = event.path("message").path("parent_id").asText("");
            if (parentId.isBlank()) {
                parentId = event.path("message").path("root_id").asText("");
            }

            String openId = event.path("sender").path("sender_id").path("open_id").asText("");

            String contentJson = event.path("message").path("content").asText("");
            boolean groupMessage = !"p2p".equals(chatType);
            boolean mentioned = !groupMessage || hasMention(event.path("message").path("mentions"), contentJson);
            String textContent = extractTextContent(contentJson);

            log.info("收到消息: chatType={}, msgType={}, openId={}, text={}",
                    chatType, msgType, openId, textContent);

            if (textContent.isEmpty()) {
                log.info("消息内容为空，跳过处理");
                return;
            }

            if (groupMessage) {
                messagePollingService.registerGroupChatForFeedback(chatId, "http_message_event");
            } else {
                messagePollingService.registerChatForFeedback(chatId, "http_message_event");
            }
            if (requireMentionInGroup && groupMessage && !mentioned) {
                log.info("HTTP 群聊消息未 @ 机器人，跳过处理: chatId={}, messageId={}", chatId, messageId);
                return;
            }
            if (wiseOnlyFeedbackMode && "text".equals(msgType)) {
                messagePollingService.scheduleFeedbackCard(chatId, messageId, textContent, createTime, openId, parentId);
                return;
            }
            feishuMessageService.handleUserMessage(openId, chatId, chatType, messageId, textContent, parentId);

        } catch (Exception e) {
            log.error("处理接收消息事件异常", e);
        }
    }

    private void handleBotP2pChatEntered(JsonNode root) {
        try {
            JsonNode event = root.path("event");
            String openId = event.path("sender").path("sender_id").path("open_id").asText("");
            String chatId = event.path("chat_id").asText("");

            log.info("用户进入会话: openId={}, chatId={}", openId, chatId);
            feishuMessageService.sendWelcomeMessage(openId, chatId);

        } catch (Exception e) {
            log.error("处理进入会话事件异常", e);
        }
    }

    private void handleBotAdded(JsonNode root) {
        try {
            JsonNode event = root.path("event");
            String chatId = event.path("chat_id").asText("");
            String name = event.path("name").asText("");
            log.info("HTTP 回调：机器人被加入群聊: chatId={}, name={}", chatId, name);
            messagePollingService.registerGroupChatForFeedback(chatId, "http_bot_added:" + name);
        } catch (Exception e) {
            log.error("处理机器人入群 HTTP 事件异常", e);
        }
    }

    private void handleBotDeleted(JsonNode root) {
        try {
            JsonNode event = root.path("event");
            String chatId = event.path("chat_id").asText("");
            String name = event.path("name").asText("");
            log.info("HTTP 回调：机器人被移出群聊: chatId={}, name={}", chatId, name);
            messagePollingService.unregisterChatForFeedback(chatId, "http_bot_deleted:" + name);
        } catch (Exception e) {
            log.error("处理机器人退群 HTTP 事件异常", e);
        }
    }

    private String extractTextContent(String contentJson) {
        try {
            if (contentJson == null || contentJson.isEmpty()) return "";
            JsonNode contentNode = objectMapper.readTree(contentJson);
            return contentNode.path("text").asText("")
                    .replaceAll("@_user_\\d+", "")
                    .replaceAll("@_all", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        } catch (Exception e) {
            log.warn("解析消息内容失败: {}", contentJson);
            return "";
        }
    }

    private boolean hasMention(JsonNode mentions, String contentJson) {
        if (mentions != null && mentions.isArray() && !mentions.isEmpty()) {
            return true;
        }
        return contentJson != null && (contentJson.contains("@_user_") || contentJson.contains("@_all"));
    }
}
