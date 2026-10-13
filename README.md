# Agentic Procurement Contract Analyzer

Upload multi-page procurement and vendor contract PDFs. The system ingests them
asynchronously: per-page text extraction, clause-aware chunking, local embeddings, and
storage in PostgreSQL with pgvector. You can then:

1. **Search** clauses semantically across contracts. No LLM is needed.
2. **Audit** a contract against a configurable rule set. A bounded agent produces grounded
   findings with page citations, and a deterministic risk score is computed from them.
3. **Ask** grounded questions about one contract and get answers with server-side citations.

Stack: Java 17, Spring Boot 3.5.16, Spring AI 1.1.8, PostgreSQL 16 + pgvector, Flyway, React 18
+ TypeScript + Vite, Docker Compose. All data is fictional.

> **Demo-grade, not production-secure.** There is no authentication and no multi-tenancy.
> Do not expose this application to untrusted networks or the public internet. It is built
> with production-style engineering practices for a portfolio and interview setting.

## Contents
- [Architecture](#architecture) · [Prerequisites](#prerequisites) · [Ports](#ports) · [Quick start (Docker)](#quick-start-full-docker)
- [LLM setup (Ollama / OpenAI)](#llm-setup) · [Run parts separately](#run-backend-and-frontend-separately) · [Environment variables](#environment-variables)
- [Migrations](#database-migrations) · [Testing](#testing) · [Demo walkthrough](#demo-walkthrough) · [Limitations](#known-limitations) · [Verified facts vs assumptions](#verified-facts-vs-assumptions)

More detail: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/API_CONTRACT.md](docs/API_CONTRACT.md),
[docs/TECHNICAL_DECISIONS.md](docs/TECHNICAL_DECISIONS.md), [INTERVIEW_NOTES.md](INTERVIEW_NOTES.md),
[backend/README.md](backend/README.md), [frontend/README.md](frontend/README.md).

## Architecture

```
Browser -> frontend (Nginx :80, React SPA) --/api--> backend (Spring Boot :8080)
                                                      |-- PostgreSQL 16 + pgvector (Compose service "postgres")
                                                      |-- local ONNX embeddings (all-MiniLM-L6-v2, 384 dims, in-process)
                                                      '-- chat LLM: Ollama on the host (llama3.2:3b) or OpenAI
RAG pipeline: PDF -> PDFBox per page -> ClauseAwareChunker -> embed -> pgvector (HNSW, cosine)
              -> contract-filtered retrieval -> prompt budget -> LLM (structured output)
              -> grounding validation -> findings / answers with page citations
```

"Agentic" here means **bounded plan-act-observe loops**. The orchestration is deterministic
Java code. The LLM is used only for judgement, through validated structured output. Every
loop has hard limits: iterations, timeouts, tool-call rounds, and a circuit breaker. All tools
are read-only, and the model has no unbounded autonomy. See
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

The backend (`backend/`) and frontend (`frontend/`) are independent projects. They
communicate only through the HTTP API in [docs/API_CONTRACT.md](docs/API_CONTRACT.md).

## Prerequisites

| Tool | Version used for validation | Needed for |
|---|---|---|
| JDK | 17 (Temurin 17.0.20) | backend build/run |
| Maven | 3.9+ (validated with 3.10.0) | backend build/tests |
| Node.js / npm | Node 20 or newer (validated with 24.17 / npm 11.13; CI uses 20) | frontend |
| Docker Desktop | validated with Engine 29.8 | Compose stack, Testcontainers tests |
| Ollama (optional) | validated with 0.40.3 | local chat model for audits and Q&A |

## Ports

| Service | Host port | Container port | Notes |
|---|---|---|---|
| PostgreSQL (pgvector) | **5434** | 5432 | this project's own database; your local PostgreSQL on 5432 is never touched |
| Backend (Spring Boot) | **8081** | 8080 | `/api/v1`, `/actuator`, `/swagger-ui.html` |
| Frontend (Nginx) | **5175** | 80 | serves the SPA, proxies `/api` to `backend:8080` |
| Vite dev server | **5176** | - | `npm run dev` only (strictPort) |
| Ollama | 11434 | - | runs on the host, not in Compose |

These avoid 5432, 5433, 8080, 5173 and 5174, which other local projects may use. Override
the host ports in `.env`.

## Quick start (full Docker)

```bash
cp .env.example .env            # optional: every value has a default
ollama pull llama3.2:3b         # optional: only needed for audits and Q&A
docker compose up --build -d
docker compose ps               # wait until backend is "healthy"
```

Open http://localhost:5175. Other URLs:
- API docs: http://localhost:8081/swagger-ui.html
- Health: http://localhost:8081/actuator/health

**First start downloads the embedding model** (all-MiniLM-L6-v2, about 80 MB). This needs
internet access once; the model is cached in the `onnx-cache` volume. The backend healthcheck
allows a 5-minute start period for it.

Stop with `docker compose down`. Add `-v` only if you want to delete the database, uploads
and model cache volumes.

Inside the Compose network, the backend connects to PostgreSQL as `postgres:5432` and to
Ollama as `host.docker.internal:11434` (through `extra_hosts: host-gateway`).

## LLM setup

The chat provider is selected by `LLM_PROVIDER`. No code or prompt changes are needed.
Embeddings are **always local** (ONNX), so switching the chat provider never invalidates
stored vectors.

### Ollama (default, free, local)

```bash
ollama pull llama3.2:3b          # about 2 GB download
ollama list                      # should list llama3.2:3b
ollama run llama3.2:3b "Say hello in five words."   # sanity check; /bye to exit
```

| Variable | Default | Meaning |
|---|---|---|
| `LLM_PROVIDER` | `ollama` | |
| `OLLAMA_BASE_URL` | `http://localhost:11434` (host run) / `http://host.docker.internal:11434` (Docker) | |
| `OLLAMA_CHAT_MODEL` | `llama3.2:3b` | larger models (for example `llama3.1:8b`) are optional, for machines with more RAM |
| `OLLAMA_NUM_CTX` | `4096` | context window; prompts are budgeted to fit it |
| `OLLAMA_READ_TIMEOUT_SECONDS` | `300` | CPU inference is slow; the first call also loads the model |
| `OLLAMA_KEEP_ALIVE` | `10m` | how long Ollama keeps the model loaded |
| `LLM_MAX_CONCURRENT_CALLS` | `1` | at most one in-flight LLM call |

If the backend container cannot reach Ollama, check that Ollama listens on all interfaces,
not just loopback. On Windows, set a user environment variable `OLLAMA_HOST=0.0.0.0` and
restart the Ollama app. On Linux, run `systemctl edit ollama` and add
`Environment="OLLAMA_HOST=0.0.0.0"`. Afterwards, http://localhost:8081/api/v1/system/status
should report `"llmReachable": true`.

**Low-RAM guidance (about 8 GB machines).** Docker Desktop, this stack, Ollama with a 3B model
and another project's stack running together can cause memory pressure. Options you control:
- close heavy applications;
- run `ollama stop llama3.2:3b` when you are finished;
- lower `BACKEND_MEM_LIMIT` or `JAVA_MAX_RAM_PERCENTAGE` in `.env`;
- stop other stacks yourself, if you decide that is safe;
- optionally cap WSL2 memory in `%UserProfile%\.wslconfig` (advice only; this project never
  edits files outside its folder).

**Expectations.** With a 3B model on a CPU-only laptop, one rule judgement takes tens of
seconds. A 12-rule audit can take many minutes. Start with the **quick demo audit (3 rules)**.
A 3B model gives limited quality: expect more NEEDS_REVIEW findings and weaker explanations.
OpenAI is the documented option for a stronger demo.

### OpenAI

```bash
# in .env (never commit it):
LLM_PROVIDER=openai
OPENAI_API_KEY=<your key>
OPENAI_CHAT_MODEL=<a chat model your account can use, e.g. gpt-4o-mini>
```

The key is read only from the environment and is never logged. Without a key the backend
still starts; LLM endpoints answer 503 `LLM_UNAVAILABLE`. For models that reject
`temperature`, set `OPENAI_TEMPERATURE=` (empty).

## Run backend and frontend separately

```bash
docker compose up -d postgres            # database only, on localhost:5434

cd backend
mvn spring-boot:run                      # dev profile: localhost:5434 + Ollama on localhost:11434

cd frontend
npm ci
npm run dev                              # http://localhost:5176, proxies /api -> http://localhost:8081
```

A host-run backend listens on 8080 by default. To match the Docker port, use
`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`, or stop the Docker
backend first with `docker compose stop backend`. See [backend/README.md](backend/README.md) and
[frontend/README.md](frontend/README.md).

## Environment variables

All variables are listed with defaults and comments in [.env.example](.env.example). The main
groups are:
- ports;
- database (`DB_*`, PostgreSQL tuning);
- HikariCP (`HIKARI_*`);
- uploads (`UPLOAD_*`);
- LLM (`LLM_PROVIDER`, `OLLAMA_*`, `OPENAI_*`, `QA_MODE`);
- executors (`INGESTION_*`, `AUDIT_*`);
- chunking (`CHUNK_*`);
- agent limits (`AGENT_*`);
- recovery;
- container memory limits.

## Database migrations

Flyway owns the schema (Hibernate runs with `ddl-auto=validate`). Migrations run
automatically at backend startup:
- `V1__init_core_tables.sql`: the pgvector extension, tables, foreign keys (audit data
  cascades on contract delete), CHECK constraints, and `vector_store` with
  `vector(384)`, an HNSW cosine index, a btree index on `metadata->>'contractId'`, and a
  GIN jsonpath index.
- `V2__seed_compliance_rules.sql`: 12 procurement rules.

## Testing

```bash
cd backend && mvn verify     # 171 tests; integration tests need a running Docker engine (Testcontainers)
cd frontend && npm test      # Vitest + React Testing Library + MSW (20 tests)
```

No test needs network access (beyond pulling the pgvector test image), Ollama, an API key or
the ONNX model. Tests use `StubChatModel` and `FakeEmbeddingModel`. The optional real-model
smoke test runs with `mvn -Plive-it verify` (see [backend/README.md](backend/README.md)). CI
(`.github/workflows/ci.yml`) runs a backend job (JDK 17) and a frontend job (Node 20).

## Demo walkthrough

The sample PDFs in `docs/samples/` are generated by `SamplePdfGenerator`:
- `contract-wellformed.pdf`: covers all 12 rules.
- `contract-weak.pdf`: has no liability cap, no audit rights and no data protection clause,
  and has weak payment and renewal terms.
- `contract-scanned-image-only.pdf`: image-only, with no extractable text.

**UI:** http://localhost:5175 → Upload → choose `contract-wellformed.pdf` → the detail page
polls until READY. Then:
- **Search**: query "limitation of liability cap".
- **Audit tab**: **Quick demo (3 rules)**, then watch progress, the risk gauge and the
  findings. Expand a finding to see the verbatim evidence and its page.
- **Ask tab**: "How much notice is needed to terminate for convenience?"
- Upload `contract-weak.pdf` and run the same 3-rule audit. Expect NOT_FOUND findings and a
  high risk score.
- Upload the scanned PDF. Expect FAILED with `NO_EXTRACTABLE_TEXT`.

**curl** (bash; on Windows PowerShell use `curl.exe`):

```bash
API=http://localhost:8081/api/v1
# Upload (202 Accepted, status QUEUED)
curl -s -F "file=@docs/samples/contract-wellformed.pdf;type=application/pdf" -F "vendorName=Brightwater Logistics (fictional)" $API/contracts
# Poll status (replace ID)
curl -s $API/contracts/ID
# Semantic search (no LLM needed)
curl -s -H "Content-Type: application/json" -d '{"query":"limitation of liability cap","topK":3}' $API/search
# Quick 3-rule audit (202 with auditId), then poll it
curl -s -H "Content-Type: application/json" -d '{"ruleCodes":["LIABILITY_CAP","AUDIT_RIGHTS","DATA_PROTECTION"]}' $API/contracts/ID/audits
curl -s $API/audits/AUDIT_ID
# Grounded question
curl -s -H "Content-Type: application/json" -d '{"question":"Which law governs the agreement?"}' $API/contracts/ID/ask
# Status
curl -s $API/system/status
```

## Known limitations

- **Out of scope (not built):** authentication and multi-tenancy, OCR for scanned PDFs, token
  streaming, non-PDF formats.
- **Demo-grade:** there is no authentication. Do not expose to untrusted networks or the
  public internet.
- **Local-model quality:** a 3B model on CPU is slow (tens of seconds per judgement) and less
  accurate. Grounding validation turns unverifiable claims into NEEDS_REVIEW instead of
  hiding them.
- **Single backend instance:** startup recovery assumes no other instance is processing.
- **HNSW and filters:** PgVectorStore applies the contract filter after the approximate
  search. At demo scale this does not matter; see TECHNICAL_DECISIONS.md.

## Verified facts vs assumptions

| Statement | Status |
|---|---|
| Spring Boot 3.5.16 + Spring AI 1.1.8 + Java 17 compile and pass `mvn verify` (171 tests) | VERIFIED (local run, 2026-10-13) |
| All Spring AI artifacts and properties used exist in 1.1.8 | VERIFIED (BOM, docs and source jars; see TECHNICAL_DECISIONS.md) |
| Context starts with no `OPENAI_API_KEY` and nothing listening on the Ollama URL | VERIFIED (`OfflineStartupIntegrationTest`, `ModelSelectionSmokeTest`) |
| Ollama 0.40.3 with llama3.2:3b emits a structured tool call for a trivial tool | VERIFIED (one probe; see TECHNICAL_DECISIONS.md §13) |
| llama3.2:3b calls tools reliably for contract Q&A | NOT VERIFIED (so `QA_MODE` defaults to `rag`) |
| Frontend lint, typecheck, 20 tests and build pass on Node 24 | VERIFIED |
| Frontend on Node 20 | NOT VERIFIED locally (the CI job uses Node 20) |
| Docker Compose startup and smoke test | see the final report of the build session |
| HTTP read timeout via `spring.http.client.read-timeout` applies to Spring AI's Ollama client | VERIFIED from source (`OllamaApiAutoConfiguration` uses the Boot `RestClient.Builder`); not measured with a real slow call |
| Interrupting a timed-out call stops the underlying JDK HTTP request | ASSUMPTION |
