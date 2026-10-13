import type { SearchRequest, SearchResponse } from '../types/api';
import { http } from './client';

export async function searchClauses(request: SearchRequest): Promise<SearchResponse> {
  return (await http.post<SearchResponse>('/search', request)).data;
}
