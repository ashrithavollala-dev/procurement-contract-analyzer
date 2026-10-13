import type { DashboardSummary, SystemStatus } from '../types/api';
import { http } from './client';

export async function getDashboardSummary(): Promise<DashboardSummary> {
  return (await http.get<DashboardSummary>('/dashboard/summary')).data;
}

export async function getSystemStatus(): Promise<SystemStatus> {
  return (await http.get<SystemStatus>('/system/status', { skipGlobalErrorToast: true })).data;
}
