import { useState } from 'react';
import { Dialog } from '../../../components/Dialog';
import { errorMessage } from '../../../lib/errors';
import { formatTimeScale } from '../../../lib/format';

const SPEEDS = [1, 10, 30, 60] as const;

interface DispatchDialogProps {
  routeCount: number;
  pending: boolean;
  error: unknown;
  onConfirm: (timeScale: number) => void;
  onClose: () => void;
}

export function DispatchDialog({ routeCount, pending, error, onConfirm, onClose }: DispatchDialogProps) {
  const [speed, setSpeed] = useState<number>(30);
  return (
    <Dialog
      title="Dispatch plan"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="button button-primary" disabled={pending} onClick={() => onConfirm(speed)}>
            {pending ? 'Dispatching…' : `Dispatch ${routeCount} routes`}
          </button>
        </>
      }
    >
      <p>Routes are sent to drivers and the plan clock starts. Choose how fast plan time runs for this demo.</p>
      <fieldset className="speed-options">
        <legend className="field-label">Clock speed</legend>
        {SPEEDS.map((value) => (
          <label key={value} className={`speed-option${speed === value ? ' speed-option-selected' : ''}`}>
            <input
              type="radio"
              name="timeScale"
              value={value}
              checked={speed === value}
              onChange={() => setSpeed(value)}
            />
            {formatTimeScale(value)}
            <span className="muted small">{value === 1 ? 'Real time' : `1 plan hour in ${60 / value} min`}</span>
          </label>
        ))}
      </fieldset>
      {Boolean(error) && (
        <p className="form-error" role="alert">
          {errorMessage(error)}
        </p>
      )}
    </Dialog>
  );
}
