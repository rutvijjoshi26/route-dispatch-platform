import { EmptyState } from '../../../components/StateMessages';
import type { ActivityEntry } from '../live/activity';

export function ActivityTab({ entries }: { entries: ActivityEntry[] }) {
  if (entries.length === 0) {
    return (
      <EmptyState title="No activity yet">
        <span>Deliveries, failures and re-plans appear here as they happen.</span>
      </EmptyState>
    );
  }
  return (
    <ol className="item-list activity" aria-live="polite">
      {entries.map((entry) => (
        <li key={entry.id} className={`item activity-${entry.tone}`}>
          <div className="item-row">
            <strong>{entry.title}</strong>
            <time className="muted small">{entry.time.slice(0, 8)}</time>
          </div>
          {entry.detail && <div className="muted small">{entry.detail}</div>}
        </li>
      ))}
    </ol>
  );
}
