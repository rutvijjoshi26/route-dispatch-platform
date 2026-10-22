import type { Stop } from '../../../api/types';
import { EmptyState } from '../../../components/StateMessages';
import { shortTime } from '../../../lib/time';

export function UnassignedTab({ stops }: { stops: Stop[] }) {
  if (stops.length === 0) {
    return <EmptyState title="Every order has a van" />;
  }
  return (
    <ul className="item-list">
      {stops.map((stop) => (
        <li key={stop.id} className="item">
          <div className="item-row">
            <strong>{stop.orderRef}</strong>
            <span className="muted">
              {shortTime(stop.windowStart)}–{shortTime(stop.windowEnd)}
            </span>
          </div>
          <div>{stop.customerName}</div>
          <div className="muted small">
            {stop.address} · {stop.size.toLowerCase()}
          </div>
        </li>
      ))}
    </ul>
  );
}
