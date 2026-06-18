package com.example.myapp.config;

import com.example.myapp.service.FeishuMessageService;
import com.example.myapp.service.MessagePollingService;
import com.lark.oapi.Client;
import com.lark.oapi.core.enums.BaseUrlEnum;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.event.cardcallback.P2CardActionTriggerHandler;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 飞书 SDK 配置 — 长连接模式。
 * <p>
 * 使用飞书 SDK 的 WebSocket 长连接接收事件，
 * 不需要配置公网 URL，不需要内网穿透。
 * <p>
 * 在飞书开发者后台选择「长连接接收事件」即可。
 */
@Configuration
public class FeishuConfig {

    private static final Logger log = LoggerFactory.getLogger(FeishuConfig.class);

    @Value("${feishu.app-id:}")
    private String appId;

    @Value("${feishu.app-secret:}")
    private String appSecret;

    @Value("${feishu.verification-token:}")
    private String verificationToken;

    @Value("${feishu.encrypt-key:}")
    private String encryptKey;

    @Value("${feishu.feedback.wise-only:true}")
    private boolean wiseOnlyFeedbackMode;

    @Value("${feishu.feedback.listen-message-events:false}")
    private boolean listenMessageEvents;

    @Value("${feishu.websocket.enabled:true}")
    private boolean websocketEnabled;

    @Value("${feishu.feedback.card-actions-enabled:true}")
    private boolean cardActionsEnabled;

    @Value("${feishu.feedback.require-mention-in-group:true}")
    private boolean requireMentionInGroup;

    /**
     * 飞书 API Client（用于主动发消息、回复等）。
     */
    @Bean
    public Client feishuClient() {
        return Client.newBuilder(appId, appSecret)
                .openBaseUrl(BaseUrlEnum.FeiShu)
                .logReqAtDebug(false)
                .build();
    }

