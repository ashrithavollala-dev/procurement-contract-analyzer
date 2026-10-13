import { API_BASE_URL, UPLOAD_TIMEOUT_MS } from '../config';
import type { Contract, ContractListParams, PageResponse } from '../types/api';
import { http } from './client';

export interface UploadInput {
  file: File;
  title?: string;
  vendorName?: string;
  onProgress?: (percent: number) => void;
}

export async function uploadContract({ file, title, vendorName, onProgress }: UploadInput): Promise<Contract> {
  const form = new FormData();
  form.append('file', file);
  if (title) form.append('title', title);
  if (vendorName) form.append('vendorName', vendorName);
  const response = await http.post<Contract>('/contracts', form, {
    timeout: UPLOAD_TIMEOUT_MS,
    // The upload page renders DUPLICATE_CONTRACT and friends inline, with a link.
    skipGlobalErrorToast: true,
    onUploadProgress: (event) => {
      if (onProgress && event.total) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    },
  });
  return response.data;
}

export async function listContracts(params: ContractListParams): Promise<PageResponse<Contract>> {
  const response = await http.get<PageResponse<Contract>>('/contracts', {
    params: {
      status: params.status || undefined,
      q: params.q || undefined,
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
  });
  return response.data;
}

export async function getContract(id: string): Promise<Contract> {
  return (await http.get<Contract>(`/contracts/${id}`)).data;
}

export async function reprocessContract(id: string, force = false): Promise<Contract> {
  return (await http.post<Contract>(`/contracts/${id}/reprocess`, null, { params: { force } })).data;
}

export async function deleteContract(id: string): Promise<void> {
  await http.delete(`/contracts/${id}`);
}

/** Direct link for the browser to download the original PDF. */
export function contractFileUrl(id: string): string {
  return `${API_BASE_URL}/contracts/${id}/file`;
}
