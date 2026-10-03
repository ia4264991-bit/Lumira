package com.lumira.backend.sarah;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SarahAiConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "lumira.sarah.provider", name = "enabled", havingValue = "true")
    AiRouter configuredAiRouter(RestClient.Builder builder, ObjectMapper mapper,
            @Value("${lumira.sarah.provider.chat-completions-url}") String endpoint,
            @Value("${lumira.sarah.provider.model}") String model,
            @Value("${lumira.sarah.provider.api-key:}") String apiKey) {
        return new ChatCompletionsAiRouter(builder.build(), mapper, endpoint, model, apiKey);
    }
}
