package com.example.myapp.service;

import com.example.myapp.model.FaqDraftRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class WiseFaqServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildFaqPayloadUsesAdminApprovedAnswerAndDeduplicatesSimilarQuestions() {
        WiseFaqService service = new WiseFaqService(null, null, objectMapper);
        ReflectionTestUtils.setField(service, "defaultTagId", "tag_qms");

        FaqDraftRecord draft = FaqDraftRecord.builder()
                .standardQuestion("IQC 检验单提交失败怎么办？")
                .similarQuestions("IQC 单子交不上去\nIQC 提交失败；IQC 单子交不上去")
                .answer("检查检验结果、单据状态和关联来料单状态。")
                .build();

        JsonNode payload = service.buildFaqPayload(draft, "faq-kb-001");

        assertThat(payload.path("standard_question").asText())
                .isEqualTo("IQC 检验单提交失败怎么办？");
        assertThat(payload.path("similar_questions")).hasSize(2);
        assertThat(payload.path("similar_questions").get(0).asText())
                .isEqualTo("IQC 单子交不上去");
        assertThat(payload.path("answers")).hasSize(1);
        assertThat(payload.path("answers").get(0).asText())
                .isEqualTo("检查检验结果、单据状态和关联来料单状态。");
        assertThat(payload.path("knowledge_base_id").asText()).isEqualTo("faq-kb-001");
        assertThat(payload.path("tag_id").asText()).isEqualTo("tag_qms");
        assertThat(payload.path("is_enabled").asBoolean()).isTrue();
    }

    @Test
    void buildFaqPayloadOmitsBlankOptionalFields() {
        WiseFaqService service = new WiseFaqService(null, null, objectMapper);

        FaqDraftRecord draft = FaqDraftRecord.builder()
                .standardQuestion("来料接收没有自动推送如何处理？")
                .answer("手工推送，并联系管理员排查自动推送失败原因。")
                .build();

        JsonNode payload = service.buildFaqPayload(draft, "");

        assertThat(payload.has("knowledge_base_id")).isFalse();
        assertThat(payload.has("tag_id")).isFalse();
        assertThat(payload.path("similar_questions")).isEmpty();
    }
}
