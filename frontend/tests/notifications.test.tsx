import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { ApiError, toApiError } from '../src/api/errors';
import { ERROR_MESSAGES, explainFailure, friendlyMessage } from '../src/notifications/errorMessages';
import { ERROR_CODES, type ErrorCode } from '../src/types/api';
import { API, errorBody } from './fixtures';
import { renderApp } from './renderApp';
import { server } from './server';

describe('error code mapping', () => {
  it('has friendly text for every backend error code', () => {
    for (const code of ERROR_CODES) {
      expect(ERROR_MESSAGES[code as ErrorCode]).toBeTruthy();
    }
    expect(friendlyMessage(new ApiError(503, 'LLM_UNAVAILABLE', 'raw server text'))).not.toContain('raw server text');
    expect(explainFailure('NO_EXTRACTABLE_TEXT')).toMatch(/OCR is not supported/);
    expect(explainFailure(null)).toMatch(/not recorded/);
  });

  it('normalises axios-less failures', () => {
    expect(toApiError(new Error('boom')).code).toBe('INTERNAL_ERROR');
  });
});

describe('global toast for unhandled API errors', () => {
  it('maps the error code to friendly text and lists validation details per field', async () => {
    server.use(http.get(`${API}/dashboard/summary`, () =>
      HttpResponse.json(errorBody(400, 'VALIDATION_FAILED', 'Request validation failed', { size: 'must be at most 100' }), {
        status: 400,
      })));
    renderApp('/');

    const title = await screen.findByText('Some fields need attention.');
    const toast = title.closest('[role="alert"]') as HTMLElement;
    expect(toast).not.toBeNull();
    expect(within(toast).getByText('size')).toBeInTheDocument();
    expect(within(toast).getByText(/must be at most 100/)).toBeInTheDocument();
  });

  it('shows the friendly message for server errors, not the raw message', async () => {
    server.use(http.get(`${API}/dashboard/summary`, () =>
      HttpResponse.json(errorBody(500, 'INTERNAL_ERROR', 'NullPointerException at line 42'), { status: 500 })));
    renderApp('/');

    expect(await screen.findByText('Something went wrong on the server.')).toBeInTheDocument();
    expect(screen.queryByText(/NullPointerException/)).not.toBeInTheDocument();
  });
});
