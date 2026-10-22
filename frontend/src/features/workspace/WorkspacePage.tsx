import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { AppHeader } from '../../components/AppHeader';
import { ErrorState, LoadingState } from '../../components/StateMessages';
import { errorMessage, isNotFound } from '../../lib/errors';
import { PlanWorkspace } from './PlanWorkspace';
import { planQuery } from './queries';

function PlanNotFound() {
  return (
    <section className="card notice-card">
      <h1>Plan not found</h1>
      <p className="muted">This plan does not exist or was removed.</p>
      <Link to="/plans" className="button button-primary">
        Back to plans
      </Link>
    </section>
  );
}

function Workspace({ planId }: { planId: number }) {
  const plan = useQuery(planQuery(planId));

  if (plan.data) {
    return <PlanWorkspace plan={plan.data} fetchedAt={plan.dataUpdatedAt} />;
  }
  return (
    <main className="centered-page">
      {plan.isPending && <LoadingState label="Loading plan…" />}
      {plan.isError &&
        (isNotFound(plan.error) ? (
          <PlanNotFound />
        ) : (
          <ErrorState title="Could not load the plan" message={errorMessage(plan.error)} onRetry={() => void plan.refetch()} />
        ))}
    </main>
  );
}

export function WorkspacePage() {
  const { planId } = useParams();
  const id = Number(planId);
  const valid = Number.isInteger(id) && id > 0;

  return (
    <div className="page page-full">
      <AppHeader />
      {valid ? (
        <Workspace key={id} planId={id} />
      ) : (
        <main className="centered-page">
          <PlanNotFound />
        </main>
      )}
    </div>
  );
}
