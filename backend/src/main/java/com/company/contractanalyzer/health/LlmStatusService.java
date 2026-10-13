package com.company.contractanalyzer.health;

import com.company.contractanalyzer.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cached reachability probe for the configured chat provider.
 *
 * <ul>
 *   <li><b>ollama</b>: {@code GET {base-url}/api/tags} with a short timeout. The model counts
 *       as reachable only if the server answers and the configured model is pulled (a missing
 *       model would make every chat call fail with 404).</li>
 *   <li><b>openai</b>: reachable when an API key is configured. Calling the paid API just to
 *       render a banner was rejected. Real call failures still invalidate the cache.</li>
 * </ul>
 * Results are cached for {@code app.llm.status-cache-ttl}, so polling clients do not hammer Ollama.
 */
@Service
public class LlmStatusService implements LlmAvailability {

    private static final Logger log = LoggerFactory.getLogger(LlmStatusService.class);
    static final String KEY_SENTINEL = "not-configured";

    private final AppProperties.Llm config;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HttpClient httpClient;
    private final AtomicReference<LlmStatus> cache = new AtomicReference<>();

    public LlmStatusService(AppProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.config = properties.getLlm();
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getProbeTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public LlmStatus status() {
        LlmStatus cached = cache.get();
        Instant now = Instant.now(clock);
        if (cached != null && cached.checkedAt().plus(config.getStatusCacheTtl()).isAfter(now)) {
            return cached;
        }
        LlmStatus fresh = probe(now);
        if (cached == null || cached.reachable() != fresh.reachable()) {
            log.info("LLM status: provider={} model={} reachable={} detail={}",
                    fresh.provider(), fresh.model(), fresh.reachable(), fresh.detail());
        }
        cache.set(fresh);
        return fresh;
    }

    @Override
    public void invalidate() {
        cache.set(null);
    }

    LlmStatus probe(Instant now) {
        String provider = config.getProvider().toLowerCase();
        String model = config.activeModel();
        if (config.isOpenAi()) {
            String key = config.getOpenaiApiKey();
            boolean configured = key != null && !key.isBlank() && !KEY_SENTINEL.equals(key);
            return new LlmStatus(provider, model, configured,
                    configured ? "OpenAI API key configured" : "OPENAI_API_KEY is not set", now);
        }
        if (!"ollama".equals(provider)) {
            return new LlmStatus(provider, model, false, "Unsupported LLM_PROVIDER '" + provider + "'", now);
        }
        return probeOllama(provider, model, now);
    }

    private LlmStatus probeOllama(String provider, String model, Instant now) {
        String base = config.getOllamaBaseUrl().replaceAll("/+$", "");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/tags"))
                    .timeout(config.getProbeTimeout().plus(Duration.ofMillis(500)))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new LlmStatus(provider, model, false, "Ollama answered HTTP " + response.statusCode(), now);
            }
            if (!modelIsPulled(response.body(), model)) {
                return new LlmStatus(provider, model, false,
                        "Ollama is running but model '" + model + "' is not pulled (ollama pull " + model + ")", now);
            }
            return new LlmStatus(provider, model, true, "Ollama reachable at " + base, now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LlmStatus(provider, model, false, "Ollama probe interrupted", now);
        } catch (IOException | IllegalArgumentException e) {
            return new LlmStatus(provider, model, false,
                    "Ollama not reachable at " + base + " (" + e.getClass().getSimpleName() + ")", now);
        }
    }

    boolean modelIsPulled(String tagsJson, String model) throws IOException {
        JsonNode models = objectMapper.readTree(tagsJson).path("models");
        String wanted = model.contains(":") ? model : model + ":latest";
        for (JsonNode node : models) {
            String name = node.path("name").asText("");
            if (name.equals(model) || name.equals(wanted)) {
                return true;
            }
        }
        return false;
    }
}
