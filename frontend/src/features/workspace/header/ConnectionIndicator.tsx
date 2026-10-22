import type { ConnectionState } from '../../../live/usePlanTopics';

const LABELS: Record<ConnectionState, string> = {
  connecting: 'Connecting',
  live: 'Live',
  offline: 'Reconnecting',
};

export function ConnectionIndicator({ state }: { state: ConnectionState }) {
  return (
    <span className={`connection connection-${state}`} role="status" title="Live update connection">
      <span className="connection-dot" aria-hidden="true" />
      {LABELS[state]}
    </span>
  );
}
