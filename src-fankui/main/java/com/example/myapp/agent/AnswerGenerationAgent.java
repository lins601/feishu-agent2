package com.example.myapp.agent;

import com.example.myapp.dto.WiseSearchResult;
import com.example.myapp.dto.WiseSearchResult.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 回答生成 Agent。
 * <p>
 * 将 WISE 检索结果组装为结构化飞书回复。
 * 当前使用模板拼接（mock 模式），后续可接入 LLM 做智能整理。
 */
@Component
public class AnswerGenerationAgent {

    private static final Logger log = LoggerFactory.getLogger(AnswerGenerationAgent.class);

    /**
     * 根据检索结果生成结构化答案。
     *
     * @param question       用户原始问题
     * @param searchResult   WISE 检索结果
     * @param systemIntent   意图识别结果（WMS/QMS/MES/null）
     * @return 结构化答案文本
     */
    public String generate(String question, WiseSearchResult searchResult, String systemIntent) {
        if (searchResult == null || !searchResult.hasResults()) {
            return null;
        }

        Chunk topChunk = searchResult.getChunks().get(0);
        String content = topChunk.getContent();
        String docTitle = topChunk.getDocumentTitle();
        String sourceUrl = topChunk.getSourceUrl();

        // 解析内容为结构化段落
        String reason = extractReason(content);
        String steps = extractSteps(content);
        String notes = extractNotes(content);

        // 组装飞书富文本格式
        StringBuilder sb = new StringBuilder();
        sb.append("📋 ").append(question).append("\n\n");

        if (reason != null && !reason.isEmpty()) {
            sb.append("原因分析：\n").append(reason).append("\n\n");
        }

        if (steps != null && !steps.isEmpty()) {
            sb.append("处理步骤：\n").append(steps).append("\n\n");
        }

        if (notes != null && !notes.isEmpty()) {
            sb.append("注意事项：\n").append(notes).append("\n\n");
        }

        if (systemIntent != null) {
            sb.append("关联系统：\n  · ").append(systemIntent).append("\n\n");
        }

        // 来源标注
        sb.append("知识来源：\n");
        if (docTitle != null && !docTitle.isEmpty()) {
            sb.append("  · ").append(docTitle);
        }
        if (sourceUrl != null && !sourceUrl.isEmpty()) {
            sb.append(" | ").append(sourceUrl);
        }
        if ((docTitle == null || docTitle.isEmpty()) && (sourceUrl == null || sourceUrl.isEmpty())) {
            sb.append("  · WISE 知识库");
        }

        String result = sb.toString().trim();
        log.info("答案生成完成: length={}", result.length());
        return result;
    }

    /**
     * 从知识内容中提取原因分析。
     * 模板模式：查找"原因"、"问题描述"等关键词后的内容。
     */
    private String extractReason(String content) {
        if (content == null) return null;

        String[] patterns = {"原因：", "原因:", "问题描述：", "问题描述:", "原因分析：", "问题原因："};
        for (String pattern : patterns) {
            int idx = content.indexOf(pattern);
            if (idx >= 0) {
                String after = content.substring(idx + pattern.length()).trim();
                int endIdx = findSectionEnd(after);
                return endIdx > 0 ? after.substring(0, endIdx).trim() : after.trim();
            }
        }

        // 未找到原因段落，返回前 200 字
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }

    /**
     * 从知识内容中提取处理步骤。
     */
    private String extractSteps(String content) {
        if (content == null) return null;

        String[] patterns = {"处理步骤：", "处理步骤:", "操作步骤：", "操作步骤:", "步骤：", "步骤:", "解决方法：", "解决方法:"};
        for (String pattern : patterns) {
            int idx = content.indexOf(pattern);
            if (idx >= 0) {
                String after = content.substring(idx + pattern.length()).trim();
                int endIdx = findSectionEnd(after);
                return endIdx > 0 ? after.substring(0, endIdx).trim() : after.trim();
            }
        }

        return null;
    }

    /**
     * 从知识内容中提取注意事项。
     */
    private String extractNotes(String content) {
        if (content == null) return null;

        String[] patterns = {"注意事项：", "注意事项:", "注意：", "注意:", "提醒：", "提醒:"};
        for (String pattern : patterns) {
            int idx = content.indexOf(pattern);
            if (idx >= 0) {
                String after = content.substring(idx + pattern.length()).trim();
                int endIdx = findSectionEnd(after);
                return endIdx > 0 ? after.substring(0, endIdx).trim() : after.trim();
            }
        }

        return null;
    }

    /**
     * 查找段落结束位置（下一个段落标题或连续两个换行）。
     */
    private int findSectionEnd(String text) {
        String[] sectionHeaders = {"原因", "处理步骤", "操作步骤", "步骤", "注意事项", "注意", "关联", "来源", "提醒"};
        int minEnd = text.length();

        for (String header : sectionHeaders) {
            // 跳过开头匹配
            int idx = text.indexOf("\n" + header, 5);
            if (idx > 0 && idx < minEnd) {
                minEnd = idx;
            }
        }

        return minEnd < text.length() ? minEnd : -1;
    }
}
