package com.dj1012h.researchpilot.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
        //在app.ai.enable=true才注入Bean
    ChatModel chatModel(AiProperties properties) {
        //模型配置
        Assert.hasText(properties.getBaseUrl(), "LLM_BASE_URL must be configured when LLM_ENABLED=true");
        Assert.hasText(properties.getApiKey(), "LLM_API_KEY must be configured when LLM_ENABLED=true");
        Assert.hasText(properties.getModelName(), "LLM_MODEL_NAME must be configured when LLM_ENABLED=true");

        return OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .timeout(properties.getTimeout())
                .maxRetries(properties.getMaxRetries())
                .temperature(properties.getTemperature())
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
