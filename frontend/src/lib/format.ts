export function formatKm(meters: number): string {
  const km = meters / 1000;
  return `${km.toFixed(km < 10 ? 1 : 0)} km`;
}

/** Duration as h:mm, e.g. 37232 s -> "10:20". */
export function formatHoursMinutes(seconds: number): string {
  const totalMinutes = Math.round(seconds / 60);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return `${hours}:${String(minutes).padStart(2, '0')}`;
}

/** Milliseconds as seconds with one decimal, e.g. 2400 -> "2.4 s". */
export function formatSeconds(millis: number): string {
  return `${(millis / 1000).toFixed(1)} s`;
}

export function formatDistance(meters: number): string {
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1)} km`;
}

export function formatServiceDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(year ?? 1970, (month ?? 1) - 1, day ?? 1).toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

export function formatTimestamp(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

export function formatTimeScale(timeScale: number): string {
  return `${Number.isInteger(timeScale) ? timeScale : timeScale.toFixed(1)}x`;
}

export function formatSpeed(speedMps: number | null): string {
  return speedMps === null ? 'speed n/a' : `${Math.round(speedMps * 3.6)} km/h`;
}

export function plural(count: number, singular: string, pluralForm = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : pluralForm}`;
}
