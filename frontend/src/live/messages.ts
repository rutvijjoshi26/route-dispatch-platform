import type { StopStatus } from '../api/types';

export interface SolveProgress {
  planId: number;
  reason: string;
  elapsedMillis: number;
  score: string;
}

export interface PlanUpdated {
  planId: number;
  version: number;
  reason: string;
  detail: string | null;
  totalMillis: number;
  solveMillis: number;
  changedVehicles: number;
  pinnedStops: number;
  score: string;
}

export interface PlanFailed {
  planId: number;
  reason: string;
  message: string;
}

export type PlanUpdateEnvelope =
  | { type: 'SOLVE_PROGRESS'; payload: SolveProgress }
  | { type: 'PLAN_UPDATED'; payload: PlanUpdated }
  | { type: 'PLAN_FAILED'; payload: PlanFailed };

export interface StopEvent {
  planId: number;
  stopId: number;
  orderRef: string;
  vehicleId: number;
  status: StopStatus;
  at: string;
}

/** Revision reasons that mean an existing plan was changed, as opposed to its first solve or dispatch. */
export const REPLAN_REASONS: ReadonlySet<string> = new Set(['NEW_ORDER', 'DRIVER_LATE', 'MANUAL', 'REOPTIMIZE']);
