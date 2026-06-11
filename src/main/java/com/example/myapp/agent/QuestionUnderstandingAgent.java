package com.example.myapp.agent;

import com.example.myapp.service.IntentClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 问题理解 Agent。
 * <p>
 * 将用户口语化的提问转化为结构化检索请求。
 * 当前使用规则匹配（IntentClassifier），后续可接入 LLM 提升准确率。
 */
@Component
public class QuestionUnderstandingAgent {

    private static final Logger log = LoggerFactory.getLogger(QuestionUnderstandingAgent.class);

    private final IntentClassifier intentClassifier;

    public QuestionUnderstandingAgent(IntentClassifier intentClassifier) {
        this.intentClassifier = intentClassifier;
    }

    /**
     * 理解用户问题，输出结构化检索请求。
     *
     * @param question 用户原始问题
     * @param context  上下文（最近对话，可为 null）
     * @return 理解结果
     */
    public UnderstandingResult understand(String question, String context) {
        if (question == null || question.isBlank()) {
            return new UnderstandingResult(null, question, List.of(), List.of());
        }

        // 使用 IntentClassifier 做规则匹配
        IntentClassifier.Intent intent = intentClassifier.classify(question);

        String systemIntent = intent.system();
        List<String> keywords = intent.keywords();

        // 生成搜索查询（当前直接使用原始问题，LLM 模式下可做口语转标准术语）
        String searchQuery = question.trim();

        // 生成标签列表
        List<String> tags = buildTags(systemIntent, keywords);

        log.info("问题理解: question={}, intent={}, keywords={}, tags={}",
                question, systemIntent, keywords, tags);

        return new UnderstandingResult(systemIntent, searchQuery, keywords, tags);
    }

    /**
     * 根据意图和关键词构建标签。
     */
    private List<String> buildTags(String system, List<String> keywords) {
        var tags = new java.util.ArrayList<String>();
        if (system != null) {
            tags.add(system);
        }
        // 添加高频关键词作为标签
        for (String kw : keywords) {
            if (kw.length() >= 2 && kw.length() <= 20) {
                tags.add(kw);
            }
            if (tags.size() >= 5) break;
        }
        return tags;
    }

    /**
     * 问题理解结果。
     */
    public record UnderstandingResult(
            String intent,           // WMS / QMS / MES / null
            String searchQuery,      // 优化后的搜索查询
            List<String> keywords,   // 提取的关键词
            List<String> tags        // 用于 WISE 检索的标签
    ) {}
}
