import type { AuditStatus, ContractStatus, FindingStatus, RiskBand, Severity } from './api';

// Friendly UI labels for the raw enum strings used on the wire.

export const CONTRACT_STATUS_LABELS: Record<ContractStatus, string> = {
  UPLOADED: 'Uploaded',
  QUEUED: 'Queued',
  PROCESSING: 'Processing',
  READY: 'Ready',
  FAILED: 'Failed',
};

export const AUDIT_STATUS_LABELS: Record<AuditStatus, string> = {
  QUEUED: 'Queued',
  RUNNING: 'Running',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
};

export const FINDING_STATUS_LABELS: Record<FindingStatus, string> = {
  COMPLIANT: 'Compliant',
  PARTIAL: 'Partial',
  NON_COMPLIANT: 'Non-compliant',
  NOT_FOUND: 'Not found',
  NEEDS_REVIEW: 'Needs review',
};

export const SEVERITY_LABELS: Record<Severity, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
};

export const RISK_BAND_LABELS: Record<RiskBand, string> = {
  LOW: 'Low risk',
  MEDIUM: 'Medium risk',
  HIGH: 'High risk',
  CRITICAL: 'Critical risk',
};

export type Tone = 'ok' | 'warn' | 'danger' | 'info' | 'neutral';

export const CONTRACT_STATUS_TONE: Record<ContractStatus, Tone> = {
  UPLOADED: 'neutral',
  QUEUED: 'info',
  PROCESSING: 'info',
  READY: 'ok',
  FAILED: 'danger',
};

export const AUDIT_STATUS_TONE: Record<AuditStatus, Tone> = {
  QUEUED: 'info',
  RUNNING: 'info',
  COMPLETED: 'ok',
  FAILED: 'danger',
};

export const FINDING_STATUS_TONE: Record<FindingStatus, Tone> = {
  COMPLIANT: 'ok',
  PARTIAL: 'warn',
  NON_COMPLIANT: 'danger',
  NOT_FOUND: 'danger',
  NEEDS_REVIEW: 'warn',
};

export const SEVERITY_TONE: Record<Severity, Tone> = {
  LOW: 'neutral',
  MEDIUM: 'info',
  HIGH: 'warn',
  CRITICAL: 'danger',
};

export function isContractInFlight(status: ContractStatus): boolean {
  return status === 'QUEUED' || status === 'PROCESSING';
}

export function isAuditInFlight(status: AuditStatus): boolean {
  return status === 'QUEUED' || status === 'RUNNING';
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString();
}

export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return minutes > 0 ? `${minutes}m ${rest.toString().padStart(2, '0')}s` : `${rest}s`;
}
