import type { LatLngTuple, LeafletEventHandlerFnMap } from 'leaflet';
import { memo, useMemo } from 'react';
import { Marker, Tooltip } from 'react-leaflet';
import type { VehiclePosition } from '../../../api/types';
import { formatSpeed } from '../../../lib/format';
import { vanIcon } from './icons';

interface VanMarkerProps {
  position: VehiclePosition;
  name: string;
  label: string;
  color: string;
  selected: boolean;
  onSelect: (vehicleId: number) => void;
}

/** A live van; re-renders only when its own report or selection changes. */
function VanMarkerView({ position, name, label, color, selected, onSelect }: VanMarkerProps) {
  const { vehicleId, lat, lon } = position;
  const latLng = useMemo<LatLngTuple>(() => [lat, lon], [lat, lon]);
  const eventHandlers = useMemo<LeafletEventHandlerFnMap>(
    () => ({ click: () => onSelect(vehicleId) }),
    [onSelect, vehicleId],
  );
  return (
    <Marker
      position={latLng}
      icon={vanIcon(label, color, selected)}
      zIndexOffset={selected ? 2000 : 1000}
      eventHandlers={eventHandlers}
    >
      <Tooltip direction="top" offset={[0, -14]}>
        {name} · {formatSpeed(position.speedMps)}
      </Tooltip>
    </Marker>
  );
}

export const VanMarker = memo(VanMarkerView);
