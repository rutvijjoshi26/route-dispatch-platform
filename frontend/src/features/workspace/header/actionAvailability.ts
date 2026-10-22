import type { Plan } from '../../../api/types';

export type PlanAction = 'import' | 'optimize' | 'dispatch' | 'replan' | 'addOrder';

export interface ActionAvailability {
  visible: boolean;
  enabled: boolean;
}

/** Which actions a plan's status allows, and whether a running solve blocks them for now. */
export function actionAvailability(plan: Plan, action: PlanAction): ActionAvailability {
  const busy = plan.optimizing || plan.status === 'PLANNING';
  const editable = plan.status === 'DRAFT' || plan.status === 'PLANNED';
  switch (action) {
    case 'import':
      return { visible: editable, enabled: !busy };
    case 'optimize':
      return { visible: editable, enabled: !busy && plan.totals.stops > 0 };
    case 'dispatch':
      return { visible: plan.status === 'PLANNED', enabled: !busy && plan.totals.assigned > 0 };
    case 'replan':
      return { visible: plan.status === 'DISPATCHED', enabled: !busy };
    case 'addOrder':
      return { visible: editable || plan.status === 'DISPATCHED', enabled: plan.status !== 'PLANNING' };
  }
}
