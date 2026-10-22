import { describe, expect, it } from 'vitest';
import { makePlan, makeRoute, makeStop } from '../test/utils';
import { applyStopEvent } from './planPatch';

describe('applyStopEvent', () => {
  const plan = makePlan([makeRoute(1, [makeStop({ id: 1 }), makeStop({ id: 2 })]), makeRoute(2, [makeStop({ id: 3 })])]);

  it('updates the stop status, times and totals', () => {
    const next = applyStopEvent(plan, {
      planId: 7,
      stopId: 2,
      orderRef: 'CHI-1001',
      vehicleId: 1,
      status: 'DELIVERED',
      at: '10:15:00',
    });

    const stop = next.routes[0]?.stops[1];
    expect(stop?.status).toBe('DELIVERED');
    expect(stop?.actualArrival).toBe('10:15:00');
    expect(stop?.actualDeparture).toBe('10:15:00');
    expect(next.totals.delivered).toBe(1);
    expect(next.routes[1]).toBe(plan.routes[1]);
  });

  it('returns the same plan for an unknown stop', () => {
    const next = applyStopEvent(plan, { planId: 7, stopId: 42, orderRef: 'X', vehicleId: 1, status: 'FAILED', at: '10:00:00' });
    expect(next).toBe(plan);
  });
});
