package com.example.myapp.service;

import com.example.myapp.model.FeedbackRecord;
import com.example.myapp.model.FaqDraftRecord;
import com.example.myapp.model.MindocSyncConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LarkBitableServiceTest {

    private final LarkBitableService service = new LarkBitableService(null);

    @Test
    void negativeFeedbackGroupUsesKnowledgeRefsFirst() {
        FeedbackRecord record = FeedbackRecord.builder()
                .question("超期复检单号作废如何处理？")
                .knowledgeRefs("FAQ.md")
                .build();

        assertThat(service.negativeFeedbackGroupKey(record))
                .isEqualTo("knowledge_refs:FAQ.md");
        assertThat(service.reviewTriggerReason(record, 3))
                .isEqualTo("同一知识来源连续 3 次负反馈");
    }

    @Test
    void negativeFeedbackGroupFallsBackToNormalizedQuestion() {
        FeedbackRecord record = FeedbackRecord.builder()
                .question(" 超期复检单号作废如何处理？！ ")
                .knowledgeRefs("")
                .build();

        assertThat(service.normalizeQuestion(record.getQuestion()))
                .isEqualTo("超期复检单号作废如何处理");
        assertThat(service.negativeFeedbackGroupKey(record))
                .isEqualTo("question:超期复检单号作废如何处理");
        assertThat(service.reviewTriggerReason(record, 3))
                .isEqualTo("同问题 3 次负反馈（未提取到知识来源）");
    }

    @Test
    void fieldValueTextExtractsBitableStructuredFields() {
        assertThat(service.fieldValueText(Map.of("text", "出现“CMES系统网络卡住了”时如何处理？")))
                .isEqualTo("出现“CMES系统网络卡住了”时如何处理？");
        assertThat(service.fieldValueText(List.of(Map.of("text", "没用", "type", "text"))))
                .isEqualTo("没用");
        assertThat(service.isNegativeFeedback(List.of(Map.of("text", "没用", "type", "text"))))
                .isTrue();
    }

    @Test
    void feedbackFromFieldsReadsChineseRecordFields() {
        Map<String, Object> fields = Map.ofEntries(
                Map.entry("反馈ID", "fb_001"),
                Map.entry("问答ID", "qa_001"),
                Map.entry("用户ID", "ou_001"),
                Map.entry("问题", "标签质量问题，导致打印补不了怎么办？"),
                Map.entry("答案摘要", "先确认标签模板和打印任务状态。"),
                Map.entry("知识来源", "标签打印FAQ.md"),
                Map.entry("反馈", List.of(Map.of("text", "没用", "type", "text")))
        );

        FeedbackRecord record = service.feedbackFromFields(fields);

        assertThat(record.getFeedbackId()).isEqualTo("fb_001");
        assertThat(record.getQaId()).isEqualTo("qa_001");
        assertThat(record.getKnowledgeRefs()).isEqualTo("标签打印FAQ.md");
        assertThat(record.getFeedback()).isEqualTo("useless");
    }

    @Test
    void mindocConfigFallsBackToExistingSyncRecordFields() {
        Map<String, Object> fields = Map.of(
                "doc_id", "mfg-root",
                "title", List.of(Map.of("text", "制造知识库")),
                "url", "https://docs.example.com/docs/mfg/root",
                "include_path", "/docs/mfg",
                "auth_type", "cookie"
        );

        MindocSyncConfig config = service.mindocConfigFromFields(
                "rec001", fields, "2fe60de0-3c43-4edb-aeff-7990519e3459");

        assertThat(config.getRecordId()).isEqualTo("rec001");
        assertThat(config.getConfigId()).isEqualTo("mfg-root");
        assertThat(config.getProjectName()).isEqualTo("制造知识库");
        assertThat(config.getSourceUrl()).isEqualTo("https://docs.example.com/docs/mfg/root");
        assertThat(config.getIncludePath()).isEqualTo("/docs/mfg");
        assertThat(config.getAuthType()).isEqualTo("cookie");
        assertThat(config.getWiseKbId()).isEqualTo("2fe60de0-3c43-4edb-aeff-7990519e3459");
    }

    @Test
    void mindocConfigReadsChineseAdminFields() {
        Map<String, Object> fields = Map.ofEntries(
                Map.entry("配置ID", "mfg-weekly"),
                Map.entry("项目名称", "制造知识库周同步"),
                Map.entry("URL地址", "https://docs.example.com/docs/mfg/root"),
                Map.entry("根链接", "https://docs.example.com"),
                Map.entry("包含路径", "/docs/mfg"),
                Map.entry("排除路径", "/archive"),
                Map.entry("访问方式", "只需要内网"),
                Map.entry("同步频率", "0 0 2 ? * THU"),
                Map.entry("同步状态", "待爬取"),
                Map.entry("目标知识库ID", "kb_001")
        );

        MindocSyncConfig config = service.mindocConfigFromFields("rec001", fields, "default_kb");

        assertThat(config.getConfigId()).isEqualTo("mfg-weekly");
        assertThat(config.getProjectName()).isEqualTo("制造知识库周同步");
        assertThat(config.getSourceUrl()).isEqualTo("https://docs.example.com/docs/mfg/root");
        assertThat(config.getRootUrl()).isEqualTo("https://docs.example.com");
        assertThat(config.getIncludePath()).isEqualTo("/docs/mfg");
        assertThat(config.getExcludePath()).isEqualTo("/archive");
        assertThat(config.getAuthType()).isEqualTo("none");
        assertThat(config.getAuthValue()).isBlank();
        assertThat(config.getSyncCron()).isEqualTo("0 0 2 ? * THU");
        assertThat(config.getStatus()).isEqualTo("待爬取");
        assertThat(config.getWiseKbId()).isEqualTo("kb_001");
    }

    @Test
    void faqDraftFromFieldsExtractsStructuredValues() {
        Map<String, Object> fields = Map.of(
                "draft_id", "faq_001",
                "source_type", "未命中",
                "source_id", "rec_miss_001",
                "standard_question", List.of(Map.of("text", "IQC 检验单提交失败怎么办？")),
                "similar_questions", "IQC 单子交不上去\nIQC 提交失败",
                "answer", "检查检验结果、单据状态和关联来料单状态。",
                "knowledge_refs", "FAQ.md",
                "confidence", 0.8,
                "status", "待入库",
                "owner", "admin"
        );

        FaqDraftRecord draft = service.faqDraftFromFields("rec001", fields);

        assertThat(draft.getRecordId()).isEqualTo("rec001");
        assertThat(draft.getDraftId()).isEqualTo("faq_001");
        assertThat(draft.getSourceType()).isEqualTo("未命中");
        assertThat(draft.getStandardQuestion()).isEqualTo("IQC 检验单提交失败怎么办？");
        assertThat(draft.getSimilarQuestions()).contains("IQC 单子交不上去");
        assertThat(draft.getAnswer()).contains("检查检验结果");
        assertThat(draft.getConfidence()).isEqualTo(0.8);
        assertThat(draft.getStatus()).isEqualTo("待入库");
    }

    @Test
    void faqDraftFromFieldsReadsChineseAdminFields() {
        Map<String, Object> fields = Map.ofEntries(
                Map.entry("草稿ID", "faq_002"),
                Map.entry("来源类型", "负反馈"),
                Map.entry("来源记录ID", "review_001"),
                Map.entry("标准问", List.of(Map.of("text", "来料接收没有自动推送如何处理？"))),
                Map.entry("相似问", "来料没推送\n接收后不自动推送"),
                Map.entry("答案", "先手工推送完成操作，并联系系统管理员排查自动推送任务。"),
                Map.entry("知识来源", "FAQ.md"),
                Map.entry("置信度", "0.9"),
                Map.entry("状态", "已确认"),
                Map.entry("负责人", "admin"),
                Map.entry("WISE FAQ ID", "107282026"),
                Map.entry("错误信息", "")
        );

        FaqDraftRecord draft = service.faqDraftFromFields("rec002", fields);

        assertThat(draft.getDraftId()).isEqualTo("faq_002");
        assertThat(draft.getSourceType()).isEqualTo("负反馈");
        assertThat(draft.getSourceId()).isEqualTo("review_001");
        assertThat(draft.getStandardQuestion()).isEqualTo("来料接收没有自动推送如何处理？");
        assertThat(draft.getSimilarQuestions()).contains("来料没推送");
        assertThat(draft.getAnswer()).contains("手工推送");
        assertThat(draft.getKnowledgeRefs()).isEqualTo("FAQ.md");
        assertThat(draft.getConfidence()).isEqualTo(0.9);
        assertThat(draft.getStatus()).isEqualTo("已确认");
        assertThat(draft.getWiseFaqId()).isEqualTo("107282026");
    }

    @Test
    void buildOperationReportHandlesUnconfiguredTables() {
        String report = service.buildOperationReport(7, "周报");

        assertThat(report).contains("制造知识助手周报");
        assertThat(report).contains("总提问：0 次");
        assertThat(report).contains("高频未命中问题 Top3：暂无");
    }
}
