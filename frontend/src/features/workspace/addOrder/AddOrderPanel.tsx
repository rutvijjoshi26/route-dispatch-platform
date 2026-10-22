import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { addOrder } from '../../../api/endpoints';
import type { NewOrderRequest, Plan, Stop } from '../../../api/types';
import { defaultWindow, suggestOrderRef } from '../../../lib/orders';
import { clockSecondsAt, type ClockAnchor } from '../../../lib/planClock';
import { AddOrderForm } from './AddOrderForm';
import { NearestVans } from './NearestVans';
import type { MapPoint } from './validation';

interface AddOrderPanelProps {
  plan: Plan;
  point: MapPoint | null;
  clock: ClockAnchor | null;
  onAdded: (stop: Stop) => void;
  onCancel: () => void;
}

export function AddOrderPanel({ plan, point, clock, onAdded, onCancel }: AddOrderPanelProps) {
  const live = plan.status === 'DISPATCHED';
  // The default window is chosen once, from plan time when the panel opens.
  const [initialWindow] = useState(() => defaultWindow(live && clock ? clockSecondsAt(clock, Date.now()) : null));
  const create = useMutation({
    mutationFn: (order: NewOrderRequest) => addOrder(plan.id, order),
    onSuccess: onAdded,
  });
  const vehicleNames = new Map(plan.routes.map((route) => [route.vehicleId, route.vehicleName]));

  return (
    <aside className="side-panel add-order-panel" aria-labelledby="add-order-title">
      <header className="panel-header">
        <h2 id="add-order-title">Add order</h2>
        <p className="muted small">
          {live
            ? 'Vans are on the road. The plan re-optimizes around this order and keeps stops drivers already reached.'
            : plan.version > 0
              ? 'The plan re-optimizes automatically once the order is saved.'
              : 'The order joins the plan; optimize when you are ready.'}
        </p>
      </header>
      <div className="tab-panel">
        <AddOrderForm
          point={point}
          suggestedRef={suggestOrderRef(plan)}
          defaultWindow={initialWindow}
          submitting={create.isPending}
          serverError={create.error}
          onSubmit={(order) => create.mutate(order)}
          onCancel={onCancel}
        />
        {live && point && <NearestVans planId={plan.id} point={point} vehicleNames={vehicleNames} />}
      </div>
    </aside>
  );
}
