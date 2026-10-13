import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { API, contract, errorBody } from './fixtures';
import { renderApp } from './renderApp';
import { server } from './server';

function pdf(name = 'contract.pdf', size = 1024): File {
  return new File([new Uint8Array(size).fill(37)], name, { type: 'application/pdf' });
}

describe('Upload page', () => {
  it('rejects a non-PDF file on the client without calling the API', async () => {
    let uploads = 0;
    server.use(http.post(`${API}/contracts`, () => {
      uploads++;
      return HttpResponse.json(contract(), { status: 202 });
    }));
    const user = userEvent.setup({ applyAccept: false });
    renderApp('/upload');

    await user.upload(screen.getByLabelText('Contract PDF'), new File(['hello'], 'notes.txt', { type: 'text/plain' }));
    await user.click(screen.getByRole('button', { name: 'Upload' }));

    expect(await screen.findByText('Only PDF files are supported')).toBeInTheDocument();
    expect(uploads).toBe(0);
  });

  it('rejects an empty file on the client', async () => {
    const user = userEvent.setup();
    renderApp('/upload');

    await user.upload(screen.getByLabelText('Contract PDF'), pdf('empty.pdf', 0));
    await user.click(screen.getByRole('button', { name: 'Upload' }));

    expect(await screen.findByText('The file is empty')).toBeInTheDocument();
  });

  it('shows a link to the existing contract on DUPLICATE_CONTRACT', async () => {
    const existingId = '11111111-2222-3333-4444-555555555555';
    server.use(http.post(`${API}/contracts`, () =>
      HttpResponse.json(errorBody(409, 'DUPLICATE_CONTRACT', 'This file has already been uploaded', { existingContractId: existingId }), {
        status: 409,
      })));
    const user = userEvent.setup();
    renderApp('/upload');

    await user.upload(screen.getByLabelText('Contract PDF'), pdf());
    await user.click(screen.getByRole('button', { name: 'Upload' }));

    expect(await screen.findByText('This exact file has already been uploaded.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open the existing contract' })).toHaveAttribute('href', `/contracts/${existingId}`);
    // Handled inline: no duplicate toast from the global interceptor.
    expect(screen.queryAllByRole('alert')).toHaveLength(1);
  });

  it('explains FILE_TOO_LARGE and INGESTION_QUEUE_FULL using the error code', async () => {
    server.use(http.post(`${API}/contracts`, () =>
      HttpResponse.json(errorBody(413, 'FILE_TOO_LARGE', 'server text'), { status: 413 })));
    const user = userEvent.setup();
    renderApp('/upload');
    await user.upload(screen.getByLabelText('Contract PDF'), pdf());
    await user.click(screen.getByRole('button', { name: 'Upload' }));
    expect(await screen.findByText('The file is larger than the upload limit.')).toBeInTheDocument();

    server.use(http.post(`${API}/contracts`, () =>
      HttpResponse.json(errorBody(503, 'INGESTION_QUEUE_FULL', 'server text', { contractId: 'abc' }), { status: 503 })));
    await user.click(screen.getByRole('button', { name: 'Upload' }));
    expect(await screen.findByText('The processing queue is full. Try again in a moment.')).toBeInTheDocument();
  });

  it('uploads a valid PDF and navigates to the contract', async () => {
    const created = contract({ id: '99999999-0000-0000-0000-000000000001', status: 'QUEUED', pageCount: null, chunkCount: null });
    // jsdom's XMLHttpRequest does not serialise FormData like a browser, so the test checks the
    // call itself; the multipart contract is covered by the backend's MockMvc tests.
    let uploadCalls = 0;
    server.use(
      http.post(`${API}/contracts`, () => {
        uploadCalls++;
        return HttpResponse.json(created, { status: 202 });
      }),
      http.get(`${API}/contracts/${created.id}`, () => HttpResponse.json({ ...created, status: 'READY' })),
    );
    const user = userEvent.setup();
    renderApp('/upload');

    await user.upload(screen.getByLabelText('Contract PDF'), pdf());
    await user.click(screen.getByRole('button', { name: 'Upload' }));

    await waitFor(() => expect(screen.getByRole('heading', { name: created.title })).toBeInTheDocument());
    expect(uploadCalls).toBe(1);
  });
});
