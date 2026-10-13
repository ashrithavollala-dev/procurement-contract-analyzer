import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { API, contract, page } from './fixtures';
import { renderApp } from './renderApp';
import { server } from './server';

/** Status text inside the contracts table (the filter dropdown also contains the status labels). */
async function tableStatus(text: string) {
  const table = await screen.findByRole('table');
  return within(table).findByText(text, {}, { timeout: 3000 });
}

describe('Contracts list polling', () => {
  it('polls while a contract is processing and stops once everything is settled', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/contracts`, () => {
        calls++;
        const status = calls < 3 ? 'PROCESSING' : 'READY';
        return HttpResponse.json(page([contract({ status })]));
      }),
    );
    renderApp('/contracts');

    expect(await tableStatus('Processing')).toBeInTheDocument();
    expect(screen.getByTestId('polling-indicator')).toBeInTheDocument();

    expect(await tableStatus('Ready')).toBeInTheDocument();
    const callsWhenReady = calls;
    expect(callsWhenReady).toBeGreaterThanOrEqual(3);

    // The poll interval is 50 ms in tests; wait well beyond it and verify no further requests.
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(calls).toBe(callsWhenReady);
    await waitFor(() => expect(screen.queryByTestId('polling-indicator')).not.toBeInTheDocument());
  });

  it('does not poll at all when nothing is in flight', async () => {
    let calls = 0;
    server.use(http.get(`${API}/contracts`, () => {
      calls++;
      return HttpResponse.json(page([contract({ status: 'FAILED', errorCode: 'NO_EXTRACTABLE_TEXT' })]));
    }));
    renderApp('/contracts');

    expect(await tableStatus('Failed')).toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(within(table).getByText(/no selectable text/)).toBeInTheDocument();
    expect(within(table).getByRole('button', { name: /Reprocess/ })).toBeInTheDocument();
    await new Promise((resolve) => setTimeout(resolve, 300));
    expect(calls).toBe(1);
  });
});
