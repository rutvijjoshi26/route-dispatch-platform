import { latLngBounds } from 'leaflet';
import { useEffect, useRef } from 'react';
import { useMap } from 'react-leaflet';
import type { Plan } from '../../../api/types';

function planPoints(plan: Plan): Array<[number, number]> {
  const stops = [...plan.routes.flatMap((route) => route.stops), ...plan.unassigned];
  return [[plan.depot.lat, plan.depot.lon], ...stops.map((stop): [number, number] => [stop.lat, stop.lon])];
}

/** Fits the view to depot and stops when a plan opens, and again the first time it gains stops. */
export function FitToPlan({ plan }: { plan: Plan }) {
  const map = useMap();
  const fitted = useRef<string | null>(null);
  const hasStops = plan.totals.stops > 0;

  useEffect(() => {
    const key = `${plan.id}:${hasStops}`;
    if (fitted.current === key) {
      return;
    }
    fitted.current = key;
    const points = planPoints(plan);
    if (points.length === 1) {
      map.setView(points[0] ?? [plan.depot.lat, plan.depot.lon], 13);
    } else {
      map.fitBounds(latLngBounds(points), { padding: [32, 32] });
    }
  }, [hasStops, map, plan]);

  return null;
}
