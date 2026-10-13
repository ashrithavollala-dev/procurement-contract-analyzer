# Technical Decisions (authoritative decision record)

This file records every version, API and configuration choice, the evidence behind it, and
what was verified on the developer machine versus what is assumed. Each entry is labelled
**VERIFIED** (checked by a command, by the official documentation, or by reading the
pinned version's source jar) or **NOT VERIFIED** (an assumption).

Date of the compatibility gate: 2026-10-13.

## 1. Toolchain used for validation (VERIFIED by command)

| Tool | Version | Command |
|---|---|---|
| Java | Temurin 17.0.20.1+1 | `java -version` |
| Maven | 3.10.0 | `mvn -v` (platform encoding Cp1252, so UTF-8 is forced in the pom) |
| Node.js | 24.17.0 locally; CI pins Node 20 | `node -v` |
| npm | 11.13.0 | `npm -v` |
| Docker Engine | 29.8.2 (Docker Desktop) | `docker version` |
| Git | 2.55.0.windows.5 | `git --version` |
| OS | Windows 11 Home 10.0.26200, about 7.7 GB RAM, Intel Core i3-1115G4, no GPU | `Win32_OperatingSystem`, `Win32_Processor` |

## 2. Backend versions

| Component | Version | How it is pinned | Evidence |
|---|---|---|---|
| Spring Boot | **3.5.16** (latest 3.5.x patch on Maven Central) | `spring-boot-starter-parent` | [Maven Central metadata](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml) |
| Spring AI | **1.1.8** (latest 1.1.x patch) | `spring-ai-bom` imported once in `dependencyManagement` | [Maven Central metadata](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-bom/maven-metadata.xml). The 1.1.8 starters declare `spring-boot-starter` 3.5.15 (read from the starter POM), so 3.5.16 is the matching patch line. |
| Java | 17 (`maven.compiler.release=17`) | pom | Spring Boot 3.5 requires Java 17+ |
| Apache PDFBox | **3.0.8** | explicit property (not Boot-managed) | Maven Central `<release>3.0.8` |
| MapStruct | **1.6.3** | explicit property | Maven Central |
| lombok-mapstruct-binding | **0.2.0** | explicit property, on the annotation-processor path | Maven Central |
| springdoc-openapi | **2.9.1** | explicit property | its parent POM is built on Spring Boot 3.5.16 (read from `springdoc-openapi-2.9.1.pom`); 3.x targets Boot 4 |
| Lombok | 1.18.46 | managed by Boot 3.5.16 | `spring-boot-dependencies-3.5.16.pom` |
| Flyway | 11.7.2 (+ `flyway-database-postgresql`) | managed by Boot | same |
| PostgreSQL JDBC | 42.7.11 | managed by Boot | same |
| HikariCP | 6.3.3 | managed by Boot | same |
| Testcontainers | 1.21.4 | managed by Boot | same |
| Awaitility | 4.2.2 | managed by Boot | same |
| Mockito | 5.17.0 | managed by Boot | same |

Rule: one authoritative BOM per ecosystem (Boot parent + Spring AI BOM). No explicit
versions are set on transitive dependencies.

Why 1.1.x / Boot 3.5 and not Spring AI 2.0 / Boot 4: Spring AI 2.0 requires Spring Boot 4
(Spring Framework 7, Jakarta EE 11 baseline). The 1.1/3.5 pair is the mature,
widely-documented line. The upgrade path is in INTERVIEW_NOTES.md (not implemented).

## 3. Spring AI artifacts (VERIFIED: present in `spring-ai-bom-1.1.8.pom`)

| Artifact | Purpose |
|---|---|
| `spring-ai-starter-vector-store-pgvector` | `PgVectorStore` auto-configuration |
| `spring-ai-starter-model-ollama` | `OllamaChatModel` (default chat provider) |
| `spring-ai-starter-model-openai` | `OpenAiChatModel` (alternative chat provider) |
| `spring-ai-starter-model-transformers` | `TransformersEmbeddingModel` (local ONNX embeddings) |
| `spring-ai-rag` | `RetrievalAugmentationAdvisor`, `VectorStoreDocumentRetriever`, `ContextualQueryAugmenter`, `DocumentPostProcessor` |

## 4. Model selection (VERIFIED: `SpringAIModelProperties` and the auto-configuration conditions in the 1.1.8 source jars)

All three model starters are on the classpath, so every selector is set explicitly in
`application.yml`:

| Property | Value | Notes |
|---|---|---|
| `spring.ai.model.chat` | `${LLM_PROVIDER:ollama}` | `OllamaChatAutoConfiguration` / `OpenAiChatAutoConfiguration` are `@ConditionalOnProperty(spring.ai.model.chat == ollama / openai)` |
| `spring.ai.model.embedding` | `transformers` | the Ollama and OpenAI embedding auto-configs are conditional on the same key, so exactly one `EmbeddingModel` is created |
| `spring.ai.model.image` | `none` | disables the OpenAI image model |
| `spring.ai.model.audio.speech` | `none` | disables OpenAI TTS |
| `spring.ai.model.audio.transcription` | `none` | disables OpenAI transcription |
| `spring.ai.model.moderation` | `none` | disables OpenAI moderation |

Documented in the [1.1 upgrade notes](https://docs.spring.io/spring-ai/reference/1.1/upgrade-notes.html)
("`<model-provider|none>`"). In the `test` profile `spring.ai.model.chat=none` and
`spring.ai.model.embedding=none`; the test configuration provides `StubChatModel` and
`FakeEmbeddingModel` as `@Primary` beans.

`spring.ai.retry.max-attempts=1`: Spring AI's default (10 attempts with exponential
backoff up to 3 minutes) would multiply a slow CPU call many times. The application does
its own bounded retry (one retry on invalid structured output) and its own circuit breaker.

## 5. Ollama configuration (VERIFIED against the [Ollama chat docs](https://docs.spring.io/spring-ai/reference/1.1/api/chat/ollama-chat.html) and `OllamaChatOptions` 1.1.8 source)

| Property | Value |
|---|---|
| `spring.ai.ollama.base-url` | `${OLLAMA_BASE_URL:http://localhost:11434}` |
| `spring.ai.ollama.chat.options.model` | `${OLLAMA_CHAT_MODEL:llama3.2:3b}` |
| `spring.ai.ollama.chat.options.temperature` | `0` |
| `spring.ai.ollama.chat.options.num-ctx` | `${OLLAMA_NUM_CTX:4096}` |
| `spring.ai.ollama.chat.options.keep-alive` | `${OLLAMA_KEEP_ALIVE:10m}`. The table lists `keep_alive`; the Java field is `keepAlive` with `@JsonProperty("keep_alive")`, so Boot relaxed binding accepts the canonical `keep-alive` form. |
| `spring.ai.ollama.init.pull-model-strategy` | `never`: the app never pulls models |

**HTTP read timeout.** The Ollama docs have no timeout property. In the 1.1.8 source,
`OllamaApiAutoConfiguration` builds `OllamaApi` from the auto-configured
`RestClient.Builder` (`restClientBuilderProvider.getIfAvailable(...)`). Boot 3.5.16
applies `spring.http.client.read-timeout` and `spring.http.client.connect-timeout`
(VERIFIED in `spring-boot-autoconfigure-3.5.16.jar` metadata) to that builder, so
`spring.http.client.read-timeout=${OLLAMA_READ_TIMEOUT_SECONDS:300}s`. Side effect: the
same timeout applies to the OpenAI client, which is acceptable.

**Structured output with Ollama.** The docs show `OllamaChatOptions.builder().outputSchema(jsonSchema)`
with a schema from `BeanOutputConverter`. `outputSchema(String)` exists in 1.1.8
(VERIFIED). `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` does **not** list Ollama
([structured output docs](https://docs.spring.io/spring-ai/reference/1.1/api/structured-output-converter.html)),
so it is not used. The judge call uses the following:
- `BeanOutputConverter<RuleJudgement>`: `getFormat()` instructions are put in the prompt and `convert()` parses the reply.
- With Ollama, per-request `OllamaChatOptions.outputSchema(converter.getJsonSchema())` makes the server enforce the schema.
- With OpenAI, per-request `OpenAiChatOptions.responseFormat(JSON_OBJECT)`, which is JSON mode. Strict JSON_SCHEMA was not chosen because OpenAI's strict mode accepts only a subset of JSON Schema.
- Application-side validation of every field, plus one retry.

## 6. OpenAI configuration (VERIFIED against the [OpenAI chat docs](https://docs.spring.io/spring-ai/reference/1.1/api/chat/openai-chat.html))

`spring.ai.openai.api-key=${OPENAI_API_KEY:not-configured}`,
`spring.ai.openai.chat.options.model=${OPENAI_CHAT_MODEL:gpt-4o-mini}`,
`spring.ai.openai.chat.options.temperature=${OPENAI_TEMPERATURE:0}`.
The `not-configured` sentinel exists only so the context can start with
`LLM_PROVIDER=openai` and no key. `LlmStatusService` treats a blank or sentinel key as
*unreachable*, and LLM endpoints then return 503 `LLM_UNAVAILABLE`. The key is never
logged. Some reasoning models reject `temperature`. For those, set `OPENAI_TEMPERATURE`
to an empty value (NOT VERIFIED against a live OpenAI account).

## 7. Embeddings (VERIFIED against the [Transformers (ONNX) docs](https://docs.spring.io/spring-ai/reference/1.1/api/embeddings/onnx.html))

- `TransformersEmbeddingModel` with its default model `sentence-transformers/all-MiniLM-L6-v2`, which the docs give as **384 dimensions** (about 80 MB, downloaded on first use).
- `spring.ai.embedding.transformer.cache.directory=${EMBEDDING_CACHE_DIR:...}` points at a mounted Docker volume, so the download happens once.
- `spring.ai.embedding.transformer.tokenizer.options.padding=true`, as the docs recommend against "Supplied array is ragged".
- The docs do not discuss glibc. The ONNX Runtime native libraries are glibc builds, so the runtime image is `eclipse-temurin:17-jre` (Ubuntu), not Alpine.
- `EmbeddingDimensionVerifier` compares `EmbeddingModel.dimensions()` with `app.vector.dimensions` (384) and with the actual `vector_store.embedding` column type read from `pg_attribute`. On any mismatch startup fails with a clear message.

## 8. PgVectorStore (VERIFIED against the [PGvector docs](https://docs.spring.io/spring-ai/reference/1.1/api/vectordbs/pgvector.html) and `PgVectorStore` 1.1.8 source)

- `initialize-schema=false`: Flyway owns the table.
- `schema-validation=true`: `PgVectorSchemaValidator` checks the table and columns at startup.
- `index-type=HNSW`, `distance-type=COSINE_DISTANCE`, `dimensions=384`, `id-type=UUID`.
- The docs' table uses `metadata json`. The 1.1.8 source inserts `?::jsonb` and filters with `metadata::jsonb @@ '<jsonpath>'::jsonpath`, so a **jsonb** column works and avoids a cast. V1 uses `metadata jsonb`.
- The filter is a jsonpath `@@` predicate, so a btree on `(metadata->>'contractId')` cannot serve it. V1 creates both indexes: the btree, as specified and used by the application's own native count and delete queries, and a `GIN (metadata jsonb_path_ops)` index that supports `@@`.
- Score: the row mapper sets `Document.score = 1 - cosine_distance` (VERIFIED). The similarity threshold is applied in SQL as `distance < 1 - threshold`.
- Delete by filter: `VectorStore.delete(Filter.Expression)` is implemented as `DELETE ... WHERE <jsonpath filter>` (VERIFIED).
- Filter construction: `new FilterExpressionBuilder().eq("contractId", id).build()`.
- Vector ids are deterministic (`UUID.nameUUIDFromBytes(contractId + ":" + chunkIndex)`). The insert is an UPSERT (`ON CONFLICT (id) DO UPDATE`), so reprocessing cannot duplicate vectors even before the explicit delete.

## 9. RAG advisor (VERIFIED in the 1.1.8 `spring-ai-rag` source)

Q&A `rag` mode uses `RetrievalAugmentationAdvisor` built from these parts:
- `VectorStoreDocumentRetriever`: its builder takes `topK`, `similarityThreshold` and `filterExpression`. The runtime filter is passed with the `VectorStoreDocumentRetriever.FILTER_EXPRESSION` advisor param.
- A custom `DocumentPostProcessor` (`PromptBudget`) drops the lowest-scoring excerpts until the prompt fits `num-ctx`.
- `ContextualQueryAugmenter`: its template needs the `query` and `context` placeholders (enforced by `PromptAssert`). It has a custom `documentFormatter` that wraps every excerpt in delimiters and a custom `emptyContextPromptTemplate`.
- The retrieved documents come back under `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT` (`"rag_document_context"`) in `ChatClientResponse.context()`. Citations are built from these documents, never parsed from model text.
- The advisor gets a `SyncTaskExecutor`, so it does not create its own 4-16 thread pool.

## 10. Tool calling (VERIFIED against the [Tools docs](https://docs.spring.io/spring-ai/reference/1.1/api/tools.html) and source)

- `@Tool` and `@ToolParam` come from `org.springframework.ai.tool.annotation`. `ToolContext` is `org.springframework.ai.chat.model.ToolContext`, and the docs state that "None of the data provided in the ToolContext is sent to the AI model".
- `ToolCallbacks.from(Object...)`, `ToolCallingChatOptions.builder().toolCallbacks(..).toolContext(..).internalToolExecutionEnabled(false)`, `ToolCallingManager.executeToolCalls(prompt, response)` and `ToolExecutionResult.conversationHistory()`.
- `tools` mode uses the documented *user-controlled* tool execution loop with a hard round limit (`app.qa.max-tool-rounds`, default 3). The docs provide no built-in limit.

## 11. Task execution (VERIFIED against the [Boot 3.5 task execution docs](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html))

User-defined `Executor` beans make the auto-configured executor back off. Spring MVC needs
`applicationTaskExecutor`. So the two custom executors are defined with
`@Bean(defaultCandidate = false)` and referenced by name (`@Async("ingestionExecutor")`,
`@Async("auditExecutor")`), and Boot's `applicationTaskExecutor` remains for MVC.

## 12. Other Boot properties (VERIFIED in 3.5.16 configuration metadata)

These properties exist: `server.shutdown`, `spring.lifecycle.timeout-per-shutdown-phase`,
`spring.datasource.hikari.leak-detection-threshold`, `spring.servlet.multipart.max-file-size`,
`spring.http.client.read-timeout`, `logging.structured.format.console`.

## 13. Local Ollama checks (read-only; executed 2026-10-13)

| Check | Result |
|---|---|
| `ollama --version` | `ollama version is 0.40.3` (VERIFIED) |
| `GET http://localhost:11434/api/version` | `{"version":"0.40.3"}` (VERIFIED) |
| `ollama list` | `llama3.2:3b  a80c4f17acd5  2.0 GB` (VERIFIED) |
| `ollama show llama3.2:3b` | arch llama, 3.2B params, context length 131072, Q4_K_M, capabilities **completion, tools** (VERIFIED) |
| Tool-calling probe (`POST /api/chat`, one trivial `get_weather(city)` tool, temperature 0, num_ctx 2048) | Returned `message.tool_calls=[{function:{name:"get_weather",arguments:{city:"Springfield"}}}]`. Total 17.5 s, of which 12.5 s was model load (VERIFIED). |

Conclusion: the local model can emit a structured tool call for a trivial tool. One probe
does not show it calls tools reliably on contract questions, so `QA_MODE` defaults to `rag`.
`tools` mode is implemented and stub-tested. It falls back to `rag` once if the model
rejects tools. No model was pulled, deleted or upgraded.

## 14. Frontend versions (VERIFIED: npm registry, `engines` field checked for Node 20 support)

| Package | Version | Note |
|---|---|---|
| react / react-dom | 18.3.1 | React 18 as required |
| typescript | 5.9.3 | strict mode |
| vite / @vitejs/plugin-react | 6.4.4 / 4.7.0 | Vite 6 supports Node 18/20/22+. Vite 7 needs Node 20.19+, so 6.x is used for margin. |
| tailwindcss / postcss / autoprefixer | 3.4.19 / 8.5.30 / 10.6.1 | v3 because the design tokens live in `tailwind.config.js` |
| axios | 1.20.0 | |
| @tanstack/react-query | 5.104.2 | `refetchInterval` as a function lets polling stop by itself |
| react-router-dom | 6.30.6 | |
| react-hook-form / zod / @hookform/resolvers | 7.90.0 / 3.25.76 / 3.10.0 | resolvers 3.x targets zod 3 |
| lucide-react | 1.56.0 | peer react ^16.5–^19 |
| vitest / jsdom / msw | 3.2.7 / 26.1.0 / 2.15.0 | Node ≥18 |
| @testing-library/react / dom / user-event / jest-dom | 16.3.3 / 10.4.2 / 14.6.7 / **6.9.1** | jest-dom 6.10.0 declares `node >= 22`, so 6.9.1 (`node >= 14`) is pinned |
| eslint / typescript-eslint / react-hooks / react-refresh | 9.39.5 / 8.71.2 / 5.2.0 / 0.5.7 | npm prints "eslint 9 no longer supported" (ESLint 10 exists). Kept on 9 because the React plugins target it. Upgrading is low risk future work. |

Every version is pinned exactly in package.json, with package-lock.json committed for
`npm ci`. Vitest runs with `pool: forks` and `maxWorkers: 2`. The default of one jsdom worker
per CPU made the full suite stall on the 7.7 GB laptop.

## 15. Container images

| Image | Tag | Why |
|---|---|---|
| backend build | `maven:3.9.11-eclipse-temurin-17` | Maven 3.9 + JDK 17 |
| backend runtime | `eclipse-temurin:17-jre` (Ubuntu) | glibc for the ONNX Runtime and DJL tokenizer natives (not Alpine) |
| frontend build | `node:20.19.5-alpine` | same Node major as CI |
| frontend runtime | `nginx:1.27.5-alpine` | static SPA + reverse proxy, envsubst templates |
| database | `pgvector/pgvector:pg16` | PostgreSQL 16 with the `vector` extension (also used by Testcontainers) |

## 16. Stage 8 findings (VERIFIED on the developer machine, 2026-10-13)

- **Model download source.** The `spring-ai-transformers-1.1.8.jar` resource `onnx/all-MiniLM-L6-v2/model.onnx` is a 133-byte Git LFS pointer. The real model (91 MB) is downloaded from `DEFAULT_ONNX_MODEL_URI` (media.githubusercontent.com) by `ResourceCacheService` through `UrlResource.getInputStream()`, which applies **no timeouts** (read in the 1.1.8 source).
- **Observed failure.** During a network outage the download stalled, startup blocked forever and the container went unhealthy.
- **Fix.** `ContractAnalyzerApplication` sets `sun.net.client.defaultConnectTimeout=30000` and `sun.net.client.defaultReadTimeout=120000` (JDK defaults for `HttpURLConnection`) unless the operator already set them. A stalled download now fails, and `restart: unless-stopped` retries it. The healthcheck start period was raised to 600 s.
- **Docker image builds** failed twice on DNS or network outages inside the build. The Dockerfile now uses a BuildKit cache mount for `~/.m2` and Maven Resolver HTTP retries (`aether.connector.http.retryHandler.count=5`).
- **Docker smoke test results.** All three services were healthy.
  - Real-embedding ingestion: READY in about 3 s (3 pages, 15 chunks).
  - Search returns the expected clauses.
  - Scanned PDF, duplicate and non-PDF uploads give the specified errors.
  - **Container → host Ollama works with default Ollama settings on this Windows host.** A 1-rule audit completed in 85 s with a grounded finding, and Ask answered in 28 s with citations. `ollama ps` showed context 4096, confirming that `num-ctx` is applied.
  - The very first status probe timed out once (3 s probe while Ollama was cold); the next probe succeeded.
- **NOT VERIFIED:** the optional `LiveModelsSmokeTest` (`mvn -Plive-it verify`). The same capabilities were exercised through the Docker stack instead. A frontend build or test run on Node 20 locally (CI uses Node 20). A real OpenAI call (no key used).

## 17. Known limitations

- No authentication or multi-tenancy: demo-grade, not for untrusted or public access.
- No OCR. Scanned PDFs fail with `NO_EXTRACTABLE_TEXT`.
- No token streaming. Ask is a single blocking request (300 s client timeout).
- With a 3B CPU model, quality is limited and one judgement takes tens of seconds.
- Startup recovery assumes a single backend instance (see ARCHITECTURE.md).
- Spring AI's HNSW search with a jsonpath filter is a post-filter. For very selective
  filters, pgvector may return fewer than topK rows. At demo scale this does not happen.
  pgvector 0.8 `hnsw.iterative_scan` is the documented mitigation (future work).
