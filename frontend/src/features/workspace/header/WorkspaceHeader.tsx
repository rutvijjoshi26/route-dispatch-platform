import { Link } from 'react-router';
import type { Plan } from '../../../api/types';
import { StatusBadge } from '../../../components/StatusBadge';
import { formatServiceDate } from '../../../lib/format';
import type { ClockAnchor } from '../../../lib/planClock';
import type { ConnectionState } from '../../../live/usePlanTopics';
import { ConnectionIndicator } from './ConnectionIndicator';
import { KpiChips } from './KpiChips';
import { PlanActions } from './PlanActions';
import { PlanClockDisplay } from './PlanClockDisplay';
import type { PlanAction } from './actionAvailability';

interface WorkspaceHeaderProps {
  plan: Plan;
  clock: ClockAnchor | null;
  connection: ConnectionState;
  pendingAction: PlanAction | null;
  addingOrder: boolean;
  onAction: (action: PlanAction) => void;
}

export function WorkspaceHeader({
  plan,
  clock,
  connection,
  pendingAction,
  addingOrder,
  onAction,
}: WorkspaceHeaderProps) {
  return (
    <header className="workspace-header">
      <div className="workspace-title">
        <Link to="/plans" className="back-link" aria-label="Back to plans">
          ←
        </Link>
        <div>
          <h1>{plan.name}</h1>
          <p className="muted">
            {formatServiceDate(plan.serviceDate)} · {plan.depot.name}
          </p>
        </div>
        <StatusBadge status={plan.status} />
        <span className="version-tag" title="Plan version">
          v{plan.version}
        </span>
        {clock && <PlanClockDisplay anchor={clock} />}
        <ConnectionIndicator state={connection} />
      </div>
      <KpiChips totals={plan.totals} />
      <PlanActions plan={plan} pending={pendingAction} addingOrder={addingOrder} onAction={onAction} />
    </header>
  );
}
