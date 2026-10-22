import type { Plan } from '../../../api/types';

/**
 * The last plan the user saw at each recent version. A re-plan is diffed against the version before it,
 * which stays correct even when a poll already loaded the new version before the live event arrived.
 */
export class SeenPlans {
  private readonly byVersion = new Map<number, Plan>();

  constructor(private readonly keep = 3) {}

  record(plan: Plan): void {
    this.byVersion.set(plan.version, plan);
    while (this.byVersion.size > this.keep) {
      this.byVersion.delete(Math.min(...this.byVersion.keys()));
    }
  }

  latestBefore(version: number): Plan | undefined {
    const earlier = [...this.byVersion.keys()].filter((seen) => seen < version);
    return earlier.length > 0 ? this.byVersion.get(Math.max(...earlier)) : undefined;
  }
}
