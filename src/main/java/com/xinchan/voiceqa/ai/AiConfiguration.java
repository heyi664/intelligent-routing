package com.xinchan.voiceqa.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean
    public QwenChatCompletionTransport qwenChatCompletionTransport() {
        return new QwenRestClientTransport(RestClient.builder().build());
    }

    @Bean
    public StreamingChatModelClient qwenChatModelClient(
        AiProperties properties,
        Environment environment,
        QwenChatCompletionTransport transport
    ) {
        properties.setApiKey(properties.resolveApiKey(environment));
        return new QwenChatModelClient(properties, transport);
    }
}