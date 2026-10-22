import type { StopStatus } from '../../../api/types';
import { formatSeconds, plural } from '../../../lib/format';
import { STOP_STATUS_LABELS } from '../../../lib/stopState';
import type { PlanUpdated, StopEvent } from '../../../live/messages';
import type { ActivityTone } from './activity';

const REASON_LABELS: Record<string, string> = {
  INITIAL: 'Optimized',
  REOPTIMIZE: 'Re-optimized',
  NEW_ORDER: 'Re-planned for a new order',
  DRIVER_LATE: 'Re-planned for a late driver',
  MANUAL: 'Re-planned on request',
  DISPATCHED: 'Dispatched',
};

export function reasonLabel(reason: string): string {
  return REASON_LABELS[reason] ?? reason;
}

/** E.g. "Re-planned in 2.4 s: Order NEW-3 added. 3 vans changed, 41 stops kept in place". */
export function replanSummary(update: PlanUpdated): string {
  const detail = update.detail ? `: ${update.detail}.` : '.';
  return `Re-planned in ${formatSeconds(update.totalMillis)}${detail} ${plural(update.changedVehicles, 'van')} changed, ${plural(update.pinnedStops, 'stop')} kept in place`;
}

const STOP_TONES: Record<StopStatus, ActivityTone> = {
  PENDING: 'info',
  EN_ROUTE: 'info',
  ARRIVED: 'info',
  DELIVERED: 'success',
  FAILED: 'danger',
};

export function stopEventTitle(event: StopEvent, vehicleName: string): string {
  return `${vehicleName}: ${event.orderRef} ${STOP_STATUS_LABELS[event.status].toLowerCase()}`;
}

export function stopEventTone(event: StopEvent): ActivityTone {
  return STOP_TONES[event.status];
}
