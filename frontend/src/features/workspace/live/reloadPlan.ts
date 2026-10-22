import type { QueryClient } from '@tanstack/react-query';
import { fetchPlan } from '../../../api/endpoints';
import { queryKeys } from '../../../api/queryKeys';
import type { Plan } from '../../../api/types';

const ATTEMPTS = 3;
const RETRY_DELAY_MS = 400;

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Fetches the plan after a PLAN_UPDATED event and stores it in the cache. Any in-flight poll is
 * cancelled first so an older response cannot win, and a response older than `minVersion` is retried briefly.
 */
export async function reloadPlan(queryClient: QueryClient, planId: number, minVersion: number): Promise<Plan> {
  const key = queryKeys.plan(planId);
  await queryClient.cancelQueries({ queryKey: key, exact: true });
  let plan = await fetchPlan(planId);
  for (let attempt = 1; attempt < ATTEMPTS && plan.version < minVersion; attempt += 1) {
    await sleep(RETRY_DELAY_MS);
    plan = await fetchPlan(planId);
  }
  queryClient.setQueryData(key, plan);
  return plan;
}
