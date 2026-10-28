import { cn } from '../../lib/cn';
import { clockTime, money } from '../../lib/format';
import type { Bid } from '../../types';

/**
 * Transparent, time-stamped bid ledger. Identities are masked by the backend
 * ("Kal*** S."); the client never receives full names or e-mail addresses.
 */
export function BidHistory({
  bids,
  currentUserId,
  emptyMessage = 'No bids yet. Be the first.',
  highlightNewest = true,
  maxHeight = 'max-h-80',
}: {
  bids: Bid[];
  currentUserId?: number | null;
  emptyMessage?: string;
  highlightNewest?: boolean;
  maxHeight?: string;
}) {
  if (bids.length === 0) {
    return <p className="py-8 text-center text-sm text-slate-500">{emptyMessage}</p>;
  }

  return (
    <ol className={cn('no-scrollbar space-y-1 overflow-y-auto pr-1', maxHeight)}>
      {bids.map((bid, index) => {
        const mine = currentUserId != null && bid.bidderId === currentUserId;
        const leading = index === 0;
        return (
          <li
            key={bid.id}
            className={cn(
              'flex items-center justify-between gap-3 rounded-lg px-3 py-2.5 text-sm transition-colors',
              leading && highlightNewest
                ? 'bg-live/[0.08] ring-1 ring-inset ring-live/25'
                : 'bg-white/[0.025] hover:bg-white/[0.05]',
            )}
          >
            <span className="flex min-w-0 items-center gap-2.5">
              <span
                className={cn(
                  'flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-[10px] font-semibold',
                  mine ? 'bg-accent/25 text-accent-soft' : 'bg-white/[0.06] text-slate-400',
                )}
                aria-hidden
              >
                {bid.automatic ? '⟳' : bid.bidderMasked.slice(0, 2).toUpperCase()}
              </span>
              <span className="min-w-0">
                <span className="block truncate text-slate-200">
                  {mine ? 'You' : bid.bidderMasked}
                  {bid.automatic && (
                    <span className="ml-1.5 text-[10px] uppercase tracking-wider text-slate-500">auto</span>
                  )}
                </span>
                <span className="block font-mono text-[11px] text-slate-500">
                  {clockTime(bid.serverTimestamp)}
                </span>
              </span>
            </span>
            <span
              className={cn(
                'shrink-0 font-display font-semibold tabular-nums',
                leading ? 'text-live' : 'text-slate-300',
              )}
            >
              {money(bid.amount, true)}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
