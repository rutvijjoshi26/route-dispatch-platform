import { useQuery } from '@tanstack/react-query';
import { EmptyState, ErrorState, LoadingState } from '../../../components/StateMessages';
import { errorMessage } from '../../../lib/errors';
import { formatHoursMinutes, formatKm, formatSeconds, formatTimestamp } from '../../../lib/format';
import { reasonLabel } from '../live/messages';
import { revisionsQuery } from '../queries';

export function HistoryTab({ planId }: { planId: number }) {
  const revisions = useQuery(revisionsQuery(planId));

  if (revisions.isPending) return <LoadingState label="Loading history…" />;
  if (revisions.isError) {
    return <ErrorState message={errorMessage(revisions.error)} onRetry={() => void revisions.refetch()} />;
  }
  if (revisions.data.length === 0) {
    return (
      <EmptyState title="No revisions yet">
        <span>Each optimize and re-plan is recorded here.</span>
      </EmptyState>
    );
  }

  return (
    <table className="data-table compact history-table">
      <thead>
        <tr>
          <th scope="col">Ver.</th>
          <th scope="col">Change</th>
          <th scope="col" className="numeric">
            Solve
          </th>
          <th scope="col" className="numeric">
            Result
          </th>
        </tr>
      </thead>
      <tbody>
        {revisions.data.map((revision) => (
          <tr key={revision.version}>
            <td>v{revision.version}</td>
            <td>
              <div className="history-reason">{reasonLabel(revision.reason)}</div>
              {revision.detail && <div className="muted small">{revision.detail}</div>}
              <div className="muted small">
                {revision.changedVehicles} vans changed · {revision.pinnedStops} kept
              </div>
              <time className="muted small" dateTime={revision.createdAt}>
                {formatTimestamp(revision.createdAt)}
              </time>
            </td>
            <td className="numeric" title={`Total ${formatSeconds(revision.totalMillis)} including routing`}>
              {formatSeconds(revision.solveMillis)}
            </td>
            <td className="numeric">
              <div>{formatKm(revision.distanceMeters)}</div>
              <div className="muted small">{formatHoursMinutes(revision.travelSeconds)} drive</div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
