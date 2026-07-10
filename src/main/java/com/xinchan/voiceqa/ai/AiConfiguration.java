package com.xinchan.voiceqa.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean
    public QwenChatCompletionTransport qwenChatCompletionTransport() {
        return new QwenRestClientTransport((connectTimeoutMs, readTimeoutMs) -> {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(connectTimeoutMs);
            requestFactory.setReadTimeout(readTimeoutMs);
            return RestClient.builder().requestFactory(requestFactory).build();
        });
    }

    @Bean
    public StreamingChatModelClient qwenChatModelClient(
        AiProperties properties,
        QwenChatCompletionTransport transport
    ) {
        return new QwenChatModelClient(properties, transport);
    }
}
