package com.example.myapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MinDoc 同步记录 — 对应飞书多维表格 sync_record。
 * <p>
 * 记录每次 MinDoc 文档同步的状态，包括爬取、转换、上传 WISE 的全链路状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncRecord {

    /** MinDoc 文档 ID */
    private String docId;

    /** 原始 MinDoc URL */
    private String url;

    /** 文档标题 */
    private String title;

    /** Markdown 内容 Hash */
    private String mdHash;

    /** WISE 文件 ID */
    private String wiseFileId;

    /** 同步状态 */
    private String syncStatus;

    /** WISE 解析状态 */
    private String parseStatus;

    /** 最近更新时间（毫秒时间戳） */
    private long updateTime;

    /** 失败原因 */
    private String errorMessage;
}
