package com.example.myapp.service;

import com.example.myapp.agent.KnowledgeAssistantAgent;
import com.example.myapp.dto.AgentResponse;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.enums.CreateMessageReceiveIdTypeEnum;
import com.lark.oapi.service.im.v1.model.*;
import com.lark.oapi.core.response.BaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 飞书消息服务 — 接收消息 → 调用 Agent → 回复飞书。
 * <p>
 * 全链路：用户消息 → Agent 处理 → 飞书回复 → 异步记录日志
 */
@Service
public class FeishuMessageService {

    private static final Logger log = LoggerFactory.getLogger(FeishuMessageService.class);

    private final Client feishuClient;
    private final KnowledgeAssistantAgent agent;

    public FeishuMessageService(Client feishuClient, KnowledgeAssistantAgent agent) {
        this.feishuClient = feishuClient;
        this.agent = agent;
    }

    /**
     * 处理用户消息（异步，避免飞书 3 秒超时）。
     */
    @Async
    public void handleUserMessage(String openId, String chatId, String chatType,
                                   String messageId, String textContent) {
        log.info("开始处理用户消息: chatType={}, text={}", chatType, textContent);
        long start = System.currentTimeMillis();

        try {
            // ─── 1. 先回复"正在查询中…" ──────────────────
            try {
                if ("p2p".equals(chatType)) {
                    sendTextMessage(openId, CreateMessageReceiveIdTypeEnum.OPEN_ID, "正在查询中…");
                } else {
                    replyTextMessage(messageId, "正在查询中…");
                }
            } catch (Exception e) {
                log.warn("发送占位消息失败，继续处理", e);
            }

            // ─── 2. 调用 Agent 处理 ───────────────────
            AgentResponse response = agent.process(textContent);

            // ─── 3. 构建飞书回复 ─────────────────────────
            String cardJson = buildResponseCard(response, textContent);

            // ─── 4. 回复飞书 ────────────────────────────
            if ("p2p".equals(chatType)) {
                sendCardMessage(openId, CreateMessageReceiveIdTypeEnum.OPEN_ID, cardJson);
            } else {
                replyCardMessage(messageId, cardJson);
            }

            // ─── 5. 异步记录日志（Phase 2 接 Bitable）────
            logQuestionAsync(openId, textContent, response);

            long elapsed = System.currentTimeMillis() - start;
            log.info("消息处理完成: chatType={}, success={}, 耗时={}ms",
                    chatType, response.isSuccess(), elapsed);

        } catch (Exception e) {
            log.error("处理用户消息异常: text={}", textContent, e);
            // 降级：发纯文本错误提示
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
     * 发送欢迎卡片消息。
     */
    public void sendWelcomeMessage(String openId, String chatId) {
        String cardJson = buildWelcomeCard();
        try {
            sendCardMessage(openId, CreateMessageReceiveIdTypeEnum.OPEN_ID, cardJson);
        } catch (Exception e) {
            log.error("发送欢迎消息失败: openId={}", openId, e);
        }
    }

    // ─── 卡片构建 ──────────────────────────────────────

    /**
     * 根据 Agent 响应构建飞书卡片 JSON。
     */
    private String buildResponseCard(AgentResponse response, String question) {
        var elements = new ArrayList<Map<String, Object>>();

        // 主体内容
        var contentElement = new LinkedHashMap<String, Object>();
        contentElement.put("tag", "markdown");
        contentElement.put("content", response.getAnswerText());
        elements.add(contentElement);

        // 卡片
        var card = new LinkedHashMap<String, Object>();
        card.put("schema", "2.0");

        // header
        var header = new LinkedHashMap<String, Object>();
        var titleObj = new LinkedHashMap<String, Object>();
        titleObj.put("tag", "plain_text");

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

        titleObj.put("content", headerTitle);
        header.put("title", titleObj);
        header.put("template", template);
        card.put("header", header);

        var body = new LinkedHashMap<String, Object>();
        body.put("elements", elements);
        card.put("body", body);

        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(card);
        } catch (Exception e) {
            return buildFallbackTextCard(response.getAnswerText());
        }
    }

    /**
     * 兜底：纯文本卡片。
     */
    private String buildFallbackTextCard(String text) {
        return "{\"schema\":\"2.0\",\"header\":{\"title\":{\"tag\":\"plain_text\",\"content\":\"制造知识助手\"},\"template\":\"blue\"},\"body\":{\"elements\":[{\"tag\":\"markdown\",\"content\":\"" +
                escapeJson(text) + "\"}]}}";
    }

    /**
     * 构建欢迎卡片。
     */
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
        card.put("schema", "2.0");

        var header = new LinkedHashMap<String, Object>();
        var titleObj = new LinkedHashMap<String, Object>();
        titleObj.put("tag", "plain_text");
        titleObj.put("content", "👋 制造知识助手");
        header.put("title", titleObj);
        header.put("template", "blue");
        card.put("header", header);

        var body = new LinkedHashMap<String, Object>();
        body.put("elements", elements);
        card.put("body", body);

        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(card);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ─── 消息发送 ──────────────────────────────────────

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

    // ─── 异步记录（Phase 2 接 Bitable）────────────────

    private void logQuestionAsync(String userId, String question, AgentResponse response) {
        try {
            log.info("问答记录: user={}, question={}, success={}, hit={}",
                    userId, question, response.isSuccess(), response.isSuccess());

            if (!response.isSuccess()) {
                log.info("未命中问题已记录: question={}", question);
            }
        } catch (Exception e) {
            log.error("记录问答日志失败", e);
        }
    }

    // ─── 工具方法 ──────────────────────────────────────

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
}
