# Architecture

## 1. Components and boundaries

```
 Browser
   |  HTTP (same origin)
   v
 +--------------------------+        /api/v1 (JSON, multipart)        +------------------------------------+
 | frontend (Nginx)         | --------------------------------------> | backend (Spring Boot 3.5, Java 17) |
 | React 18 SPA, static     |   proxy_pass http://backend:8080        |  Controller -> Service -> Repository|
 +--------------------------+                                          |  @Async workers, AI agents         |
                                                                       +----+---------------+-----------+---+
                                                                            | JDBC (HikariCP)  | HTTP       | in-process
                                                                            v                  v            v
                                                               +--------------------+  +-------------+ +----------------+
                                                               | PostgreSQL 16 +    |  | Ollama on   | | ONNX embedding |
                                                               | pgvector (Compose) |  | the HOST    | | all-MiniLM-L6  |
                                                               | contracts, rules,  |  | llama3.2:3b | | (384 dims,     |
                                                               | audits, vector_store| | or OpenAI   | |  local CPU)    |
                                                               +--------------------+  +-------------+ +----------------+
```

- The frontend and the backend share nothing but the HTTP contract in `docs/API_CONTRACT.md`.
- Backend layering is strict. Controllers are HTTP adapters only: validation, status codes,
  DTOs. Services hold the use cases. Repositories (Spring Data JPA plus `VectorChunkRepository`
  for `vector_store`) hold the data access. Entities never leave the service layer: MapStruct
  maps them to DTO records.
- The schema is owned by Flyway (`ddl-auto=validate`, PgVectorStore `initialize-schema=false`).

## 2. Ingestion (asynchronous)

```
Client            ContractController   ContractServiceImpl            ContractLifecycleService  IngestionWorker(@Async)   IngestionPipeline
  | POST /contracts       |                     |                                |                       |                       |
  |---------------------->| upload(request) --->| validate size/type/%PDF-/pages |                       |                       |
  |                       |                     | SHA-256, duplicate check       |                       |                       |
  |                       |                     | createUploaded()  ------------>| tx1: INSERT UPLOADED  |                       |
  |                       |                     | store file {id}.pdf (disk)     |                       |                       |
  |                       |                     | markQueued() ----------------->| tx2: UPDATE QUEUED    |                       |
  |                       |                     | ingest(id) [proxy] ----------------------------------->| queued on             |
  |                       |                     |   (TaskRejected -> FAILED INGESTION_QUEUE_FULL, 503)   | ingestionExecutor     |
  |<-- 202 Accepted ------|<--------------------|                                |                       |                       |
  |                                                                              |                       |--- process(id) ------>|
  |                                                                              |<- tx3: QUEUED->PROCESSING (atomic CAS) -------|
  |                                                                              |   no transaction: read file, PDFBox per page,  |
  |                                                                              |   normalise, < min chars -> NO_EXTRACTABLE_TEXT|
  |                                                                              |   ClauseAwareChunker (O(n))                    |
  |                                                                              |   delete old vectors; embed+insert in batches  |
  |                                                                              |<- tx4: PROCESSING->READY (pages, chunks) ------|
  |                                                                              |   on failure: delete vectors, tx: FAILED+code  |
```

Key properties:
- **Commit before dispatch.** The worker only ever sees committed QUEUED rows. Dispatching
  inside a transaction would race with the commit.
- **No connection held across slow work.** PDF parsing and embedding run outside any
  transaction. PgVectorStore borrows a connection only for each batch INSERT.
- **Idempotency.** Vector ids are `UUID.nameUUIDFromBytes(contractId:chunkIndex)` and the
  INSERT is an UPSERT. Reprocess also deletes the contract's vectors first.
- **Bounded executor.** `ingestionExecutor` has core 2, max 4, queue 25 and `AbortPolicy`.
  The `@Async` method lives in a separate bean (`IngestionWorker`), so the call goes through
  the proxy; self-invocation would silently run synchronously.
