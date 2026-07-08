package com.xinchan.voiceqa.memory;

import com.xinchan.voiceqa.conversation.ConversationStateRepository;
import com.xinchan.voiceqa.conversation.InMemoryConversationStateRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfiguration {
    @Bean
    @ConditionalOnMissingBean(ChatHistoryRepository.class)
    InMemoryChatHistoryRepository inMemoryChatHistoryRepository() {
        return new InMemoryChatHistoryRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ConversationSummaryService.class)
    NoopConversationSummaryService noopConversationSummaryService() {
        return new NoopConversationSummaryService();
    }

    @Bean
    @ConditionalOnMissingBean(ConversationStateRepository.class)
    @ConditionalOnProperty(prefix = "app.memory", name = "enabled", havingValue = "false", matchIfMissing = true)
    InMemoryConversationStateRepository inMemoryConversationStateRepository() {
        return new InMemoryConversationStateRepository();
    }
}
