import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { queryKeys } from '../../../api/queryKeys';
import type { Plan } from '../../../api/types';
import { errorMessage } from '../../../lib/errors';
import { changedVehicleIds } from '../../../lib/planDiff';
import { applyStopEvent } from '../../../lib/planPatch';
import { REPLAN_REASONS, type PlanUpdateEnvelope, type PlanUpdated, type SolveProgress, type StopEvent } from '../../../live/messages';
import { PositionsStore } from '../../../live/positionsStore';
import { usePlanTopics } from '../../../live/usePlanTopics';
import { positionsQuery } from '../queries';
import { reloadPlan } from './reloadPlan';
import { SeenPlans } from './seenPlans';
import { prependActivity, type ActivityEntry, type ActivityTone } from './activity';
import { reasonLabel, replanSummary, stopEventTitle, stopEventTone } from './messages';
import { useTimedValue } from './useTimedValue';

export interface WorkspaceNotice {
  id: number;
  tone: 'replan' | 'success' | 'danger';
  message: string;
}

const NO_VEHICLES: ReadonlySet<number> = new Set();
const HIGHLIGHT_MS = 6000;
const NOTICE_MS = 12000;

function wallClock(): string {
  return new Date().toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

/** Everything the workspace learns from the plan's live topics. */
export function useWorkspaceLive(plan: Plan) {
  const planId = plan.id;
  const queryClient = useQueryClient();
  const [positions] = useState(() => new PositionsStore());
  const [seenPlans] = useState(() => new SeenPlans());
  useEffect(() => {
    seenPlans.record(plan);
  }, [plan, seenPlans]);
  const [solveProgress, setSolveProgress] = useState<SolveProgress | null>(null);
  const [activity, setActivity] = useState<ActivityEntry[]>([]);
  const [notice, showNotice, dismissNotice] = useTimedValue<WorkspaceNotice | null>(null, NOTICE_MS);
  const [changedVehicles, flashVehicles] = useTimedValue<ReadonlySet<number>>(NO_VEHICLES, HIGHLIGHT_MS);
  const nextId = useRef(0);

  const initialPositions = useQuery(positionsQuery(planId));
  useEffect(() => {
    if (initialPositions.data) {
      positions.pushAll(initialPositions.data);
    }
  }, [initialPositions.data, positions]);
  useEffect(() => () => positions.dispose(), [positions]);

  const log = useCallback((title: string, tone: ActivityTone, detail?: string, time = wallClock()) => {
    nextId.current += 1;
    const entry: ActivityEntry = { id: nextId.current, time, title, tone, ...(detail ? { detail } : {}) };
    setActivity((feed) => prependActivity(feed, entry));
  }, []);

  const notify = useCallback(
    (tone: WorkspaceNotice['tone'], message: string) => {
      nextId.current += 1;
      showNotice({ id: nextId.current, tone, message });
    },
    [showNotice],
  );

  const handlePlanUpdated = useCallback(
    async (update: PlanUpdated) => {
      setSolveProgress(null);
      const previous = seenPlans.latestBefore(update.version);
      void queryClient.invalidateQueries({ queryKey: queryKeys.revisions(planId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.plans });
      let next: Plan;
      try {
        next = await reloadPlan(queryClient, planId, update.version);
      } catch (error) {
        log('Could not reload the plan', 'danger', errorMessage(error));
        return;
      }
      seenPlans.record(next);
      if (!REPLAN_REASONS.has(update.reason)) {
        log(`${reasonLabel(update.reason)}: version ${update.version}`, 'info', update.detail ?? undefined);
        return;
      }
      const summary = replanSummary(update);
      notify('replan', summary);
      log(reasonLabel(update.reason), 'warning', summary);
      const changed = previous ? changedVehicleIds(previous, next) : [];
      if (changed.length > 0) {
        flashVehicles(new Set(changed));
      }
    },
    [flashVehicles, log, notify, planId, queryClient, seenPlans],
  );

  const onUpdate = useCallback(
    (message: PlanUpdateEnvelope) => {
      switch (message.type) {
        case 'SOLVE_PROGRESS':
          setSolveProgress(message.payload);
          break;
        case 'PLAN_UPDATED':
          void handlePlanUpdated(message.payload);
          break;
        case 'PLAN_FAILED':
          setSolveProgress(null);
          notify('danger', `Planning failed: ${message.payload.message}`);
          log('Planning failed', 'danger', message.payload.message);
          void queryClient.invalidateQueries({ queryKey: queryKeys.plan(planId) });
          break;
      }
    },
    [handlePlanUpdated, log, notify, planId, queryClient],
  );

  const onStop = useCallback(
    (event: StopEvent) => {
      const key = queryKeys.plan(planId);
      const plan = queryClient.getQueryData<Plan>(key);
      const vehicleName = plan?.routes.find((route) => route.vehicleId === event.vehicleId)?.vehicleName;
      if (plan) {
        // Keep the fetch timestamp so the client-side plan clock stays anchored to the last real fetch.
        const { dataUpdatedAt } = queryClient.getQueryState(key) ?? {};
        queryClient.setQueryData<Plan>(key, applyStopEvent(plan, event), { updatedAt: dataUpdatedAt });
      }
      log(stopEventTitle(event, vehicleName ?? `Van ${event.vehicleId}`), stopEventTone(event), undefined, event.at);
    },
    [log, planId, queryClient],
  );

  // Anything may have changed while the socket was down: reload the plan, van positions and history.
  const onConnected = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.plan(planId), exact: true });
    void queryClient.invalidateQueries({ queryKey: queryKeys.positions(planId) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.revisions(planId) });
  }, [planId, queryClient]);

  const connection = usePlanTopics(planId, {
    onConnected,
    onUpdate,
    onStop,
    onPosition: (position) => positions.push(position),
  });

  return { connection, positions, solveProgress, activity, notice, notify, dismissNotice, changedVehicles };
}
