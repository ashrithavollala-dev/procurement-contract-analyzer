import type { RiskBand } from '../types/api';
import { RISK_BAND_LABELS } from '../types/labels';

/** Horizontal bar for a 0..1 similarity score. */
export function ScoreBar({ score }: { score: number }) {
  const percent = Math.round(Math.max(0, Math.min(1, score)) * 100);
  return (
    <div className="flex items-center gap-2" title={`Similarity ${score.toFixed(3)}`}>
      <div className="h-2 w-24 overflow-hidden rounded-full bg-surface-sunken">
        <div className="h-full rounded-full bg-brand-500" style={{ width: `${percent}%` }} />
      </div>
      <span className="font-mono text-xs text-ink-muted">{score.toFixed(3)}</span>
    </div>
  );
}

export function ProgressBar({ completed, total, label }: { completed: number; total: number; label: string }) {
  const percent = total === 0 ? 0 : Math.round((completed / total) * 100);
  return (
    <div>
      <div className="mb-1 flex justify-between text-xs text-ink-muted">
        <span>{label}</span>
        <span>
          {completed} / {total} rules ({percent}%)
        </span>
      </div>
      <div
        className="h-2.5 overflow-hidden rounded-full bg-surface-sunken"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={total}
        aria-valuenow={completed}
        aria-label={label}
      >
        <div className="h-full rounded-full bg-brand-500 transition-all" style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}

const BAND_COLOR: Record<RiskBand, string> = {
  LOW: 'text-status-ok',
  MEDIUM: 'text-status-info',
  HIGH: 'text-status-warn',
  CRITICAL: 'text-status-danger',
};

/** Semicircular gauge for the deterministic 0..100 risk score. */
export function RiskGauge({ score, band }: { score: number; band: RiskBand }) {
  const clamped = Math.max(0, Math.min(100, score));
  const radius = 54;
  const circumference = Math.PI * radius;
  const dash = (clamped / 100) * circumference;
  return (
    <div className="flex flex-col items-center" aria-label={`Risk score ${clamped.toFixed(1)}, ${RISK_BAND_LABELS[band]}`}>
      <svg viewBox="0 0 128 72" className={`h-24 w-40 ${BAND_COLOR[band]}`} aria-hidden>
        <path d="M10 66 A54 54 0 0 1 118 66" fill="none" className="stroke-surface-sunken" strokeWidth="12" strokeLinecap="round" />
        <path
          d="M10 66 A54 54 0 0 1 118 66"
          fill="none"
          stroke="currentColor"
          strokeWidth="12"
          strokeLinecap="round"
          strokeDasharray={`${dash} ${circumference}`}
        />
      </svg>
      <div className="-mt-8 text-center">
        <div className="text-2xl font-semibold text-ink">{clamped.toFixed(1)}</div>
        <div className={`text-xs font-medium ${BAND_COLOR[band]}`}>{RISK_BAND_LABELS[band]}</div>
      </div>
    </div>
  );
}
