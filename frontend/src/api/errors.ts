import { AxiosError } from 'axios';
import { ERROR_CODES, type ApiErrorBody, type ErrorCode } from '../types/api';

/**
 * Normalised error for every failed API call. UI code branches on `code` and `status`, never
 * on `message` text.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: ErrorCode | 'NETWORK_ERROR' | 'TIMEOUT';
  readonly details: Record<string, unknown>;

  constructor(status: number, code: ApiError['code'], message: string, details: Record<string, unknown> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }

  /** Field-level validation messages (fieldName -> message) for VALIDATION_FAILED responses. */
  fieldErrors(): Record<string, string> {
    const result: Record<string, string> = {};
    for (const [field, value] of Object.entries(this.details)) {
      if (typeof value === 'string') {
        result[field] = value;
      }
    }
    return result;
  }

  detail(key: string): string | undefined {
    const value = this.details[key];
    return typeof value === 'string' ? value : undefined;
  }
}

function isApiErrorBody(data: unknown): data is ApiErrorBody {
  if (typeof data !== 'object' || data === null) return false;
  const code = (data as { code?: unknown }).code;
  return typeof code === 'string' && (ERROR_CODES as readonly string[]).includes(code);
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (error instanceof AxiosError) {
    if (error.code === AxiosError.ECONNABORTED || error.code === 'ETIMEDOUT') {
      return new ApiError(0, 'TIMEOUT', 'The request timed out.');
    }
    const response = error.response;
    if (!response) {
      return new ApiError(0, 'NETWORK_ERROR', 'The backend could not be reached.');
    }
    if (isApiErrorBody(response.data)) {
      const body = response.data;
      return new ApiError(body.status, body.code, body.message, body.details ?? {});
    }
    if (response.status === 413) {
      // A reverse proxy (Nginx client_max_body_size) may answer before the backend does.
      return new ApiError(413, 'FILE_TOO_LARGE', 'The file is too large.');
    }
    return new ApiError(response.status, 'INTERNAL_ERROR', `Unexpected response (HTTP ${response.status}).`);
  }
  return new ApiError(0, 'INTERNAL_ERROR', error instanceof Error ? error.message : 'Unknown error');
}
