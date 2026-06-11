package com.example.myapp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户反馈记录 POJO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRecord {

    private String userId;
    private String question;
    private String knowledgeSource;
    /** useful / useless */
    private String feedback;
    private LocalDateTime createdAt = LocalDateTime.now();
}
