import type { Plan } from '../../../api/types';
import { actionAvailability, type PlanAction } from './actionAvailability';

interface PlanActionsProps {
  plan: Plan;
  pending: PlanAction | null;
  addingOrder: boolean;
  onAction: (action: PlanAction) => void;
}

const BUTTONS: Array<{ action: PlanAction; label: string; busyLabel: string; primary?: boolean }> = [
  { action: 'import', label: 'Import CSV', busyLabel: 'Importing…' },
  { action: 'addOrder', label: 'Add order', busyLabel: 'Adding…' },
  { action: 'optimize', label: 'Optimize', busyLabel: 'Starting…', primary: true },
  { action: 'dispatch', label: 'Dispatch', busyLabel: 'Dispatching…', primary: true },
  { action: 'replan', label: 'Re-plan now', busyLabel: 'Requesting…' },
];

export function PlanActions({ plan, pending, addingOrder, onAction }: PlanActionsProps) {
  return (
    <div className="plan-actions" role="toolbar" aria-label="Plan actions">
      {BUTTONS.map(({ action, label, busyLabel, primary }) => {
        const { visible, enabled } = actionAvailability(plan, action);
        if (!visible) {
          return null;
        }
        const active = action === 'addOrder' && addingOrder;
        return (
          <button
            key={action}
            type="button"
            className={`button${primary ? ' button-primary' : ''}${active ? ' button-active' : ''}`}
            disabled={!enabled || pending !== null}
            aria-pressed={action === 'addOrder' ? addingOrder : undefined}
            onClick={() => onAction(action)}
          >
            {pending === action ? busyLabel : label}
          </button>
        );
      })}
    </div>
  );
}
