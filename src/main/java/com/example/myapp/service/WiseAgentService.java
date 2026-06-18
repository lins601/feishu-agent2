package com.example.myapp.service;

import com.example.myapp.config.WiseConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * WISE 智能体对话服务。
 * <p>
 * 通过 WISE Agent Chat API（SSE 流式）获取智能体回答。
 * 流程：创建会话 -> 发送消息 -> 解析 SSE 流 -> 拼接回答文本。
 * <p>
 * 同步机制：
 * - 每次用户提问创建一个新的 WISE 会话（无状态，不支持多轮追问）
 * - SSE 流式接收 answer 事件，逐 token 拼接
 * - 过滤掉 think 思考块，只保留最终回答
 * - 完整回答通过飞书卡片消息返回给用户
 */
@Service
public class WiseAgentService {

    private static final Logger log = LoggerFactory.getLogger(WiseAgentService.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    /** think 开始标签（拼接避免被 XML 解析器误读） */
    private static final String THINK_OPEN = "<" + "think>";
    /** think 结束标签（拼接避免被 XML 解析器误读） */
    private static final String THINK_CLOSE = "<" + "/think>";

    private final WiseConfig wiseConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WiseAgentService(WiseConfig wiseConfig, OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.wiseConfig = wiseConfig;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return wiseConfig != null
                && wiseConfig.getApiKey() != null
                && !wiseConfig.getApiKey().isBlank()
                && wiseConfig.getAgentId() != null
                && !wiseConfig.getAgentId().isBlank();
    }

    /**
     * 向 WISE 智能体发送问题，同步等待完整回答。
     *
     * @param userQuestion 用户原始提问文本
     * @return 智能体的完整回答（已过滤思考块）；异常时返回错误提示
     */
    public String chat(String userQuestion) {
        try {
            // 1. 创建新会话
            String conversationId = createConversation();
            log.info("WISE conversation created: {}", conversationId);

            // 2. 发送消息并同步读取 SSE 流（内部完成累积和思考块过滤）
            String answer = sendMessageAndCollect(conversationId, userQuestion);
            log.info("WISE answer length={}", answer.length());

            return answer;
        } catch (Exception e) {
            log.error("WISE agent call failed", e);
            return "抱歉，AI 助手暂时不可用，请稍后再试。";
        }
    }

    // ---- Internal implementation ----

    /**
     * 创建 WISE 会话，返回 conversationId。
     */
    private String createConversation() throws IOException {
        String url = wiseConfig.getApiUrl() + "/agent/conversation";
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("agentId", wiseConfig.getAgentId()));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + wiseConfig.getApiKey())
                .post(RequestBody.create(body, JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Create conversation failed: " + response.code()
                        + " " + (response.body() != null ? response.body().string() : ""));
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            String id = root.path("data").path("conversationId").asText(null);
            if (id == null) {
                throw new IOException("Missing conversationId in response: " + root);
            }
            return id;
        }
    }

    /**
     * 发送消息并以 SSE 流式方式收集完整回答。
     */
    private String sendMessageAndCollect(String conversationId, String question) throws IOException {
        String url = wiseConfig.getApiUrl() + "/agent/chat";
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "conversation_id", conversationId,
                "message", question,
                "stream", true
        ));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + wiseConfig.getApiKey())
                .addHeader("Accept", "text/event-stream")
                .post(RequestBody.create(body, JSON_TYPE))
                .build();

        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            response.close();
            throw new IOException("Chat request failed: " + response.code());
        }

        return parseSseStream(response);
    }

    /**
     * 解析 SSE 流，拼接所有 answer 事件的 content，最终统一去除思考块。
     * 采用"先拼接、后清洗"策略：流式阶段原样累积全部 token，
     * 流结束后用 stripThinkBlocks() 一次性剥离所有 think 块。
     *
     * @param response OkHttp 响应（调用方无需提前关闭，本方法负责关闭）
     * @return 清洗后的完整回答文本
     */
    private String parseSseStream(Response response) throws IOException {
        StringBuilder rawAnswer = new StringBuilder();
        int answerEventCount = 0;
        String currentEvent = null;

        try (Response resp = response;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(resp.body().byteStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // 空行 = SSE 事件分隔符
                    currentEvent = null;
                    continue;
                }

                if (line.startsWith("event:")) {
                    currentEvent = line.substring("event:".length()).trim();
                    continue;
                }

                if (line.startsWith("data:")) {
                    String data = line.substring("data:".length()).trim();

                    if ("answer".equals(currentEvent)) {
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            String content = node.path("content").asText("");
                            if (!content.isEmpty()) {
                                rawAnswer.append(content);
                                answerEventCount++;
                                if (answerEventCount <= 3 || answerEventCount % 50 == 0) {
                                    log.info("SSE answer event #{}: contentLength={}, totalLength={}",
                                            answerEventCount, content.length(), rawAnswer.length());
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse SSE answer data: {}", data, e);
                        }
                    } else if ("complete".equals(currentEvent)) {
                        log.debug("SSE stream complete");
                        break;
                    } else if ("error".equals(currentEvent)) {
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            String errMsg = node.path("message").asText("unknown error");
                            log.error("WISE SSE error event: {}", errMsg);
                            return "AI 处理出错：" + errMsg;
                        } catch (Exception e) {
                            log.error("WISE SSE error event (unparsed): {}", data);
                            return "AI 处理出错，请稍后再试。";
                        }
                    }
                }
            }
        }

        // -- 流结束：一次性清洗所有 think 块 --
        String raw = rawAnswer.toString();
        int rawLength = raw.length();
        String cleaned = stripThinkBlocks(raw);
        int finalLength = cleaned.length();

        log.info("SSE stream done: rawLength={}, finalLength={}, answerEvents={}",
                rawLength, finalLength, answerEventCount);

        return cleaned.trim();
    }

    /**
     * 去除文本中所有 think 思考块。
     * <p>
     * 使用 indexOf 逐块查找并剥除，能正确处理多个 think 块和未闭合的 think 块。
     *
     * @param text 原始回答文本（可能包含 think 标签）
     * @return 去除思考块后的纯净回答
     */
    private String stripThinkBlocks(String text) {
        if (text == null || text.isEmpty()) return text;
        if (!text.contains(THINK_CLOSE)) return text;

        StringBuilder sb = new StringBuilder(text.length());
        int pos = 0;
        while (pos < text.length()) {
            int thinkStart = text.indexOf(THINK_OPEN, pos);
            if (thinkStart < 0) {
                sb.append(text, pos, text.length());
                break;
            }
            sb.append(text, pos, thinkStart);
            int thinkEnd = text.indexOf(THINK_CLOSE, thinkStart);
            if (thinkEnd < 0) {
                // 未闭合的 think 块，丢弃从 thinkStart 到末尾的所有内容
                break;
            }
            pos = thinkEnd + THINK_CLOSE.length();
        }

        return sb.toString();
    }
}
