import type { Plan, Stop } from '../api/types';
import type { StopEvent } from '../live/messages';

function patchStop(stop: Stop, event: StopEvent): Stop {
  const finished = event.status === 'DELIVERED' || event.status === 'FAILED';
  return {
    ...stop,
    status: event.status,
    actualArrival: stop.actualArrival ?? (event.status === 'ARRIVED' || finished ? event.at : null),
    actualDeparture: finished ? event.at : stop.actualDeparture,
  };
}

/** Applies a driver stop event to a cached plan without refetching it. */
export function applyStopEvent(plan: Plan, event: StopEvent): Plan {
  let found = false;
  const routes = plan.routes.map((route) => {
    if (!route.stops.some((stop) => stop.id === event.stopId)) {
      return route;
    }
    found = true;
    return { ...route, stops: route.stops.map((stop) => (stop.id === event.stopId ? patchStop(stop, event) : stop)) };
  });
  if (!found) {
    return plan;
  }

  const stops = routes.flatMap((route) => route.stops);
  return {
    ...plan,
    routes,
    totals: {
      ...plan.totals,
      delivered: stops.filter((stop) => stop.status === 'DELIVERED').length,
      failed: stops.filter((stop) => stop.status === 'FAILED').length,
    },
  };
}
