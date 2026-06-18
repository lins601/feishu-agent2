package com.example.myapp.service;

import com.example.myapp.model.MindocSyncConfig;
import com.example.myapp.model.MindocDocumentMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MindocSyncSchedulerTest {

    private static final String DEFAULT_KB_ID = "2fe60de0-3c43-4edb-aeff-7990519e3459";

    @Test
    void buildCommandIncludesWiseKbAndMindocFilters() {
        MindocSyncScheduler scheduler = scheduler();
        MindocSyncConfig config = MindocSyncConfig.builder()
                .configId("mfg")
                .projectName("制造 知识库")
                .sourceUrl("https://docs.example.com/docs/mfg/root")
                .rootUrl("https://docs.example.com/docs/mfg")
                .includePath("/docs/mfg")
                .excludePath("/draft")
                .authType("cookie")
                .authValue("mindoc_id=abc")
                .build();

        List<String> command = scheduler.buildCommand(
                config,
                DEFAULT_KB_ID,
                "https://wise.example.com/api/v1",
                "test-token"
        );

        assertThat(command).containsSequence("python3", "scripts/crawl_mindoc_wise_v4_incremental_delete.py");
        assertThat(command).containsSequence("--project", "制造_知识库");
        assertThat(command).containsSequence("--entry", "https://docs.example.com/docs/mfg/root");
        assertThat(command).containsSequence("--root-url", "https://docs.example.com/docs/mfg");
        assertThat(command).containsSequence("--include", "/docs/mfg");
        assertThat(command).containsSequence("--exclude", "/draft");
        assertThat(command).containsSequence("--auth-type", "cookie");
        assertThat(command).containsSequence("--auth-value", "mindoc_id=abc");
        assertThat(command).containsSequence("--wise-kb-id", DEFAULT_KB_ID);
        assertThat(command).contains("--resume", "--upload", "--delete-old-on-update");
    }

    @Test
    void buildCommandNormalizesUnsupportedAuthType() {
        MindocSyncScheduler scheduler = scheduler();
        MindocSyncConfig config = MindocSyncConfig.builder()
                .configId("faq")
                .sourceUrl("https://docs.example.com/docs/faq")
                .authType("unknown")
                .build();

        List<String> command = scheduler.buildCommand(
                config,
                DEFAULT_KB_ID,
                "https://wise.example.com/api/v1",
                "test-token"
        );

        assertThat(command).containsSequence("--auth-type", "none");
        assertThat(command).doesNotContain("--auth-value");
    }

    @Test
    void readMappingsConvertsCrawlerJsonToDocumentMappings(@TempDir Path tempDir) throws Exception {
        Path mapping = tempDir.resolve("mindoc_document_mapping.json");
        Files.writeString(mapping, """
                {
                  "MFG_abc123": {
                    "source_url": "https://docs.example.com/docs/mfg/a",
                    "normalized_url": "https://docs.example.com/docs/mfg/a",
                    "url_hash": "abc123",
                    "title": "物料参数管理",
                    "md_hash": "d626f6ef",
                    "doc_id": "mfg-a",
                    "wise_knowledge_id": "wise_001",
                    "old_wise_knowledge_id": "wise_000",
                    "crawled_at": "2026-06-10 15:57:57"
                  }
                }
                """);

        List<MindocDocumentMapping> mappings = scheduler().readMappings("MFG", mapping);

        assertThat(mappings).hasSize(1);
        MindocDocumentMapping parsed = mappings.get(0);
        assertThat(parsed.getDocumentKey()).isEqualTo("MFG_abc123");
        assertThat(parsed.getNormalizedUrl()).isEqualTo("https://docs.example.com/docs/mfg/a");
        assertThat(parsed.getMdHash()).isEqualTo("d626f6ef");
        assertThat(parsed.getWiseFileId()).isEqualTo("wise_001");
        assertThat(parsed.getOldWiseFileId()).isEqualTo("wise_000");
        assertThat(parsed.getSyncStatus()).isEqualTo("completed");
        assertThat(parsed.getParseStatus()).isEqualTo("completed");
        assertThat(parsed.getLastSeenTime()).isEqualTo("2026-06-10 15:57:57");
    }

    @Test
    void readMappingsAcceptsArrayJsonAndBuildsDocumentKey(@TempDir Path tempDir) throws Exception {
        Path mapping = tempDir.resolve("mindoc_document_mapping.json");
        Files.writeString(mapping, """
                [
                  {
                    "project_name": "QMS",
                    "source_url": "https://docs.example.com/docs/qms/a?from=feishu",
                    "normalized_url": "https://docs.example.com/docs/qms/a",
                    "url_hash": "qms123",
                    "title": "IQC 提交",
                    "md_hash": "abcde123",
                    "wise_file_id": "file_001",
                    "parse_status": "failed",
                    "sync_status": "parse_failed",
                    "expired": true
                  }
                ]
                """);

        List<MindocDocumentMapping> mappings = scheduler().readMappings("MFG", mapping);

        assertThat(mappings).hasSize(1);
        MindocDocumentMapping parsed = mappings.get(0);
        assertThat(parsed.getProjectName()).isEqualTo("QMS");
        assertThat(parsed.getDocumentKey()).isEqualTo("QMS_qms123");
        assertThat(parsed.getWiseFileId()).isEqualTo("file_001");
        assertThat(parsed.getParseStatus()).isEqualTo("failed");
        assertThat(parsed.getExpired()).isTrue();
    }

    @Test
    void findMappingPathSearchesNestedProjectDirectories(@TempDir Path tempDir) throws Exception {
        Path nested = tempDir.resolve("mfg").resolve("crawled");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("mindoc_document_mapping.json"), "{}");
        MindocSyncScheduler scheduler = scheduler();
        ReflectionTestUtils.setField(scheduler, "outputDir", tempDir.toString());

        assertThat(scheduler.findMappingPath("MFG"))
                .isEqualTo(nested.resolve("mindoc_document_mapping.json"));
    }

    private MindocSyncScheduler scheduler() {
        MindocSyncScheduler scheduler = new MindocSyncScheduler(null, null);
        ReflectionTestUtils.setField(scheduler, "pythonCommand", "python3");
        ReflectionTestUtils.setField(scheduler, "scriptPath", "scripts/crawl_mindoc_wise_v4_incremental_delete.py");
        ReflectionTestUtils.setField(scheduler, "outputDir", "knowledge-base");
        ReflectionTestUtils.setField(scheduler, "maxPages", 0);
        ReflectionTestUtils.setField(scheduler, "concurrency", 10);
        ReflectionTestUtils.setField(scheduler, "uploadConcurrency", 5);
        ReflectionTestUtils.setField(scheduler, "delaySeconds", 0.05);
        ReflectionTestUtils.setField(scheduler, "timeoutSeconds", 30);
        ReflectionTestUtils.setField(scheduler, "deleteOldOnUpdate", true);
        ReflectionTestUtils.setField(scheduler, "injectImages", true);
        ReflectionTestUtils.setField(scheduler, "pollParseStatus", true);
        return scheduler;
    }
}
