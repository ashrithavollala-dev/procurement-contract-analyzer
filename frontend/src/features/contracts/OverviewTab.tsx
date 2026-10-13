import { Download, RefreshCw, Trash2 } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { contractFileUrl } from '../../api/contracts';
import { Card } from '../../components/Feedback';
import { buttonClasses } from '../../components/styles';
import { ConfirmDialog } from '../../components/Modal';
import { explainFailure } from '../../notifications/errorMessages';
import { useToast } from '../../notifications/toastContext';
import type { Contract } from '../../types/api';
import { formatBytes, formatDateTime, isContractInFlight } from '../../types/labels';
import { useDeleteContract, useReprocessContract } from './hooks';

export function OverviewTab({ contract }: { contract: Contract }) {
  const [confirmDelete, setConfirmDelete] = useState(false);
  const reprocess = useReprocessContract();
  const remove = useDeleteContract();
  const toast = useToast();
  const navigate = useNavigate();
  const inFlight = isContractInFlight(contract.status);

  return (
    <div className="grid gap-5 lg:grid-cols-3">
      <Card title="Details" className="lg:col-span-2">
        <dl className="grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
          <Field label="File">{contract.originalFilename}</Field>
          <Field label="Size">{formatBytes(contract.fileSizeBytes)}</Field>
          <Field label="Pages">{contract.pageCount ?? '-'}</Field>
          <Field label="Indexed chunks">{contract.chunkCount ?? '-'}</Field>
          <Field label="Uploaded">{formatDateTime(contract.createdAt)}</Field>
          <Field label="Processed">{formatDateTime(contract.processedAt)}</Field>
          <Field label="SHA-256">
            <span className="break-all font-mono text-xs">{contract.contentHash}</span>
          </Field>
        </dl>
        {inFlight && (
          <p className="mt-4 rounded-md bg-status-infoBg p-3 text-sm text-status-info">
            Extracting text, chunking clauses and embedding them. This page updates automatically.
          </p>
        )}
        {contract.status === 'FAILED' && (
          <div className="mt-4 rounded-md bg-status-dangerBg p-3 text-sm">
            <p className="font-medium text-status-danger">{explainFailure(contract.errorCode)}</p>
            {contract.errorMessage && <p className="mt-1 text-ink-muted">{contract.errorMessage}</p>}
            <p className="mt-1 font-mono text-xs text-ink-faint">{contract.errorCode}</p>
          </div>
        )}
      </Card>
      <Card title="Actions">
        <div className="flex flex-col gap-2">
          <a href={contractFileUrl(contract.id)} className={buttonClasses.secondary}>
            <Download className="h-4 w-4" aria-hidden /> Download original PDF
          </a>
          {(contract.status === 'FAILED' || contract.status === 'READY') && (
            <button
              type="button"
              className={buttonClasses.secondary}
              disabled={reprocess.isPending}
              onClick={() =>
                reprocess.mutate(
                  { id: contract.id, force: contract.status === 'READY' },
                  { onSuccess: () => toast.notify('success', 'Reprocessing started') },
                )
              }
            >
              <RefreshCw className="h-4 w-4" aria-hidden /> {contract.status === 'READY' ? 'Re-index' : 'Reprocess'}
            </button>
          )}
          <button type="button" className={buttonClasses.danger} disabled={inFlight} onClick={() => setConfirmDelete(true)}>
            <Trash2 className="h-4 w-4" aria-hidden /> Delete
          </button>
        </div>
      </Card>
      <ConfirmDialog
        open={confirmDelete}
        title="Delete contract"
        confirmLabel="Delete"
        busy={remove.isPending}
        message="The file, its search index and all audits are removed permanently."
        onCancel={() => setConfirmDelete(false)}
        onConfirm={() =>
          remove.mutate(contract.id, {
            onSuccess: () => {
              toast.notify('success', `Deleted "${contract.title}"`);
              navigate('/contracts');
            },
            onSettled: () => setConfirmDelete(false),
          })
        }
      />
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs uppercase text-ink-faint">{label}</dt>
      <dd className="mt-0.5 text-ink">{children}</dd>
    </div>
  );
}
