import type { Stop } from '../api/types';

export type StopVisualState = 'delivered' | 'failed' | 'active' | 'late' | 'pending' | 'unassigned';

export function stopVisualState(stop: Stop): StopVisualState {
  if (stop.status === 'DELIVERED') return 'delivered';
  if (stop.status === 'FAILED') return 'failed';
  if (stop.vehicleId === null) return 'unassigned';
  if (stop.status === 'EN_ROUTE' || stop.status === 'ARRIVED') return 'active';
  if (stop.late) return 'late';
  return 'pending';
}

export const STOP_STATUS_LABELS: Record<Stop['status'], string> = {
  PENDING: 'Pending',
  EN_ROUTE: 'En route',
  ARRIVED: 'Arrived',
  DELIVERED: 'Delivered',
  FAILED: 'Failed',
};

/** Best known arrival: actual if the driver got there, else the committed ETA, else the plan. */
export function bestArrival(stop: Stop): string | null {
  return stop.actualArrival ?? stop.committedEta ?? stop.plannedArrival;
}
