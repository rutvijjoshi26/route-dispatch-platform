import { useEffect, useRef, type CSSProperties } from 'react';
import type { Route } from '../../../api/types';
import { formatHoursMinutes, formatKm, plural } from '../../../lib/format';
import type { ClockAnchor } from '../../../lib/planClock';
import { shortTime } from '../../../lib/time';
import { RouteTimeline } from './RouteTimeline';

interface RouteCardProps {
  route: Route;
  color: string;
  selected: boolean;
  changed: boolean;
  clock: ClockAnchor | null;
  canShowTrail: boolean;
  showTrail: boolean;
  onSelect: (vehicleId: number) => void;
  onToggleTrail: (show: boolean) => void;
}

export function RouteCard({
  route,
  color,
  selected,
  changed,
  clock,
  canShowTrail,
  showTrail,
  onSelect,
  onToggleTrail,
}: RouteCardProps) {
  const card = useRef<HTMLElement>(null);

  // Selecting a route on the map brings its card into view.
  useEffect(() => {
    if (selected) {
      card.current?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' });
    }
  }, [selected]);

  const loadPercent = route.capacity > 0 ? Math.min(100, (route.load / route.capacity) * 100) : 0;
  const done = route.stops.filter((stop) => stop.status === 'DELIVERED' || stop.status === 'FAILED').length;
  const classes = ['route-card', selected && 'route-card-selected', changed && 'route-card-changed']
    .filter(Boolean)
    .join(' ');

  return (
    <article ref={card} className={classes} style={{ '--route-color': color } as CSSProperties}>
      <button type="button" className="route-card-header" aria-pressed={selected} onClick={() => onSelect(route.vehicleId)}>
        <span className="swatch" aria-hidden="true" />
        <span className="route-card-name">{route.vehicleName}</span>
        {changed && <span className="changed-tag">Changed</span>}
        <span className="route-card-count">
          {done > 0 ? `${done}/${route.stops.length} done` : plural(route.stops.length, 'stop')}
        </span>
      </button>

      <div className="load" title={`Load ${route.load} of ${route.capacity}`}>
        <div
          className="load-bar"
          role="meter"
          aria-label={`${route.vehicleName} load`}
          aria-valuemin={0}
          aria-valuemax={route.capacity}
          aria-valuenow={route.load}
        >
          <span style={{ width: `${loadPercent}%` }} />
        </div>
        <span className="load-text">
          {route.load}/{route.capacity}
        </span>
      </div>

      <dl className="route-stats">
        <div>
          <dt>Distance</dt>
          <dd>{formatKm(route.distanceMeters)}</dd>
        </div>
        <div>
          <dt>Drive</dt>
          <dd>{formatHoursMinutes(route.travelSeconds)}</dd>
        </div>
        <div>
          <dt>Depart</dt>
          <dd>{shortTime(route.departDepot)}</dd>
        </div>
        <div>
          <dt>Return</dt>
          <dd>{shortTime(route.returnDepot)}</dd>
        </div>
      </dl>

      <RouteTimeline route={route} clock={clock} />

      {selected && canShowTrail && (
        <label className="checkbox">
          <input type="checkbox" checked={showTrail} onChange={(event) => onToggleTrail(event.target.checked)} />
          Show driven GPS trail
        </label>
      )}
    </article>
  );
}
