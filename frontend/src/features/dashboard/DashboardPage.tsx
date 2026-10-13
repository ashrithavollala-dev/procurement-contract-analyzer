import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { queryKeys } from '../../api/queryKeys';
import { getDashboardSummary } from '../../api/system';
import { Card, EmptyState, PageHeader, Skeleton } from '../../components/Feedback';
import { AuditStatusBadge, SeverityBadge } from '../../components/StatusBadges';
import { CONTRACT_STATUSES } from '../../types/api';
import { CONTRACT_STATUS_LABELS, RISK_BAND_LABELS, formatDateTime } from '../../types/labels';
import { RiskScoreText } from './RiskScoreText';

export function DashboardPage() {
  const { data, isLoading } = useQuery({ queryKey: queryKeys.dashboard, queryFn: getDashboardSummary });

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Portfolio overview of ingested contracts and compliance audits." />
      {isLoading || !data ? (
        <div className="grid gap-4 md:grid-cols-3">
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
        </div>
      ) : (
        <div className="space-y-5">
          <div className="grid gap-4 md:grid-cols-3">
            <Card>
              <p className="text-xs uppercase text-ink-faint">Contracts</p>
              <p className="mt-1 text-3xl font-semibold">{data.totalContracts}</p>
              <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-ink-muted">
                {CONTRACT_STATUSES.map((status) => (
                  <li key={status}>
                    {CONTRACT_STATUS_LABELS[status]}: <span className="font-medium text-ink">{data.contractsByStatus[status] ?? 0}</span>
                  </li>
                ))}
              </ul>
            </Card>
            <Card>
              <p className="text-xs uppercase text-ink-faint">Audits run</p>
              <p className="mt-1 text-3xl font-semibold">{data.totalAudits}</p>
            </Card>
            <Card>
              <p className="text-xs uppercase text-ink-faint">Average risk score</p>
              <p className="mt-1 text-3xl font-semibold">
                {data.averageRiskScore === null ? '-' : data.averageRiskScore.toFixed(1)}
              </p>
              <p className="mt-1 text-xs text-ink-faint">Completed audits only (0 = no gaps, 100 = all gaps)</p>
            </Card>
          </div>

          <div className="grid gap-5 lg:grid-cols-2">
            <Card title="Recent audits">
              {data.recentAudits.length === 0 ? (
                <EmptyState title="No audits yet">Open a READY contract and run the quick demo audit.</EmptyState>
              ) : (
                <ul className="divide-y divide-surface-border text-sm">
                  {data.recentAudits.map((run) => (
                    <li key={run.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                      <div>
                        <Link to={`/contracts/${run.contractId}?tab=audit`} className="font-medium text-brand-700 hover:underline">
                          {run.contractTitle}
                        </Link>
                        <div className="text-xs text-ink-faint">{formatDateTime(run.createdAt)}</div>
                      </div>
                      <div className="flex items-center gap-3">
                        {run.riskScore !== null && run.riskBand && (
                          <RiskScoreText score={run.riskScore} label={RISK_BAND_LABELS[run.riskBand]} />
                        )}
                        <AuditStatusBadge status={run.status} />
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
            <Card title="Most frequent gaps">
              {data.topNonCompliantRules.length === 0 ? (
                <EmptyState title="No gaps recorded" />
              ) : (
                <ul className="divide-y divide-surface-border text-sm">
                  {data.topNonCompliantRules.map((rule) => (
                    <li key={rule.ruleCode} className="flex items-center justify-between gap-2 py-2">
                      <div>
                        <div className="font-medium">{rule.ruleTitle}</div>
                        <div className="font-mono text-xs text-ink-faint">{rule.ruleCode}</div>
                      </div>
                      <div className="flex items-center gap-3">
                        <SeverityBadge severity={rule.severity} />
                        <span className="font-mono text-sm">{rule.count}</span>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </div>
      )}
    </>
  );
}
