import type { ApiError } from '../api/errors';

// Friendly text per backend error code. The UI branches on the code, never on server message text.
export const ERROR_MESSAGES: Record<ApiError['code'], string> = {
  RESOURCE_NOT_FOUND: 'That item no longer exists. It may have been deleted.',
  VALIDATION_FAILED: 'Some fields need attention.',
  UNSUPPORTED_FILE_TYPE: 'Only PDF documents can be uploaded.',
  FILE_TOO_LARGE: 'The file is larger than the upload limit.',
  DUPLICATE_CONTRACT: 'This exact file has already been uploaded.',
  CONTRACT_NOT_READY: 'The contract is not in a state that allows this action yet.',
  NO_ACTIVE_RULES: 'No compliance rule is active. Activate at least one rule first.',
  NO_EXTRACTABLE_TEXT: 'The PDF has no selectable text (it looks scanned). OCR is not supported.',
  INGESTION_QUEUE_FULL: 'The processing queue is full. Try again in a moment.',
  AUDIT_QUEUE_FULL: 'Too many audits are queued. Try again later.',
  INTERRUPTED: 'Processing was interrupted by a server restart. Retry it.',
  LLM_UNAVAILABLE: 'The language model is not available. Check that Ollama is running or the API key is set.',
  LLM_TIMEOUT: 'The language model took too long to answer. Try again or use a faster model.',
  LLM_OUTPUT_INVALID: 'The language model returned an unusable answer.',
  INTERNAL_ERROR: 'Something went wrong on the server.',
  NETWORK_ERROR: 'The backend cannot be reached. Is it running?',
  TIMEOUT: 'The request timed out.',
};

/** Plain-language explanation of a contract or audit failure code (shown next to "Reprocess"). */
export function explainFailure(code: string | null | undefined): string {
  if (!code) return 'The failure reason was not recorded.';
  return (ERROR_MESSAGES as Record<string, string>)[code] ?? 'Processing failed.';
}

export function friendlyMessage(error: ApiError): string {
  return ERROR_MESSAGES[error.code] ?? 'Something went wrong.';
}
