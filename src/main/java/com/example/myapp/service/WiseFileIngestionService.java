package com.example.myapp.service;

import com.example.myapp.config.WiseConfig;
import com.example.myapp.dto.WiseFileIngestionResult;
import com.example.myapp.model.MindocDocumentMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * WISE 文件入库服务（PDR V8.1 US-05）。
 * <p>
 * 负责通过 WISE File API 以 multipart/form-data 形式上传 Markdown 文件，
 * 并在上传后轮询 {@code parse_status} 确认解析完成。
 * <p>
 * 调用流程：
 * <pre>
 * 1. uploadMarkdownFile(file, metadata) → 得到 wise_file_id
 * 2. pollParseStatus(wiseFileId)        → 轮询直到 completed / failed
 * 3. 更新文档映射表（由调用方维护）
 * </pre>
 *
 * <p>
 * 设计说明（PDR V8.1）：
 * <ul>
 *   <li>不使用 WISE Markdown 文件入库接口，而使用文件入库接口</li>
 *   <li>不上传 URL，而以文件形式上传本地 Markdown</li>
 *   <li>上传成功后轮询 parse_status 确认解析完成后再更新映射表</li>
 *   <li>新文件解析失败时保留旧文件继续生效</li>
 * </ul>
 */
@Service
public class WiseFileIngestionService {

    private static final Logger log = LoggerFactory.getLogger(WiseFileIngestionService.class);

    /** parse_status 轮询间隔（秒） */
    private static final int POLL_INTERVAL_SECONDS = 5;

    /** parse_status 轮询超时（秒） */
    private static final int POLL_TIMEOUT_SECONDS = 300;

