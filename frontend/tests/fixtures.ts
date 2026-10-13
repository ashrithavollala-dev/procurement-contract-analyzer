import type {
  AuditRunDetail,
  Contract,
  DashboardSummary,
  Finding,
  PageResponse,
  Rule,
  SystemStatus,
} from '../src/types/api';

export const API = 'http://localhost/api/v1';

export function contract(overrides: Partial<Contract> = {}): Contract {
  return {
    id: '6f1c1f9e-2c7a-4b5e-9d55-0d8e4a2c1b11',
    title: 'Master Services Agreement',
    vendorName: 'Brightwater Logistics Ltd (fictional)',
    originalFilename: 'contract-wellformed.pdf',
    contentHash: 'a'.repeat(64),
    fileSizeBytes: 4675,
    pageCount: 3,
    chunkCount: 21,
    status: 'READY',
    errorCode: null,
    errorMessage: null,
    processedAt: '2026-10-13T09:16:02.511Z',
    createdAt: '2026-10-13T09:15:30.123Z',
    updatedAt: '2026-10-13T09:16:02.511Z',
    ...overrides,
  };
}

export function page<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0 };
}

export function finding(overrides: Partial<Finding> = {}): Finding {
  return {
    id: 1,
    ruleCode: 'LIABILITY_CAP',
    ruleTitle: 'Limitation of liability cap',
    severity: 'CRITICAL',
    mandatory: true,
    status: 'COMPLIANT',
    confidence: 0.86,
    explanation: 'Clause 8.1 caps aggregate liability at twelve months of fees.',
    evidenceQuote: 'shall not exceed the total fees paid or payable',
    evidencePage: 3,
    recommendation: 'No action required.',
    iterations: 1,
    queriesUsed: ['Does the contract cap the supplier total aggregate liability?'],
    errorCode: null,
    ...overrides,
  };
}

export function audit(overrides: Partial<AuditRunDetail> = {}): AuditRunDetail {
  return {
    id: 'b0c4e9a2-1d2f-4c55-8e61-2f0b6a9d3c77',
    contractId: contract().id,
    contractTitle: contract().title,
    status: 'COMPLETED',
    totalRules: 3,
    completedRules: 3,
    riskScore: 33.3,
    riskBand: 'MEDIUM',
    summary: '3 rules evaluated: 2 compliant, 0 partial, 1 non-compliant, 0 not found, 0 need review. Risk score 33.3 (MEDIUM).',
    errorCode: null,
    startedAt: '2026-10-13T09:20:00.000Z',
    completedAt: '2026-10-13T09:22:41.000Z',
    createdAt: '2026-10-13T09:19:59.000Z',
    findings: [],
    ...overrides,
  };
}

export const rules: Rule[] = [
  {
    id: 3,
    code: 'LIABILITY_CAP',
    title: 'Limitation of liability cap',
    question: 'Does the contract cap the supplier total aggregate liability?',
    guidance: 'Compliant: an explicit cap with carve-outs.',
    severity: 'CRITICAL',
    mandatory: true,
    active: true,
    createdAt: '2026-10-13T09:00:00.000Z',
    updatedAt: '2026-10-13T09:00:00.000Z',
  },
];

export const systemStatusOk: SystemStatus = {
  llmProvider: 'ollama',
  llmModel: 'llama3.2:3b',
  llmReachable: true,
  llmDetail: 'Ollama reachable at http://localhost:11434',
  qaMode: 'rag',
  embeddingModel: 'sentence-transformers/all-MiniLM-L6-v2 (ONNX)',
  embeddingDimension: 384,
  checkedAt: '2026-10-13T09:15:30.123Z',
};

export const dashboard: DashboardSummary = {
  totalContracts: 1,
  contractsByStatus: { UPLOADED: 0, QUEUED: 0, PROCESSING: 0, READY: 1, FAILED: 0 },
  totalAudits: 0,
  averageRiskScore: null,
  recentAudits: [],
  topNonCompliantRules: [],
};

export function errorBody(status: number, code: string, message: string, details?: Record<string, unknown>) {
  return { timestamp: '2026-10-13T09:15:30.123Z', status, error: 'Error', code, message, path: '/api/v1', details };
}
