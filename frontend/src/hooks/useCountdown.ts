import { useEffect, useState } from 'react';
import { secondsUntil } from '../lib/serverTime';
import { splitDuration, type CountdownParts } from '../lib/format';

/**
 * Counts down to a server timestamp.
 *
 * The target is always an absolute instant produced by the backend, and the remaining time is
 * recomputed from the clock-synchronised "now" on every tick, so the display stays correct even
 * if the tab is throttled or the device clock is wrong.
 */
export function useCountdown(endTimeIso: string | null | undefined, active = true): CountdownParts {
  const [parts, setParts] = useState<CountdownParts>(() =>
    splitDuration(endTimeIso ? secondsUntil(endTimeIso) : 0),
  );

  useEffect(() => {
    if (!endTimeIso) {
      setParts(splitDuration(0));
      return;
    }
    const update = () => setParts(splitDuration(secondsUntil(endTimeIso)));
    update();
    if (!active) return;

    const interval = window.setInterval(update, 1000);
    const onVisible = () => update();
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [endTimeIso, active]);

  return parts;
}
