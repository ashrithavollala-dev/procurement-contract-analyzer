import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { setGlobalErrorListener } from '../api/client';
import type { ApiError } from '../api/errors';
import { friendlyMessage } from './errorMessages';
import { ToastContext, type Toast, type ToastApi, type ToastKind } from './toastContext';

const AUTO_DISMISS_MS = 6000;

/**
 * Global notifications. Registers itself as the Axios error listener, so every API error that
 * a component does not handle itself becomes a toast with friendly text for its error code.
 * VALIDATION_FAILED toasts also list the per-field messages.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const notify = useCallback(
    (kind: ToastKind, title: string, details?: Record<string, string>) => {
      const id = nextId.current++;
      setToasts((current) => [...current.slice(-4), { id, kind, title, details }]);
      window.setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
    },
    [dismiss],
  );

  useEffect(() => {
    setGlobalErrorListener((error: ApiError) => {
      const details = error.code === 'VALIDATION_FAILED' ? error.fieldErrors() : undefined;
      notify('error', friendlyMessage(error), details && Object.keys(details).length > 0 ? details : undefined);
    });
    return () => setGlobalErrorListener(null);
  }, [notify]);

  const api = useMemo<ToastApi>(() => ({ notify, dismiss }), [notify, dismiss]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-96 max-w-[calc(100vw-2rem)] flex-col gap-2" aria-live="polite">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role={toast.kind === 'error' ? 'alert' : 'status'}
            className={`pointer-events-auto rounded-card border p-3 shadow-card ${
              toast.kind === 'error'
                ? 'border-status-danger/30 bg-status-dangerBg'
                : toast.kind === 'success'
                  ? 'border-status-ok/30 bg-status-okBg'
                  : 'border-status-info/30 bg-status-infoBg'
            }`}
          >
            <div className="flex items-start gap-2">
              {toast.kind === 'error' ? (
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-status-danger" aria-hidden />
              ) : toast.kind === 'success' ? (
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-status-ok" aria-hidden />
              ) : (
                <Info className="mt-0.5 h-4 w-4 shrink-0 text-status-info" aria-hidden />
              )}
              <div className="flex-1 text-sm">
                <p className="font-medium text-ink">{toast.title}</p>
                {toast.details && (
                  <ul className="mt-1 list-disc pl-4 text-ink-muted">
                    {Object.entries(toast.details).map(([field, message]) => (
                      <li key={field}>
                        <span className="font-mono">{field}</span>: {message}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <button
                type="button"
                className="text-ink-faint hover:text-ink"
                onClick={() => dismiss(toast.id)}
                aria-label="Dismiss notification"
              >
                <X className="h-4 w-4" aria-hidden />
              </button>
            </div>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
