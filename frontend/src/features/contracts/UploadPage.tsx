import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { FileUp, UploadCloud } from 'lucide-react';
import { useState, type DragEvent } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { uploadContract } from '../../api/contracts';
import { ApiError } from '../../api/errors';
import { queryKeys } from '../../api/queryKeys';
import { Card, PageHeader } from '../../components/Feedback';
import { buttonClasses, inputClasses } from '../../components/styles';
import { ProgressBar } from '../../components/Meters';
import { friendlyMessage } from '../../notifications/errorMessages';
import { useToast } from '../../notifications/toastContext';
import { formatBytes } from '../../types/labels';
import { uploadSchema, type UploadForm } from './uploadSchema';

interface UploadProblem {
  message: string;
  existingContractId?: string;
  contractId?: string;
}

export function UploadPage() {
  const [dragging, setDragging] = useState(false);
  const [progress, setProgress] = useState<number | null>(null);
  const [problem, setProblem] = useState<UploadProblem | null>(null);
  const navigate = useNavigate();
  const client = useQueryClient();
  const toast = useToast();

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<UploadForm>({ resolver: zodResolver(uploadSchema) });
  const file = watch('file');

  const chooseFile = (chosen: File | undefined) => {
    setProblem(null);
    if (chosen) {
      setValue('file', chosen, { shouldValidate: true });
    }
  };

  const onDrop = (event: DragEvent<HTMLLabelElement>) => {
    event.preventDefault();
    setDragging(false);
    chooseFile(event.dataTransfer.files[0]);
  };

  const onSubmit = async (values: UploadForm) => {
    setProblem(null);
    setProgress(0);
    try {
      const contract = await uploadContract({
        file: values.file,
        title: values.title || undefined,
        vendorName: values.vendorName || undefined,
        onProgress: setProgress,
      });
      void client.invalidateQueries({ queryKey: queryKeys.contractsAll });
      void client.invalidateQueries({ queryKey: queryKeys.dashboard });
      toast.notify('success', `"${contract.title}" uploaded. Processing has started.`);
      navigate(`/contracts/${contract.id}`);
    } catch (error) {
      const apiError = error instanceof ApiError ? error : null;
      setProgress(null);
      if (!apiError) {
        setProblem({ message: 'Upload failed.' });
        return;
      }
      setProblem({
        message: apiError.code === 'VALIDATION_FAILED'
          ? Object.values(apiError.fieldErrors()).join(' ') || friendlyMessage(apiError)
          : friendlyMessage(apiError),
        existingContractId: apiError.code === 'DUPLICATE_CONTRACT' ? apiError.detail('existingContractId') : undefined,
        contractId: apiError.code === 'INGESTION_QUEUE_FULL' ? apiError.detail('contractId') : undefined,
      });
    }
  };

  return (
    <>
      <PageHeader title="Upload contract" subtitle="PDF with selectable text. Processing runs in the background." />
      <Card className="max-w-2xl">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
          <label
            htmlFor="file"
            onDragOver={(event) => {
              event.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={onDrop}
            data-testid="dropzone"
            className={`flex cursor-pointer flex-col items-center justify-center rounded-card border-2 border-dashed p-8 text-center ${
              dragging ? 'border-brand-500 bg-brand-50' : 'border-surface-border bg-surface-muted'
            }`}
          >
            <UploadCloud className="mb-2 h-8 w-8 text-brand-500" aria-hidden />
            <span className="text-sm font-medium">Drag a PDF here or click to choose</span>
            <span className="mt-1 text-xs text-ink-faint">Fictional sample contracts are in docs/samples</span>
            <input
              id="file"
              type="file"
              accept="application/pdf,.pdf"
              className="sr-only"
              aria-label="Contract PDF"
              onChange={(event) => chooseFile(event.target.files?.[0])}
            />
          </label>
          {file instanceof File && (
            <p className="flex items-center gap-2 text-sm text-ink-muted">
              <FileUp className="h-4 w-4" aria-hidden /> {file.name} ({formatBytes(file.size)})
            </p>
          )}
          {errors.file && (
            <p role="alert" className="text-sm text-status-danger">
              {errors.file.message}
            </p>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="title" className="mb-1 block text-sm font-medium">
                Title <span className="text-ink-faint">(optional)</span>
              </label>
              <input id="title" className={inputClasses} {...register('title')} />
              {errors.title && <p className="mt-1 text-xs text-status-danger">{errors.title.message}</p>}
            </div>
            <div>
              <label htmlFor="vendorName" className="mb-1 block text-sm font-medium">
                Vendor <span className="text-ink-faint">(optional)</span>
              </label>
              <input id="vendorName" className={inputClasses} {...register('vendorName')} />
              {errors.vendorName && <p className="mt-1 text-xs text-status-danger">{errors.vendorName.message}</p>}
            </div>
          </div>

          {progress !== null && <ProgressBar completed={progress} total={100} label="Uploading" />}

          {problem && (
            <div role="alert" className="rounded-md border border-status-danger/30 bg-status-dangerBg p-3 text-sm">
              <p className="text-status-danger">{problem.message}</p>
              {problem.existingContractId && (
                <Link to={`/contracts/${problem.existingContractId}`} className="mt-1 inline-block font-medium text-brand-700 underline">
                  Open the existing contract
                </Link>
              )}
              {problem.contractId && (
                <Link to={`/contracts/${problem.contractId}`} className="mt-1 inline-block font-medium text-brand-700 underline">
                  Open the contract (it can be reprocessed once the queue has room)
                </Link>
              )}
            </div>
          )}

          <button type="submit" className={buttonClasses.primary} disabled={isSubmitting}>
            {isSubmitting ? 'Uploading...' : 'Upload'}
          </button>
        </form>
      </Card>
    </>
  );
}
