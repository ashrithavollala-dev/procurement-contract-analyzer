package com.company.contractanalyzer.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads StringTemplate prompt files from {@code classpath:prompts/*.st} once and renders them
 * with Spring AI's {@link PromptTemplate} (StringTemplate, {@code {name}} placeholders).
 * Variable values are inserted literally and never re-parsed as templates, so braces or markers
 * inside contract text cannot break or inject template syntax.
 */
@Component
public class PromptLoader {

    public static final String COMPLIANCE_SYSTEM = "compliance-system";
    public static final String COMPLIANCE_JUDGE = "compliance-judge";
    public static final String QA_SYSTEM = "qa-system";
    public static final String QA_RAG_CONTEXT = "qa-rag-context";
    public static final String QA_EMPTY_CONTEXT = "qa-empty-context";
    public static final String QA_TOOLS_SYSTEM = "qa-tools-system";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /** Raw template text. */
    public String template(String name) {
        return cache.computeIfAbsent(name, PromptLoader::load);
    }

    /** Renders a template with the given variables. Every placeholder must be supplied. */
    public String render(String name, Map<String, Object> variables) {
        return PromptTemplate.builder().template(template(name)).variables(variables).build().render();
    }

    private static String load(String name) {
        ClassPathResource resource = new ClassPathResource("prompts/" + name + ".st");
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt template not found: prompts/" + name + ".st", e);
        }
    }
}
