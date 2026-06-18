package com.example.myapp.service;

import com.example.myapp.config.WiseConfig;
import com.example.myapp.dto.WiseFaqIngestionResult;
import com.example.myapp.model.FaqDraftRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * WISE FAQ 入库服务。
 * <p>
 * 只负责把管理员确认后的 FAQ 草稿写入 WISE FAQ，不自动生成事实答案。
 */
@Service
public class WiseFaqService {

    private static final Logger log = LoggerFactory.getLogger(WiseFaqService.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final WiseConfig wiseConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${wise.faq.entry-path:/faq/entry}")
    private String faqEntryPath;

    @Value("${wise.faq.default-tag-id:}")
    private String defaultTagId;

    public WiseFaqService(WiseConfig wiseConfig, OkHttpClient wiseHttpClient,
                          ObjectMapper objectMapper) {
        this.wiseConfig = wiseConfig;
        this.httpClient = wiseHttpClient;
        this.objectMapper = objectMapper;
    }

    public WiseFaqIngestionResult createFaq(FaqDraftRecord draft) {
        if (draft == null || blank(draft.getStandardQuestion())) {
            return WiseFaqIngestionResult.failure("标准问为空", 0);
        }
        if (blank(draft.getAnswer())) {
            return WiseFaqIngestionResult.failure("答案为空，需管理员补充后再入库", 0);
        }

        String url = wiseConfig.getApiUrl() + normalizedPath(faqEntryPath);
        ObjectNode payload = buildFaqPayload(draft, wiseConfig.getFaqKnowledgeBaseId());
        IOException lastException = null;

        for (int attempt = 1; attempt <= wiseConfig.getMaxRetries(); attempt++) {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(payload.toString(), JSON_TYPE))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "{}";
                    WiseFaqIngestionResult result = parseResponse(response.code(), body);
                    if (result.isSuccess()) {
                        log.info("WISE FAQ 入库成功: draftId={}, faqId={}",
                                draft.getDraftId(), result.getFaqId());
                        return result;
                    }

                    if (response.code() >= 400 && response.code() < 500) {
                        log.warn("WISE FAQ 入库失败（不重试）: draftId={}, status={}, error={}",
                                draft.getDraftId(), response.code(), result.getErrorMessage());
                        return result;
                    }

                    log.warn("WISE FAQ 入库失败（第 {}/{} 次）: draftId={}, status={}, error={}",
                            attempt, wiseConfig.getMaxRetries(), draft.getDraftId(),
                            response.code(), result.getErrorMessage());
                }
            } catch (IOException e) {
                lastException = e;
                log.warn("WISE FAQ 入库异常（第 {}/{} 次）: draftId={}",
                        attempt, wiseConfig.getMaxRetries(), draft.getDraftId(), e);
            }

            if (attempt < wiseConfig.getMaxRetries()) {
                sleepBackoff(attempt);
            }
        }

        String message = lastException != null ? lastException.getMessage() : "WISE FAQ 入库失败";
        return WiseFaqIngestionResult.failure(message, 0);
    }

    ObjectNode buildFaqPayload(FaqDraftRecord draft, String faqKnowledgeBaseId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("standard_question", draft.getStandardQuestion().trim());

        ArrayNode similarQuestions = payload.putArray("similar_questions");
        for (String question : splitSimilarQuestions(draft)) {
            similarQuestions.add(question);
        }

        ArrayNode answers = payload.putArray("answers");
        answers.add(draft.getAnswer().trim());

        if (!blank(defaultTagId)) {
            payload.put("tag_id", defaultTagId.trim());
        }
        if (!blank(faqKnowledgeBaseId)) {
            payload.put("knowledge_base_id", faqKnowledgeBaseId.trim());
        }
        payload.put("is_enabled", true);
        return payload;
    }

    private Set<String> splitSimilarQuestions(FaqDraftRecord draft) {
        Set<String> questions = new LinkedHashSet<>();
        String standardQuestion = draft.getStandardQuestion() != null
                ? draft.getStandardQuestion().trim() : "";
        if (!blank(draft.getSimilarQuestions())) {
            for (String item : draft.getSimilarQuestions().split("[\\n;；]+")) {
                addQuestion(questions, item, standardQuestion);
            }
        }
        return questions;
    }

    private void addQuestion(Set<String> questions, String question, String standardQuestion) {
        if (blank(question)) {
            return;
        }
        String trimmed = question.trim();
        if (!trimmed.equals(standardQuestion)) {
            questions.add(trimmed);
        }
    }

    private WiseFaqIngestionResult parseResponse(int httpStatus, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            int apiCode = root.has("errcode")
                    ? root.path("errcode").asInt()
                    : root.path("code").asInt(0);
            String apiMessage = root.path("errmsg").asText(root.path("msg").asText(""));
            boolean success = httpStatus >= 200 && httpStatus < 300 && apiCode == 0;
            if (!success) {
                return WiseFaqIngestionResult.failure(
                        firstNonBlank(apiMessage, body, "WISE FAQ API 返回失败"), httpStatus);
            }

            JsonNode data = root.path("data");
            String faqId = firstNonBlank(
                    data.path("faq_id").asText(""),
                    data.path("entry_id").asText(""),
                    data.path("id").asText(""),
                    root.path("faq_id").asText("")
            );
            return WiseFaqIngestionResult.success(faqId, httpStatus);
        } catch (Exception e) {
            if (httpStatus >= 200 && httpStatus < 300) {
                return WiseFaqIngestionResult.success("", httpStatus);
            }
            return WiseFaqIngestionResult.failure(body, httpStatus);
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep((long) attempt * attempt * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String normalizedPath(String path) {
        if (path == null || path.isBlank()) {
            return "/faq/entry";
        }
        return path.startsWith("/") ? path : "/" + path;
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
}
