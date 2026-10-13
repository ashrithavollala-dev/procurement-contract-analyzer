import { ArrowLeft } from 'lucide-react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { EmptyState, PageHeader, Skeleton } from '../../components/Feedback';
import { ContractStatusBadge } from '../../components/StatusBadges';
import { AskTab } from '../ask/AskTab';
import { AuditTab } from '../audit/AuditTab';
import { useContract } from './hooks';
import { OverviewTab } from './OverviewTab';

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'audit', label: 'Audit' },
  { id: 'ask', label: 'Ask' },
] as const;
type TabId = (typeof TABS)[number]['id'];

export function ContractDetailPage() {
  const { id = '' } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const requested = searchParams.get('tab');
  const tab: TabId = TABS.some((t) => t.id === requested) ? (requested as TabId) : 'overview';
  const { data: contract, isLoading, error } = useContract(id);

  if (isLoading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-8 w-80" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }
  if (error || !contract) {
    return (
      <EmptyState title="Contract not found">
        <Link to="/contracts" className="text-brand-700 underline">
          Back to contracts
        </Link>
      </EmptyState>
    );
  }

  return (
    <>
      <Link to="/contracts" className="mb-3 inline-flex items-center gap-1 text-sm text-ink-muted hover:text-ink">
        <ArrowLeft className="h-4 w-4" aria-hidden /> Contracts
      </Link>
      <PageHeader
        title={contract.title}
        subtitle={contract.vendorName ?? contract.originalFilename}
        actions={<ContractStatusBadge status={contract.status} />}
      />
      <div role="tablist" aria-label="Contract views" className="mb-5 flex gap-1 border-b border-surface-border">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setSearchParams(t.id === 'overview' ? {} : { tab: t.id })}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
              tab === t.id ? 'border-brand-600 text-brand-700' : 'border-transparent text-ink-muted hover:text-ink'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel">
        {tab === 'overview' && <OverviewTab contract={contract} />}
        {tab === 'audit' && <AuditTab contract={contract} />}
        {tab === 'ask' && <AskTab contract={contract} />}
      </div>
    </>
  );
}
