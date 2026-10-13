import type { ContractListParams } from '../types/api';

/** Central TanStack Query keys so invalidation is consistent across features. */
export const queryKeys = {
  contracts: (params: ContractListParams) => ['contracts', params] as const,
  contractsAll: ['contracts'] as const,
  contract: (id: string) => ['contract', id] as const,
  audits: (contractId: string) => ['audits', contractId] as const,
  audit: (auditId: string) => ['audit', auditId] as const,
  rules: ['rules'] as const,
  dashboard: ['dashboard'] as const,
  systemStatus: ['system-status'] as const,
};
