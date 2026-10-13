import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { API, systemStatusOk } from './fixtures';
import { renderApp } from './renderApp';
import { server } from './server';

describe('LLM status banner', () => {
  it('appears when /system/status reports the LLM unreachable', async () => {
    server.use(http.get(`${API}/system/status`, () =>
      HttpResponse.json({ ...systemStatusOk, llmReachable: false, llmDetail: 'Ollama not reachable at http://localhost:11434' })));
    renderApp('/');

    expect(await screen.findByText(/Language model unavailable \(ollama \/ llama3.2:3b\)/)).toBeInTheDocument();
    expect(screen.getByText(/uploads and semantic search keep working/)).toBeInTheDocument();
  });

  it('is hidden when the LLM is reachable', async () => {
    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(screen.queryByText(/Language model unavailable/)).not.toBeInTheDocument();
  });
});
