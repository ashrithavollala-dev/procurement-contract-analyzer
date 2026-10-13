import { ChevronLeft, ChevronRight } from 'lucide-react';
import { buttonClasses } from './styles';

export function Pagination({ page, totalPages, totalElements, onChange }: {
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) {
    return <p className="mt-3 text-xs text-ink-faint">{totalElements} item(s)</p>;
  }
  return (
    <div className="mt-3 flex items-center justify-between text-sm text-ink-muted">
      <span>
        Page {page + 1} of {totalPages} ({totalElements} items)
      </span>
      <div className="flex gap-1">
        <button type="button" className={buttonClasses.ghost} disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="Previous page">
          <ChevronLeft className="h-4 w-4" aria-hidden />
        </button>
        <button
          type="button"
          className={buttonClasses.ghost}
          disabled={page + 1 >= totalPages}
          onClick={() => onChange(page + 1)}
          aria-label="Next page"
        >
          <ChevronRight className="h-4 w-4" aria-hidden />
        </button>
      </div>
    </div>
  );
}
