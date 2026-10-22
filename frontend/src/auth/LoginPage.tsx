import { Navigate, useLocation } from 'react-router';
import { LoginForm } from './LoginForm';
import { useAuth } from './useAuth';

export function LoginPage() {
  const { session, login } = useAuth();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/plans';

  if (session) {
    return <Navigate to={from} replace />;
  }

  return (
    <main className="centered-page login-page">
      <section className="card login-card" aria-labelledby="login-title">
        <div className="brand brand-large">
          <span className="brand-mark" aria-hidden="true" />
          Route Dispatch
        </div>
        <h1 id="login-title">Sign in</h1>
        <p className="muted">Plan delivery routes and follow your vans live.</p>
        <LoginForm onSubmit={login} />
        <p className="login-hint">
          Demo dispatcher: <code>dispatcher@demo.local</code> / <code>dispatch-demo</code>
        </p>
      </section>
    </main>
  );
}
