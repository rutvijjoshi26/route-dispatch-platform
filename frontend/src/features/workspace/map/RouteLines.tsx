import { memo } from 'react';
import type { Route } from '../../../api/types';
import { routeColor } from '../../../lib/colors';
import { RouteLine } from './RouteLine';

interface RouteLinesProps {
  routes: Route[];
  selectedVehicleId: number | null;
  changedVehicles: ReadonlySet<number>;
  onSelect: (vehicleId: number) => void;
}

function RouteLinesView({ routes, selectedVehicleId, changedVehicles, onSelect }: RouteLinesProps) {
  return routes
    .filter((route) => route.path.length > 1)
    .map((route) => {
      const changed = changedVehicles.has(route.vehicleId);
      return (
        <RouteLine
          // Leaflet cannot change a path's class after creation, so the highlight state is part of the key.
          key={`${route.vehicleId}-${changed ? 'changed' : 'steady'}`}
          vehicleId={route.vehicleId}
          path={route.path}
          color={routeColor(route.vehicleId)}
          selected={route.vehicleId === selectedVehicleId}
          dimmed={selectedVehicleId !== null && route.vehicleId !== selectedVehicleId}
          changed={changed}
          onSelect={onSelect}
        />
      );
    });
}

export const RouteLines = memo(RouteLinesView);
