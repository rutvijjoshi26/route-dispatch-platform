import { formatTimeScale } from '../../../lib/format';
import type { ClockAnchor } from '../../../lib/planClock';
import { toClock } from '../../../lib/time';
import { usePlanClock } from '../usePlanClock';

export function PlanClockDisplay({ anchor }: { anchor: ClockAnchor }) {
  const seconds = usePlanClock(anchor) ?? 0;
  const speed = formatTimeScale(anchor.clock.timeScale);
  return (
    <div className="plan-clock" aria-label={`Plan time ${toClock(seconds)} at ${speed} speed`}>
      <span className="plan-clock-time">{toClock(seconds)}</span>
      <span className="plan-clock-speed">{speed}</span>
    </div>
  );
}
