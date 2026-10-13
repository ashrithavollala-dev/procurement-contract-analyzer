import axios, { type AxiosInstance } from 'axios';
import { API_BASE_URL, DEFAULT_TIMEOUT_MS } from '../config';
import { ApiError, toApiError } from './errors';

declare module 'axios' {
  interface AxiosRequestConfig {
    /** Set when the calling component renders the error itself (e.g. duplicate-upload link). */
    skipGlobalErrorToast?: boolean;
  }
}

type ErrorListener = (error: ApiError) => void;

let errorListener: ErrorListener | null = null;

/** The toast provider registers here; the interceptor feeds it every API error. */
export function setGlobalErrorListener(listener: ErrorListener | null): void {
  errorListener = listener;
}

export const http: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: DEFAULT_TIMEOUT_MS,
  headers: { Accept: 'application/json' },
});

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const apiError = toApiError(error);
    const config = axios.isAxiosError(error) ? error.config : undefined;
    if (!config?.skipGlobalErrorToast && errorListener) {
      errorListener(apiError);
    }
    return Promise.reject(apiError);
  },
);
