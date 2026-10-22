import { useQuery } from '@tanstack/react-query';
import { routeColor } from '../../../lib/colors';
import { errorMessage } from '../../../lib/errors';
import { formatDistance } from '../../../lib/format';
import { nearestQuery } from '../queries';
import type { MapPoint } from './validation';

interface NearestVansProps {
  planId: number;
  point: MapPoint;
  vehicleNames: ReadonlyMap<number, string>;
}

export function NearestVans({ planId, point, vehicleNames }: NearestVansProps) {
  const nearest = useQuery(nearestQuery(planId, point.lat, point.lon));

  return (
    <section className="nearest" aria-labelledby="nearest-title">
      <h3 id="nearest-title" className="field-label">
        Nearest vans right now
      </h3>
      {nearest.isPending && <p className="muted small">Finding vans…</p>}
      {nearest.isError && <p className="form-error small">{errorMessage(nearest.error)}</p>}
      {nearest.data?.length === 0 && <p className="muted small">No van has reported a position yet.</p>}
      {nearest.data && nearest.data.length > 0 && (
        <ol className="nearest-list">
          {nearest.data.map((van) => (
            <li key={van.vehicleId}>
              <span className="swatch" style={{ background: routeColor(van.vehicleId) }} aria-hidden="true" />
              {vehicleNames.get(van.vehicleId) ?? `Van ${van.vehicleId}`}
              <span className="muted">{formatDistance(van.distanceMeters)}</span>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
