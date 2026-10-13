import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Route, Routes } from 'react-router-dom';
import { EmptyState } from './components/Feedback';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { ContractDetailPage } from './features/contracts/ContractDetailPage';
import { ContractsPage } from './features/contracts/ContractsPage';
import { UploadPage } from './features/contracts/UploadPage';
import { RulesPage } from './features/rules/RulesPage';
import { SearchPage } from './features/search/SearchPage';
import { AppLayout } from './layouts/AppLayout';
import { ToastProvider } from './notifications/ToastProvider';

export function AppProviders({ client, children }: { client: QueryClient; children: ReactNode }) {
  return (
    <QueryClientProvider client={client}>
      <ToastProvider>{children}</ToastProvider>
    </QueryClientProvider>
  );
}

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="contracts" element={<ContractsPage />} />
        <Route path="contracts/:id" element={<ContractDetailPage />} />
        <Route path="upload" element={<UploadPage />} />
        <Route path="search" element={<SearchPage />} />
        <Route path="rules" element={<RulesPage />} />
        <Route path="*" element={<EmptyState title="Page not found" />} />
      </Route>
    </Routes>
  );
}
