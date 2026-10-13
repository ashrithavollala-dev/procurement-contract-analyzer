// Runtime configuration from Vite env variables (build time). See frontend/README.md.

export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || '/api/v1';

/** Polling interval for in-flight ingestion and audits. */
export const POLL_INTERVAL_MS: number = Number(import.meta.env.VITE_POLL_INTERVAL_MS || 2000);

/** Default HTTP timeout; the Ask call overrides it because CPU-only models are slow. */
export const DEFAULT_TIMEOUT_MS = 30_000;
export const ASK_TIMEOUT_MS = 300_000;
export const UPLOAD_TIMEOUT_MS = 120_000;

/** Must not exceed the backend's UPLOAD_MAX_FILE_SIZE (default 25 MB). */
export const MAX_UPLOAD_BYTES: number = Number(import.meta.env.VITE_MAX_UPLOAD_MB || 25) * 1024 * 1024;

/** Rule codes used by the "quick demo" audit, which is small enough for a CPU-only 3B model. */
export const DEMO_RULE_CODES = ['LIABILITY_CAP', 'AUDIT_RIGHTS', 'DATA_PROTECTION'];
