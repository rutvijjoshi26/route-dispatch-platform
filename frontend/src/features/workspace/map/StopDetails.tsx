import type { Stop } from '../../../api/types';
import { STOP_STATUS_LABELS } from '../../../lib/stopState';
import { shortTime } from '../../../lib/time';

interface StopDetailsProps {
  stop: Stop;
  vehicleName: string | null;
}

export function StopDetails({ stop, vehicleName }: StopDetailsProps) {
  return (
    <div className="stop-details">
      <div className="stop-details-title">
        <strong>{stop.orderRef}</strong>
        <span className={`stop-status stop-status-${stop.status.toLowerCase()}`}>{STOP_STATUS_LABELS[stop.status]}</span>
      </div>
      <div>{stop.customerName}</div>
      <div className="muted">{stop.address}</div>
      <dl className="stop-times">
        <dt>Window</dt>
        <dd>
          {shortTime(stop.windowStart)}–{shortTime(stop.windowEnd)}
        </dd>
        <dt>Van</dt>
        <dd>{vehicleName ?? 'Unassigned'}</dd>
        <dt>Planned</dt>
        <dd>{shortTime(stop.plannedArrival)}</dd>
        {stop.committedEta && (
          <>
            <dt>Committed ETA</dt>
            <dd>{shortTime(stop.committedEta)}</dd>
          </>
        )}
        {stop.actualArrival && (
          <>
            <dt>Arrived</dt>
            <dd>{shortTime(stop.actualArrival)}</dd>
          </>
        )}
        {stop.actualDeparture && (
          <>
            <dt>Left</dt>
            <dd>{shortTime(stop.actualDeparture)}</dd>
          </>
        )}
        <dt>Size</dt>
        <dd>{stop.size.toLowerCase()}</dd>
      </dl>
      {(stop.late || stop.addedMidShift) && (
        <div className="stop-flags">
          {stop.late && <span className="flag flag-late">Predicted late</span>}
          {stop.addedMidShift && <span className="flag">Added mid-shift</span>}
        </div>
      )}
    </div>
  );
}
