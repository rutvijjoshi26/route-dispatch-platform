import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../test/utils';
import { ApiError, configureClient, request } from './client';

describe('request', () => {
  afterEach(() => configureClient({ getToken: () => null, onUnauthorized: () => {} }));

  it('attaches the bearer token and JSON body', async () => {
    configureClient({ getToken: () => 'secret-token' });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ id: 3 }));

    const result = await request<{ id: number }>('/api/plans', { method: 'POST', body: { name: 'Monday' } });

    expect(result).toEqual({ id: 3 });
    const init = fetchMock.mock.calls[0]?.[1];
    const headers = new Headers(init?.headers);
    expect(headers.get('Authorization')).toBe('Bearer secret-token');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(init?.body).toBe('{"name":"Monday"}');
  });

  it('maps a problem detail to an ApiError with status, detail and errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(
        {
          title: 'Bad Request',
          status: 400,
          detail: 'Validation failed',
          errors: [{ field: 'orderRef', message: 'must not be blank' }],
        },
        400,
      ),
    );

    const error = await request('/api/plans/1/orders', { method: 'POST', body: {} }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.detail).toBe('Validation failed');
    expect(apiError.fieldErrors).toEqual([{ field: 'orderRef', message: 'must not be blank' }]);
    expect(apiError.rowErrors).toEqual([]);
  });

  it('signals the session on 401 when a token was sent', async () => {
    const onUnauthorized = vi.fn();
    configureClient({ getToken: () => 'expired', onUnauthorized });
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ status: 401, title: 'Unauthorized' }, 401));

    await expect(request('/api/plans')).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('ignores a late 401 for a token that is no longer the current session', async () => {
    const onUnauthorized = vi.fn();
    let current = 'old-token';
    configureClient({ getToken: () => current, onUnauthorized });
    let respond: (response: Response) => void = () => {};
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise((resolve) => (respond = resolve)));

    const pending = request('/api/plans');
    current = 'new-token';
    respond(jsonResponse({ status: 401, title: 'Unauthorized' }, 401));

    await expect(pending).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('omits the authorization header without a session and sends multipart bodies as-is', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ imported: 2 }));
    const form = new FormData();
    form.append('file', new Blob(['a,b']), 'orders.csv');

    await request('/api/plans/1/orders/import', { method: 'POST', body: form });

    const init = fetchMock.mock.calls[0]?.[1];
    const headers = new Headers(init?.headers);
    expect(headers.has('Authorization')).toBe(false);
    expect(headers.has('Content-Type')).toBe(false);
    expect(init?.body).toBe(form);
  });
});
