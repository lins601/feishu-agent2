package com.example.myapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WISE 文件入库结果 DTO（PDR V8.1 US-05）。
 * <p>
 * 对应 WISE File API 的 multipart file upload 响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WiseFileIngestionResult {

    /** WISE 返回的文件 ID (wise_file_id) */
    private String fileId;

    /** WISE 返回的知识 ID (knowledge_id) */
    private String knowledgeId;

    /** 解析状态: processing / completed / failed */
    private String parseStatus;

    /** 失败原因 */
    private String failureReason;

    /** 原始 API 响应码 (0=成功) */
    private int errcode;

    /** 原始 API 响应消息 */
    private String errmsg;

    /** 是否成功 */
    public boolean isSuccess() {
        return errcode == 0 && fileId != null && !fileId.isEmpty();
    }

    /** 是否解析完成 */
    public boolean isParsed() {
        return "completed".equals(parseStatus);
    }

    /** 是否解析失败 */
    public boolean isParseFailed() {
        return "failed".equals(parseStatus);
    }

    /** 是否正在解析 */
    public boolean isProcessing() {
        return "processing".equals(parseStatus);
    }
}