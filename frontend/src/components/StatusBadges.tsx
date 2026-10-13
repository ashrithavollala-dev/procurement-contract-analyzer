import type { AuditStatus, ContractStatus, FindingStatus, Severity } from '../types/api';
import {
  AUDIT_STATUS_LABELS,
  AUDIT_STATUS_TONE,
  CONTRACT_STATUS_LABELS,
  CONTRACT_STATUS_TONE,
  FINDING_STATUS_LABELS,
  FINDING_STATUS_TONE,
  SEVERITY_LABELS,
  SEVERITY_TONE,
  isAuditInFlight,
  isContractInFlight,
} from '../types/labels';
import { Badge } from './Badge';

export function ContractStatusBadge({ status }: { status: ContractStatus }) {
  return (
    <Badge tone={CONTRACT_STATUS_TONE[status]} pulse={isContractInFlight(status)}>
      {CONTRACT_STATUS_LABELS[status]}
    </Badge>
  );
}

export function AuditStatusBadge({ status }: { status: AuditStatus }) {
  return (
    <Badge tone={AUDIT_STATUS_TONE[status]} pulse={isAuditInFlight(status)}>
      {AUDIT_STATUS_LABELS[status]}
    </Badge>
  );
}

export function FindingStatusBadge({ status }: { status: FindingStatus }) {
  return <Badge tone={FINDING_STATUS_TONE[status]}>{FINDING_STATUS_LABELS[status]}</Badge>;
}

export function SeverityBadge({ severity }: { severity: Severity }) {
  return <Badge tone={SEVERITY_TONE[severity]}>{SEVERITY_LABELS[severity]}</Badge>;
}
