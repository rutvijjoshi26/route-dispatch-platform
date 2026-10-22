import type { PathOptions } from 'leaflet';
import { FAILED_COLOR, UNASSIGNED_COLOR } from '../../../lib/colors';
import type { StopVisualState } from '../../../lib/stopState';

export interface StopStyle {
  radius: number;
  pathOptions: PathOptions;
}

/** Circle styling for every state except delivered, which is drawn as a check mark icon. */
export function stopStyle(state: Exclude<StopVisualState, 'delivered'>, color: string, dimmed: boolean): StopStyle {
  const fade = dimmed ? 0.25 : 1;
  switch (state) {
    case 'failed':
      return {
        radius: 7,
        pathOptions: { color: '#7F1D1D', weight: 2, fillColor: FAILED_COLOR, fillOpacity: 0.95 * fade, opacity: fade },
      };
    case 'active':
      return {
        radius: 9,
        pathOptions: { color: '#111827', weight: 3, fillColor: color, fillOpacity: fade, opacity: fade },
      };
    case 'late':
      return {
        radius: 7,
        pathOptions: { color: FAILED_COLOR, weight: 3, fillColor: color, fillOpacity: 0.9 * fade, opacity: fade },
      };
    case 'unassigned':
      return {
        radius: 6,
        pathOptions: { color: '#000000', weight: 2, fillColor: UNASSIGNED_COLOR, fillOpacity: 0.85, opacity: 1 },
      };
    case 'pending':
      return {
        radius: 6,
        pathOptions: { color: '#FFFFFF', weight: 1.5, fillColor: color, fillOpacity: 0.95 * fade, opacity: fade },
      };
  }
}
