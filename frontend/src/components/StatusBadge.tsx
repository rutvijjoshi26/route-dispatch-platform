import type { PlanStatus } from '../api/types';

const LABELS: Record<PlanStatus, string> = {
  DRAFT: 'Draft',
  PLANNING: 'Planning',
  PLANNED: 'Planned',
  DISPATCHED: 'Dispatched',
  COMPLETED: 'Completed',
};

export function StatusBadge({ status }: { status: PlanStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{LABELS[status]}</span>;
}
