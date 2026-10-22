import type { Plan } from '../api/types';
import { parseClock, toHourMinute } from './time';

const NEW_REF = /^NEW-(\d+)$/;

/** Next free "NEW-<n>" order reference in the plan. */
export function suggestOrderRef(plan: Plan): string {
  const refs = [...plan.routes.flatMap((route) => route.stops), ...plan.unassigned].map((stop) => stop.orderRef);
  const highest = refs.reduce((max, ref) => {
    const match = NEW_REF.exec(ref);
    return match ? Math.max(max, Number(match[1])) : max;
  }, 0);
  return `NEW-${highest + 1}`;
}

export interface TimeWindow {
  start: string;
  end: string;
}

const HALF_HOUR = 1800;
const LATEST_START = parseClock('21:00');

/** A sensible default window: the working day before dispatch, the next two hours once vans are out. */
export function defaultWindow(clockSeconds: number | null): TimeWindow {
  if (clockSeconds === null) {
    return { start: '09:00', end: '17:00' };
  }
  const start = Math.min(Math.ceil((clockSeconds + 1) / HALF_HOUR) * HALF_HOUR, LATEST_START);
  return { start: toHourMinute(start), end: toHourMinute(start + 4 * HALF_HOUR) };
}
