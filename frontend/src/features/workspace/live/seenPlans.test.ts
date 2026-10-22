import { describe, expect, it } from 'vitest';
import { makePlan } from '../../../test/utils';
import { SeenPlans } from './seenPlans';

describe('SeenPlans', () => {
  it('returns the newest plan seen before a version, keeping a bounded history', () => {
    const seen = new SeenPlans(2);
    const [v1, v2, v3] = [1, 2, 3].map((version) => makePlan([], { version }));
    seen.record(v1!);
    seen.record(v2!);
    expect(seen.latestBefore(3)).toBe(v2);
    expect(seen.latestBefore(2)).toBe(v1);

    seen.record(v3!);
    expect(seen.latestBefore(2)).toBeUndefined();
    expect(seen.latestBefore(1)).toBeUndefined();
  });
});
