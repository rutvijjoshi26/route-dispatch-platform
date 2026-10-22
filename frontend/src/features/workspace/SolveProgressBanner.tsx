import type { SolveProgress } from '../../live/messages';

const FULL_SOLVES: ReadonlySet<string> = new Set(['INITIAL', 'REOPTIMIZE']);

/** Shown while a solve runs; live numbers appear once the solver reports progress. */
export function SolveProgressBanner({ progress }: { progress: SolveProgress | null }) {
  return (
    <div className="banner banner-progress" role="status">
      <span className="spinner" aria-hidden="true" />
      <strong>{progress && !FULL_SOLVES.has(progress.reason) ? 'Re-planning routes…' : 'Optimizing routes…'}</strong>
      {progress ? (
        <span>
          {(progress.elapsedMillis / 1000).toFixed(0)} s elapsed · best score <code>{progress.score}</code>
        </span>
      ) : (
        <span className="muted">Waiting for the solver…</span>
      )}
    </div>
  );
}
