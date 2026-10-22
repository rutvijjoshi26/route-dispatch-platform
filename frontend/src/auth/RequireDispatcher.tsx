import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuth } from './useAuth';

/** Gate for dispatcher pages: sends guests to login and tells drivers this console is not for them. */
export function RequireDispatcher({ children }: { children: ReactNode }) {
  const { session, logout } = useAuth();
  const location = useLocation();

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (session.role !== 'DISPATCHER') {
    return (
      <main className="centered-page">
        <section className="card notice-card" aria-labelledby="driver-notice-title">
          <h1 id="driver-notice-title">This console is for dispatchers</h1>
          <p>
            You are signed in as <strong>{session.displayName}</strong>, a driver account. Drivers receive their
            route and report deliveries through the driver app. Sign in with a dispatcher account to plan and monitor
            routes.
          </p>
          <button type="button" className="button button-primary" onClick={logout}>
            Sign out
          </button>
        </section>
      </main>
    );
  }

  return children;
}
