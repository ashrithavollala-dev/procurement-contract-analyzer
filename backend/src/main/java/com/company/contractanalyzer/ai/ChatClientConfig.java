package com.company.contractanalyzer.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The ChatClient is built from Spring AI's auto-configured {@link ChatClient.Builder}, which
 * wraps whichever single ChatModel {@code spring.ai.model.chat} (LLM_PROVIDER) selected:
 * OllamaChatModel or OpenAiChatModel, or the StubChatModel in tests. No prompt or code changes
 * are needed to switch provider. Creating it does not contact the model, so the application
 * starts even when Ollama is stopped.
 */
@Configuration(proxyBeanMethods = false)
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