    private final WiseConfig wiseConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WiseFileIngestionService(WiseConfig wiseConfig, OkHttpClient wiseHttpClient,
                                    ObjectMapper objectMapper) {
        this.wiseConfig = wiseConfig;
        this.httpClient = wiseHttpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 上传 Markdown 文件到 WISE 知识库。
     *
     * <p>
     * 通过 multipart/form-data 接口：
     * {@code POST /api/v1/knowledge-bases/{kbId}/knowledge/file}
     *
     * @param kbId     WISE 知识库 ID
     * @param file     要上传的 Markdown 文件
     * @param mapping  文档映射信息（作为 metadata 字段传入）
     * @return 上传结果，包含 wise_file_id
     */
    public WiseFileIngestionResult uploadMarkdownFile(String kbId, File file,
                                                       MindocDocumentMapping mapping) {
        String url = wiseConfig.getApiUrl() + "/knowledge-bases/" + kbId + "/knowledge/file";
        log.info("上传 Markdown 文件到 WISE: file={}, kbId={}, title={}",
                file.getName(), kbId, mapping.getTitle());

        try {
            // 构造 multipart body
            MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.getName(),
                            RequestBody.create(file, MediaType.parse("text/markdown")))
                    .addFormDataPart("metadata.source", "MinDoc")
                    .addFormDataPart("metadata.project_name", mapping.getProjectName())
                    .addFormDataPart("metadata.source_url", mapping.getSourceUrl())
                    .addFormDataPart("metadata.normalized_url", mapping.getNormalizedUrl())
                    .addFormDataPart("metadata.url_hash", mapping.getUrlHash());

            if (mapping.getDocId() != null) {
                bodyBuilder.addFormDataPart("metadata.doc_id", mapping.getDocId());
            }
            bodyBuilder.addFormDataPart("metadata.md_hash", mapping.getMdHash());
            bodyBuilder.addFormDataPart("metadata.title", mapping.getTitle());

            Request request = new Request.Builder()
                    .url(url)
                    .post(bodyBuilder.build())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null
                        ? response.body().string() : "{}";
                JsonNode root = objectMapper.readTree(responseBody);

                WiseFileIngestionResult result = new WiseFileIngestionResult();
                result.setErrcode(root.path("errcode").asInt(-1));
                result.setErrmsg(root.path("errmsg").asText(""));

                if (result.isSuccess()) {
                    JsonNode data = root.path("data");
                    result.setFileId(data.path("file_id").asText(
                            data.path("id").asText("")));
                    result.setKnowledgeId(data.path("knowledge_id").asText(""));
                    result.setParseStatus(data.path("parse_status").asText("processing"));

                    log.info("WISE 文件上传成功: fileId={}, knowledgeId={}, parseStatus={}",
                            result.getFileId(), result.getKnowledgeId(), result.getParseStatus());
                } else {
                    log.error("WISE 文件上传失败: errcode={}, errmsg={}, response={}",
                            result.getErrcode(), result.getErrmsg(), responseBody);
                    result.setFailureReason("上传失败: " + result.getErrmsg());
                }

                return result;
            }

        } catch (IOException e) {
            log.error("WISE 文件上传异常: file={}", file.getName(), e);
            WiseFileIngestionResult result = new WiseFileIngestionResult();
            result.setErrcode(-1);
            result.setFailureReason("上传异常: " + e.getMessage());
            return result;
        }
    }

    /**
     * 轮询 WISE 文件解析状态。
     *
     * <p>
     * 直到 parse_status 变为 completed 或 failed，或超时。
     *
     * @param kbId       WISE 知识库 ID
     * @param wiseFileId WISE 文件 ID
     * @return 最终解析状态: "completed" / "failed" / "timeout"
     */
    public String pollParseStatus(String kbId, String wiseFileId) {
        String url = wiseConfig.getApiUrl() + "/knowledge-bases/" + kbId
                + "/knowledge/" + wiseFileId;
        log.info("轮询 WISE 解析状态: fileId={}", wiseFileId);

        int elapsed = 0;
        while (elapsed < POLL_TIMEOUT_SECONDS) {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        log.warn("WISE 状态查询返回非成功: code={}, fileId={}",
                                response.code(), wiseFileId);
                        Thread.sleep(POLL_INTERVAL_SECONDS * 1000L);
                        elapsed += POLL_INTERVAL_SECONDS;
                        continue;
                    }

                    String body = response.body() != null ? response.body().string() : "{}";
                    JsonNode root = objectMapper.readTree(body);
                    JsonNode data = root.path("data");
                    String status = data.path("parse_status").asText("processing");

                    if ("completed".equals(status)) {
                        log.info("WISE 解析完成: fileId={}", wiseFileId);
                        return "completed";
                    } else if ("failed".equals(status)) {
                        String reason = data.path("failure_reason").asText("unknown");
                        log.warn("WISE 解析失败: fileId={}, reason={}", wiseFileId, reason);
                        return "failed";
                    }

                    // processing — 继续轮询
                }

            } catch (IOException e) {
                log.warn("轮询 WISE 解析状态异常: fileId={}", wiseFileId, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("轮询被中断: fileId={}", wiseFileId, e);
                return "interrupted";
            }

            try {
                Thread.sleep(POLL_INTERVAL_SECONDS * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "interrupted";
            }
            elapsed += POLL_INTERVAL_SECONDS;
        }

        log.warn("WISE 解析超时: fileId={}, timeout={}s", wiseFileId, POLL_TIMEOUT_SECONDS);
        return "timeout";
    }

    /**
     * 完整的 Markdown 文件入库流程（上传 + 轮询）。
     *
     * <p>
     * 如果新文件解析失败，保留原映射中的 wise_file_id 继续生效。
     *
     * @param kbId        WISE 知识库 ID
     * @param file        要上传的 Markdown 文件
     * @param mapping     文档映射信息
     * @param oldMapping  旧的映射记录（用于回退保护）
     * @return 入库结果
     */
    public WiseFileIngestionResult ingestMarkdownFile(String kbId, File file,
                                                       MindocDocumentMapping mapping,
                                                       MindocDocumentMapping oldMapping) {
        // 1. 上传文件
        WiseFileIngestionResult uploadResult = uploadMarkdownFile(kbId, file, mapping);
        if (!uploadResult.isSuccess()) {
            return uploadResult;
        }

        // 2. 轮询 parse_status
        String parseStatus = pollParseStatus(kbId, uploadResult.getFileId());
        uploadResult.setParseStatus(parseStatus);

        if (parseStatus.equals("completed")) {
            log.info("文件入库完成: title={}, wiseFileId={}", mapping.getTitle(), uploadResult.getFileId());
        } else {
            log.warn("文件解析未完成: title={}, status={}", mapping.getTitle(), parseStatus);
            // 解析失败时保留旧文件继续生效（由调用方根据 this result 决定是否回退）
        }

        return uploadResult;
    }

    /**
     * 计算文件的 MD5 或 SHA256 哈希。
     */
    public static String computeFileHash(File file, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
            byte[] hashBytes = digest.digest(fileBytes);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("计算文件哈希失败: " + algorithm, e);
        }
    }
}