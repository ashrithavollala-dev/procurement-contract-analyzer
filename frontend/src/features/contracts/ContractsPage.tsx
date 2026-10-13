import { RefreshCw, Search, Trash2, Upload } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Card, EmptyState, PageHeader, TableSkeleton } from '../../components/Feedback';
import { buttonClasses, inputClasses } from '../../components/styles';
import { ConfirmDialog } from '../../components/Modal';
import { Pagination } from '../../components/Pagination';
import { ContractStatusBadge } from '../../components/StatusBadges';
import { explainFailure } from '../../notifications/errorMessages';
import { useToast } from '../../notifications/toastContext';
import { CONTRACT_STATUSES, type Contract, type ContractStatus } from '../../types/api';
import { CONTRACT_STATUS_LABELS, formatDateTime, isContractInFlight } from '../../types/labels';
import { useContractList, useDeleteContract, useReprocessContract } from './hooks';

const PAGE_SIZE = 10;

export function ContractsPage() {
  const [status, setStatus] = useState<ContractStatus | ''>('');
  const [q, setQ] = useState('');
  const [submittedQ, setSubmittedQ] = useState('');
  const [page, setPage] = useState(0);
  const [toDelete, setToDelete] = useState<Contract | null>(null);
  const toast = useToast();

  const { data, isLoading, isFetching } = useContractList({
    status: status || undefined,
    q: submittedQ || undefined,
    page,
    size: PAGE_SIZE,
  });
  const reprocess = useReprocessContract();
  const remove = useDeleteContract();
  const polling = data?.content.some((c) => isContractInFlight(c.status)) ?? false;

  return (
    <>
      <PageHeader
        title="Contracts"
        subtitle="Uploaded vendor contracts and their ingestion status."
        actions={
          <Link to="/upload" className={buttonClasses.primary}>
            <Upload className="h-4 w-4" aria-hidden />
            Upload contract
          </Link>
        }
      />
      <Card>
        <form
          className="mb-4 flex flex-wrap gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            setSubmittedQ(q.trim());
            setPage(0);
          }}
        >
          <div className="relative min-w-56 flex-1">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-ink-faint" aria-hidden />
            <input
              className={`${inputClasses} pl-9`}
              placeholder="Search title, vendor or file name"
              value={q}
              onChange={(event) => setQ(event.target.value)}
              aria-label="Search contracts"
            />
          </div>
          <select
            className={`${inputClasses} w-44`}
            value={status}
            aria-label="Filter by status"
            onChange={(event) => {
              setStatus(event.target.value as ContractStatus | '');
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            {CONTRACT_STATUSES.map((s) => (
              <option key={s} value={s}>
                {CONTRACT_STATUS_LABELS[s]}
              </option>
            ))}
          </select>
          <button type="submit" className={buttonClasses.secondary}>
            Apply
          </button>
          {polling && (
            <span className="flex items-center gap-1 text-xs text-ink-faint" data-testid="polling-indicator">
              <RefreshCw className={`h-3 w-3 ${isFetching ? 'animate-spin' : ''}`} aria-hidden /> Auto-refreshing while processing
            </span>
          )}
        </form>

        {isLoading ? (
          <TableSkeleton />
        ) : !data || data.content.length === 0 ? (
          <EmptyState title="No contracts found">
            Upload one of the sample PDFs in <code>docs/samples</code> to get started.
          </EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-surface-border text-xs uppercase text-ink-faint">
                <tr>
                  <th className="py-2 pr-4">Title</th>
                  <th className="py-2 pr-4">Vendor</th>
                  <th className="py-2 pr-4">Status</th>
                  <th className="py-2 pr-4">Pages</th>
                  <th className="py-2 pr-4">Uploaded</th>
                  <th className="py-2 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((contract) => (
                  <tr key={contract.id} className="border-b border-surface-border last:border-0 align-top">
                    <td className="py-3 pr-4">
                      <Link to={`/contracts/${contract.id}`} className="font-medium text-brand-700 hover:underline">
                        {contract.title}
                      </Link>
                      <div className="text-xs text-ink-faint">{contract.originalFilename}</div>
                    </td>
                    <td className="py-3 pr-4 text-ink-muted">{contract.vendorName ?? '-'}</td>
                    <td className="py-3 pr-4">
                      <ContractStatusBadge status={contract.status} />
                      {contract.status === 'FAILED' && (
                        <p className="mt-1 max-w-xs text-xs text-status-danger">{explainFailure(contract.errorCode)}</p>
                      )}
                    </td>
                    <td className="py-3 pr-4 text-ink-muted">{contract.pageCount ?? '-'}</td>
                    <td className="py-3 pr-4 text-ink-muted">{formatDateTime(contract.createdAt)}</td>
                    <td className="py-3 text-right">
                      <div className="flex justify-end gap-1">
                        {contract.status === 'FAILED' && (
                          <button
                            type="button"
                            className={buttonClasses.ghost}
                            disabled={reprocess.isPending}
                            onClick={() =>
                              reprocess.mutate(
                                { id: contract.id },
                                { onSuccess: () => toast.notify('success', `Reprocessing "${contract.title}"`) },
                              )
                            }
                          >
                            <RefreshCw className="h-4 w-4" aria-hidden /> Reprocess
                          </button>
                        )}
                        <button
                          type="button"
                          className={buttonClasses.ghost}
                          disabled={isContractInFlight(contract.status)}
                          onClick={() => setToDelete(contract)}
                          aria-label={`Delete ${contract.title}`}
                        >
                          <Trash2 className="h-4 w-4" aria-hidden />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && (
          <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
        )}
      </Card>

      <ConfirmDialog
        open={toDelete !== null}
        title="Delete contract"
        confirmLabel="Delete"
        busy={remove.isPending}
        message={
          <>
            Delete <strong>{toDelete?.title}</strong>? Its file, search index and audit history are removed permanently.
          </>
        }
        onCancel={() => setToDelete(null)}
        onConfirm={() => {
          if (!toDelete) return;
          const title = toDelete.title;
          remove.mutate(toDelete.id, {
            onSuccess: () => toast.notify('success', `Deleted "${title}"`),
            onSettled: () => setToDelete(null),
          });
        }}
      />
    </>
  );
}
