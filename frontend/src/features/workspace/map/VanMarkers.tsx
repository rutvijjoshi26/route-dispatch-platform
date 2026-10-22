import { useSyncExternalStore } from 'react';
import { routeColor } from '../../../lib/colors';
import type { PositionsStore } from '../../../live/positionsStore';
import { VanMarker } from './VanMarker';

interface VanMarkersProps {
  store: PositionsStore;
  vehicleNames: ReadonlyMap<number, string>;
  selectedVehicleId: number | null;
  onSelect: (vehicleId: number) => void;
}

function vanLabel(name: string | undefined, vehicleId: number): string {
  return name?.match(/\d+/)?.[0] ?? String(vehicleId);
}

export function VanMarkers({ store, vehicleNames, selectedVehicleId, onSelect }: VanMarkersProps) {
  const positions = useSyncExternalStore(store.subscribe, store.getSnapshot);

  return [...positions.values()].map((position) => {
    const name = vehicleNames.get(position.vehicleId);
    return (
      <VanMarker
        key={position.vehicleId}
        position={position}
        name={name ?? `Van ${position.vehicleId}`}
        label={vanLabel(name, position.vehicleId)}
        color={routeColor(position.vehicleId)}
        selected={position.vehicleId === selectedVehicleId}
        onSelect={onSelect}
      />
    );
  });
}
