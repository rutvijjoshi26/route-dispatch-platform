import { Link } from 'react-router';
import { useAuth } from '../auth/useAuth';

export function AppHeader() {
  const { session, logout } = useAuth();
  return (
    <header className="app-header">
      <Link to="/plans" className="brand">
        <span className="brand-mark" aria-hidden="true" />
        Route Dispatch
      </Link>
      <nav className="app-nav" aria-label="Main">
        <Link to="/plans">Plans</Link>
      </nav>
      {session && (
        <div className="app-user">
          <span className="muted">{session.displayName}</span>
          <button type="button" className="button button-ghost button-small" onClick={logout}>
            Sign out
          </button>
        </div>
      )}
    </header>
  );
}
