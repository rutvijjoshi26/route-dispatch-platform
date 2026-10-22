import { useEffect, useState } from 'react';
import { clockSecondsAt, type ClockAnchor } from '../../lib/planClock';

const TICK_MS = 500;

/**
 * Live plan-day time in seconds, re-rendering the calling component every tick while the clock runs.
 * Call it from the small components that display time so the rest of the workspace stays still.
 */
export function usePlanClock(anchor: ClockAnchor | null): number | null {
  const [now, setNow] = useState(() => Date.now());
  const running = anchor?.running ?? false;

  useEffect(() => {
    if (!running) {
      return undefined;
    }
    const timer = setInterval(() => setNow(Date.now()), TICK_MS);
    return () => clearInterval(timer);
  }, [running]);

  return anchor ? clockSecondsAt(anchor, now) : null;
}
