import { Link } from 'react-router';
import type { PlanSummary } from '../../api/types';
import { StatusBadge } from '../../components/StatusBadge';
import { formatServiceDate } from '../../lib/format';

export function PlansTable({ plans }: { plans: PlanSummary[] }) {
  const newestFirst = [...plans].sort((a, b) => b.id - a.id);
  return (
    <div className="table-scroll">
      <table className="data-table">
        <thead>
          <tr>
            <th scope="col">Plan</th>
            <th scope="col">Service date</th>
            <th scope="col">Depot</th>
            <th scope="col">Status</th>
            <th scope="col" className="numeric">
              Version
            </th>
            <th scope="col" className="numeric">
              Stops
            </th>
            <th scope="col">
              <span className="visually-hidden">Open</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {newestFirst.map((plan) => (
            <tr key={plan.id}>
              <td>
                <Link to={`/plans/${plan.id}`} className="table-link">
                  {plan.name}
                </Link>
              </td>
              <td>{formatServiceDate(plan.serviceDate)}</td>
              <td>{plan.depotName}</td>
              <td>
                <StatusBadge status={plan.status} />
              </td>
              <td className="numeric">{plan.version}</td>
              <td className="numeric">{plan.stops}</td>
              <td className="numeric">
                <Link to={`/plans/${plan.id}`} className="button button-small" aria-label={`Open ${plan.name}`}>
                  Open
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
