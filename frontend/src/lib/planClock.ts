import type { PlanClock } from '../api/types';
import { parseClock } from './time';

/** A plan clock reading plus when it was received, enough to advance it locally. */
export interface ClockAnchor {
  clock: PlanClock;
  /** Wall-clock milliseconds when the reading arrived (react-query's dataUpdatedAt). */
  anchoredAt: number;
  /** Whether plan time is advancing (the plan is dispatched). */
  running: boolean;
}

/** Plan-day time in seconds at wall-clock `nowMs`, advanced at the plan's time scale. */
export function clockSecondsAt(anchor: ClockAnchor, nowMs: number): number {
  const base = parseClock(anchor.clock.now);
  if (!anchor.running) {
    return base;
  }
  return base + (Math.max(0, nowMs - anchor.anchoredAt) / 1000) * anchor.clock.timeScale;
}
