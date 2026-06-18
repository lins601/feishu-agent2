package com.example.myapp.service;

import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.enums.CreateMessageReceiveIdTypeEnum;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends operational alerts to the administrator Feishu chat.
 */
@Service
public class FeishuAdminNotifier {

    private static final Logger log = LoggerFactory.getLogger(FeishuAdminNotifier.class);

    private final Client feishuClient;

    @Value("${feishu.admin.notify.enabled:true}")
    private boolean enabled;

    @Value("${feishu.admin.chat-id:}")
    private String adminChatId;

    public FeishuAdminNotifier(Client feishuClient) {
        this.feishuClient = feishuClient;
    }

    public void notifySyncFailure(String projectName, String sourceUrl, String errorMessage) {
        notifyText("MinDoc 同步失败",
                "项目：" + safe(projectName) + "\n"
                        + "入口：" + safe(sourceUrl) + "\n"
                        + "原因：" + safe(errorMessage));
    }

    public void notifyWiseParseFailure(String projectName, String title, String errorMessage) {
        notifyText("WISE 解析失败",
                "项目：" + safe(projectName) + "\n"
                        + "文档：" + safe(title) + "\n"
                        + "原因：" + safe(errorMessage));
    }

    public void notifyNegativeFeedback(String question, String knowledgeRefs, int negativeCount) {
        notifyText("收到负反馈",
                "问题：" + safe(question) + "\n"
                        + "知识来源：" + blank(knowledgeRefs) + "\n"
                        + "累计负反馈：" + negativeCount);
    }

    public void notifyTopMissQuestion(String question, int count) {
        notifyText("未命中高频问题",
                "问题：" + safe(question) + "\n"
                        + "出现次数：" + count + "\n"
                        + "建议补充 FAQ 或知识文档。");
    }

    public void notifyWeeklyReport(String report) {
        notifyText("制造知识助手周报", report);
    }

    public void notifyMonthlyReport(String report) {
        notifyText("制造知识助手月报", report);
    }

    public void notifyText(String title, String content) {
        if (!enabled || adminChatId == null || adminChatId.isBlank()) {
            log.debug("管理员通知未配置，跳过: title={}", title);
            return;
        }
        String text = "【" + safe(title) + "】\n" + safe(content);
        try {
            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType(CreateMessageReceiveIdTypeEnum.CHAT_ID)
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(adminChatId)
                            .msgType("text")
                            .content("{\"text\":\"" + escapeJson(text) + "\"}")
                            .build())
                    .build();
            CreateMessageResp resp = feishuClient.im().message().create(req);
            if (resp == null || resp.getCode() != 0) {
                log.warn("发送管理员通知失败: title={}, code={}, msg={}",
                        title, resp != null ? resp.getCode() : "null",
                        resp != null ? resp.getMsg() : "null");
            }
        } catch (Exception e) {
            log.warn("发送管理员通知异常: title={}", title, e);
        }
    }

    private String safe(String text) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        return text.length() <= 1200 ? text : text.substring(0, 1200) + "...";
    }

    private String blank(String text) {
        return text == null || text.isBlank() ? "未提取" : safe(text);
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
