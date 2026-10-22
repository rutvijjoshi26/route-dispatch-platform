import type { PathOptions } from 'leaflet';
import { memo, useMemo } from 'react';
import { MapContainer, Marker, Polyline, TileLayer, Tooltip } from 'react-leaflet';
import type { LatLonTuple, Plan } from '../../../api/types';
import { routeColor, UNASSIGNED_COLOR } from '../../../lib/colors';
import type { PositionsStore } from '../../../live/positionsStore';
import type { MapPoint } from '../addOrder/validation';
import { FitToPlan } from './FitToPlan';
import { depotIcon } from './icons';
import { LocationPicker } from './LocationPicker';
import { RouteLines } from './RouteLines';
import { StopMarker } from './StopMarker';
import { VanMarkers } from './VanMarkers';

/** Dark so the driven trail stands out on top of the planned route in its own color. */
const TRAIL_OPTIONS: PathOptions = { color: '#111827', weight: 3, dashArray: '6 8', opacity: 0.9 };

export interface PlanMapProps {
  plan: Plan;
  positions: PositionsStore;
  selectedVehicleId: number | null;
  changedVehicles: ReadonlySet<number>;
  trail: LatLonTuple[] | null;
  picking: boolean;
  pickedPoint: MapPoint | null;
  onPick: (point: MapPoint) => void;
  onSelectRoute: (vehicleId: number) => void;
}

function PlanMapView({
  plan,
  positions,
  selectedVehicleId,
  changedVehicles,
  trail,
  picking,
  pickedPoint,
  onPick,
  onSelectRoute,
}: PlanMapProps) {
  const vehicleNames = useMemo(
    () => new Map(plan.routes.map((route) => [route.vehicleId, route.vehicleName])),
    [plan.routes],
  );

  return (
    <MapContainer className="plan-map" center={[plan.depot.lat, plan.depot.lon]} zoom={12} zoomControl>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        maxZoom={19}
      />
      <FitToPlan plan={plan} />
      <RouteLines
        routes={plan.routes}
        selectedVehicleId={selectedVehicleId}
        changedVehicles={changedVehicles}
        onSelect={onSelectRoute}
      />
      {trail && selectedVehicleId !== null && trail.length > 1 && (
        <Polyline
          positions={trail}
          pathOptions={TRAIL_OPTIONS}
          interactive={false}
        />
      )}
      {plan.routes.flatMap((route) =>
        route.stops.map((stop) => (
          <StopMarker
            key={stop.id}
            stop={stop}
            color={routeColor(route.vehicleId)}
            vehicleName={route.vehicleName}
            dimmed={selectedVehicleId !== null && selectedVehicleId !== route.vehicleId}
          />
        )),
      )}
      {plan.unassigned.map((stop) => (
        <StopMarker key={stop.id} stop={stop} color={UNASSIGNED_COLOR} vehicleName={null} dimmed={false} />
      ))}
      <Marker position={[plan.depot.lat, plan.depot.lon]} icon={depotIcon} zIndexOffset={500}>
        <Tooltip direction="top" offset={[0, -14]}>
          {plan.depot.name}
          <br />
          {plan.depot.address}
        </Tooltip>
      </Marker>
      <VanMarkers
        store={positions}
        vehicleNames={vehicleNames}
        selectedVehicleId={selectedVehicleId}
        onSelect={onSelectRoute}
      />
      {picking && <LocationPicker point={pickedPoint} onPick={onPick} />}
    </MapContainer>
  );
}

/** Memoized so workspace state that does not touch the map (banners, tabs, activity) skips it entirely. */
export const PlanMap = memo(PlanMapView);