    /**
     * 飞书长连接 WebSocket Client。
     * <p>
     * 应用完全启动成功后连接飞书服务器，通过 WebSocket 接收事件推送。
     * 无需公网 URL，无需内网穿透。
     */
    @Bean
    public com.lark.oapi.ws.Client feishuWsClient(EventDispatcher eventDispatcher) {
        return new com.lark.oapi.ws.Client.Builder(appId, appSecret)
                .eventHandler(eventDispatcher)
                .autoReconnect(true)
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startFeishuWsClient(ApplicationReadyEvent event) {
        if (!websocketEnabled) {
            log.info("飞书长连接未启动：当前由 WISE 平台直连飞书，本服务仅通过轮询做反馈旁路");
            return;
        }
        com.lark.oapi.ws.Client wsClient = event.getApplicationContext()
                .getBean(com.lark.oapi.ws.Client.class);
        log.info("启动飞书长连接...");
        wsClient.start();
        log.info("飞书长连接已启动，等待事件推送...");
    }

    /**
     * 事件分发器 — 注册消息接收、进入会话和卡片交互回调的处理逻辑。
     */
    @Bean
    public EventDispatcher eventDispatcher(FeishuMessageService feishuMessageService,
                                           MessagePollingService messagePollingService) {
        var builder = EventDispatcher.newBuilder(verificationToken, encryptKey);

        if (!wiseOnlyFeedbackMode || listenMessageEvents) {
            builder.onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) {
                        handleReceiveMessage(event, feishuMessageService, messagePollingService);
                    }
                });
        }

        builder.onP2ChatMemberBotAddedV1(new ImService.P2ChatMemberBotAddedV1Handler() {
            @Override
            public void handle(P2ChatMemberBotAddedV1 event) {
                handleBotAdded(event, messagePollingService);
            }
        });

        builder.onP2ChatMemberBotDeletedV1(new ImService.P2ChatMemberBotDeletedV1Handler() {
            @Override
            public void handle(P2ChatMemberBotDeletedV1 event) {
                handleBotDeleted(event, messagePollingService);
            }
        });

        if (cardActionsEnabled) {
            builder.onP2CardActionTrigger(new P2CardActionTriggerHandler() {
                    @Override
                    public P2CardActionTriggerResponse handle(P2CardActionTrigger event) {
                        return handleCardAction(event, feishuMessageService);
                    }
                });
        }

        if (wiseOnlyFeedbackMode && !listenMessageEvents) {
            log.info("WISE-only 模式下不处理消息事件，仅通过轮询等待 WISE 平台回复");
        }

        if (!cardActionsEnabled) {
            log.info("反馈卡片按钮回调未启用，用户可通过文本「有用/没用/👍/👎」反馈");
        }

        if (!wiseOnlyFeedbackMode) {
            builder.onP2ChatAccessEventBotP2pChatEnteredV1(new ImService.P2ChatAccessEventBotP2pChatEnteredV1Handler() {
                @Override
                public void handle(P2ChatAccessEventBotP2pChatEnteredV1 event) {
                    handleP2pChatEntered(event, feishuMessageService);
                }
            });
        }

        return builder.build();
    }

    private void handleReceiveMessage(P2MessageReceiveV1 event, FeishuMessageService service,
                                      MessagePollingService pollingService) {
        try {
            P2MessageReceiveV1Data data = event.getEvent();
            EventSender sender = data.getSender();
            EventMessage message = data.getMessage();

            String openId = sender.getSenderId().getOpenId();
            String chatType = message.getChatType();
            String messageId = message.getMessageId();
            String chatId = message.getChatId();
            String msgType = message.getMessageType();
            String content = message.getContent();
            String parentId = message.getParentId();
            String createTime = message.getCreateTime();
            boolean groupMessage = !"p2p".equals(chatType);
            boolean mentioned = !groupMessage || hasMention(message.getMentions(), content);

            // 群聊消息需先清除 @mention 标签
            if (groupMessage) {
                content = stripMentions(content, message.getMentions());
            }

            // 提取文本内容
            String textContent = extractText(content);

            log.info("长连接收到消息: chatType={}, msgType={}, openId={}, parentId={}, text={}",
                    chatType, msgType, openId, parentId, textContent);

            if (textContent.isEmpty()) {
                log.info("消息内容为空，跳过");
                return;
            }

            if (groupMessage) {
                pollingService.registerGroupChatForFeedback(chatId, "message_event");
            } else {
                pollingService.registerChatForFeedback(chatId, "message_event");
            }
            if (requireMentionInGroup && groupMessage && !mentioned) {
                log.info("群聊消息未 @ 机器人，跳过处理: chatId={}, messageId={}", chatId, messageId);
                return;
            }

            if ("text".equals(msgType)) {
                pollingService.scheduleFeedbackCard(chatId, messageId, textContent, createTime, openId, parentId);
            }

            if (wiseOnlyFeedbackMode) {
                log.info("WISE-only 反馈模式已开启，仅等待 WISE 回答后发送反馈卡片: messageId={}", messageId);
                return;
            }

            service.handleUserMessage(openId, chatId, chatType, messageId, textContent, parentId);

        } catch (Exception e) {
            log.error("处理长连接消息异常", e);
        }
    }

    private void handleP2pChatEntered(P2ChatAccessEventBotP2pChatEnteredV1 event, FeishuMessageService service) {
        try {
            String chatId = event.getEvent().getChatId();
            String openId = event.getEvent().getOperatorId().getOpenId();

            log.info("用户进入会话: openId={}, chatId={}", openId, chatId);
            service.sendWelcomeMessage(openId, chatId);

        } catch (Exception e) {
            log.error("处理进入会话事件异常", e);
        }
    }

    private void handleBotAdded(P2ChatMemberBotAddedV1 event, MessagePollingService pollingService) {
        try {
            String chatId = event.getEvent().getChatId();
            String groupName = event.getEvent().getName();
            log.info("机器人被加入群聊: chatId={}, name={}", chatId, groupName);
            pollingService.registerGroupChatForFeedback(chatId, "bot_added:" + groupName);
        } catch (Exception e) {
            log.error("处理机器人入群事件异常", e);
        }
    }

    private void handleBotDeleted(P2ChatMemberBotDeletedV1 event, MessagePollingService pollingService) {
        try {
            String chatId = event.getEvent().getChatId();
            String groupName = event.getEvent().getName();
            log.info("机器人被移出群聊: chatId={}, name={}", chatId, groupName);
            pollingService.unregisterChatForFeedback(chatId, "bot_deleted:" + groupName);
        } catch (Exception e) {
            log.error("处理机器人退群事件异常", e);
        }
    }

    /**
     * 处理卡片交互回调（用户点击反馈按钮）。
     * <p>
     * 通过 WebSocket 长连接接收 card.action.trigger 事件，
     * 委托给 FeishuMessageService 处理反馈逻辑。
     */
    private P2CardActionTriggerResponse handleCardAction(P2CardActionTrigger event, FeishuMessageService service) {
        try {
            log.info("收到卡片交互回调: operator={}, action={}",
                    event.getEvent().getOperator().getOpenId(),
                    event.getEvent().getAction().getValue());

            return service.handleFeedbackAction(event);

        } catch (Exception e) {
            log.error("处理卡片交互回调异常", e);
            return null;
        }
    }

    /**
     * 清理群聊消息中的 @mention 标签。
     * <p>
     * 飞书群聊中 @机器人 时，消息 content 的 text 字段会包含类似 {@code @_user_1} 的占位符。
     * 优先使用 SDK 提供的 {@link MentionEvent} 精确匹配并替换，
     * 同时用正则兜底处理 {@code @_user_N} 和 {@code @_all} 等常见格式。
     *
     * @param contentJson 原始消息 content JSON 字符串
     * @param mentions    SDK 解析出的 mention 数组（可能为 null）
     * @return 清除 mention 标签后的 content JSON
     */
    private String stripMentions(String contentJson, MentionEvent[] mentions) {
        if (contentJson == null || contentJson.isEmpty()) {
            return contentJson;
        }

        // 1. 使用 SDK 提供的 mention key 精确替换
        if (mentions != null) {
            for (MentionEvent mention : mentions) {
                String key = mention.getKey();
                if (key != null && !key.isEmpty()) {
                    contentJson = contentJson.replace(key, "");
                }
            }
        }

        // 2. 正则兜底：处理 SDK 未覆盖的 @_user_N 和 @_all 格式
        contentJson = contentJson.replaceAll("@_user_\\d+", "")
                .replaceAll("@_all", "");

        return contentJson;
    }

    private boolean hasMention(MentionEvent[] mentions, String contentJson) {
        if (mentions != null && mentions.length > 0) {
            return true;
        }
        return contentJson != null && (contentJson.contains("@_user_") || contentJson.contains("@_all"));
    }

    private String extractText(String contentJson) {
        try {
            if (contentJson == null || contentJson.isEmpty()) return "";
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(contentJson);
            String text = node.path("text").asText("").trim();
            // 清理群聊消息中的 @mention 标签（如 @_user_1、@_all 等）
            text = text.replaceAll("@_user_\\d+", "").replaceAll("@_all", "").replaceAll("\\s+", " ").trim();
            return text;
        } catch (Exception e) {
            return "";
        }
    }

    public String getAppId() { return appId; }
    public String getAppSecret() { return appSecret; }
    public String getVerificationToken() { return verificationToken; }
    public String getEncryptKey() { return encryptKey; }
}
