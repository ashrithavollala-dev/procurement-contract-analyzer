import { ChevronDown, ChevronRight } from 'lucide-react';
import { Fragment, useMemo, useState } from 'react';
import { inputClasses } from '../../components/styles';
import { FindingStatusBadge, SeverityBadge } from '../../components/StatusBadges';
import { explainFailure } from '../../notifications/errorMessages';
import { FINDING_STATUSES, SEVERITIES, type Finding, type FindingStatus, type Severity } from '../../types/api';
import { FINDING_STATUS_LABELS, SEVERITY_LABELS } from '../../types/labels';

export function FindingsTable({ findings }: { findings: Finding[] }) {
  const [status, setStatus] = useState<FindingStatus | ''>('');
  const [severity, setSeverity] = useState<Severity | ''>('');
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  const visible = useMemo(
    () => findings.filter((f) => (!status || f.status === status) && (!severity || f.severity === severity)),
    [findings, status, severity],
  );

  const toggle = (id: number) =>
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });

  return (
    <div>
      <div className="mb-3 flex flex-wrap gap-2">
        <select
          className={`${inputClasses} w-48`}
          value={status}
          onChange={(e) => setStatus(e.target.value as FindingStatus | '')}
          aria-label="Filter findings by status"
        >
          <option value="">All statuses</option>
          {FINDING_STATUSES.map((s) => (
            <option key={s} value={s}>
              {FINDING_STATUS_LABELS[s]}
            </option>
          ))}
        </select>
        <select
          className={`${inputClasses} w-44`}
          value={severity}
          onChange={(e) => setSeverity(e.target.value as Severity | '')}
          aria-label="Filter findings by severity"
        >
          <option value="">All severities</option>
          {SEVERITIES.map((s) => (
            <option key={s} value={s}>
              {SEVERITY_LABELS[s]}
            </option>
          ))}
        </select>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-surface-border text-xs uppercase text-ink-faint">
            <tr>
              <th className="w-6 py-2" />
              <th className="py-2 pr-4">Rule</th>
              <th className="py-2 pr-4">Severity</th>
              <th className="py-2 pr-4">Status</th>
              <th className="py-2 pr-4">Confidence</th>
              <th className="py-2">Evidence</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((finding) => {
              const open = expanded.has(finding.id);
              return (
                <Fragment key={finding.id}>
                  <tr className="cursor-pointer border-b border-surface-border hover:bg-surface-muted" onClick={() => toggle(finding.id)}>
                    <td className="py-2">
                      <button type="button" aria-label={`${open ? 'Collapse' : 'Expand'} ${finding.ruleCode}`} aria-expanded={open}>
                        {open ? <ChevronDown className="h-4 w-4" aria-hidden /> : <ChevronRight className="h-4 w-4" aria-hidden />}
                      </button>
                    </td>
                    <td className="py-2 pr-4">
                      <div className="font-medium">{finding.ruleTitle}</div>
                      <div className="font-mono text-xs text-ink-faint">{finding.ruleCode}</div>
                    </td>
                    <td className="py-2 pr-4">
                      <SeverityBadge severity={finding.severity} />
                    </td>
                    <td className="py-2 pr-4">
                      <FindingStatusBadge status={finding.status} />
                    </td>
                    <td className="py-2 pr-4 font-mono text-xs">{Math.round(finding.confidence * 100)}%</td>
                    <td className="py-2 text-xs text-ink-muted">
                      {finding.evidencePage ? `Page ${finding.evidencePage}` : 'No verified evidence'}
                    </td>
                  </tr>
                  {open && (
                    <tr className="border-b border-surface-border bg-surface-muted">
                      <td />
                      <td colSpan={5} className="space-y-2 py-3 pr-4 text-sm">
                        {finding.evidenceQuote && (
                          <blockquote className="border-l-4 border-brand-500 bg-surface px-3 py-2 italic text-ink">
                            "{finding.evidenceQuote}"
                            <span className="ml-2 not-italic text-xs text-ink-faint">(page {finding.evidencePage})</span>
                          </blockquote>
                        )}
                        {finding.explanation && <p className="text-ink">{finding.explanation}</p>}
                        {finding.recommendation && (
                          <p className="text-ink-muted">
                            <span className="font-medium text-ink">Recommendation:</span> {finding.recommendation}
                          </p>
                        )}
                        <p className="text-xs text-ink-faint">
                          {finding.iterations} agent iteration(s){finding.mandatory ? ' | mandatory rule' : ''}
                          {finding.errorCode ? ` | ${finding.errorCode}: ${explainFailure(finding.errorCode)}` : ''}
                        </p>
                        {finding.queriesUsed.length > 0 && (
                          <ul className="list-disc pl-5 text-xs text-ink-faint">
                            {finding.queriesUsed.map((query, index) => (
                              <li key={index}>
                                Query {index + 1}: {query}
                              </li>
                            ))}
                          </ul>
                        )}
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
        {visible.length === 0 && <p className="py-6 text-center text-sm text-ink-faint">No findings match the filters.</p>}
      </div>
    </div>
  );
}
