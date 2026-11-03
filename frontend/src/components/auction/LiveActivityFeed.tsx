import { cn } from '../../lib/cn';
import { clockTime } from '../../lib/format';
import type { AuctionEvent } from '../../types';

export interface ActivityEntry {
  id: string;
  type: AuctionEvent['type'];
  text: string;
  at: string;
}

const tone: Partial<Record<AuctionEvent['type'], string>> = {
  BID_PLACED: 'text-live',
  AUCTION_EXTENDED: 'text-gold',
  AUCTION_ENDING: 'text-danger',
  AUCTION_CLOSED: 'text-slate-400',
  AUCTION_STARTED: 'text-accent-soft',
  OUTBID: 'text-danger',
};

/** Rolling log of what the server has pushed for this auction during the session. */
export function LiveActivityFeed({ entries }: { entries: ActivityEntry[] }) {
  if (entries.length === 0) {
    return (
      <p className="py-6 text-center text-sm text-slate-500">
        Live activity will appear here the moment something happens.
      </p>
    );
  }

  return (
    <ul className="no-scrollbar max-h-64 space-y-1.5 overflow-y-auto pr-1">
      {entries.map((entry) => (
        <li
          key={entry.id}
          className="flex animate-slide-in items-start gap-2.5 rounded-lg bg-white/[0.025] px-3 py-2 text-[13px]"
        >
          <span className={cn('mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-current', tone[entry.type] ?? 'text-slate-500')} />
          <span className="min-w-0 flex-1 text-slate-300">{entry.text}</span>
          <span className="shrink-0 font-mono text-[11px] text-slate-600">{clockTime(entry.at)}</span>
        </li>
      ))}
    </ul>
  );
}
