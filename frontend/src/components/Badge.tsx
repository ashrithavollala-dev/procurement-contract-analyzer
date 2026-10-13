import type { Tone } from '../types/labels';

const TONE_CLASSES: Record<Tone, string> = {
  ok: 'bg-status-okBg text-status-ok',
  warn: 'bg-status-warnBg text-status-warn',
  danger: 'bg-status-dangerBg text-status-danger',
  info: 'bg-status-infoBg text-status-info',
  neutral: 'bg-status-neutralBg text-status-neutral',
};

export function Badge({ tone, children, pulse = false }: { tone: Tone; children: string; pulse?: boolean }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${TONE_CLASSES[tone]}`}>
      {pulse && <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-current" aria-hidden />}
      {children}
    </span>
  );
}
