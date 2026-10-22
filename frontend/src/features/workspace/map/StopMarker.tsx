import { memo } from 'react';
import { CircleMarker, Marker, Popup, Tooltip } from 'react-leaflet';
import type { Stop } from '../../../api/types';
import { STOP_STATUS_LABELS, stopVisualState } from '../../../lib/stopState';
import { deliveredIcon } from './icons';
import { StopDetails } from './StopDetails';
import { stopStyle } from './stopStyle';

interface StopMarkerProps {
  stop: Stop;
  color: string;
  vehicleName: string | null;
  dimmed: boolean;
}

function StopMarkerView({ stop, color, vehicleName, dimmed }: StopMarkerProps) {
  const state = stopVisualState(stop);
  const overlays = (
    <>
      <Tooltip direction="top" offset={[0, -6]}>
        {stop.orderRef} · {STOP_STATUS_LABELS[stop.status]}
      </Tooltip>
      <Popup>
        <StopDetails stop={stop} vehicleName={vehicleName} />
      </Popup>
    </>
  );

  if (state === 'delivered') {
    return (
      <Marker position={[stop.lat, stop.lon]} icon={deliveredIcon(color, dimmed)} keyboard={false}>
        {overlays}
      </Marker>
    );
  }

  const { radius, pathOptions } = stopStyle(state, color, dimmed);
  return (
    <CircleMarker center={[stop.lat, stop.lon]} radius={radius} pathOptions={pathOptions}>
      {overlays}
    </CircleMarker>
  );
}

/** Memoized: a stop only re-renders when its own data or styling inputs change. */
export const StopMarker = memo(StopMarkerView);
