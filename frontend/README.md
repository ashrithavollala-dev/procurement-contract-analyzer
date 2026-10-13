# Frontend: React 18 + TypeScript + Vite

A self-contained Vite project. It talks to the backend only through `/api/v1`, as specified in
`../docs/API_CONTRACT.md`. The types in `src/types/api.ts` mirror that contract exactly.

## Stack (exact pins in package.json)

React 18.3, TypeScript 5.9 (strict), Vite 6.4, Tailwind CSS 3.4 (design tokens in
`tailwind.config.js`), Axios, TanStack Query 5, React Router 6, react-hook-form + Zod, Lucide
React, Vitest 3 + React Testing Library + MSW 2, ESLint 9.

`engines.node` is `>=20`. Validated locally on Node 24.17; CI uses Node 20. All dependencies
support Node 20. For example, `@testing-library/jest-dom` is pinned to 6.9.1 because 6.10
requires Node 22.

## Commands

```bash
npm ci              # install from package-lock.json
npm run dev         # http://localhost:5176 (strictPort), proxies /api -> http://localhost:8081
npm run lint        # ESLint, zero warnings allowed
npm run typecheck   # tsc --noEmit
npm test            # Vitest (jsdom, MSW), 20 tests
npm run build       # typecheck + production build into dist/
```

To proxy to a different backend in dev:
`VITE_DEV_PROXY_TARGET=http://localhost:8080 npm run dev`.

## Configuration (build time)

| Variable | Default | Meaning |
|---|---|---|
| `VITE_API_BASE_URL` | `/api/v1` | same-origin API base (Vite proxy in dev, Nginx in Docker) |
| `VITE_POLL_INTERVAL_MS` | `2000` | polling interval while ingestion or audits are in flight |
| `VITE_MAX_UPLOAD_MB` | `25` | client-side upload size check (the backend re-validates) |

## Structure

```
src/api            Axios instance + global error interceptor, one module per resource, query keys
src/types          contract types (api.ts) and friendly labels/tones (labels.ts)
src/notifications  ToastProvider (maps error codes to friendly text, validation details per field)
src/components     badges, cards, skeletons, empty states, modal/confirm, meters (score bar, progress, risk gauge),
                   pagination, error boundary, LLM status banner
src/layouts        responsive sidebar layout
src/features       dashboard, contracts (list/upload/detail/overview), audit, ask, search, rules
tests              Vitest + RTL + MSW tests and fixtures
```

Behaviour worth knowing:
- The contracts list and the contract detail page poll only while something is QUEUED or
  PROCESSING. Audits poll only while QUEUED or RUNNING.
- The Ask call has a 300 s timeout, because CPU-only local models are slow. Starting an
  audit returns 202 immediately.
- Errors: the UI branches on `code` and `status`, never on the message text. Unhandled errors
  become toasts. Upload, Audit-start and Ask render their errors inline: for example,
  DUPLICATE_CONTRACT shows a link to the existing contract.

## Docker

`Dockerfile`: a `node:20.19.5-alpine` build stage, then `nginx:1.27.5-alpine`. `nginx.conf`
is installed as an envsubst template and provides:
- SPA fallback;
- `/api` proxied to `${BACKEND_UPSTREAM}` (default `backend:8080`);
- `client_max_body_size 30m` and `proxy_read_timeout 300s`;
- `/healthz`.

`vite dev` is never used in the container.
