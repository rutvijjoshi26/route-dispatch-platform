import type { FieldError, ProblemDetail, RowError } from './types';

/** An HTTP error carrying the server's problem details. */
export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail: string;
  readonly errors: Array<FieldError | RowError>;

  constructor(status: number, problem: ProblemDetail) {
    const detail = problem.detail ?? problem.title ?? `Request failed with status ${status}`;
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.title = problem.title ?? 'Request failed';
    this.detail = detail;
    this.errors = problem.errors ?? [];
  }

  get fieldErrors(): FieldError[] {
    return this.errors.filter((e): e is FieldError => 'field' in e);
  }

  get rowErrors(): RowError[] {
    return this.errors.filter((e): e is RowError => 'row' in e);
  }
}

interface ClientConfig {
  getToken: () => string | null;
  onUnauthorized: () => void;
}

const config: ClientConfig = {
  getToken: () => null,
  onUnauthorized: () => {},
};

/** Wires the client to the session: where the token comes from and what a 401 does. */
export function configureClient(next: Partial<ClientConfig>): void {
  Object.assign(config, next);
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, signal } = options;
  const headers = new Headers({ Accept: 'application/json' });
  const token = config.getToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  let payload: BodyInit | undefined;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers.set('Content-Type', 'application/json');
    payload = JSON.stringify(body);
  }

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body: payload, signal });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause;
    }
    throw new ApiError(0, { title: 'Network error', detail: 'Cannot reach the server. Check your connection and try again.' });
  }

  if (!response.ok) {
    const error = new ApiError(response.status, await readProblem(response));
    // Only end the session the request belonged to; a late 401 for an old token must not log out a new one.
    if (response.status === 401 && token !== null && token === config.getToken()) {
      config.onUnauthorized();
    }
    throw error;
  }

  if (response.status === 204 || response.status === 202) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function readProblem(response: Response): Promise<ProblemDetail> {
  try {
    const parsed: unknown = await response.json();
    if (parsed && typeof parsed === 'object') {
      return parsed as ProblemDetail;
    }
  } catch {
    // Body was not JSON; fall back to the status text.
  }
  return { title: response.statusText || 'Request failed', status: response.status };
}
