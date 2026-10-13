import { FileSearch, FileText, LayoutDashboard, ListChecks, Menu, Upload, X } from 'lucide-react';
import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { ErrorBoundary } from '../components/ErrorBoundary';
import { LlmStatusBanner } from '../components/LlmStatusBanner';

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/contracts', label: 'Contracts', icon: FileText, end: false },
  { to: '/upload', label: 'Upload', icon: Upload, end: true },
  { to: '/search', label: 'Search', icon: FileSearch, end: true },
  { to: '/rules', label: 'Compliance rules', icon: ListChecks, end: true },
];

function Navigation({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav className="flex flex-col gap-1 p-3" aria-label="Main">
      {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          onClick={onNavigate}
          className={({ isActive }) =>
            `flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium ${
              isActive ? 'bg-brand-50 text-brand-700' : 'text-ink-muted hover:bg-surface-muted hover:text-ink'
            }`
          }
        >
          <Icon className="h-4 w-4" aria-hidden />
          {label}
        </NavLink>
      ))}
    </nav>
  );
}

/** Responsive shell: fixed sidebar on desktop, slide-over menu on small screens, LLM banner on top. */
export function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-60 shrink-0 border-r border-surface-border bg-surface md:block">
        <Brand />
        <Navigation />
        <Disclaimer />
      </aside>

      {menuOpen && (
        <div className="fixed inset-0 z-30 bg-ink/40 md:hidden" onClick={() => setMenuOpen(false)}>
          <aside className="h-full w-64 bg-surface" onClick={(event) => event.stopPropagation()}>
            <div className="flex items-center justify-between pr-3">
              <Brand />
              <button type="button" onClick={() => setMenuOpen(false)} aria-label="Close menu" className="p-2 text-ink-muted">
                <X className="h-5 w-5" aria-hidden />
              </button>
            </div>
            <Navigation onNavigate={() => setMenuOpen(false)} />
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-3 border-b border-surface-border bg-surface px-4 py-3 md:hidden">
          <button type="button" onClick={() => setMenuOpen(true)} aria-label="Open menu" className="text-ink-muted">
            <Menu className="h-5 w-5" aria-hidden />
          </button>
          <span className="font-semibold">Contract Analyzer</span>
        </header>
        <LlmStatusBanner />
        <main className="mx-auto w-full max-w-7xl flex-1 p-4 md:p-8">
          <ErrorBoundary key={location.pathname}>
            <Outlet />
          </ErrorBoundary>
        </main>
      </div>
    </div>
  );
}

function Brand() {
  return (
    <div className="px-5 py-5">
      <p className="text-sm font-semibold text-brand-900">Contract Analyzer</p>
      <p className="text-xs text-ink-faint">Agentic procurement review</p>
    </div>
  );
}

function Disclaimer() {
  return (
    <p className="mx-5 mt-6 border-t border-surface-border pt-4 text-xs leading-relaxed text-ink-faint">
      Demo system with fictional data. No authentication: do not expose to untrusted networks.
    </p>
  );
}
