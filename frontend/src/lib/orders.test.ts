import { describe, expect, it } from 'vitest';
import { makePlan, makeRoute, makeStop } from '../test/utils';
import { defaultWindow, suggestOrderRef } from './orders';
import { parseClock } from './time';

describe('suggestOrderRef', () => {
  it('picks the next free NEW-<n> reference', () => {
    const plan = makePlan([makeRoute(1, [makeStop({ id: 1, orderRef: 'NEW-2' }), makeStop({ id: 2, orderRef: 'CHI-9' })])], {
      unassigned: [makeStop({ id: 3, orderRef: 'NEW-7', vehicleId: null })],
    });
    expect(suggestOrderRef(plan)).toBe('NEW-8');
    expect(suggestOrderRef(makePlan([]))).toBe('NEW-1');
  });
});

describe('defaultWindow', () => {
  it('uses the working day before dispatch and the next two hours afterwards', () => {
    expect(defaultWindow(null)).toEqual({ start: '09:00', end: '17:00' });
    expect(defaultWindow(parseClock('10:05:00'))).toEqual({ start: '10:30', end: '12:30' });
  });
});
