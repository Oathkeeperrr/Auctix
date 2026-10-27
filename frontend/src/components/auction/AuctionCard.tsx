import { Link } from 'react-router-dom';
import { cn } from '../../lib/cn';
import { money } from '../../lib/format';
import type { AuctionSummary } from '../../types';
import { StatusBadge } from '../ui/Badge';
import { InlineCountdown } from './CountdownTimer';
import { ItemThumbnail } from './ItemThumbnail';

/**
 * Catalogue card. Shows the live price, countdown, bid count and a watchlist toggle.
 * Prices update in place when the parent receives a WebSocket event.
 */
export function AuctionCard({
  auction,
  onToggleWatch,
  highlight,
}: {
  auction: AuctionSummary;
  onToggleWatch?: (auction: AuctionSummary) => void;
  highlight?: boolean;
}) {
  const live = auction.status === 'LIVE' || auction.status === 'ENDING';
  const upcoming = auction.status === 'SCHEDULED';
  const countdownTarget = upcoming ? auction.startTime : auction.endTime;

  return (
    <article
      className={cn(
        'glass edge-glow group relative flex flex-col overflow-hidden p-0 transition-transform duration-300',
        'hover:-translate-y-1',
        highlight && 'is-active',
      )}
    >
      <Link to={`/auctions/${auction.id}`} className="relative block aspect-[4/3] overflow-hidden">
        <ItemThumbnail
          kind={auction.modelKind}
          imageUrl={auction.imageUrl}
          title={auction.title}
          className="transition-transform duration-500 group-hover:scale-[1.06]"
        />
        <div className="absolute inset-x-0 bottom-0 h-20 bg-gradient-to-t from-ink-950/90 to-transparent" />
        <div className="absolute left-3 top-3">
          <StatusBadge status={auction.status} />
        </div>
        <div className="absolute bottom-3 left-3 flex items-center gap-2">
          <span className="rounded-md bg-ink-950/70 px-2 py-1 font-mono text-[11px] text-slate-200 backdrop-blur-sm">
            <InlineCountdown endTime={countdownTarget} running={live || upcoming} />
          </span>
          {auction.bidCount > 0 && (
            <span className="rounded-md bg-ink-950/70 px-2 py-1 text-[11px] text-slate-300 backdrop-blur-sm">
              {auction.bidCount} bid{auction.bidCount === 1 ? '' : 's'}
            </span>
          )}
        </div>
      </Link>

      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="min-w-0">
          <p className="label-eyebrow truncate">{auction.categoryLabel}</p>
          <Link to={`/auctions/${auction.id}`}>
            <h3 className="mt-1 line-clamp-2 text-[15px] font-semibold leading-snug text-white transition-colors group-hover:text-accent-soft">
              {auction.title}
            </h3>
          </Link>
        </div>

        <div className="mt-auto flex items-end justify-between gap-3">
          <div className="min-w-0">
            <p className="text-[10px] uppercase tracking-[0.18em] text-slate-500">
              {auction.bidCount > 0 ? 'Current bid' : 'Starting bid'}
            </p>
            <p className="font-display text-lg font-semibold tabular-nums text-white">
              {money(auction.bidCount > 0 ? auction.currentPrice : auction.startingPrice, false)}
            </p>
          </div>

          {onToggleWatch && (
            <button
              type="button"
              onClick={() => onToggleWatch(auction)}
              aria-pressed={auction.watched}
              aria-label={auction.watched ? 'Remove from watchlist' : 'Add to watchlist'}
              className={cn(
                'flex h-9 w-9 shrink-0 items-center justify-center rounded-lg transition-all',
                auction.watched
                  ? 'bg-danger/15 text-danger ring-1 ring-inset ring-danger/30'
                  : 'bg-white/[0.05] text-slate-500 hover:bg-white/[0.1] hover:text-slate-200',
              )}
            >
              {auction.watched ? '♥' : '♡'}
            </button>
          )}
        </div>

        <Link
          to={`/auctions/${auction.id}`}
          className="flex h-9 items-center justify-center rounded-lg bg-white/[0.05] text-[12px] font-medium text-slate-300 transition-colors hover:bg-accent/15 hover:text-accent-soft"
        >
          {live ? 'Place a bid' : upcoming ? 'View lot' : 'View result'}
        </Link>
      </div>
    </article>
  );
}
