package com.example.myapp.controller;

import com.example.myapp.service.WiseAgentService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 测试端点 - 用于验证 WISE API 集成
 */
@RestController
@RequestMapping("/test")
public class TestController {

    private final WiseAgentService wiseAgentService;

    public TestController(WiseAgentService wiseAgentService) {
        this.wiseAgentService = wiseAgentService;
    }

    /**
     * 测试 WISE API 调用
     *
     * 用法: curl "http://localhost:8080/test/wise?query=IQC检验单提交失败怎么办"
     */
    @GetMapping("/wise")
    public Map<String, Object> testWise(@RequestParam String query) {
        long start = System.currentTimeMillis();

        try {
            String answer = wiseAgentService.chat(query);
            long elapsed = System.currentTimeMillis() - start;

            return Map.of(
                "success", true,
                "query", query,
                "answer", answer != null ? answer : "空回答",
                "answerLength", answer != null ? answer.length() : 0,
                "elapsed", elapsed + "ms",
                "timestamp", System.currentTimeMillis()
            );
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return Map.of(
                "success", false,
                "query", query,
                "error", e.getMessage(),
                "elapsed", elapsed + "ms"
            );
        }
    }
}
