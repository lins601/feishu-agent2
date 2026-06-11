package com.example.myapp.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * WISE URL 入库结果 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WiseIngestionResult {

    /** WISE 返回的 knowledge_id */
    private String knowledgeId;

    /** 解析状态: processing / completed / failed */
    private String parseStatus;

    /** 失败原因 */
    private String failureReason;

    /** 是否成功 */
    public boolean isSuccess() {
        return "completed".equals(parseStatus);
    }

    /** 是否仍在处理 */
    public boolean isProcessing() {
        return "processing".equals(parseStatus);
    }
}
