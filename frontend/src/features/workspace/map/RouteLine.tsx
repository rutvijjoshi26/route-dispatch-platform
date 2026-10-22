import type { LeafletEventHandlerFnMap, PathOptions } from 'leaflet';
import { memo, useMemo } from 'react';
import { Polyline } from 'react-leaflet';
import type { LatLonTuple } from '../../../api/types';

interface RouteLineProps {
  vehicleId: number;
  path: LatLonTuple[];
  color: string;
  selected: boolean;
  dimmed: boolean;
  changed: boolean;
  onSelect: (vehicleId: number) => void;
}

/**
 * One route's road geometry. Options and handlers are memoized because react-leaflet restyles
 * and rebinds a path whenever their references change, and these paths hold thousands of points.
 */
function RouteLineView({ vehicleId, path, color, selected, dimmed, changed, onSelect }: RouteLineProps) {
  const pathOptions = useMemo<PathOptions>(
    () => ({
      color,
      weight: selected || changed ? 6 : 4,
      opacity: dimmed ? 0.18 : 0.85,
      className: changed ? 'route-line-changed' : 'route-line',
    }),
    [changed, color, dimmed, selected],
  );
  const eventHandlers = useMemo<LeafletEventHandlerFnMap>(
    () => ({ click: () => onSelect(vehicleId) }),
    [onSelect, vehicleId],
  );
  return <Polyline positions={path} pathOptions={pathOptions} eventHandlers={eventHandlers} />;
}

export const RouteLine = memo(RouteLineView);
