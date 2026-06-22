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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

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
    /** 支持 Markdown 内换行的 Base64 图片。 */
    private static final Pattern BASE64_IMAGE = Pattern.compile(
            "data:image/[^;\\s]+;base64,[A-Za-z0-9+/=\\r\\n]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^]]*]\\(([^)]+)\\)");
    private static final Pattern MINDOC_SOURCE = Pattern.compile(
            "https://docs\\.cvte\\.com/docs/([^/\\s)]+)/([^\\s)]+)", Pattern.CASE_INSENSITIVE);

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
            String sessionId = createConversation();
            String answer = sendMessageAndCollect(sessionId, userQuestion);
            log.info("WISE answer length={}", answer.length());

            return answer;
        } catch (Exception e) {
            log.error("WISE agent call failed", e);
            return "抱歉，AI 助手暂时不可用，请稍后再试。";
        }
    }

    public record ChatResult(String answer, List<String> imageDataUris) {}

    public ChatResult chatWithAssets(String question) {
        try {
            return sendMessageAndCollectWithAssets(createConversation(), question);
        } catch (Exception e) {
            log.error("WISE agent call failed", e);
            return new ChatResult("抱歉，AI 助手暂时不可用，请稍后再试。", List.of());
        }
    }

    /** 仅用于本地诊断，保留 WISE 的原始失败原因。 */
    public String chatForDiagnosis(String userQuestion) {
        try {
            return sendMessageAndCollect(createConversation(), userQuestion);
        } catch (Exception e) {
            return "ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    // ---- Internal implementation ----

    /**
     * 创建 WISE 会话，返回 conversationId。
     */
    private String createConversation() throws IOException {
        String url = wiseConfig.getApiUrl() + "/sessions";
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("title", "飞书知识问答"));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Create conversation failed: " + response.code()
                        + " " + (response.body() != null ? response.body().string() : ""));
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            String id = root.path("data").path("id").asText(null);
            if (id == null) {
                throw new IOException("Missing session id in response: " + root);
            }
            return id;
        }
    }

    /**
     * 发送消息并以 SSE 流式方式收集完整回答。
     */
    private String sendMessageAndCollect(String conversationId, String question) throws IOException {
        return sendMessageAndCollectWithAssets(conversationId, question).answer();
    }

    private ChatResult sendMessageAndCollectWithAssets(String conversationId, String question) throws IOException {
        String url = wiseConfig.getApiUrl() + "/chat/" + conversationId;
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "query", question,
                "agent_id", wiseConfig.getAgentId(),
                "channel", "im",
                "stream", true
        ));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Accept", "text/event-stream")
                .post(RequestBody.create(body, JSON_TYPE))
                .build();

        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            String error = response.body() != null ? response.body().string() : "";
            response.close();
            throw new IOException("Chat request failed: " + response.code() + " " + error);
        }
        return parseSseStream(response, conversationId);
    }

    /**
     * 解析 SSE 流，拼接所有 answer 事件的 content，最终统一去除思考块。
     * 采用"先拼接、后清洗"策略：流式阶段原样累积全部 token，
     * 流结束后用 stripThinkBlocks() 一次性剥离所有 think 块。
     *
     * @param response OkHttp 响应（调用方无需提前关闭，本方法负责关闭）
     * @return 清洗后的完整回答文本
     */
    private ChatResult parseSseStream(Response response, String conversationId) throws IOException {
        StringBuilder rawAnswer = new StringBuilder();
        Set<String> knowledgeIds = new LinkedHashSet<>();
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

                    if ("message".equals(currentEvent) || currentEvent == null) {
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            if ("references".equals(node.path("response_type").asText(""))) {
                                for (JsonNode ref : node.path("knowledge_references")) {
                                    String id = ref.path("knowledge_id").asText("");
                                    if (!id.isBlank()) knowledgeIds.add(id);
                                }
                                continue;
                            }
                            // Chat API 通过 response_type 区分 thinking、tool_call、answer 等事件。
                            if (!"answer".equals(node.path("response_type").asText(""))) {
                                continue;
                            }
                            String content = node.path("content").asText("");
                            if (!content.isEmpty()) {
                                if (!rawAnswer.toString().endsWith(content)) {
                                    rawAnswer.append(content);
                                }
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
                            return new ChatResult("AI 处理出错：" + errMsg, List.of());
                        } catch (Exception e) {
                            log.error("WISE SSE error event (unparsed): {}", data);
                            return new ChatResult("AI 处理出错，请稍后再试。", List.of());
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

        // Agent 模式有时不会在 SSE 中发 references，但会在会话历史落库后提供。
        // 回读一次会话，避免漏掉 WISE 前端可以显示、API 流却未直接携带的图片来源。
        knowledgeIds.addAll(loadSessionKnowledgeIds(conversationId));

        // 有些 Agent 会把 data URI 直接放进回答；另一些只在引用知识的 Markdown 中保留图片。
        // 两种来源都需要同步，不能因来源不同而丢图。
        int expectedImageCount = countMarkdownImages(raw);
        List<String> images = new ArrayList<>();
        collectBase64Images(raw, images, expectedImageCount > 0 ? expectedImageCount : 6);
        for (String image : loadReferencedImages(knowledgeIds)) {
            if (!images.contains(image) && images.size() < Math.max(expectedImageCount, 6)) {
                images.add(image);
            }
        }
        // WISE 当前 Agent 对 s3:// 图片只在文本中给出内部地址，可能不返回 references。
        // 这些 Markdown 是本服务已同步入库的同源文档；按来源 URL 精确回找并提取 Base64 图片。
        if (expectedImageCount > images.size()) {
            collectLocalSourceImages(raw, images, expectedImageCount);
        }
        log.info("WISE 引用知识数={}, 提取到 Base64 图片数={}", knowledgeIds.size(), images.size());
        return new ChatResult(cleaned.trim(), images);
    }

    private int countMarkdownImages(String markdown) {
        if (markdown == null) return 0;
        int count = 0;
        Matcher matcher = MARKDOWN_IMAGE.matcher(markdown);
        while (matcher.find()) count++;
        return count;
    }

    /** 从本项目知识源中按 MinDoc 文档 ID 定位同源 Markdown，避免依赖不可公开访问的 s3:// URL。 */
    private void collectLocalSourceImages(String answer, List<String> images, int expectedImageCount) {
        Matcher sourceMatcher = MINDOC_SOURCE.matcher(answer == null ? "" : answer);
        while (sourceMatcher.find() && images.size() < expectedImageCount) {
            String project = sourceMatcher.group(1);
            String docId = sourceMatcher.group(2);
            Path projectRoot = Path.of("knowledge-base", project);
            if (!Files.isDirectory(projectRoot)) {
                log.warn("本地知识源目录不存在，无法补齐图片: project={}, docId={}", project, docId);
                continue;
            }
            try (var paths = Files.walk(projectRoot, 2)) {
                Path sourceFile = paths
                        .filter(path -> path.toString().endsWith(".md"))
                        .filter(path -> hasDocumentId(path, docId))
                        .findFirst().orElse(null);
                if (sourceFile == null) {
                    log.warn("本地知识源未找到同源文档: docId={}", docId);
                    continue;
                }
                int before = images.size();
                collectBase64Images(Files.readString(sourceFile), images, expectedImageCount);
                log.info("从本地同源 Markdown 补齐图片: file={}, 新增图片数={}",
                        sourceFile.getFileName(), images.size() - before);
            } catch (Exception e) {
                log.warn("读取本地同源 Markdown 图片失败: docId={}", docId, e);
            }
        }
    }

    /** 只读取文件头，避免为定位一个文档把所有含图片的 Markdown 全量加载到内存。 */
    private boolean hasDocumentId(Path path, String docId) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            for (int line = 0; line < 20; line++) {
                String value = reader.readLine();
                if (value == null) return false;
                if (value.equals("doc_id: " + docId) || value.equals("source_url: https://docs.cvte.com/docs/mfg/" + docId)
                        || value.contains("/" + docId)) return true;
            }
        } catch (IOException e) {
            log.debug("读取本地知识文档头失败: {}", path, e);
        }
        return false;
    }

    /**
     * 读取 WISE 已持久化的 assistant 消息，补齐 SSE 中可能缺失的知识引用。
     * 消息落库可能略晚于 SSE 结束，因此做短暂、有限次数的重试。
     */
    private Set<String> loadSessionKnowledgeIds(String sessionId) {
        Set<String> ids = new LinkedHashSet<>();
        OkHttpClient messageClient = httpClient.newBuilder().callTimeout(8, TimeUnit.SECONDS).build();
        String url = wiseConfig.getApiUrl() + "/messages/" + sessionId + "/load?limit=20";
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Request req = new Request.Builder().url(url).get().build();
                try (Response resp = messageClient.newCall(req).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        log.warn("读取 WISE 会话消息失败: sessionId={}, attempt={}, httpCode={}",
                                sessionId, attempt, resp.code());
                    } else {
                        JsonNode messages = objectMapper.readTree(resp.body().string()).path("data");
                        for (JsonNode message : messages) {
                            if (!"assistant".equals(message.path("role").asText())) continue;
                            for (JsonNode ref : message.path("knowledge_references")) {
                                String id = ref.path("knowledge_id").asText("");
                                if (!id.isBlank()) ids.add(id);
                            }
                        }
                        if (!ids.isEmpty()) {
                            log.info("从 WISE 会话历史补齐知识引用: sessionId={}, count={}", sessionId, ids.size());
                            return ids;
                        }
                    }
                }
                if (attempt < 3) Thread.sleep(attempt * 400L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ids;
            } catch (Exception e) {
                log.warn("读取 WISE 会话消息异常: sessionId={}, attempt={}", sessionId, attempt, e);
            }
        }
        log.info("WISE 会话历史未返回知识引用: sessionId={}", sessionId);
        return ids;
    }

    private List<String> loadReferencedImages(Set<String> knowledgeIds) {
        List<String> images = new ArrayList<>();
        // 知识文件可能较大；图片是答案的一部分，不能用过短超时静默放弃。
        OkHttpClient assetClient = httpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build();
        for (String id : knowledgeIds.stream().limit(3).toList()) {
            try {
                Request req = new Request.Builder().url(wiseConfig.getApiUrl() + "/knowledge/" + id + "/download").get().build();
                try (Response resp = assetClient.newCall(req).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        log.warn("下载引用知识失败: knowledgeId={}, httpCode={}", id, resp.code());
                        continue;
                    }
                    int before = images.size();
                    collectBase64Images(resp.body().string(), images, 6);
                    log.info("引用知识下载完成: knowledgeId={}, 新增图片数={}", id, images.size() - before);
                }
            } catch (Exception e) { log.warn("下载引用知识图片失败: knowledgeId={}", id, e); }
        }
        return images;
    }

    private void collectBase64Images(String markdown, List<String> images, int maxImages) {
        if (markdown == null || images.size() >= maxImages) {
            return;
        }
        Matcher matcher = BASE64_IMAGE.matcher(markdown);
        while (matcher.find() && images.size() < maxImages) {
            String dataUri = matcher.group().replaceAll("\\s", "");
            if (!images.contains(dataUri)) {
                images.add(dataUri);
            }
        }
    }

    /** 兼容不同版本 Chat API 的 SSE 正文载荷。 */
    private String extractStreamContent(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        for (String field : new String[]{"content", "answer", "text"}) {
            String value = node.path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        for (String container : new String[]{"data", "delta", "message"}) {
            JsonNode nested = node.path(container);
            if (nested.isObject()) {
                String value = extractStreamContent(nested);
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
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
        // smart-reasoning 的流式协议会把工具调用轨迹与最终回答拼在同一流中。
        // 飞书只展示 final_answer，避免把内部推理与检索日志暴露给用户。
        String finalAnswerMarker = "Calling tool: final_answer#";
        int finalAnswerIndex = text.lastIndexOf(finalAnswerMarker);
        if (finalAnswerIndex >= 0) {
            text = text.substring(finalAnswerIndex + finalAnswerMarker.length()).trim();
        }
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
