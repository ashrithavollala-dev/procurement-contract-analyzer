import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getAudit, listAudits, startAudit } from '../../api/audits';
import { queryKeys } from '../../api/queryKeys';
import { POLL_INTERVAL_MS } from '../../config';
import type { StartAuditRequest } from '../../types/api';
import { isAuditInFlight } from '../../types/labels';

export function useAuditHistory(contractId: string) {
  return useQuery({
    queryKey: queryKeys.audits(contractId),
    queryFn: () => listAudits(contractId),
    refetchInterval: (query) => (query.state.data?.some((run) => isAuditInFlight(run.status)) ? POLL_INTERVAL_MS : false),
  });
}

/** Polls the run while QUEUED/RUNNING so the progress bar and findings fill in as rules complete. */
export function useAudit(auditId: string | null) {
  return useQuery({
    queryKey: queryKeys.audit(auditId ?? 'none'),
    queryFn: () => getAudit(auditId as string),
    enabled: auditId !== null,
    refetchInterval: (query) => (query.state.data && isAuditInFlight(query.state.data.status) ? POLL_INTERVAL_MS : false),
  });
}

export function useStartAudit(contractId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: StartAuditRequest) => startAudit(contractId, request),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: queryKeys.audits(contractId) });
      void client.invalidateQueries({ queryKey: queryKeys.dashboard });
    },
  });
}
