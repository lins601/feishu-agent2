package com.example.myapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图识别服务。
 * <p>
 * 根据用户问题文本识别所属系统（WMS/QMS/MES），
 * 并提取关键词用于优化检索。
 * <p>
 * 当前实现基于关键词规则匹配（无需 LLM，响应快 ~1ms），
 * 后续可接入 LLM 提升准确率。
 * <p>
 * 规则优先级：
 * 1. 明确包含系统名（WMS/QMS/MES）→ 直接判定
 * 2. 包含系统特有的业务术语 → 按术语映射判定
 * 3. 无法判定 → 返回 null（检索时不限定系统）
 */
@Service
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    // ─── WMS 关键词 ──────────────────────────────────────
    private static final Set<String> WMS_KEYWORDS = Set.of(
            "仓储", "仓库", "入库", "出库", "盘点", "移库", "移位",
            "库位", "库内", "库存", "领料", "退料", "发料",
            "收货", "上架", "拣货", "波次", "周转箱",
            "wms", "WMS"
    );

    // ─── QMS 关键词 ──────────────────────────────────────
    private static final Set<String> QMS_KEYWORDS = Set.of(
            "质量", "品质", "IQC", "iqc", "OQC", "oqc", "IPQC", "ipqc",
            "来料检验", "出货检验", "制程检验", "检验单", "检验报告",
            "不良", "缺陷", "异常单", "不合格", "返工", "报废",
            "供应商质量", "客诉", "8D", "纠正预防", "CAPA",
            "物料检验", "质检", "品质异常",
            "qms", "QMS"
    );

    // ─── MES 关键词 ──────────────────────────────────────
    private static final Set<String> MES_KEYWORDS = Set.of(
            "生产", "制造", "工单", "报工", "工艺", "制程",
            "产线", "流水线", "SMT", "DIP", "组装", "测试",
            "工站", "排产", "排程", "投产", "完工",
            "过站", "工单状态", "报工状态",
            "mes", "MES"
    );

    /** 系统名直接匹配模式 */
    private static final Pattern SYSTEM_PATTERN = Pattern.compile(
            "\\b(WMS|QMS|MES|wms|qms|mes)\\b"
    );

    /**
     * 识别结果。
     */
    public record Intent(String system, List<String> keywords, double confidence) {}

    /**
     * 识别用户问题的意图。
     *
     * @param question 用户问题文本
     * @return Intent 包含系统标识、提取的关键词、置信度
     */
    public Intent classify(String question) {
        if (question == null || question.isBlank()) {
            return new Intent(null, List.of(), 0.0);
        }

        String normalized = question.trim();

        // ─── 1. 直接匹配系统名 ──────────────────────────
        Matcher m = SYSTEM_PATTERN.matcher(normalized);
        if (m.find()) {
            String sys = m.group(1).toUpperCase();
            List<String> keywords = extractKeywords(normalized, sys);
            log.debug("系统名直接匹配: system={}, keywords={}", sys, keywords);
            return new Intent(sys, keywords, 1.0);
        }

        // ─── 2. 关键词映射判定 ──────────────────────────
        int wmsHits = countKeywordHits(normalized, WMS_KEYWORDS);
        int qmsHits = countKeywordHits(normalized, QMS_KEYWORDS);
        int mesHits = countKeywordHits(normalized, MES_KEYWORDS);

        int maxHits = Math.max(wmsHits, Math.max(qmsHits, mesHits));

        if (maxHits == 0) {
            // 无明确关键词，不限定系统
            List<String> keywords = extractGeneralKeywords(normalized);
            log.debug("无法判定系统，提取通用关键词: {}", keywords);
            return new Intent(null, keywords, 0.3);
        }

        String system;
        List<String> keywords;

        if (wmsHits == maxHits && wmsHits > qmsHits && wmsHits > mesHits) {
            system = "WMS";
            keywords = extractKeywords(normalized, "WMS");
        } else if (qmsHits == maxHits && qmsHits > wmsHits && qmsHits > mesHits) {
            system = "QMS";
            keywords = extractKeywords(normalized, "QMS");
        } else if (mesHits == maxHits && mesHits > wmsHits && mesHits > qmsHits) {
            system = "MES";
            keywords = extractKeywords(normalized, "MES");
        } else {
            // 平局，无法确定
            List<String> allKeywords = extractGeneralKeywords(normalized);
            log.debug("多系统命中相同，不确定: wms={}, qms={}, mes={}", wmsHits, qmsHits, mesHits);
            return new Intent(null, allKeywords, 0.5);
        }

        // 置信度基于命中词数
        double confidence = Math.min(1.0, 0.5 + maxHits * 0.2);
        log.debug("意图识别: system={}, keywords={}, confidence={}", system, keywords, confidence);

        return new Intent(system, keywords, confidence);
    }

    /**
     * 统计文本中命中某系统关键词集合的次数。
     */
    private int countKeywordHits(String text, Set<String> keywords) {
        String lower = text.toLowerCase();
        int count = 0;
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 提取与指定系统相关的关键词。
     */
    private List<String> extractKeywords(String text, String system) {
        Set<String> targetKeywords = switch (system) {
            case "WMS" -> WMS_KEYWORDS;
            case "QMS" -> QMS_KEYWORDS;
            case "MES" -> MES_KEYWORDS;
            default -> Collections.emptySet();
        };

        List<String> found = new ArrayList<>();
        String lower = text.toLowerCase();
        for (String kw : targetKeywords) {
            if (lower.contains(kw.toLowerCase()) && !kw.equalsIgnoreCase(system)) {
                found.add(kw);
            }
        }

        // 如果没找到具体关键词，把去掉系统名的原文作为关键词
        if (found.isEmpty()) {
            String remaining = text.replaceAll("(?i)\\b" + system + "\\b", "").trim();
            if (!remaining.isEmpty()) {
                found.add(remaining);
            }
        }

        return found;
    }

    /**
     * 提取通用关键词（无法判定系统时）。
     * 去除常见停用词后取核心片段。
     */
    private List<String> extractGeneralKeywords(String text) {
        // 简单策略：去除常见停用词，剩余部分作为关键词
        String cleaned = text
                .replaceAll("(?i)(怎么办|怎么|如何|为什么|什么|能不能|可以|吗|呢|的|了|是|在|有|不|报错|失败|不行)", "")
                .trim();

        if (cleaned.isEmpty()) {
            cleaned = text;
        }

        // 按常见分词符拆分
        List<String> keywords = new ArrayList<>();
        for (String part : cleaned.split("[\\s,，、]+")) {
            if (part.length() >= 2) {
                keywords.add(part);
            }
        }

        return keywords.isEmpty() ? List.of(text) : keywords;
    }
}
