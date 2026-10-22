import { useQuery } from '@tanstack/react-query';
import { fetchPlans } from '../../api/endpoints';
import { queryKeys } from '../../api/queryKeys';
import { AppHeader } from '../../components/AppHeader';
import { EmptyState, ErrorState, LoadingState } from '../../components/StateMessages';
import { errorMessage } from '../../lib/errors';
import { NewPlanForm } from './NewPlanForm';
import { PlansTable } from './PlansTable';

export function PlansPage() {
  const plans = useQuery({ queryKey: queryKeys.plans, queryFn: fetchPlans });

  return (
    <div className="page">
      <AppHeader />
      <main className="page-content plans-layout">
        <section className="card" aria-labelledby="plans-title">
          <div className="card-header">
            <h1 id="plans-title">Delivery plans</h1>
            {plans.data && <span className="muted">{plans.data.length} total</span>}
          </div>
          {plans.isPending && <LoadingState label="Loading plans…" />}
          {plans.isError && (
            <ErrorState
              title="Could not load plans"
              message={errorMessage(plans.error)}
              onRetry={() => void plans.refetch()}
            />
          )}
          {plans.data?.length === 0 && (
            <EmptyState title="No plans yet">
              <span>Create a plan, then import orders or add them on the map.</span>
            </EmptyState>
          )}
          {plans.data && plans.data.length > 0 && <PlansTable plans={plans.data} />}
        </section>
        <aside className="card">
          <NewPlanForm />
        </aside>
      </main>
    </div>
  );
}
