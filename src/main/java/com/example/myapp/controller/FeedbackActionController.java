package com.example.myapp.controller;

import com.example.myapp.service.MessagePollingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackActionController {

    private final MessagePollingService messagePollingService;

    public FeedbackActionController(MessagePollingService messagePollingService) {
        this.messagePollingService = messagePollingService;
    }

    @GetMapping("/record")
    public ResponseEntity<String> record(@RequestParam String qaId,
                                         @RequestParam String feedback) {
        boolean success = messagePollingService.recordFeedbackByQaId(qaId, feedback);
        String title = success ? "反馈已记录" : "反馈未记录";
        String detail = success
                ? ("useful".equalsIgnoreCase(feedback) ? "已记录为有用。" : "已记录为没用。")
                : "未找到对应的问答记录，可能是反馈已过期。";
        String html = "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + title + "</title></head>"
                + "<body style=\"font-family:-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;"
                + "padding:32px;line-height:1.6;\">"
                + "<h2>" + title + "</h2><p>" + detail + "</p>"
                + "</body></html>";
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}
