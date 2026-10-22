import type { PlanTotals } from '../../../api/types';
import { formatHoursMinutes, formatKm } from '../../../lib/format';

interface Kpi {
  label: string;
  value: string;
  tone?: 'success' | 'danger' | 'warning';
}

function kpis(totals: PlanTotals): Kpi[] {
  return [
    { label: 'Stops', value: String(totals.stops) },
    { label: 'Delivered', value: String(totals.delivered), tone: 'success' },
    { label: 'Failed', value: String(totals.failed), ...(totals.failed > 0 ? { tone: 'danger' as const } : {}) },
    {
      label: 'Predicted late',
      value: String(totals.predictedLate),
      ...(totals.predictedLate > 0 ? { tone: 'warning' as const } : {}),
    },
    {
      label: 'Unassigned',
      value: String(totals.unassigned),
      ...(totals.unassigned > 0 ? { tone: 'warning' as const } : {}),
    },
    { label: 'Distance', value: formatKm(totals.distanceMeters) },
    { label: 'Drive time', value: formatHoursMinutes(totals.travelSeconds) },
  ];
}

export function KpiChips({ totals }: { totals: PlanTotals }) {
  return (
    <dl className="kpis">
      {kpis(totals).map((kpi) => (
        <div key={kpi.label} className={`kpi${kpi.tone ? ` kpi-${kpi.tone}` : ''}`}>
          <dt>{kpi.label}</dt>
          <dd>{kpi.value}</dd>
        </div>
      ))}
    </dl>
  );
}
