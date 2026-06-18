package com.example.myapp.service;

import com.example.myapp.config.WiseConfig;
import com.example.myapp.model.MindocDocumentMapping;
import com.example.myapp.model.MindocSyncConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * MinDoc -> WISE 定时同步。
 * <p>
 * 管理员在飞书 sync_record 表维护 MinDoc 入口链接，本服务每周读取配置并调用爬虫脚本上传到 WISE。
 */
@Service
public class MindocSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(MindocSyncScheduler.class);
    private static final int OUTPUT_TAIL_LIMIT = 4000;
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final LarkBitableService bitableService;
    private final WiseConfig wiseConfig;
    private final ObjectMapper objectMapper;
    private final FeishuAdminNotifier adminNotifier;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${mindoc.sync.enabled:true}")
    private boolean enabled;

    @Value("${mindoc.sync.python:python3}")
    private String pythonCommand;

    @Value("${mindoc.sync.script-path:scripts/crawl_mindoc_wise_v4_incremental_delete.py}")
    private String scriptPath;

    @Value("${mindoc.sync.output-dir:knowledge-base}")
    private String outputDir;

    @Value("${mindoc.sync.default-kb-id:}")
    private String defaultKnowledgeBaseId;

    @Value("${mindoc.sync.max-pages:0}")
    private int maxPages;

    @Value("${mindoc.sync.concurrency:10}")
    private int concurrency;

    @Value("${mindoc.sync.upload-concurrency:5}")
    private int uploadConcurrency;

    @Value("${mindoc.sync.delay:0.05}")
    private double delaySeconds;

    @Value("${mindoc.sync.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${mindoc.sync.delete-old-on-update:true}")
    private boolean deleteOldOnUpdate;

    @Value("${mindoc.sync.inject-images:true}")
    private boolean injectImages;

    @Value("${mindoc.sync.poll:true}")
    private boolean pollParseStatus;

    public MindocSyncScheduler(LarkBitableService bitableService, WiseConfig wiseConfig) {
        this(bitableService, wiseConfig, new ObjectMapper(), null);
    }

    @Autowired
    public MindocSyncScheduler(LarkBitableService bitableService, WiseConfig wiseConfig,
                               ObjectMapper objectMapper, FeishuAdminNotifier adminNotifier) {
        this.bitableService = bitableService;
        this.wiseConfig = wiseConfig;
        this.objectMapper = objectMapper;
        this.adminNotifier = adminNotifier;
    }

    @Async
    @Scheduled(cron = "${mindoc.sync.cron:0 0 2 ? * THU}",
            zone = "${mindoc.sync.zone:Asia/Shanghai}")
    public void runWeeklySync() {
        runSyncOnce();
    }

    public SyncSummary runSyncOnce() {
        if (!enabled) {
            log.info("MinDoc 定时同步已关闭");
            return new SyncSummary(0, 0, 0, 0);
        }
        if (!running.compareAndSet(false, true)) {
            log.info("已有 MinDoc 同步任务运行中，本轮跳过");
            return new SyncSummary(0, 0, 0, 0);
        }

        int total = 0;
        int success = 0;
        int failed = 0;
        int skipped = 0;
        try {
            String defaultKbId = resolveDefaultKnowledgeBaseId();
            List<MindocSyncConfig> configs = bitableService.listMindocSyncConfigs(defaultKbId);
            log.info("读取到 MinDoc 同步任务: {} 条", configs.size());

            for (MindocSyncConfig config : configs) {
                if (!isEnabled(config)) {
                    skipped++;
                    continue;
                }
                total++;
                if (syncOne(config)) {
                    success++;
                } else {
                    failed++;
                }
            }

            log.info("MinDoc 同步完成: total={}, success={}, failed={}, skipped={}",
                    total, success, failed, skipped);
            return new SyncSummary(total, success, failed, skipped);
        } finally {
            running.set(false);
        }
    }

    private boolean syncOne(MindocSyncConfig config) {
        String recordId = config.getRecordId();
        String projectName = safeProjectName(firstNonBlank(config.getProjectName(), config.getConfigId(), "MINDOC"));
        String sourceUrl = config.getSourceUrl();
        String kbId = firstNonBlank(config.getWiseKbId(), resolveDefaultKnowledgeBaseId());

        log.info("开始 MinDoc 同步: configId={}, project={}, url={}",
                config.getConfigId(), projectName, sourceUrl);
        bitableService.updateMindocSyncStatus(recordId, "running", "processing", "");

        try {
            if (sourceUrl == null || sourceUrl.isBlank() || !sourceUrl.startsWith("http")) {
                throw new IllegalArgumentException("source_url/url 不是有效的 HTTP 链接");
            }
            if (kbId.isBlank()) {
                throw new IllegalArgumentException("WISE 知识库 ID 为空");
            }

            List<String> command = buildCommand(config, kbId, wiseConfig.getApiUrl(), wiseConfig.getApiKey());
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(Path.of(System.getProperty("user.dir")).toFile())
                    .redirectErrorStream(true);

            Process process = processBuilder.start();
            String outputTail = readOutputTail(process);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                bitableService.updateMindocSyncStatus(recordId, "success", "completed", "");
                syncMappingsAndSummary(recordId, projectName);
                log.info("MinDoc 同步成功: configId={}, project={}", config.getConfigId(), projectName);
                return true;
            }

            String error = "脚本退出码: " + exitCode + "\n" + outputTail;
            syncMappingsAndSummary(recordId, projectName);
            bitableService.updateMindocSyncStatus(recordId, "failed", "failed", truncate(error));
            notifySyncFailure(projectName, sourceUrl, error);
            log.warn("MinDoc 同步失败: configId={}, exitCode={}", config.getConfigId(), exitCode);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            bitableService.updateMindocSyncStatus(recordId, "failed", "failed", "同步任务被中断");
            notifySyncFailure(projectName, sourceUrl, "同步任务被中断");
            log.warn("MinDoc 同步被中断: configId={}", config.getConfigId(), e);
            return false;
        } catch (Exception e) {
            bitableService.updateMindocSyncStatus(recordId, "failed", "failed", truncate(e.getMessage()));
            notifySyncFailure(projectName, sourceUrl, e.getMessage());
            log.warn("MinDoc 同步异常: configId={}", config.getConfigId(), e);
            return false;
        }
    }

    List<MindocDocumentMapping> syncDocumentMappings(String projectName) {
        if (bitableService == null) {
            return List.of();
        }
        try {
            Path mappingPath = findMappingPath(projectName);
            if (mappingPath == null) {
                log.warn("未找到 MinDoc 文档映射 JSON: project={}, outputDir={}", projectName, outputDir);
                notifySyncFailure(projectName, outputDir, "同步脚本执行成功，但未生成 mindoc_document_mapping.json");
                return List.of();
            }
            List<MindocDocumentMapping> mappings = readMappings(projectName, mappingPath);
            bitableService.upsertMindocDocumentMappings(mappings);
            return mappings;
        } catch (Exception e) {
            log.warn("同步 MinDoc 文档映射到多维表格失败: project={}", projectName, e);
            notifySyncFailure(projectName, outputDir, "文档映射回写失败: " + e.getMessage());
            return List.of();
        }
    }

    private void syncMappingsAndSummary(String recordId, String projectName) {
        List<MindocDocumentMapping> mappings = syncDocumentMappings(projectName);
        bitableService.updateMindocSyncMappingSummary(recordId, mappings);
    }

    Path findMappingPath(String projectName) throws Exception {
        Path root = Path.of(outputDir);
        String mappingFile = "mindoc_document_mapping.json";
        Path direct = root.resolve(projectName).resolve(mappingFile);
        if (Files.exists(direct)) {
            return direct;
        }
        Path lower = root.resolve(projectName.toLowerCase(Locale.ROOT)).resolve(mappingFile);
        if (Files.exists(lower)) {
            return lower;
        }
        if (!Files.exists(root)) {
            return null;
        }
        try (Stream<Path> paths = Files.find(root, 4,
                (path, attrs) -> attrs.isRegularFile() && mappingFile.equals(path.getFileName().toString()))) {
            String normalizedProject = projectName.toLowerCase(Locale.ROOT);
            return paths
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).contains(normalizedProject))
                    .findFirst()
                    .orElse(null);
        }
    }

    List<MindocDocumentMapping> readMappings(String projectName, Path mappingPath) throws Exception {
        JsonNode root = objectMapper.readTree(mappingPath.toFile());
        List<MindocDocumentMapping> mappings = new ArrayList<>();
        String now = TIMESTAMP_FMT.format(Instant.now());
        if (root.isArray()) {
            root.forEach(node -> mappings.add(mappingFromJson(projectName, "", node, now)));
        } else {
            root.fields().forEachRemaining(entry ->
                    mappings.add(mappingFromJson(projectName, entry.getKey(), entry.getValue(), now)));
        }
        return mappings;
    }

    private MindocDocumentMapping mappingFromJson(String defaultProjectName, String entryKey,
                                                  JsonNode node, String now) {
        String projectName = firstNonBlank(node.path("project_name").asText(""), defaultProjectName);
        String urlHash = node.path("url_hash").asText("");
        String documentKey = firstNonBlank(node.path("document_key").asText(""), entryKey,
                !urlHash.isBlank() ? projectName + "_" + urlHash : "");

        MindocDocumentMapping mapping = new MindocDocumentMapping();
        mapping.setProjectName(projectName);
        mapping.setDocumentKey(documentKey);
        mapping.setSourceUrl(node.path("source_url").asText(""));
        mapping.setNormalizedUrl(node.path("normalized_url").asText(""));
        mapping.setUrlHash(urlHash);
        mapping.setDocId(node.path("doc_id").asText(""));
        mapping.setTitle(node.path("title").asText(""));
        mapping.setMdHash(node.path("md_hash").asText(""));
        mapping.setWiseFileId(firstNonBlank(node.path("wise_file_id").asText(""),
                node.path("wise_knowledge_id").asText("")));
        mapping.setOldWiseFileId(firstNonBlank(node.path("old_wise_file_id").asText(""),
                node.path("old_wise_knowledge_id").asText("")));
        mapping.setSyncStatus(firstNonBlank(node.path("sync_status").asText(""), "completed"));
        mapping.setParseStatus(firstNonBlank(node.path("parse_status").asText(""), "completed"));
        mapping.setLastSeenTime(firstNonBlank(node.path("last_seen_time").asText(""),
                node.path("crawled_at").asText(""), now));
        mapping.setLastSyncTime(firstNonBlank(node.path("last_sync_time").asText(""), now));
        mapping.setErrorMessage(node.path("error_message").asText(""));
        mapping.setExpired(node.path("expired").asBoolean("expired".equalsIgnoreCase(mapping.getSyncStatus())));
        return mapping;
    }

    List<String> buildCommand(MindocSyncConfig config, String knowledgeBaseId,
                              String wiseBaseUrl, String wiseApiKey) {
        List<String> command = new ArrayList<>();
        command.add(pythonCommand);
        command.add(scriptPath);
        addArg(command, "--project", safeProjectName(firstNonBlank(config.getProjectName(), config.getConfigId(), "MINDOC")));
        addArg(command, "--entry", config.getSourceUrl());
        addArg(command, "--output", outputDir);

        if (!blank(config.getRootUrl())) {
            addArg(command, "--root-url", config.getRootUrl());
        }
        if (!blank(config.getIncludePath())) {
            addArg(command, "--include", config.getIncludePath());
        }
        if (!blank(config.getExcludePath())) {
            addArg(command, "--exclude", config.getExcludePath());
        }
        if (maxPages > 0) {
            addArg(command, "--max-pages", String.valueOf(maxPages));
        }

        addArg(command, "--concurrency", String.valueOf(concurrency));
        addArg(command, "--upload-concurrency", String.valueOf(uploadConcurrency));
        addArg(command, "--delay", String.valueOf(delaySeconds));
        addArg(command, "--timeout", String.valueOf(timeoutSeconds));
        command.add("--resume");

        addArg(command, "--auth-type", normalizeAuthType(config.getAuthType()));
        if (!blank(config.getAuthValue())) {
            addArg(command, "--auth-value", config.getAuthValue());
        }

        command.add("--upload");
        addArg(command, "--wise-base-url", wiseBaseUrl);
        addArg(command, "--wise-token", wiseApiKey);
        addArg(command, "--wise-kb-id", knowledgeBaseId);

        if (deleteOldOnUpdate) {
            command.add("--delete-old-on-update");
        }
        if (!injectImages) {
            command.add("--no-inject-images");
        }
        if (!pollParseStatus) {
            command.add("--no-poll");
        }
        return command;
    }

    private String readOutputTail(Process process) throws Exception {
        StringBuilder tail = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[mindoc-sync] {}", line);
                appendTail(tail, line);
            }
        }
        return tail.toString();
    }

    private void appendTail(StringBuilder tail, String line) {
        tail.append(line).append('\n');
        if (tail.length() > OUTPUT_TAIL_LIMIT) {
            tail.delete(0, tail.length() - OUTPUT_TAIL_LIMIT);
        }
    }

    private boolean isEnabled(MindocSyncConfig config) {
        String status = firstNonBlank(config.getStatus()).toLowerCase(Locale.ROOT);
        return status.isBlank()
                || status.contains("启用")
                || status.contains("enable")
                || status.contains("true")
                || status.contains("待同步")
                || status.contains("待爬取")
                || status.contains("待更新")
                || status.contains("pending")
                || status.contains("失败")
                || status.contains("failed");
    }

    private String resolveDefaultKnowledgeBaseId() {
        return firstNonBlank(defaultKnowledgeBaseId,
                wiseConfig != null ? wiseConfig.getKnowledgeBaseId() : "");
    }

    private String normalizeAuthType(String authType) {
        String normalized = firstNonBlank(authType, "none").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "cookie", "token", "bearer" -> normalized;
            default -> "none";
        };
    }

    private String safeProjectName(String value) {
        String project = firstNonBlank(value, "MINDOC")
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return project.isBlank() ? "MINDOC" : project;
    }

    private static void addArg(List<String> command, String name, String value) {
        command.add(name);
        command.add(value == null ? "" : value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= OUTPUT_TAIL_LIMIT
                ? value
                : value.substring(value.length() - OUTPUT_TAIL_LIMIT);
    }

    private void notifySyncFailure(String projectName, String sourceUrl, String errorMessage) {
        if (adminNotifier != null) {
            adminNotifier.notifySyncFailure(projectName, sourceUrl, truncate(errorMessage));
        }
    }

    public record SyncSummary(int total, int success, int failed, int skipped) {
    }
}
