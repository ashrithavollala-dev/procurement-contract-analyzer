package com.company.contractanalyzer.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Stage-1 compatibility gate, fully offline: no database, no Ollama server, no API key, no
 * network and no ONNX download. It loads the real Spring AI auto-configurations of all three
 * model starters and proves that the explicit selectors from application.yml activate exactly
 * one chat model and exactly one (Transformers) embedding model.
 */
class ModelSelectionSmokeTest {

    /** Marks every bean lazy so bean *definitions* can be inspected without instantiating them. */
    private static final BeanFactoryPostProcessor LAZY_ALL = beanFactory -> {
        for (String name : beanFactory.getBeanDefinitionNames()) {
            beanFactory.getBeanDefinition(name).setLazyInit(true);
        }
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    HttpClientAutoConfiguration.class,
                    RestClientAutoConfiguration.class,
                    WebClientAutoConfiguration.class,
                    SpringAiRetryAutoConfiguration.class,
                    ToolCallingAutoConfiguration.class,
                    OllamaApiAutoConfiguration.class,
                    OllamaChatAutoConfiguration.class,
                    OllamaEmbeddingAutoConfiguration.class,
                    OpenAiChatAutoConfiguration.class,
                    OpenAiEmbeddingAutoConfiguration.class,
                    OpenAiImageAutoConfiguration.class,
                    OpenAiAudioSpeechAutoConfiguration.class,
                    OpenAiAudioTranscriptionAutoConfiguration.class,
                    OpenAiModerationAutoConfiguration.class,
                    TransformersEmbeddingModelAutoConfiguration.class,
                    ChatClientAutoConfiguration.class))
            .withPropertyValues(
                    "spring.ai.model.embedding=transformers",
                    "spring.ai.model.image=none",
                    "spring.ai.model.audio.speech=none",
                    "spring.ai.model.audio.transcription=none",
                    "spring.ai.model.moderation=none",
                    "spring.ai.retry.max-attempts=1",
                    // Nothing listens here: proves startup never contacts Ollama.
                    "spring.ai.ollama.base-url=http://127.0.0.1:9",
                    "spring.ai.ollama.init.pull-model-strategy=never",
                    "spring.ai.ollama.chat.options.model=llama3.2:3b",
                    "spring.ai.openai.api-key=not-configured");

    @Test
    void ollamaProviderCreatesOnlyTheOllamaChatModelAndStartsWithoutOllamaOrKey() {
        runner.withPropertyValues("spring.ai.model.chat=ollama", "spring.ai.model.embedding=none")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context.getBean(ChatModel.class)).isInstanceOf(OllamaChatModel.class);
                    assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
                    assertThat(context).doesNotHaveBean(EmbeddingModel.class);
                    assertThat(context).doesNotHaveBean(ImageModel.class);
                    assertThat(context).doesNotHaveBean(ModerationModel.class);
                    assertThat(context).hasSingleBean(ChatClient.Builder.class);
                });
    }

    @Test
    void openAiProviderCreatesOnlyTheOpenAiChatModelWithoutARealKey() {
        runner.withPropertyValues("spring.ai.model.chat=openai", "spring.ai.model.embedding=none")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context.getBean(ChatModel.class)).isInstanceOf(OpenAiChatModel.class);
                    assertThat(context).doesNotHaveBean(OllamaChatModel.class);
                    assertThat(context).doesNotHaveBean(EmbeddingModel.class);
                });
    }

    @Test
    void embeddingSelectorDefinesExactlyOneTransformersEmbeddingModel() {
        // Definitions only (lazy): instantiating TransformersEmbeddingModel would download the ONNX model.
        runner.withPropertyValues("spring.ai.model.chat=ollama")
                .withInitializer(ctx -> ctx.addBeanFactoryPostProcessor(LAZY_ALL))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    String[] embeddingBeans = context.getBeanFactory()
                            .getBeanNamesForType(EmbeddingModel.class, true, false);
                    assertThat(embeddingBeans).hasSize(1);
                    assertThat(context.getBeanFactory().getType(embeddingBeans[0]))
                            .isEqualTo(TransformersEmbeddingModel.class);
                    assertThat(context.getBeanFactory().getBeanNamesForType(ChatModel.class, true, false))
                            .hasSize(1);
                });
    }

    @Test
    void chatCanBeDisabledEntirely() {
        runner.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChatModel.class);
                });
    }
}
