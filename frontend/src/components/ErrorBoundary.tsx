import { Component, type ErrorInfo, type ReactNode } from 'react';

interface State {
  error: Error | null;
}

/** Catches render errors so one broken view does not blank the whole application. */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('UI error boundary caught an error', error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return (
        <div role="alert" className="m-6 rounded-card border border-status-danger/30 bg-status-dangerBg p-6">
          <h2 className="text-lg font-semibold text-status-danger">This page failed to render</h2>
          <p className="mt-2 text-sm text-ink-muted">{this.state.error.message}</p>
          <button
            type="button"
            className="mt-4 rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white"
            onClick={() => this.setState({ error: null })}
          >
            Try again
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
