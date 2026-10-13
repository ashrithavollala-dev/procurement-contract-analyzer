# Backend: Spring Boot 3.5 + Spring AI 1.1

A self-contained Maven project. It talks to the frontend only through the HTTP API in
`../docs/API_CONTRACT.md`.

## Pinned versions

| Component | Version | Managed by |
|---|---|---|
| Java | 17 | `maven.compiler.release` |
| Spring Boot | 3.5.16 | parent POM |
| Spring AI | 1.1.8 | `spring-ai-bom` (single BOM) |
| Apache PDFBox | 3.0.8 | explicit |
| MapStruct | 1.6.3 (+ lombok-mapstruct-binding 0.2.0) | explicit |
| springdoc-openapi | 2.9.1 | explicit |
| Flyway 11.7.2, PostgreSQL JDBC 42.7.11, HikariCP 6.3.3, Lombok 1.18.46, Testcontainers 1.21.4, Awaitility 4.2.2, Mockito 5.17.0 | | Spring Boot |

Rationale and evidence: `../docs/TECHNICAL_DECISIONS.md`. Source encoding is forced to UTF-8,
because the Windows default (Cp1252) would otherwise apply.

## Commands

```bash
mvn verify                 # compile + all tests (needs a running Docker engine for Testcontainers)
mvn spring-boot:run        # dev profile: DB on localhost:5434 (start it with: docker compose up -d postgres)
mvn -DskipTests package    # build target/contract-analyzer-backend-1.0.0.jar
mvn -Plive-it verify       # OPTIONAL real-model smoke test (see below)
```

Profiles:
- `dev` (default): host run, readable logs.
- `prod`: container; ECS JSON logs; DB at `postgres:5432`.
- `test`: in `src/test/resources`, with no real models.

Swagger UI: http://localhost:8081/swagger-ui.html in Docker, or :8080 for a plain host run.

## Package layout (`com.company.contractanalyzer`)

| Package | Contents |
|---|---|
| `controller` | HTTP adapters only (validation, status codes, DTOs) |
| `service` (+ `impl`) | use cases; `ContractLifecycleService` and `AuditPersistenceService` hold the short transactions; `AuditWorker` is the `@Async` audit |
| `repository` | Spring Data JPA repositories, specifications, projections, `VectorChunkRepository` (PgVectorStore + native counts) |
| `entity`, `enums`, `dto/request`, `dto/response`, `mapper` | JPA model, enums, request/response records, MapStruct interfaces |
| `ingestion` | `PdfInspector`, `PdfTextExtractor`, `TextNormalizer`, `TokenEstimator`, `ClauseAwareChunker`, `IngestionPipeline`, `IngestionWorker` (`@Async`), `StartupRecovery`, `EmbeddingDimensionVerifier` |
| `ai` | `ChatClientConfig`, `PromptLoader` (`prompts/*.st`), `LlmGateway` (semaphore and deadline), `ComplianceAgent`, `ContractQaAgent`, `ContractTools`, `GroundingValidator`, `RiskScorer`, `PromptBudget`, `QueryExpander` |
| `health` | LLM reachability probe and Actuator indicators |
| `config` | `AppProperties`, executors, clock and JPA auditing, OpenAPI, CORS |
| `exception` | `ApiException` family + `GlobalExceptionHandler` (uniform error payload) |

## Tests

There are 173 tests, and no test needs Ollama, an API key or the ONNX model download.
`TestModelConfig` provides `StubChatModel` (scripted replies) and `FakeEmbeddingModel` (a
deterministic hashed bag of words, 384 dims) as `@Primary` beans. Real model
auto-configuration is disabled in the `test` profile.

- **Unit:**
  - `ClauseAwareChunkerTest`, `TextNormalizerTest`, `PdfTextExtractorTest`
  - `RiskScorerTest`, `GroundingValidatorTest`, `PromptBudgetTest`, `QueryExpanderTest`
  - `LlmGatewayTest`, `LlmStatusServiceTest`, `EmbeddingDimensionVerifierTest`
  - `ComplianceAgentTest`, `ContractQaAgentTest`, `AuditWorkerTest`, `IngestionPipelineTest`
  - `ModelSelectionSmokeTest`: offline Spring AI model selection
- **Integration (Testcontainers `pgvector/pgvector:pg16` on a random port, never 5432 or 5434):**
  - `FlywayMigrationTest`, `IngestionPipelineIntegrationTest`
  - `ContractApiIntegrationTest`, `SearchApiIntegrationTest`, `AuditApiIntegrationTest`,
    `AskApiIntegrationTest`, `ComplianceRuleApiIntegrationTest`, `ErrorPayloadIntegrationTest`
  - `IngestionQueueSaturationIntegrationTest`, `OfflineStartupIntegrationTest`,
    `OpenApiDocumentationTest`

**Docker is required** for the integration tests. The first run pulls the
`pgvector/pgvector:pg16` image.

### Optional real-model smoke test

`LiveModelsSmokeTest` (`@Tag("live")`) is excluded from `mvn verify` and CI. Run it with
`mvn -Plive-it verify`. It needs Docker and internet access (or a warm cache in
`~/.contract-analyzer/onnx-cache`) to download the ONNX embedding model. It checks:
- exactly one `TransformersEmbeddingModel` emits 384-dimensional vectors;
- ingestion and search work with real embeddings;
- if Ollama is reachable, one real rule judgement completes (otherwise this step is skipped).

## Sample PDFs

`src/test/java/.../support/SamplePdfGenerator.java` creates the fictional samples. To
regenerate `../docs/samples`:

```bash
mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
# Windows (Git Bash) uses ';' as the separator; Linux/macOS use ':'
java -Djava.awt.headless=true -cp "target/test-classes;target/classes;$(cat target/cp.txt)" \
  com.company.contractanalyzer.support.SamplePdfGenerator ../docs/samples
```

## Docker

`Dockerfile` has two stages:
- build: `maven:3.9.11-eclipse-temurin-17`, `-DskipTests`, because the tests need a Docker
  engine and run in CI;
- runtime: `eclipse-temurin:17-jre` (Ubuntu/glibc for the ONNX native libraries), a non-root
  user, a healthcheck on `/actuator/health/liveness`, and
  `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60`.

It is normally built through the root `docker-compose.yml`.
