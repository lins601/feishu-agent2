package com.example.myapp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 未命中问题记录 POJO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MissRecord {

    private String question;
    private String userId;
    /** 推测所属系统: WMS / QMS / MES / null */
    private String systemHint;
    private int hitCount = 1;
    /** pending / resolved / ignored */
    private String status = "pending";
    private LocalDateTime createdAt = LocalDateTime.now();
}
