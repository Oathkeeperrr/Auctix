/**
 * Clock synchronisation.
 *
 * Every countdown in the UI is rendered against the server's clock, not the browser's.
 * We measure the offset once at start-up (and refresh it periodically), then every
 * component asks this module for "now".
 */

let offsetMs = 0;
let synced = false;

/** Record the offset from a server timestamp carried by any API response. */
export function observeServerTime(serverTimeIso: string | undefined | null): void {
  if (!serverTimeIso) return;
  const serverMs = Date.parse(serverTimeIso);
  if (Number.isNaN(serverMs)) return;
  offsetMs = serverMs - Date.now();
  synced = true;
}

/** Milliseconds since the epoch, corrected to the server's clock. */
export function serverNow(): number {
  return Date.now() + offsetMs;
}

export function secondsUntil(isoTimestamp: string): number {
  const target = Date.parse(isoTimestamp);
  if (Number.isNaN(target)) return 0;
  return Math.max(0, Math.floor((target - serverNow()) / 1000));
}

export function isSynced(): boolean {
  return synced;
}

export function getOffsetMs(): number {
  return offsetMs;
}
