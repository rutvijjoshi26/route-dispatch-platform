import type { VehiclePosition } from '../api/types';

export type PositionMap = ReadonlyMap<number, VehiclePosition>;

/**
 * Applies one position report. Returns the same map when the report is not newer than the
 * one already held for that van (or has no valid timestamp), so out-of-order messages are dropped
 * and callers can skip work.
 */
export function applyPosition(positions: PositionMap, position: VehiclePosition): PositionMap {
  const recordedAt = Date.parse(position.recordedAt);
  if (Number.isNaN(recordedAt)) {
    return positions;
  }
  const current = positions.get(position.vehicleId);
  if (current && Date.parse(current.recordedAt) >= recordedAt) {
    return positions;
  }
  const next = new Map(positions);
  next.set(position.vehicleId, position);
  return next;
}

type Listener = () => void;

/**
 * Holds the latest position per van outside React state. Writes can arrive many times a second;
 * subscribers are notified at most once per `intervalMs`, which keeps map re-renders cheap.
 */
export class PositionsStore {
  private positions: PositionMap = new Map();
  private readonly listeners = new Set<Listener>();
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(private readonly intervalMs = 250) {}

  readonly getSnapshot = (): PositionMap => this.positions;

  readonly subscribe = (listener: Listener): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  push(position: VehiclePosition): void {
    const next = applyPosition(this.positions, position);
    if (next !== this.positions) {
      this.positions = next;
      this.scheduleNotify();
    }
  }

  pushAll(positions: Iterable<VehiclePosition>): void {
    let next = this.positions;
    for (const position of positions) {
      next = applyPosition(next, position);
    }
    if (next !== this.positions) {
      this.positions = next;
      this.scheduleNotify();
    }
  }

  /** Cancels a pending notification; listeners unsubscribe themselves. */
  dispose(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }

  private scheduleNotify(): void {
    if (this.timer !== null) {
      return;
    }
    this.timer = setTimeout(() => {
      this.timer = null;
      this.listeners.forEach((listener) => listener());
    }, this.intervalMs);
  }
}
