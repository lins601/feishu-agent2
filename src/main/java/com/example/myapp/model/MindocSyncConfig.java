package com.example.myapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MinDoc 同步任务配置。
 * <p>
 * 管理员在飞书多维表格 sync_record 中维护入口 URL，后台定时读取并上传到 WISE。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MindocSyncConfig {

    /** 飞书多维表格记录 ID，用于回写同步状态 */
    private String recordId;

    /** 配置主键，可由管理员维护；为空时使用 recordId */
    private String configId;

    /** 项目名，用于输出目录和 WISE 文件分组 */
    private String projectName;

    /** MinDoc 入口 URL */
    private String sourceUrl;

    /** 限定爬取根 URL */
    private String rootUrl;

    /** 仅爬取路径包含该值的链接 */
    private String includePath;

    /** 跳过路径包含该值的链接 */
    private String excludePath;

    /** none / cookie / token / bearer */
    private String authType;

    /** Cookie、token 或 bearer token 值 */
    private String authValue;

    /** 行级 cron，当前仅保留展示，调度使用全局 cron */
    private String syncCron;

    /** 启停状态：为空或启用时会同步 */
    private String status;

    /** 目标 WISE 知识库 ID */
    private String wiseKbId;
}
