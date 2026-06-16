package com.example.myapp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 知识源入口配置 POJO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceConfig {

    private String name;
    private String entryUrl;
    /** WMS / QMS / MES / CROSS */
    private String system;
    /** URL 前缀/路径规则 */
    private String includePattern;
    private String excludePattern;
    /** cron 表达式，默认每日凌晨 */
    private String cronExpression = "0 0 2 * * *";
    /** WISE 知识库 ID */
    private String targetKnowledgeBaseId;
    private boolean enabled = true;
}
