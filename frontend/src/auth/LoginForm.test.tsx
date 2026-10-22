import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { login } from '../api/endpoints';
import { jsonResponse } from '../test/utils';
import { LoginForm } from './LoginForm';

describe('LoginForm', () => {
  it('submits the entered credentials', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<LoginForm onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText('Email'), ' dispatcher@demo.local ');
    await userEvent.type(screen.getByLabelText('Password'), 'dispatch-demo');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(onSubmit).toHaveBeenCalledWith({ email: 'dispatcher@demo.local', password: 'dispatch-demo' });
  });

  it('shows the server message when the login is rejected with 401', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ title: 'Unauthorized', status: 401, detail: 'Wrong email or password' }, 401),
    );
    render(<LoginForm onSubmit={login} />);

    await userEvent.type(screen.getByLabelText('Email'), 'dispatcher@demo.local');
    await userEvent.type(screen.getByLabelText('Password'), 'nope');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong email or password');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/api/auth/login');
    expect(JSON.parse(String(init?.body))).toEqual({ email: 'dispatcher@demo.local', password: 'nope' });
  });
});
