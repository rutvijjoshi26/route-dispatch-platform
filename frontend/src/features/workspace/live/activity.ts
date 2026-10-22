export type ActivityTone = 'info' | 'success' | 'warning' | 'danger';

export interface ActivityEntry {
  id: number;
  /** Plan-day time for stop events, wall-clock time for plan events. */
  time: string;
  title: string;
  detail?: string;
  tone: ActivityTone;
}

export const ACTIVITY_LIMIT = 150;

/** Prepends an entry, newest first, keeping the feed bounded. */
export function prependActivity(feed: ActivityEntry[], entry: ActivityEntry, limit = ACTIVITY_LIMIT): ActivityEntry[] {
  return [entry, ...feed].slice(0, limit);
}
