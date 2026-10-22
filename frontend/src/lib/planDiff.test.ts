import { describe, expect, it } from 'vitest';
import { makePlan, makeRoute, makeStop } from '../test/utils';
import { changedVehicleIds } from './planDiff';

const stop = (id: number, vehicleId: number) => makeStop({ id, vehicleId });

describe('changedVehicleIds', () => {
  const before = makePlan([
    makeRoute(1, [stop(1, 1), stop(2, 1)]),
    makeRoute(2, [stop(3, 2), stop(4, 2)]),
    makeRoute(3, [stop(5, 3)]),
  ]);

  it('reports nothing when every sequence is unchanged', () => {
    const after = makePlan(before.routes.map((route) => ({ ...route, travelSeconds: route.travelSeconds + 60 })));
    expect(changedVehicleIds(before, after)).toEqual([]);
  });

  it('detects reordered, moved and inserted stops', () => {
    const after = makePlan([
      makeRoute(1, [stop(2, 1), stop(1, 1)]),
      makeRoute(2, [stop(3, 2)]),
      makeRoute(3, [stop(5, 3), stop(4, 3), stop(99, 3)]),
    ]);
    expect(changedVehicleIds(before, after)).toEqual([1, 2, 3]);
  });

  it('treats a route that appears or empties as changed', () => {
    const after = makePlan([
      makeRoute(1, [stop(1, 1), stop(2, 1)]),
      makeRoute(2, [stop(3, 2), stop(4, 2)]),
      makeRoute(3, []),
      makeRoute(4, [stop(5, 4)]),
    ]);
    expect(changedVehicleIds(before, after)).toEqual([3, 4]);
  });
});
