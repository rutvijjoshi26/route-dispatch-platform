import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <main className="centered-page">
      <section className="card notice-card">
        <h1>Page not found</h1>
        <p className="muted">The page you were looking for does not exist.</p>
        <Link to="/plans" className="button button-primary">
          Go to plans
        </Link>
      </section>
    </main>
  );
}
