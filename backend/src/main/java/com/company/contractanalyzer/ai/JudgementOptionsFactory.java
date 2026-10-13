package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.config.AppProperties;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Component;

/**
 * Per-request chat options for structured judgement calls. Only provider-specific
 * structured-output settings are set here. Model, temperature (0) and num_ctx come from the
 * provider's default options in application.yml, and runtime options are merged over them.
 *
 * <ul>
 *   <li>Ollama: {@code outputSchema(jsonSchema)}, documented in Spring AI 1.1. Ollama
 *       constrains generation to the JSON schema from BeanOutputConverter.</li>
 *   <li>OpenAI: {@code response_format = json_object} (JSON mode). The schema is still given
 *       through the format instructions in the prompt and validated server-side.</li>
 * </ul>
 */
@Component
public class JudgementOptionsFactory {

    private final boolean openAi;

    public JudgementOptionsFactory(AppProperties properties) {
        this.openAi = properties.getLlm().isOpenAi();
    }

    public ChatOptions structuredOutput(String jsonSchema) {
        if (openAi) {
            return OpenAiChatOptions.builder()
                    .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                    .build();
        }
        return OllamaChatOptions.builder()
                .outputSchema(jsonSchema)
                .temperature(0.0)
                .build();
    }
}
