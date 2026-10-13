import { createContext, useContext } from 'react';

export type ToastKind = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  title: string;
  /** Optional per-field details (e.g. validation messages). */
  details?: Record<string, string>;
}

export interface ToastApi {
  notify: (kind: ToastKind, title: string, details?: Record<string, string>) => void;
  dismiss: (id: number) => void;
}

export const ToastContext = createContext<ToastApi | null>(null);

export function useToast(): ToastApi {
  const api = useContext(ToastContext);
  if (!api) {
    throw new Error('useToast must be used inside <ToastProvider>');
  }
  return api;
}
