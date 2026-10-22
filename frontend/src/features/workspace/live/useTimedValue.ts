import { useCallback, useEffect, useRef, useState } from 'react';

/** State that resets to `initial` a fixed time after each `show`. */
export function useTimedValue<T>(initial: T, durationMs: number) {
  const [value, setValue] = useState<T>(initial);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clear = useCallback(() => {
    if (timer.current !== null) {
      clearTimeout(timer.current);
      timer.current = null;
    }
    setValue(initial);
  }, [initial]);

  const show = useCallback(
    (next: T) => {
      if (timer.current !== null) {
        clearTimeout(timer.current);
      }
      setValue(next);
      timer.current = setTimeout(() => {
        timer.current = null;
        setValue(initial);
      }, durationMs);
    },
    [durationMs, initial],
  );

  useEffect(
    () => () => {
      if (timer.current !== null) {
        clearTimeout(timer.current);
      }
    },
    [],
  );

  return [value, show, clear] as const;
}
