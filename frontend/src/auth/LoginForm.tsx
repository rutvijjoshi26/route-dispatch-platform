import { useState, type FormEvent } from 'react';
import type { LoginRequest } from '../api/types';
import { errorMessage } from '../lib/errors';

interface LoginFormProps {
  onSubmit: (credentials: LoginRequest) => Promise<unknown>;
}

export function LoginForm({ onSubmit }: LoginFormProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({ email: email.trim(), password });
    } catch (cause) {
      setError(errorMessage(cause));
      setSubmitting(false);
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} aria-describedby={error ? 'login-error' : undefined}>
      <label className="field">
        <span className="field-label">Email</span>
        <input
          type="email"
          autoComplete="username"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
      </label>
      <label className="field">
        <span className="field-label">Password</span>
        <input
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
      </label>
      {error && (
        <p id="login-error" className="form-error" role="alert">
          {error}
        </p>
      )}
      <button type="submit" className="button button-primary button-block" disabled={submitting}>
        {submitting ? 'Signing in…' : 'Sign in'}
      </button>
    </form>
  );
}