- **Graceful shutdown.** `server.shutdown=graceful` lets HTTP requests finish. Executors wait
  up to `await-termination` for running jobs.
- **Recovery.** On `ApplicationReadyEvent`, `StartupRecovery` marks QUEUED/PROCESSING contracts
  and QUEUED/RUNNING audits as FAILED/INTERRUPTED. The executors' queues live in memory, so
  nothing would ever finish them. This assumes a single backend instance; see
  `RECOVERY_STUCK_THRESHOLD`.

## 3. Compliance audit agent (bounded plan-act-observe loop)

`AuditServiceImpl.start` checks the preconditions before anything is queued: contract READY,
rules resolved, LLM reachable. It then persists a QUEUED run and dispatches
`AuditWorker.run` to `auditExecutor` (core 1, max 1, queue 5), which returns HTTP 202. The
worker evaluates the rules one after another:

```
for each rule (per-rule Deadline = AGENT_RULE_TIMEOUT_SECONDS, own try/catch):
  iteration = 1 .. AGENT_MAX_ITERATIONS (2)
    RETRIEVE  vector search (question, topK 6, threshold, filter contractId), merged with the pool
    BUDGET    PromptBudget keeps the highest-scoring excerpts that fit num_ctx
    JUDGE     ChatClient call -> BeanOutputConverter<RuleJudgement> (Ollama: outputSchema)
              invalid output -> one retry -> NEEDS_REVIEW / LLM_OUTPUT_INVALID
    GROUND    GroundingValidator: cited ids exist, quote is a verbatim (normalised) substring
              of a cited excerpt, else NEEDS_REVIEW with confidence capped at 0.3
    keep best (evidence-backed beats NOT_FOUND, then higher confidence)
    OBSERVE   stop if status != NOT_FOUND and confidence >= 0.6
    REFLECT   QueryExpander gives a deterministic synonym query -> next iteration
  PERSIST     finding + completedRules++ in one short transaction (the UI polls progress)
  circuit breaker: 3 consecutive LLM_UNAVAILABLE -> run FAILED / LLM_UNAVAILABLE
finally: RiskScorer (deterministic) + AuditSummaryBuilder (counts only) -> COMPLETED
```

Hard limits: iterations per rule, one retry per judgement, the per-rule deadline (it covers
waiting for the LLM semaphore too), consecutive-failure abort, a single in-flight LLM call
(`LLM_MAX_CONCURRENT_CALLS`), and read-only retrieval. The model never decides the control
flow, never computes the score and never writes data.

## 4. Contract Q&A agent

- **rag mode (default).** Spring AI `RetrievalAugmentationAdvisor` with these parts:
  - `VectorStoreDocumentRetriever`: the contract filter is passed per request as
    `FILTER_EXPRESSION`. The default filter matches nothing.
  - A `PromptBudget` document post-processor.
  - A `ContextualQueryAugmenter` whose formatter wraps each excerpt in `<<<EXCERPT …>>>` /
    `<<<END …>>>` delimiters.

  Citations are taken from `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT`.
- **tools mode.** The user-controlled tool loop: `ToolCallingManager`,
  `internalToolExecutionEnabled=false`, at most `QA_MAX_TOOL_ROUNDS`. The read-only tools
  are `searchClauses`, `getContractInfo`, `getLatestAuditSummary` and `getFinding`. The contract
  id travels in the server-side `ToolContext`, which Spring AI never sends to the model. If the
  model rejects tools, the request falls back to rag once.
- In both modes, no retrieved chunk means `grounded=false` and the fixed answer
  "I cannot find this in the contract."

## 5. Connection-pool and executor sizing

