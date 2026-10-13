import { useMutation, useQuery } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { listContracts } from '../../api/contracts';
import { searchClauses } from '../../api/search';
import { Card, EmptyState, PageHeader, Spinner } from '../../components/Feedback';
import { buttonClasses, inputClasses } from '../../components/styles';
import { ScoreBar } from '../../components/Meters';

export function SearchPage() {
  const [query, setQuery] = useState('');
  const [contractId, setContractId] = useState('');
  const [topK, setTopK] = useState(5);
  const contracts = useQuery({
    queryKey: ['contracts', { status: 'READY', size: 100, purpose: 'search-filter' }],
    queryFn: () => listContracts({ status: 'READY', size: 100 }),
  });
  const search = useMutation({ mutationFn: searchClauses });

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (query.trim().length < 2) return;
    search.mutate({ query: query.trim(), contractId: contractId || null, topK });
  };

  return (
    <>
      <PageHeader
        title="Semantic clause search"
        subtitle="Finds clauses by meaning using local embeddings and pgvector. Works without a language model."
      />
      <Card className="mb-5">
        <form onSubmit={onSubmit} className="grid gap-3 md:grid-cols-[1fr_16rem_10rem_auto] md:items-end">
          <div>
            <label htmlFor="query" className="mb-1 block text-sm font-medium">
              Query
            </label>
            <input
              id="query"
              className={inputClasses}
              placeholder="e.g. limitation of liability cap"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              maxLength={1000}
            />
          </div>
          <div>
            <label htmlFor="contract" className="mb-1 block text-sm font-medium">
              Contract
            </label>
            <select id="contract" className={inputClasses} value={contractId} onChange={(event) => setContractId(event.target.value)}>
              <option value="">All ready contracts</option>
              {contracts.data?.content.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.title}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="topK" className="mb-1 block text-sm font-medium">
              Results: {topK}
            </label>
            <input
              id="topK"
              type="range"
              min={1}
              max={20}
              value={topK}
              onChange={(event) => setTopK(Number(event.target.value))}
              className="w-full accent-brand-600"
            />
          </div>
          <button type="submit" className={buttonClasses.primary} disabled={search.isPending || query.trim().length < 2}>
            <Search className="h-4 w-4" aria-hidden /> Search
          </button>
        </form>
      </Card>

      {search.isPending && <Spinner label="Searching" />}
      {search.data &&
        (search.data.results.length === 0 ? (
          <EmptyState title="No matching clauses">Try different wording or search all contracts.</EmptyState>
        ) : (
          <ol className="space-y-3">
            {search.data.results.map((result) => (
              <li key={`${result.contractId}-${result.chunkIndex}`}>
                <Card>
                  <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <Link to={`/contracts/${result.contractId}`} className="font-medium text-brand-700 hover:underline">
                        {result.contractTitle}
                      </Link>
                      <span className="ml-2 text-xs text-ink-faint">
                        page {result.page} | chunk {result.chunkIndex}
                      </span>
                    </div>
                    <ScoreBar score={result.score} />
                  </div>
                  {result.heading && <p className="mb-1 text-xs font-semibold uppercase text-ink-muted">{result.heading}</p>}
                  <p className="text-sm text-ink">{result.snippet}</p>
                </Card>
              </li>
            ))}
          </ol>
        ))}
    </>
  );
}
