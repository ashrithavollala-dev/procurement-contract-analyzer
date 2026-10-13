# PROGRESS (resume checklist; deleted at the end)

## Stages
- [x] 1. Compatibility gate: docs read, versions pinned, Ollama checks, TECHNICAL_DECISIONS.md, API_CONTRACT.md, file-tree plan, smoke tests
- [x] 2. Backend foundation: pom, yml profiles, Flyway V1/V2, entities, repositories, HikariCP, `mvn -U verify`
      (VERIFIED 2026-10-13: `mvn -B verify` BUILD SUCCESS, 10 tests: ModelSelectionSmokeTest 4, FlywayMigrationTest 6;
      context starts with ddl-auto=validate and PgVectorStore schema-validation=true)
- [x] 3. Ingestion: extractor, chunker, pipeline, executors, recovery, vector store config, dimension check
      (VERIFIED 2026-10-13: `mvn -B verify` BUILD SUCCESS, 53 tests incl. ClauseAwareChunkerTest 28,
      IngestionPipelineIntegrationTest 3 against Testcontainers pgvector. AuditPersistenceService and
      RuleEvaluation were written early because StartupRecovery needs them.)
      Remaining for stage 4: ContractService upload/dispatch (TaskRejectedException -> INGESTION_QUEUE_FULL),
      delete/reprocess, LlmAvailability, all AI classes, DTOs, mappers, controllers, GlobalExceptionHandler.
- [x] 4. AI layer and API: prompts, provider switching, agents, grounding, risk scorer, services, DTOs, mappers, controllers, errors
      (VERIFIED 2026-10-13: compiles; full test-profile context with all beans starts; `mvn -B verify` 53 tests green.
      Behavioural tests for these classes are stage 5.)
- [ ] 5. Backend tests and SamplePdfGenerator (+ committed sample PDFs)
- [ ] 6. Frontend core, features, tests
- [ ] 7. Docker, nginx, compose, CI, READMEs, INTERVIEW_NOTES.md
- [ ] 8. Cross-layer validation, docker smoke test, ZIP

## File-tree plan
```
backend/src/main/java/com/company/contractanalyzer/
  ContractAnalyzerApplication
  config/      AppProperties, AsyncConfig, JpaAuditingConfig, OpenApiConfig, WebConfig, ClockConfig
  controller/  ContractController, SearchController, AuditController, AskController,
               ComplianceRuleController, DashboardController, SystemController
  dto/request  UploadContractRequest, SemanticSearchRequest, StartAuditRequest, AskRequest,
               CreateRuleRequest, UpdateRuleRequest, RuleActiveRequest
  dto/response ContractResponse, PageResponse, SearchResponse, SearchResultItem, StartAuditResponse,
               AuditRunSummaryResponse, AuditRunDetailResponse, FindingResponse, AskResponse,
               CitationResponse, RuleResponse, DashboardSummaryResponse, RuleViolationStat,
               SystemStatusResponse, ErrorResponse
  entity/      AuditableEntity, Contract, ComplianceRule, AuditRun, AuditFinding
  enums/       ContractStatus, AuditStatus, FindingStatus, Severity, RiskBand, ErrorCode, QaMode
  exception/   ApiException, ResourceNotFoundException, LlmUnavailableException, LlmTimeoutException, GlobalExceptionHandler
  mapper/      ContractMapper, ComplianceRuleMapper, AuditMapper
  repository/  ContractRepository, ComplianceRuleRepository, AuditRunRepository, AuditFindingRepository, VectorChunkRepository
  service/     ContractService(+Impl), ContractLifecycleService, FileStorageService(+Impl), SearchService(+Impl),
               AuditService(+Impl), AuditPersistenceService, AuditWorker, AskService(+Impl),
               ComplianceRuleService(+Impl), DashboardService(+Impl), SystemStatusService(+Impl)
  ingestion/   PdfInspector, PdfTextExtractor, PageText, TextNormalizer, TokenEstimator, ClauseAwareChunker,
               Chunk, ChunkingOptions, IngestionPipeline, IngestionWorker, IngestionException,
               StartupRecovery, EmbeddingDimensionVerifier
  ai/          ChatClientConfig, PromptLoader, LlmGateway, LlmErrorClassifier, ContractRetriever, Excerpt,
               PromptBudget, QueryExpander, RuleJudgement, JudgementOptionsFactory, ComplianceAgent,
               RuleEvaluation, GroundingValidator, RiskScorer, AuditSummaryBuilder, ContractQaAgent,
               QaAnswer, ContractTools
  health/      LlmStatusService, LlmHealthIndicator, EmbeddingHealthIndicator
backend/src/main/resources/ application.yml, application-dev.yml, application-prod.yml,
  db/migration/V1__init_core_tables.sql, V2__seed_compliance_rules.sql, prompts/*.st
backend/src/test/... support (StubChatModel, FakeEmbeddingModel, TestModelConfig, PostgresContainerSupport),
  unit tests, integration tests, SamplePdfGenerator
frontend/ (Vite) src/api, layouts, features/*, notifications, types, components, tests
```

## Notes for resume
- Do NOT write Java/SQL via bash heredocs: the Bash tool strips backslashes. Use the Write tool.
- Test doubles live in backend/src/test/.../support (StubChatModel, FakeEmbeddingModel, TestModelConfig,
  TestcontainersConfig, AbstractIntegrationTest). Planned: LlmAvailability interface so tests can control reachability.
- Ollama checks done (0.40.3, llama3.2:3b, tool call probe OK) — see docs/TECHNICAL_DECISIONS.md section 13.
