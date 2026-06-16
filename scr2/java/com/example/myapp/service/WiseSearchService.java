package com.example.myapp.service;

import com.example.myapp.config.WiseConfig;
import com.example.myapp.dto.WiseSearchResult;
import com.example.myapp.dto.WiseSearchResult.Chunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * WISE 知识库检索服务。
 * <p>
 * 封装 WISE API 调用：search、read_chunks、expand_context 等。
 * 包含重试（3 次指数退避）和降级（返回空结果）策略。
 */
@Service
public class WiseSearchService {

    private static final Logger log = LoggerFactory.getLogger(WiseSearchService.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final WiseConfig wiseConfig;
    private final ObjectMapper objectMapper;

    public WiseSearchService(OkHttpClient wiseHttpClient, WiseConfig wiseConfig, ObjectMapper objectMapper) {
        this.httpClient = wiseHttpClient;
        this.wiseConfig = wiseConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * 语义检索知识。
     *
     * @param query 用户问题
     * @return 检索结果（可能为空）
     */
    public WiseSearchResult search(String query) {
        return search(query, null, 5);
    }

    /**
     * 语义检索知识（带标签过滤）。
     *
     * @param query 用户问题
     * @param tags  标签过滤（如 ["QMS", "IQC"]）
     * @param topK  返回 Top-K 结果
     * @return 检索结果
     */
    public WiseSearchResult search(String query, List<String> tags, int topK) {
        String url = wiseConfig.getApiUrl() + "/knowledge/search";

        try {
            var requestBody = objectMapper.createObjectNode();
            requestBody.put("query", query);
            requestBody.put("top_k", topK);
            if (tags != null && !tags.isEmpty()) {
                var tagsArray = requestBody.putArray("tags");
                tags.forEach(tagsArray::add);
            }

            String responseJson = executeWithRetry(url, requestBody.toString());
            return parseSearchResponse(responseJson);

        } catch (Exception e) {
            log.error("WISE 检索失败: query={}", query, e);
            return new WiseSearchResult(List.of());
        }
    }

    /**
     * 读取指定 Chunk 的完整内容。
     */
    public String readChunk(String chunkId) {
        String url = wiseConfig.getApiUrl() + "/knowledge/read-chunks";

        try {
            var requestBody = objectMapper.createObjectNode();
            var idsArray = requestBody.putArray("chunk_ids");
            idsArray.add(chunkId);

            String responseJson = executeWithRetry(url, requestBody.toString());
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode chunks = root.path("chunks");
            if (chunks.isArray() && chunks.size() > 0) {
                return chunks.get(0).path("content").asText("");
            }
            return "";

        } catch (Exception e) {
            log.error("读取 Chunk 失败: chunkId={}", chunkId, e);
            return "";
        }
    }

    /**
     * 扩展上下文（获取关联知识）。
     */
    public WiseSearchResult expandContext(String chunkId) {
        String url = wiseConfig.getApiUrl() + "/knowledge/expand-context";

        try {
            var requestBody = objectMapper.createObjectNode();
            requestBody.put("chunk_id", chunkId);

            String responseJson = executeWithRetry(url, requestBody.toString());
            return parseSearchResponse(responseJson);

        } catch (Exception e) {
            log.error("扩展上下文失败: chunkId={}", chunkId, e);
            return new WiseSearchResult(List.of());
        }
    }

    // ─── 内部方法 ─────────────────────────────────────

    /**
     * 带重试的 HTTP 调用（指数退避：1s → 4s → 9s）。
     */
    private String executeWithRetry(String url, String jsonBody) throws IOException {
        RequestBody body = RequestBody.create(jsonBody, JSON_TYPE);
        Request request = new Request.Builder().url(url).post(body).build();

        IOException lastException = null;

        for (int attempt = 1; attempt <= wiseConfig.getMaxRetries(); attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().string();
                }

                int code = response.code();
                String errorBody = response.body() != null ? response.body().string() : "";
                log.warn("WISE API 返回错误: attempt={}, code={}, body={}", attempt, code, errorBody);

                if (code >= 400 && code < 500) {
                    // 客户端错误，不重试
                    throw new IOException("WISE API 客户端错误: " + code + " " + errorBody);
                }

            } catch (IOException e) {
                lastException = e;
                log.warn("WISE API 调用失败: attempt={}, url={}", attempt, url, e);
            }

            // 指数退避
            if (attempt < wiseConfig.getMaxRetries()) {
                long delayMs = (long) attempt * attempt * 1000;
                log.info("等待 {}ms 后重试...", delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("重试被中断", ie);
                }
            }
        }

        throw new IOException("WISE API 调用失败（已重试 " + wiseConfig.getMaxRetries() + " 次）", lastException);
    }

    /**
     * 解析 WISE 搜索响应。
     */
    private WiseSearchResult parseSearchResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode resultsNode = root.path("results");
            if (!resultsNode.isArray()) {
                resultsNode = root.path("chunks");
            }

            List<Chunk> chunks = new ArrayList<>();

            if (resultsNode.isArray()) {
                for (JsonNode node : resultsNode) {
                    Chunk chunk = new Chunk();
                    chunk.setContent(node.path("content").asText(
                            node.path("text").asText("")));
                    chunk.setScore(node.path("score").asDouble(
                            node.path("relevance_score").asDouble(0.0)));
                    chunk.setDocumentTitle(node.path("document_title").asText(
                            node.path("title").asText("")));
                    chunk.setSourceUrl(node.path("source_url").asText(
                            node.path("url").asText("")));
                    chunk.setChunkId(node.path("chunk_id").asText(
                            node.path("id").asText("")));
                    chunk.setSystem(node.path("system").asText(null));

                    // 解析标签
                    JsonNode tagsNode = node.path("tags");
                    List<String> tags = new ArrayList<>();
                    if (tagsNode.isArray()) {
                        for (JsonNode tag : tagsNode) {
                            tags.add(tag.asText());
                        }
                    }
                    chunk.setTags(tags);

                    chunks.add(chunk);
                }
            }

            log.info("WISE 检索结果: {} 条", chunks.size());
            return new WiseSearchResult(chunks);

        } catch (Exception e) {
            log.error("解析 WISE 响应失败: {}", responseJson, e);
            return new WiseSearchResult(List.of());
        }
    }
}
