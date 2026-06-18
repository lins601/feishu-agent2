package com.example.myapp.config;

import okhttp3.OkHttpClient;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * WISE 知识库 API 连接配置。
 * <p>
 * 提供 OkHttpClient，自动附加 Bearer Token 认证头。
 */
@Configuration
public class WiseConfig {

    private static final Logger log = LoggerFactory.getLogger(WiseConfig.class);

    @Value("${wise.api-url:https://wise.cvte.com/api/v1}")
    private String apiUrl;

    @Value("${wise.api-key:}")
    private String apiKey;

    @Value("${wise.search-timeout:10}")
    private int searchTimeout;

    @Value("${wise.search-max-retries:3}")
    private int maxRetries;

    @Value("${wise.agent-id:}")
    private String agentId;

    @Value("${wise.knowledge-base-id:}")
    private String knowledgeBaseId;

    @Value("${wise.faq.knowledge-base-id:}")
    private String faqKnowledgeBaseId;

    /**
     * WISE API 专用 OkHttpClient。
     * 自动添加 x-api-key header。
     */
    @Bean
    public OkHttpClient wiseHttpClient() {
        log.info("初始化 WISE API 客户端: url={}, agentId={}", apiUrl, agentId);

        Interceptor authInterceptor = new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request original = chain.request();
                Request.Builder builder = original.newBuilder()
                        .header("x-api-key", apiKey)
                        .header("Content-Type", "application/json");
                // 保留原始请求的 Accept header（SSE 需要 text/event-stream）
                if (original.header("Accept") == null) {
                    builder.header("Accept", "application/json");
                }
                return chain.proceed(builder.build());
            }
        };

        return new OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .connectTimeout(searchTimeout, TimeUnit.SECONDS)
                .readTimeout(searchTimeout, TimeUnit.SECONDS)
                .writeTimeout(searchTimeout, TimeUnit.SECONDS)
                .build();
    }

    public String getApiUrl() { return apiUrl; }
    public String getApiKey() { return apiKey; }
    public String getAgentId() { return agentId; }
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public String getFaqKnowledgeBaseId() { return faqKnowledgeBaseId; }
    public int getMaxRetries() { return maxRetries; }
}
