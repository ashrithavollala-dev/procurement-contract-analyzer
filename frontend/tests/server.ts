import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { API, dashboard, page, rules, systemStatusOk } from './fixtures';

/** Default handlers; individual tests override them with server.use(...). */
export const server = setupServer(
  http.get(`${API}/system/status`, () => HttpResponse.json(systemStatusOk)),
  http.get(`${API}/dashboard/summary`, () => HttpResponse.json(dashboard)),
  http.get(`${API}/compliance-rules`, () => HttpResponse.json(rules)),
  http.get(`${API}/contracts`, () => HttpResponse.json(page([]))),
);
