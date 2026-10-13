import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { API, contract, errorBody } from './fixtures';
import { renderApp } from './renderApp';
import { server } from './server';

const c = contract();

async function ask(question: string) {
  const user = userEvent.setup();
  renderApp(`/contracts/${c.id}?tab=ask`);
  await user.type(await screen.findByLabelText('Question'), question);
  await user.click(screen.getByRole('button', { name: /Ask/ }));
}

describe('Ask tab', () => {
  it('renders a grounded answer with citation chips', async () => {
    server.use(
      http.get(`${API}/contracts/${c.id}`, () => HttpResponse.json(c)),
      http.post(`${API}/contracts/${c.id}/ask`, () =>
        HttpResponse.json({
          answer: 'Sixty days written notice (page 3).',
          grounded: true,
          mode: 'rag',
          citations: [{ page: 3, chunkIndex: 14, snippet: '10.3 The Customer may terminate for convenience...', score: 0.71 }],
        })),
    );

    await ask('How much notice is needed?');

    expect(await screen.findByText('Sixty days written notice (page 3).')).toBeInTheDocument();
    expect(screen.getByText('p.3')).toBeInTheDocument();
    expect(screen.queryByText(/Not grounded/)).not.toBeInTheDocument();
  });

  it('shows a clear warning when the answer is not grounded', async () => {
    server.use(
      http.get(`${API}/contracts/${c.id}`, () => HttpResponse.json(c)),
      http.post(`${API}/contracts/${c.id}/ask`, () =>
        HttpResponse.json({ answer: 'I cannot find this in the contract.', grounded: false, mode: 'rag', citations: [] })),
    );

    await ask('What colour is the logo?');

    expect(await screen.findByText(/Not grounded: no relevant passage was found/)).toBeInTheDocument();
    expect(screen.getByText('I cannot find this in the contract.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Citations')).not.toBeInTheDocument();
  });

  it('explains LLM_UNAVAILABLE', async () => {
    server.use(
      http.get(`${API}/contracts/${c.id}`, () => HttpResponse.json(c)),
      http.post(`${API}/contracts/${c.id}/ask`, () =>
        HttpResponse.json(errorBody(503, 'LLM_UNAVAILABLE', 'down'), { status: 503 })),
    );

    await ask('Which law governs?');

    expect(await screen.findByText(/language model is not available/)).toBeInTheDocument();
  });
});
