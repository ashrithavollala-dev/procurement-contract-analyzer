import { useQuery } from '@tanstack/react-query';
import { Play, Timer } from 'lucide-react';
import { useEffect, useState } from 'react';
import { ApiError } from '../../api/errors';
import { queryKeys } from '../../api/queryKeys';
import { listRules } from '../../api/rules';
import { Card, EmptyState, Spinner } from '../../components/Feedback';
import { buttonClasses } from '../../components/styles';
import { ProgressBar, RiskGauge } from '../../components/Meters';
import { AuditStatusBadge } from '../../components/StatusBadges';
import { DEMO_RULE_CODES } from '../../config';
import { explainFailure } from '../../notifications/errorMessages';
import type { AuditRunDetail, Contract } from '../../types/api';
import { formatDateTime, formatDuration, isAuditInFlight } from '../../types/labels';
import { FindingsTable } from './FindingsTable';
import { useAudit, useAuditHistory, useStartAudit } from './hooks';

export function AuditTab({ contract }: { contract: Contract }) {
  const history = useAuditHistory(contract.id);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const currentId = selectedId ?? history.data?.[0]?.id ?? null;
  const audit = useAudit(currentId);
  const start = useStartAudit(contract.id);
  const rules = useQuery({ queryKey: queryKeys.rules, queryFn: () => listRules(false) });
  const activeCodes = (rules.data ?? []).filter((r) => r.active).map((r) => r.code);
  const ready = contract.status === 'READY';

  const launch = (ruleCodes: string[] | undefined) =>
    start.mutate(ruleCodes ? { ruleCodes } : {}, { onSuccess: (response) => setSelectedId(response.auditId) });

  return (
    <div className="space-y-5">
      <Card title="Run a compliance audit">
        {!ready && (
          <p className="mb-3 text-sm text-ink-muted">Audits are available once the contract is READY.</p>
        )}
        <p className="mb-4 text-sm text-ink-muted">
          The agent retrieves clauses for each rule, asks the language model for a structured judgement, retries or
          re-searches when unsure, and keeps only evidence it can verify verbatim. With a small local model each rule
          can take tens of seconds, so start with the quick demo.
        </p>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className={buttonClasses.primary}
            disabled={!ready || start.isPending}
            onClick={() => launch(DEMO_RULE_CODES)}
          >
            <Play className="h-4 w-4" aria-hidden /> Quick demo ({DEMO_RULE_CODES.length} rules)
          </button>
          <button
            type="button"
            className={buttonClasses.secondary}
            disabled={!ready || start.isPending || activeCodes.length === 0}
            onClick={() => launch(undefined)}
          >
            Full audit ({activeCodes.length} active rules)
          </button>
        </div>
        {start.error instanceof ApiError && (
          <p role="alert" className="mt-3 text-sm text-status-danger">
            {explainFailure(start.error.code)}
          </p>
        )}
      </Card>

      {history.isLoading ? (
        <Spinner label="Loading audits" />
      ) : !currentId ? (
        <EmptyState title="No audits yet">Run the quick demo to see grounded findings and a risk score.</EmptyState>
      ) : audit.data ? (
        <AuditRunView run={audit.data} />
      ) : (
        <Spinner label="Loading audit" />
      )}

      {history.data && history.data.length > 0 && (
        <Card title="Audit history">
          <ul className="divide-y divide-surface-border text-sm">
            {history.data.map((run) => (
              <li key={run.id}>
                <button
                  type="button"
                  onClick={() => setSelectedId(run.id)}
                  className={`flex w-full flex-wrap items-center justify-between gap-2 px-2 py-2 text-left hover:bg-surface-muted ${
                    run.id === currentId ? 'bg-brand-50' : ''
                  }`}
                >
                  <span className="text-ink-muted">{formatDateTime(run.createdAt)}</span>
                  <span className="text-xs text-ink-faint">
                    {run.completedRules}/{run.totalRules} rules
                    {run.riskScore !== null ? ` | score ${run.riskScore.toFixed(1)}` : ''}
                  </span>
                  <AuditStatusBadge status={run.status} />
                </button>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}

function AuditRunView({ run }: { run: AuditRunDetail }) {
  const inFlight = isAuditInFlight(run.status);
  const elapsed = useElapsedSeconds(run.startedAt, run.completedAt, inFlight);

  return (
    <Card
      title="Audit result"
      actions={
        <div className="flex items-center gap-3">
          {run.startedAt && (
            <span className="flex items-center gap-1 text-xs text-ink-faint">
              <Timer className="h-3 w-3" aria-hidden /> {formatDuration(elapsed)}
            </span>
          )}
          <AuditStatusBadge status={run.status} />
        </div>
      }
    >
      <div className="grid gap-6 md:grid-cols-[1fr_auto]">
        <div className="space-y-3">
          <ProgressBar completed={run.completedRules} total={run.totalRules} label="Rules evaluated" />
          {run.summary && <p className="text-sm text-ink">{run.summary}</p>}
          {run.status === 'FAILED' && (
            <p role="alert" className="rounded-md bg-status-dangerBg p-3 text-sm text-status-danger">
              {explainFailure(run.errorCode)}
            </p>
          )}
          {inFlight && (
            <p className="text-xs text-ink-faint">
              Findings appear as each rule completes. Local CPU inference is slow; this page keeps polling.
            </p>
          )}
        </div>
        {run.riskScore !== null && run.riskBand && <RiskGauge score={run.riskScore} band={run.riskBand} />}
      </div>
      {run.findings.length > 0 && (
        <div className="mt-6">
          <FindingsTable findings={run.findings} />
        </div>
      )}
    </Card>
  );
}

function useElapsedSeconds(startedAt: string | null, completedAt: string | null, ticking: boolean): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!ticking) return undefined;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [ticking]);
  if (!startedAt) return 0;
  const end = completedAt ? new Date(completedAt).getTime() : now;
  return (end - new Date(startedAt).getTime()) / 1000;
}
