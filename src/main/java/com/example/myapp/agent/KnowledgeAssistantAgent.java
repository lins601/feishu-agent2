package com.example.myapp.agent;

import com.example.myapp.dto.AgentResponse;
import com.example.myapp.service.WiseAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 制造知识助手主 Agent — 编排问答全流程。
 * <p>
 * 直接调用 WISE 智能体 API，透传其回答到飞书。
 * WISE 智能体自身已包含意图识别、知识检索、RAG 回答等完整流程。
 */
@Component
public class KnowledgeAssistantAgent {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAssistantAgent.class);

    private final WiseAgentService wiseAgentService;

    public KnowledgeAssistantAgent(WiseAgentService wiseAgentService) {
        this.wiseAgentService = wiseAgentService;
    }

    /**
     * 处理用户问题，返回 Agent 响应。
     *
     * @param question 用户原始问题
     * @return AgentResponse
     */
    public AgentResponse process(String question) {
        return process(question, null);
    }

    /**
     * 处理用户问题（带上下文）。
     *
     * @param question 用户原始问题
     * @param context  上下文（最近对话，可为 null）
     * @return AgentResponse
     */
    public AgentResponse process(String question, String context) {
        log.info("Agent 开始处理: question={}", question);
        long start = System.currentTimeMillis();

        try {
            // 直接调用 WISE 智能体
            String answer = wiseAgentService.chat(question);

            long elapsed = System.currentTimeMillis() - start;

            if (answer == null || answer.isBlank()) {
                log.info("WISE 智能体未返回有效回答, 耗时={}ms", elapsed);
                return AgentResponse.miss(question);
            }

            log.info("Agent 处理完成: answerLength={}, 耗时={}ms", answer.length(), elapsed);
            return AgentResponse.success(answer);

        } catch (Exception e) {
            log.error("Agent 处理异常: question={}", question, e);
            return AgentResponse.degraded(e.getMessage());
        }
    }
}
