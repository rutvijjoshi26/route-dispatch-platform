import type { Stop } from '../../../api/types';
import { parseClock } from '../../../lib/time';

export type TimelineState = 'ontime' | 'late' | 'delivered' | 'failed' | 'active';

export function timelineState(stop: Stop): TimelineState {
  switch (stop.status) {
    case 'DELIVERED':
      return 'delivered';
    case 'FAILED':
      return 'failed';
    case 'EN_ROUTE':
    case 'ARRIVED':
      return 'active';
    default:
      return stop.late ? 'late' : 'ontime';
  }
}

/** Arrival used to place a stop: actual when known, otherwise the plan. */
export function timelineArrival(stop: Stop): string | null {
  return stop.actualArrival ?? stop.plannedArrival;
}

/** Percent offset of `seconds` within [start, end], clamped to the track. */
export function trackPercent(seconds: number, start: number, end: number): number {
  if (end <= start) {
    return 0;
  }
  return Math.min(100, Math.max(0, ((seconds - start) / (end - start)) * 100));
}

export function shiftBounds(shiftStart: string, shiftEnd: string): { start: number; end: number } {
  const start = parseClock(shiftStart);
  const end = parseClock(shiftEnd);
  // Shifts that cross midnight end on the next day.
  return { start, end: end > start ? end : end + 86400 };
}
