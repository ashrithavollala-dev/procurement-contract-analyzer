import type { AuditRunDetail, AuditRunSummary, StartAuditRequest, StartAuditResponse } from '../types/api';
import { http } from './client';

export async function startAudit(contractId: string, request: StartAuditRequest): Promise<StartAuditResponse> {
  // The Audit tab renders start errors (CONTRACT_NOT_READY, LLM_UNAVAILABLE...) inline.
  return (await http.post<StartAuditResponse>(`/contracts/${contractId}/audits`, request, { skipGlobalErrorToast: true }))
    .data;
}

export async function listAudits(contractId: string): Promise<AuditRunSummary[]> {
  return (await http.get<AuditRunSummary[]>(`/contracts/${contractId}/audits`)).data;
}

export async function getAudit(auditId: string): Promise<AuditRunDetail> {
  return (await http.get<AuditRunDetail>(`/audits/${auditId}`)).data;
}
