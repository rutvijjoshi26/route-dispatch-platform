import { afterEach, describe, expect, it, vi } from 'vitest';
import type { VehiclePosition } from '../api/types';
import { applyPosition, PositionsStore } from './positionsStore';

function ping(vehicleId: number, recordedAt: string, lat = 41.9): VehiclePosition {
  return { vehicleId, lat, lon: -87.7, speedMps: 8, headingDeg: 90, recordedAt };
}

describe('applyPosition', () => {
  it('keeps the newest report per van', () => {
    let positions = applyPosition(new Map(), ping(1, '2026-10-21T15:00:00Z', 41.1));
    positions = applyPosition(positions, ping(1, '2026-10-21T15:00:05Z', 41.2));
    positions = applyPosition(positions, ping(2, '2026-10-21T15:00:01Z', 41.3));

    expect(positions.get(1)?.lat).toBe(41.2);
    expect(positions.get(2)?.lat).toBe(41.3);
  });

  it('ignores an older out-of-order report and returns the same map', () => {
    const current = applyPosition(new Map(), ping(1, '2026-10-21T15:00:05Z', 41.2));
    const after = applyPosition(current, ping(1, '2026-10-21T15:00:01Z', 41.0));

    expect(after).toBe(current);
    expect(after.get(1)?.lat).toBe(41.2);
  });
});

describe('applyPosition timestamps', () => {
  it('drops a report whose recordedAt is not a valid timestamp', () => {
    const current = applyPosition(new Map(), ping(1, '2026-10-21T15:00:05Z', 41.2));
    expect(applyPosition(current, ping(1, 'not-a-date', 40.0))).toBe(current);
    expect(applyPosition(new Map(), ping(2, ''))).toEqual(new Map());
  });
});

describe('PositionsStore', () => {
  afterEach(() => vi.useRealTimers());

  it('batches notifications and skips stale reports', () => {
    vi.useFakeTimers();
    const store = new PositionsStore(200);
    const listener = vi.fn();
    store.subscribe(listener);

    store.push(ping(1, '2026-10-21T15:00:02Z', 41.2));
    store.push(ping(1, '2026-10-21T15:00:03Z', 41.3));
    store.push(ping(1, '2026-10-21T15:00:01Z', 41.0));
    expect(listener).not.toHaveBeenCalled();

    vi.advanceTimersByTime(200);
    expect(listener).toHaveBeenCalledOnce();
    expect(store.getSnapshot().get(1)?.lat).toBe(41.3);

    store.push(ping(1, '2026-10-21T15:00:00Z'));
    vi.advanceTimersByTime(200);
    expect(listener).toHaveBeenCalledOnce();
  });
});
