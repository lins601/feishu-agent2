package com.example.myapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WISE FAQ 入库结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WiseFaqIngestionResult {

    private boolean success;
    private String faqId;
    private String errorMessage;
    private int statusCode;

    public static WiseFaqIngestionResult success(String faqId, int statusCode) {
        return WiseFaqIngestionResult.builder()
                .success(true)
                .faqId(faqId)
                .statusCode(statusCode)
                .build();
    }

    public static WiseFaqIngestionResult failure(String errorMessage, int statusCode) {
        return WiseFaqIngestionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .statusCode(statusCode)
                .build();
    }
}
