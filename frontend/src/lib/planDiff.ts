import type { Plan } from '../api/types';

function sequences(plan: Plan): Map<number, string> {
  return new Map(plan.routes.map((route) => [route.vehicleId, route.stops.map((stop) => stop.id).join(',')]));
}

/** Vehicle ids whose ordered stop list differs between two versions of a plan. */
export function changedVehicleIds(previous: Plan, next: Plan): number[] {
  const before = sequences(previous);
  const after = sequences(next);
  const vehicleIds = new Set([...before.keys(), ...after.keys()]);
  return [...vehicleIds]
    .filter((vehicleId) => (before.get(vehicleId) ?? '') !== (after.get(vehicleId) ?? ''))
    .sort((a, b) => a - b);
}
