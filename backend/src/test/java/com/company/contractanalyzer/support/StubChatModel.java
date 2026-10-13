package com.company.contractanalyzer.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Deterministic scripted chat model. Each call consumes the next scripted step; when the script
 * is empty the fallback responder answers. Every prompt is recorded so tests can assert on what
 * the application actually sent (prompt budget, delimiters, tool results).
 */
public class StubChatModel implements ChatModel {

    /** One scripted reaction to a prompt. */
    @FunctionalInterface
    public interface Step {
        ChatResponse respond(Prompt prompt);
    }

    private final Deque<Step> script = new ConcurrentLinkedDeque<>();
    private final List<Prompt> prompts = Collections.synchronizedList(new ArrayList<>());
    private volatile Function<Prompt, String> fallback = prompt -> "{}";

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        Step step = script.pollFirst();
        if (step != null) {
            return step.respond(prompt);
        }
        return text(fallback.apply(prompt));
    }

    public StubChatModel reset() {
        script.clear();
        prompts.clear();
        fallback = prompt -> "{}";
        return this;
    }

    public StubChatModel thenReply(String text) {
        script.addLast(prompt -> text(text));
        return this;
    }

    public StubChatModel thenReply(Function<Prompt, String> responder) {
        script.addLast(prompt -> text(responder.apply(prompt)));
        return this;
    }

    public StubChatModel thenThrow(RuntimeException failure) {
        script.addLast(prompt -> {
            throw failure;
        });
        return this;
    }

    /** Simulates a slow model: sleeps (interruptibly) and then replies. */
    public StubChatModel thenReplyAfter(Duration delay, String text) {
        script.addLast(prompt -> {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            }
            return text(text);
        });
        return this;
    }

    /** The model asks to call a tool (OpenAI/Ollama style tool_calls). */
    public StubChatModel thenToolCall(String toolName, String argumentsJson) {
        script.addLast(prompt -> new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-" + prompts.size(), "function", toolName, argumentsJson)))
                .build()))));
        return this;
    }

    public StubChatModel otherwiseReply(Function<Prompt, String> responder) {
        this.fallback = responder;
        return this;
    }

    public List<Prompt> prompts() {
        synchronized (prompts) {
            return List.copyOf(prompts);
        }
    }

    public int callCount() {
        return prompts.size();
    }

    public int remainingSteps() {
        return script.size();
    }

    public static ChatResponse text(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
