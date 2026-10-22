import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { queryKeys } from '../../../api/queryKeys';
import type { Plan } from '../../../api/types';
import type { PlanTopicHandlers } from '../../../live/usePlanTopics';
import { usePlanTopics } from '../../../live/usePlanTopics';
import { jsonResponse, makePlan, makeRoute, makeStop } from '../../../test/utils';
import { useWorkspaceLive } from './useWorkspaceLive';

vi.mock('../../../live/usePlanTopics', () => ({ usePlanTopics: vi.fn() }));

const v1 = makePlan([makeRoute(1, [makeStop({ id: 1 }), makeStop({ id: 2 })]), makeRoute(2, [makeStop({ id: 3 })])]);
const v2 = makePlan(
  [makeRoute(1, [makeStop({ id: 2 }), makeStop({ id: 9 }), makeStop({ id: 1 })]), makeRoute(2, [makeStop({ id: 3 })])],
  { version: 2 },
);

function setup() {
  let handlers: PlanTopicHandlers | undefined;
  vi.mocked(usePlanTopics).mockImplementation((_planId, next) => {
    handlers = next;
    return 'live';
  });
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    if (url === `/api/plans/${v1.id}`) return jsonResponse(v2);
    return jsonResponse([]);
  });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(queryKeys.plan(v1.id), v1);
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const hook = renderHook(({ plan }: { plan: Plan }) => useWorkspaceLive(plan), { initialProps: { plan: v1 }, wrapper });
  return { ...hook, client, handlers: () => handlers! };
}

const update = {
  planId: v1.id,
  version: 2,
  reason: 'NEW_ORDER',
  detail: 'Order NEW-1 added',
  totalMillis: 2400,
  solveMillis: 2000,
  changedVehicles: 1,
  pinnedStops: 41,
  score: '0hard/0medium/-90soft',
};

describe('useWorkspaceLive', () => {
  it('highlights changed vans and announces the re-plan', async () => {
    const { result, handlers } = setup();

    act(() => handlers().onUpdate({ type: 'PLAN_UPDATED', payload: update }));

    await waitFor(() => expect([...result.current.changedVehicles]).toEqual([1]));
    expect(result.current.notice?.message).toBe(
      'Re-planned in 2.4 s: Order NEW-1 added. 1 van changed, 41 stops kept in place',
    );
  });

  it('still highlights when a poll loaded the new version before the live event', async () => {
    const { result, rerender, client, handlers } = setup();

    // A background poll lands version 2 first, and the workspace renders it.
    client.setQueryData(queryKeys.plan(v1.id), v2);
    rerender({ plan: v2 });
    act(() => handlers().onUpdate({ type: 'PLAN_UPDATED', payload: update }));

    await waitFor(() => expect([...result.current.changedVehicles]).toEqual([1]));
  });
});