| Consumer | Threads | DB connection use |
|---|---|---|
| Tomcat request threads | up to 200 (default) | short: one query or transaction per request; no transaction around uploads, asks or searches |
| `ingestionExecutor` | 2 core / 4 max | a few short transactions per job (status CAS, READY/FAILED) plus one per embedding batch insert |
| `auditExecutor` | 1 | one short transaction per finding, plus retrieval queries |
| `llm-call-*` (gateway) | 1 (`LLM_MAX_CONCURRENT_CALLS`) | none, except tools-mode lookups (short reads) |
| Startup recovery | 1, once | one transaction |

The pool is `HIKARI_MAX_POOL_SIZE=10`. Background work needs at most about 4 + 1 + 1 = 6
connections, and only at the same instant if every worker happens to be inside a statement at
once. That leaves 4 or more for HTTP traffic. Because no connection is held during PDF parsing,
embedding or LLM calls (which take seconds to minutes), connection hold times are
milliseconds. A small pool therefore serves many more threads, following the HikariCP sizing
guidance of `connections ≈ cores × 2 + spindles`. `connection-timeout=10s` makes pool
exhaustion fail fast instead of hanging. `leak-detection-threshold=20s` logs any connection
held longer, which would mean someone held one across slow work. Pool metrics
(`hikaricp.connections.active/idle/pending`) are exposed at `/actuator/metrics`.

## 6. Failure modes

| Failure | Behaviour |
|---|---|
| Ollama stopped or model not pulled | The backend starts. `/system/status` reports `llmReachable=false` and the UI shows a banner. Audit start and Ask return 503 `LLM_UNAVAILABLE`. Ingestion and search work. A running audit aborts after 3 consecutive connection failures. |
| Slow model | Per-rule deadline, then a NEEDS_REVIEW `LLM_TIMEOUT` finding. The run continues. Ask: 504 `LLM_TIMEOUT` after `QA_TIMEOUT_SECONDS`. |
| Invalid structured output | One retry, then NEEDS_REVIEW `LLM_OUTPUT_INVALID`. |
| Hallucinated quote or citation | GroundingValidator downgrades to NEEDS_REVIEW, caps confidence and explains why. |
| Scanned (image-only) PDF | FAILED `NO_EXTRACTABLE_TEXT`, no vectors. |
| Ingestion queue full | Contract FAILED `INGESTION_QUEUE_FULL`, HTTP 503. It can be reprocessed later. |
| Crash or restart mid-job | On the next start, the contract or audit is marked FAILED `INTERRUPTED`. |
| Failure during embedding | Partial vectors are deleted; FAILED with a sanitised message. |
| Delete: vector or file deletion fails | 500. The rows are kept and the delete can be retried, because each step is idempotent. |
| Embedding dimension mismatch | Startup fails with a clear message (`EmbeddingDimensionVerifier`). |
| Prompt injection inside contract text | Delimited as data, system prompt says so, marker sequences neutralised, read-only tools, schema plus grounding validation. |

## 7. Ports and run modes

| Service | Host port | Container port | Reached from containers as |
|---|---|---|---|
| PostgreSQL (pgvector) | 5434 | 5432 | `postgres:5432` |
| Backend | 8081 | 8080 | `backend:8080` |
| Frontend (Nginx) | 5175 | 80 | n/a |
| Vite dev server (host only) | 5176 | n/a | n/a |
| Ollama (host, not Compose) | 11434 | n/a | `host.docker.internal:11434` |

1. **Full Docker.** `docker compose up --build`. The browser uses http://localhost:5175. The
   backend reaches the database at `postgres:5432` and Ollama at `host.docker.internal:11434`.
2. **Host-run backend and/or frontend against the Docker database.** Run
   `docker compose up -d postgres`. Start the backend with `mvn spring-boot:run` (dev profile).
   It uses `localhost:5434` and Ollama on `localhost:11434`. Start the frontend with
   `npm run dev`; it serves http://localhost:5176 and proxies `/api` to `localhost:8081`.

The developer's own PostgreSQL on 5432 and any stack on 5433, 8080, 5173 or 5174 are never
used. Testcontainers starts its own database on a random port.
