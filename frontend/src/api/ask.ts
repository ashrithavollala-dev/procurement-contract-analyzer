import { ASK_TIMEOUT_MS } from '../config';
import type { AskRequest, AskResponse } from '../types/api';
import { http } from './client';

/** Synchronous Q&A; a 3B model on a CPU-only laptop can need minutes, hence the long timeout. */
export async function askContract(contractId: string, request: AskRequest): Promise<AskResponse> {
  return (
    await http.post<AskResponse>(`/contracts/${contractId}/ask`, request, {
      timeout: ASK_TIMEOUT_MS,
      // The Ask panel renders its own error states (LLM_UNAVAILABLE, LLM_TIMEOUT...).
      skipGlobalErrorToast: true,
    })
  ).data;
}
