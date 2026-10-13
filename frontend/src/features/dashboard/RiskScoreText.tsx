export function RiskScoreText({ score, label }: { score: number; label: string }) {
  return (
    <span className="text-xs text-ink-muted" title={label}>
      <span className="font-mono font-medium text-ink">{score.toFixed(1)}</span> {label}
    </span>
  );
}
