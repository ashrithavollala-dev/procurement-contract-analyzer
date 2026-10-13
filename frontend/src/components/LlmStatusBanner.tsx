import { useQuery } from '@tanstack/react-query';
import { AlertTriangle } from 'lucide-react';
import { queryKeys } from '../api/queryKeys';
import { getSystemStatus } from '../api/system';

const STATUS_REFRESH_MS = 30_000;

/** Shown while GET /system/status reports the chat model unreachable; search and ingestion still work. */
export function LlmStatusBanner() {
  const { data } = useQuery({
    queryKey: queryKeys.systemStatus,
    queryFn: getSystemStatus,
    refetchInterval: STATUS_REFRESH_MS,
    retry: false,
  });
  if (!data || data.llmReachable) return null;
  return (
    <div role="alert" className="flex items-start gap-3 border-b border-status-warn/30 bg-status-warnBg px-6 py-3 text-sm">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-status-warn" aria-hidden />
      <div>
        <p className="font-medium text-ink">
          Language model unavailable ({data.llmProvider} / {data.llmModel})
        </p>
        <p className="text-ink-muted">
          {data.llmDetail}. Audits and Q&amp;A are disabled; uploads and semantic search keep working.
        </p>
      </div>
    </div>
  );
}
