import type { Route } from '../../../api/types';
import type { ClockAnchor } from '../../../lib/planClock';
import { STOP_STATUS_LABELS } from '../../../lib/stopState';
import { parseClock, shortTime, toHourMinute } from '../../../lib/time';
import { usePlanClock } from '../usePlanClock';
import { shiftBounds, timelineArrival, timelineState, trackPercent, type TimelineState } from './timeline';

const STATE_LABELS: Record<TimelineState, string> = {
  ontime: 'on time',
  late: 'predicted late',
  delivered: 'delivered',
  failed: 'failed',
  active: 'in progress',
};

interface RouteTimelineProps {
  route: Pick<Route, 'vehicleName' | 'shiftStart' | 'shiftEnd' | 'stops'>;
  clock: ClockAnchor | null;
}

/** The van's shift as a horizontal track with one tick per stop at its arrival time. */
export function RouteTimeline({ route, clock }: RouteTimelineProps) {
  const { start, end } = shiftBounds(route.shiftStart, route.shiftEnd);
  const placed = route.stops.flatMap((stop) => {
    const arrival = timelineArrival(stop);
    return arrival ? [{ stop, arrival, state: timelineState(stop) }] : [];
  });

  return (
    <div className="timeline">
      <ol className="timeline-track" aria-label={`${route.vehicleName} stops from ${shortTime(route.shiftStart)} to ${shortTime(route.shiftEnd)}`}>
        {placed.map(({ stop, arrival, state }) => {
          const label = `${stop.orderRef} at ${shortTime(arrival)}, ${STATE_LABELS[state]} (${STOP_STATUS_LABELS[stop.status]})`;
          return (
            <li
              key={stop.id}
              className={`timeline-stop timeline-stop-${state}`}
              style={{ left: `${trackPercent(parseClock(arrival), start, end)}%` }}
              title={label}
              aria-label={label}
            />
          );
        })}
      </ol>
      {clock && <NowLine clock={clock} start={start} end={end} />}
      <div className="timeline-axis" aria-hidden="true">
        <span>{shortTime(route.shiftStart)}</span>
        <span>{shortTime(route.shiftEnd)}</span>
      </div>
    </div>
  );
}

interface NowLineProps {
  clock: ClockAnchor;
  start: number;
  end: number;
}

/** The only part of the timeline that changes every clock tick. */
function NowLine({ clock, start, end }: NowLineProps) {
  const seconds = usePlanClock(clock) ?? start;
  const label = `Now ${toHourMinute(seconds)}`;
  return (
    <span
      className="timeline-now"
      style={{ left: `${trackPercent(seconds, start, end)}%` }}
      role="img"
      aria-label={label}
      title={label}
    />
  );
}
