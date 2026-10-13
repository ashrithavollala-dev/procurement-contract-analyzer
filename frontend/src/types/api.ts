// Mirrors docs/API_CONTRACT.md exactly. Enums travel as raw uppercase strings; ids are UUID
// strings (contracts, audits) or numbers (rules, findings). Timestamps are ISO-8601 strings.

export const CONTRACT_STATUSES = ['UPLOADED', 'QUEUED', 'PROCESSING', 'READY', 'FAILED'] as const;
export type ContractStatus = (typeof CONTRACT_STATUSES)[number];

export const AUDIT_STATUSES = ['QUEUED', 'RUNNING', 'COMPLETED', 'FAILED'] as const;
export type AuditStatus = (typeof AUDIT_STATUSES)[number];

export const FINDING_STATUSES = ['COMPLIANT', 'PARTIAL', 'NON_COMPLIANT', 'NOT_FOUND', 'NEEDS_REVIEW'] as const;
export type FindingStatus = (typeof FINDING_STATUSES)[number];

export const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;
export type Severity = (typeof SEVERITIES)[number];
export type RiskBand = Severity;

export type QaMode = 'rag' | 'tools';
export type LlmProvider = 'ollama' | 'openai';

export const ERROR_CODES = [
  'RESOURCE_NOT_FOUND',
  'VALIDATION_FAILED',
  'UNSUPPORTED_FILE_TYPE',
  'FILE_TOO_LARGE',
  'DUPLICATE_CONTRACT',
  'CONTRACT_NOT_READY',
  'NO_ACTIVE_RULES',
  'NO_EXTRACTABLE_TEXT',
  'INGESTION_QUEUE_FULL',
  'AUDIT_QUEUE_FULL',
  'INTERRUPTED',
  'LLM_UNAVAILABLE',
  'LLM_TIMEOUT',
  'LLM_OUTPUT_INVALID',
  'INTERNAL_ERROR',
] as const;
export type ErrorCode = (typeof ERROR_CODES)[number];

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  code: ErrorCode;
  message: string;
  path: string;
  details?: Record<string, unknown>;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Contract {
  id: string;
  title: string;
  vendorName: string | null;
  originalFilename: string;
  contentHash: string;
  fileSizeBytes: number;
  pageCount: number | null;
  chunkCount: number | null;
  status: ContractStatus;
  errorCode: ErrorCode | null;
  errorMessage: string | null;
  processedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ContractListParams {
  status?: ContractStatus;
  q?: string;
  page?: number;
  size?: number;
}

export interface SearchRequest {
  query: string;
  contractId?: string | null;
  topK?: number;
  minScore?: number;
}

export interface SearchResultItem {
  contractId: string;
  contractTitle: string;
  page: number;
  chunkIndex: number;
  heading: string | null;
  snippet: string;
  score: number;
}

export interface SearchResponse {
  query: string;
  results: SearchResultItem[];
}

export interface StartAuditRequest {
  ruleCodes?: string[];
}

export interface StartAuditResponse {
  auditId: string;
  status: AuditStatus;
}

export interface AuditRunSummary {
  id: string;
  contractId: string;
  contractTitle: string;
  status: AuditStatus;
  totalRules: number;
  completedRules: number;
  riskScore: number | null;
  riskBand: RiskBand | null;
  summary: string | null;
  errorCode: ErrorCode | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export interface Finding {
  id: number;
  ruleCode: string;
  ruleTitle: string;
  severity: Severity;
  mandatory: boolean;
  status: FindingStatus;
  confidence: number;
  explanation: string | null;
  evidenceQuote: string | null;
  evidencePage: number | null;
  recommendation: string | null;
  iterations: number;
  queriesUsed: string[];
  errorCode: ErrorCode | null;
}

export interface AuditRunDetail extends AuditRunSummary {
  findings: Finding[];
}

export interface AskRequest {
  question: string;
}

export interface Citation {
  page: number;
  chunkIndex: number;
  snippet: string;
  score: number;
}

export interface AskResponse {
  answer: string;
  grounded: boolean;
  mode: QaMode;
  citations: Citation[];
}

export interface Rule {
  id: number;
  code: string;
  title: string;
  question: string;
  guidance: string;
  severity: Severity;
  mandatory: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRuleRequest {
  code: string;
  title: string;
  question: string;
  guidance: string;
  severity: Severity;
  mandatory: boolean;
  active?: boolean;
}

export interface UpdateRuleRequest {
  title: string;
  question: string;
  guidance: string;
  severity: Severity;
  mandatory: boolean;
  active: boolean;
}

export interface RuleActiveRequest {
  active: boolean;
}

export interface RuleViolationStat {
  ruleCode: string;
  ruleTitle: string;
  severity: Severity;
  count: number;
}

export interface DashboardSummary {
  totalContracts: number;
  contractsByStatus: Record<ContractStatus, number>;
  totalAudits: number;
  averageRiskScore: number | null;
  recentAudits: AuditRunSummary[];
  topNonCompliantRules: RuleViolationStat[];
}

export interface SystemStatus {
  llmProvider: LlmProvider | string;
  llmModel: string;
  llmReachable: boolean;
  llmDetail: string;
  qaMode: QaMode;
  embeddingModel: string;
  embeddingDimension: number;
  checkedAt: string;
}
