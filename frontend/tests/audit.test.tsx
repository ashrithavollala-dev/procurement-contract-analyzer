import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { API, audit, contract, errorBody, finding } from './fixtures';
import { renderApp } from './renderApp';
import { server } from './server';

const c = contract();

describe('Audit tab', () => {
  it('shows progress while running, then the risk gauge and findings', async () => {
    let polls = 0;
    const running = audit({ status: 'RUNNING', completedRules: 1, riskScore: null, riskBand: null, summary: null, completedAt: null,
      findings: [finding()] });
    const completed = audit({
      findings: [
        finding(),
        finding({ id: 2, ruleCode: 'AUDIT_RIGHTS', ruleTitle: 'Audit rights', severity: 'HIGH', status: 'NON_COMPLIANT',
          evidenceQuote: 'audit only financial records', evidencePage: 4 }),
        finding({ id: 3, ruleCode: 'DATA_PROTECTION', ruleTitle: 'Data protection', status: 'NOT_FOUND',
          evidenceQuote: null, evidencePage: null, iterations: 2 }),
      ],
    });
    server.use(
      http.get(`${API}/contracts/${c.id}`, () => HttpResponse.json(c)),
      http.get(`${API}/contracts/${c.id}/audits`, () => HttpResponse.json([polls < 2 ? running : completed])),
      http.get(`${API}/audits/${running.id}`, () => {
        polls++;
        return HttpResponse.json(polls < 3 ? running : completed);
      }),
    );
    renderApp(`/contracts/${c.id}?tab=audit`);

    const progress = await screen.findByRole('progressbar', { name: 'Rules evaluated' });
    expect(progress).toHaveAttribute('aria-valuenow', '1');
    expect(progress).toHaveAttribute('aria-valuemax', '3');

    await waitFor(() => expect(screen.getByLabelText(/Risk score 33.3/)).toBeInTheDocument(), { timeout: 3000 });
    expect(screen.getByRole('progressbar', { name: 'Rules evaluated' })).toHaveAttribute('aria-valuenow', '3');
    expect(screen.getByText('Audit rights')).toBeInTheDocument();
    expect(screen.getByText('No verified evidence')).toBeInTheDocument();
  });

  it('filters findings and expands evidence with page citation and iterations', async () => {
    const done = audit({
      findings: [
        finding(),
        finding({ id: 2, ruleCode: 'AUDIT_RIGHTS', ruleTitle: 'Audit rights', severity: 'HIGH', status: 'NON_COMPLIANT' }),
      ],
    });
    server.use(
      http.get(`${API}/contracts/${c.id}`, () => HttpResponse.json(c)),
      http.get(`${API}/contracts/${c.id}/audits`, () => HttpResponse.json([done])),
      http.get(`${API}/audits/${done.id}`, () => HttpResponse.json(done)),
    );
    const user = userEvent.setup();
    renderApp(`/contracts/${c.id}?tab=audit`);

    await screen.findByText('Audit rights');
    await user.selectOptions(screen.getByLabelText('Filter findings by status'), 'NON_COMPLIANT');
    expect(screen.queryByText('Limitation of liability cap')).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Filter findings by status'), '');

    await user.click(screen.getByRole('button', { name: 'Expand LIABILITY_CAP' }));
    const quote = screen.getByText(/shall not exceed the total fees paid or payable/);
    expect(within(quote).getByText('(page 3)')).toBeInTheDocument();
    expect(screen.getByText(/1 agent iteration/)).toBeInTheDocument();
    expect(screen.getByText(/Clause 8.1 caps aggregate liability/)).toBeInTheDocument();
  });

  it('disables starting an audit unless the contract is READY', async () => {
    const processing = contract({ status: 'PROCESSING' });
    server.use(
      http.get(`${API}/contracts/${c.id}`, () => HttpResponse.json(processing)),
      http.get(`${API}/contracts/${c.id}/audits`, () => HttpResponse.json([])),
    );
    renderApp(`/contracts/${c.id}?tab=audit`);

    expect(await screen.findByRole('button', { name: /Quick demo/ })).toBeDisabled();
    expect(screen.getByText('Audits are available once the contract is READY.')).toBeInTheDocument();
  });

  it('starts the quick demo audit and explains LLM_UNAVAILABLE inline', async () => {
    let body: unknown = null;
    server.use(
      http.get(`${API}/contracts/${c.id}`, () => HttpResponse.json(c)),
      http.get(`${API}/contracts/${c.id}/audits`, () => HttpResponse.json([])),
      http.post(`${API}/contracts/${c.id}/audits`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(errorBody(503, 'LLM_UNAVAILABLE', 'down'), { status: 503 });
      }),
    );
    const user = userEvent.setup();
    renderApp(`/contracts/${c.id}?tab=audit`);

    await user.click(await screen.findByRole('button', { name: /Quick demo/ }));

    expect(await screen.findByText(/language model is not available/)).toBeInTheDocument();
    expect(body).toEqual({ ruleCodes: ['LIABILITY_CAP', 'AUDIT_RIGHTS', 'DATA_PROTECTION'] });
  });
});
