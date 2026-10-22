import type { Plan } from '../../../api/types';
import { EmptyState } from '../../../components/StateMessages';
import { routeColor } from '../../../lib/colors';
import type { ClockAnchor } from '../../../lib/planClock';
import { RouteCard } from './RouteCard';

interface RoutesTabProps {
  plan: Plan;
  selectedVehicleId: number | null;
  changedVehicles: ReadonlySet<number>;
  clock: ClockAnchor | null;
  showTrail: boolean;
  onSelect: (vehicleId: number) => void;
  onToggleTrail: (show: boolean) => void;
}

export function RoutesTab({
  plan,
  selectedVehicleId,
  changedVehicles,
  clock,
  showTrail,
  onSelect,
  onToggleTrail,
}: RoutesTabProps) {
  const routes = plan.routes.filter((route) => route.stops.length > 0);
  if (routes.length === 0) {
    return (
      <EmptyState title="No routes yet">
        <span>
          {plan.totals.stops === 0
            ? 'Import a CSV or add orders on the map, then optimize.'
            : 'Optimize the plan to assign orders to vans.'}
        </span>
      </EmptyState>
    );
  }
  const canShowTrail = plan.status === 'DISPATCHED' || plan.status === 'COMPLETED';
  return (
    <div className="route-list">
      {routes.map((route) => (
        <RouteCard
          key={route.vehicleId}
          route={route}
          color={routeColor(route.vehicleId)}
          selected={route.vehicleId === selectedVehicleId}
          changed={changedVehicles.has(route.vehicleId)}
          clock={clock}
          canShowTrail={canShowTrail}
          showTrail={showTrail}
          onSelect={onSelect}
          onToggleTrail={onToggleTrail}
        />
      ))}
    </div>
  );
}
