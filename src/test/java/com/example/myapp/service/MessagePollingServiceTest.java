package com.example.myapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.service.im.v1.model.Message;
import com.lark.oapi.service.im.v1.model.MessageBody;
import com.lark.oapi.service.im.v1.model.Sender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessagePollingServiceTest {

    private final MessagePollingService service =
            new MessagePollingService(null, Runnable::run, new ObjectMapper(), null);

    @Test
    void selectBotReplyCandidateSkipsReplyThreadedToAnotherQuestion() {
        long now = System.currentTimeMillis();
        Message otherReply = appMessage("reply-1", now + 1000, "question-1", "", "WISE answer");

        Message candidate = service.selectBotReplyCandidate(
                new Message[]{otherReply}, "question-2", now);

        assertThat(candidate).isNull();
    }

    @Test
    void selectBotReplyCandidateAcceptsReplyThreadedToCurrentQuestion() {
        long now = System.currentTimeMillis();
        Message currentReply = appMessage("reply-1", now + 1000, "question-1", "", "WISE answer");

        Message candidate = service.selectBotReplyCandidate(
                new Message[]{currentReply}, "question-1", now);

        assertThat(candidate).isSameAs(currentReply);
    }

    @Test
    void selectBotReplyCandidateDoesNotAttachUnthreadedAnswerAcrossNewerQuestion() {
        long now = System.currentTimeMillis();
        Message nextQuestion = userMessage("question-2", now + 1000, "second question");
        Message unthreadedReply = appMessage("reply-1", now + 2000, "", "", "WISE answer");

        Message firstCandidate = service.selectBotReplyCandidate(
                new Message[]{nextQuestion, unthreadedReply}, "question-1", now);
        Message secondCandidate = service.selectBotReplyCandidate(
                new Message[]{nextQuestion, unthreadedReply}, "question-2", now + 1000);

        assertThat(firstCandidate).isNull();
        assertThat(secondCandidate).isSameAs(unthreadedReply);
    }

    @Test
    void selectBotReplyCandidateSkipsSystemTimeoutReply() {
        long now = System.currentTimeMillis();
        Message timeoutReply = appMessage("reply-1", now + 1000, "question-1", "",
                "抱歉，当前系统繁忙，暂时没有检测到知识库回复。");

        Message candidate = service.selectBotReplyCandidate(
                new Message[]{timeoutReply}, "question-1", now);

        assertThat(candidate).isNull();
    }

    @Test
    void detectFeedbackSupportsTextAndEmoji() {
        assertThat(service.detectFeedback("有用")).isEqualTo("useful");
        assertThat(service.detectFeedback("👍")).isEqualTo("useful");
        assertThat(service.detectFeedback("没用")).isEqualTo("useless");
        assertThat(service.detectFeedback("👎")).isEqualTo("useless");
        assertThat(service.detectFeedback("这个问题怎么处理")).isNull();
    }

    @Test
    void matchFeedbackContextSupportsAnswerCardAndQuestionReferences() {
        MessagePollingService.QaContext context = service.cacheQaContext(
                "chat-1", "user-1", "qa-1", "question", "answer", "",
                "question-1", "wise-answer-1");
        service.cacheReplyAlias("feedback-card-1", context);

        assertThat(service.matchFeedbackContext("chat-1", "user-1", "wise-answer-1").context())
                .isSameAs(context);
        assertThat(service.matchFeedbackContext("chat-1", "user-1", "feedback-card-1").context())
                .isSameAs(context);
        assertThat(service.matchFeedbackContext("chat-1", "user-1", "question-1").context())
                .isSameAs(context);
    }

    @Test
    void matchFeedbackContextUsesRecentUniqueOrReportsAmbiguous() {
        MessagePollingService oneQaService =
                new MessagePollingService(null, Runnable::run, new ObjectMapper(), null);
        MessagePollingService.QaContext onlyContext = oneQaService.cacheQaContext(
                "chat-1", "user-1", "qa-1", "question", "answer", "",
                "question-1", "wise-answer-1");

        MessagePollingService.FeedbackMatch unique =
                oneQaService.matchFeedbackContext("chat-1", "user-1", "");
        assertThat(unique.ambiguous()).isFalse();
        assertThat(unique.context()).isSameAs(onlyContext);

        oneQaService.cacheQaContext("chat-1", "user-1", "qa-2", "question2", "answer2", "",
                "question-2", "wise-answer-2");

        MessagePollingService.FeedbackMatch ambiguous =
                oneQaService.matchFeedbackContext("chat-1", "user-1", "");
        assertThat(ambiguous.ambiguous()).isTrue();
        assertThat(ambiguous.context()).isNull();
    }

    @Test
    void autoRegisterChatMergesConfiguredAndPersistedChats(@TempDir Path tempDir) throws Exception {
        Path persistPath = tempDir.resolve("feedback-chats.txt");
        MessagePollingService autoService =
                new MessagePollingService(null, Runnable::run, new ObjectMapper(), null);
        ReflectionTestUtils.setField(autoService, "chatIds", "oc_static");
        ReflectionTestUtils.setField(autoService, "autoRegisterEnabled", true);
        ReflectionTestUtils.setField(autoService, "autoRegisterPersistPath", persistPath.toString());

        autoService.registerChatForFeedback("oc_dynamic", "bot_added");
        autoService.registerChatForFeedback("oc_dynamic", "duplicate");

        assertThat(autoService.monitorChatIdsForTest())
                .containsExactly("oc_static", "oc_dynamic");
        assertThat(Files.readAllLines(persistPath)).containsExactly("oc_dynamic");

        MessagePollingService restarted =
                new MessagePollingService(null, Runnable::run, new ObjectMapper(), null);
        ReflectionTestUtils.setField(restarted, "autoRegisterEnabled", true);
        ReflectionTestUtils.setField(restarted, "autoRegisterPersistPath", persistPath.toString());

        assertThat(restarted.monitorChatIdsForTest()).containsExactly("oc_dynamic");
    }

    @Test
    void unregisterChatRemovesPersistedAutoRegisteredChat(@TempDir Path tempDir) throws Exception {
        Path persistPath = tempDir.resolve("feedback-chats.txt");
        Files.write(persistPath, List.of("oc_dynamic"));
        MessagePollingService autoService =
                new MessagePollingService(null, Runnable::run, new ObjectMapper(), null);
        ReflectionTestUtils.setField(autoService, "autoRegisterEnabled", true);
        ReflectionTestUtils.setField(autoService, "autoRegisterPersistPath", persistPath.toString());

        autoService.unregisterChatForFeedback("oc_dynamic", "bot_deleted");

        assertThat(autoService.monitorChatIdsForTest()).isEmpty();
        assertThat(Files.readAllLines(persistPath)).isEmpty();
    }

    private Message userMessage(String messageId, long createTime, String text) {
        return message(messageId, "user", createTime, "", "", text);
    }

    private Message appMessage(String messageId, long createTime, String parentId,
                               String rootId, String text) {
        return message(messageId, "app", createTime, parentId, rootId, text);
    }

    private Message message(String messageId, String senderType, long createTime,
                            String parentId, String rootId, String text) {
        Message message = new Message();
        message.setMessageId(messageId);
        message.setMsgType("text");
        message.setCreateTime(String.valueOf(createTime));
        message.setUpdateTime(String.valueOf(createTime));
        message.setParentId(parentId);
        message.setRootId(rootId);

        Sender sender = new Sender();
        sender.setSenderType(senderType);
        message.setSender(sender);

        MessageBody body = new MessageBody();
        body.setContent("{\"text\":\"" + text.replace("\"", "\\\"") + "\"}");
        message.setBody(body);
        return message;
    }
}
