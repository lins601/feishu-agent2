package com.example.myapp;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 基础冒烟测试 — 验证核心组件可以正确创建。
 */
class SmokeTest {

    @Test
    void mainClassExists() {
        assertThat(MyappApplication.class).isNotNull();
    }

    @Test
    void agentPackageExists() {
        assertThat(com.example.myapp.agent.KnowledgeAssistantAgent.class).isNotNull();
        assertThat(com.example.myapp.agent.QuestionUnderstandingAgent.class).isNotNull();
        assertThat(com.example.myapp.agent.AnswerGenerationAgent.class).isNotNull();
    }

    @Test
    void servicePackageExists() {
        assertThat(com.example.myapp.service.WiseSearchService.class).isNotNull();
        assertThat(com.example.myapp.service.FeishuMessageService.class).isNotNull();
        assertThat(com.example.myapp.service.IntentClassifier.class).isNotNull();
    }

    @Test
    void configPackageExists() {
        assertThat(com.example.myapp.config.WiseConfig.class).isNotNull();
        assertThat(com.example.myapp.config.FeishuConfig.class).isNotNull();
    }

    @Test
    void dtoPackageExists() {
        assertThat(com.example.myapp.dto.WiseSearchResult.class).isNotNull();
        assertThat(com.example.myapp.dto.AgentResponse.class).isNotNull();
        assertThat(com.example.myapp.dto.WiseIngestionResult.class).isNotNull();
    }

    @Test
    void agentResponse_miss() {
        var resp = com.example.myapp.dto.AgentResponse.miss("test question");
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getAnswerText()).contains("暂未收录");
    }

    @Test
    void agentResponse_degraded() {
        var resp = com.example.myapp.dto.AgentResponse.degraded("error");
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.isDegraded()).isTrue();
        assertThat(resp.getAnswerText()).contains("系统繁忙");
    }

    @Test
    void intentClassifier_basic() {
        var classifier = new com.example.myapp.service.IntentClassifier();

        var wmsIntent = classifier.classify("WMS入库单提交不了");
        assertThat(wmsIntent.system()).isEqualTo("WMS");
        assertThat(wmsIntent.confidence()).isGreaterThan(0.5);

        var qmsIntent = classifier.classify("IQC检验单报错");
        assertThat(qmsIntent.system()).isEqualTo("QMS");

        var mesIntent = classifier.classify("MES工单报工");
        assertThat(mesIntent.system()).isEqualTo("MES");

        var unknownIntent = classifier.classify("怎么吃饭");
        assertThat(unknownIntent.system()).isNull();
    }

    @Test
    void wiseSearchResult_empty() {
        var result = new com.example.myapp.dto.WiseSearchResult(java.util.List.of());
        assertThat(result.hasResults()).isFalse();
        assertThat(result.getTopScore()).isEqualTo(0.0);
    }
}
