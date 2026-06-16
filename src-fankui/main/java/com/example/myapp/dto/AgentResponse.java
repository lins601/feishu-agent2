package com.example.myapp.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Agent 输出结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    /** 是否成功生成答案 */
    private boolean success;

    /** 答案文本（WISE 智能体返回的 markdown） */
    private String answerText;

    /** 是否为降级回复 */
    private boolean degraded;

    /**
     * 构建成功响应。
     */
    public static AgentResponse success(String answerText) {
        AgentResponse r = new AgentResponse();
        r.setSuccess(true);
        r.setAnswerText(answerText);
        r.setDegraded(false);
        return r;
    }

    /**
     * 构建未命中响应。
     */
    public static AgentResponse miss(String question) {
        AgentResponse r = new AgentResponse();
        r.setSuccess(false);
        r.setAnswerText("暂未收录该问题。\n已记录您的问题并提交管理员补充 📝\n您也可以尝试换一种方式描述。");
        r.setDegraded(false);
        return r;
    }

    /**
     * 构建降级响应。
     */
    public static AgentResponse degraded(String errorMsg) {
        AgentResponse r = new AgentResponse();
        r.setSuccess(false);
        r.setAnswerText("⚠️ 系统繁忙，请稍后重试。\n如持续异常请联系管理员。");
        r.setDegraded(true);
        return r;
    }
}
