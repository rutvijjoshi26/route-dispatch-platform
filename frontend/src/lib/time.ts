/** Plan times arrive as "HH:mm" or "HH:mm:ss" strings in plan-day local time. */
export function parseClock(value: string): number {
  const [hours = 0, minutes = 0, seconds = 0] = value.split(':').map(Number);
  return hours * 3600 + minutes * 60 + seconds;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** Seconds since midnight to "HH:mm:ss"; wraps past midnight. */
export function toClock(totalSeconds: number): string {
  const seconds = ((Math.floor(totalSeconds) % 86400) + 86400) % 86400;
  return `${pad(Math.floor(seconds / 3600))}:${pad(Math.floor((seconds % 3600) / 60))}:${pad(seconds % 60)}`;
}

/** Seconds since midnight to "HH:mm". */
export function toHourMinute(totalSeconds: number): string {
  return toClock(totalSeconds).slice(0, 5);
}

/** "HH:mm:ss" to "HH:mm" for display; null becomes a dash. */
export function shortTime(value: string | null | undefined): string {
  return value ? value.slice(0, 5) : '–';
}

export function todayIso(now: Date = new Date()): string {
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
