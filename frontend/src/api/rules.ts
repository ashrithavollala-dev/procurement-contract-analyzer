import type { CreateRuleRequest, Rule, UpdateRuleRequest } from '../types/api';
import { http } from './client';

export async function listRules(activeOnly = false): Promise<Rule[]> {
  return (await http.get<Rule[]>('/compliance-rules', { params: { activeOnly } })).data;
}

export async function createRule(request: CreateRuleRequest): Promise<Rule> {
  return (await http.post<Rule>('/compliance-rules', request, { skipGlobalErrorToast: true })).data;
}

export async function updateRule(id: number, request: UpdateRuleRequest): Promise<Rule> {
  return (await http.put<Rule>(`/compliance-rules/${id}`, request, { skipGlobalErrorToast: true })).data;
}

export async function setRuleActive(id: number, active: boolean): Promise<Rule> {
  return (await http.patch<Rule>(`/compliance-rules/${id}/active`, { active })).data;
}
