import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';
import type { AuctionStatus } from '../../types';

type Tone = 'live' | 'upcoming' | 'neutral' | 'gold' | 'danger' | 'violet';

const tones: Record<Tone, string> = {
  live: 'bg-live/12 text-live border-live/35',
  upcoming: 'bg-accent/12 text-accent-soft border-accent/35',
  neutral: 'bg-white/[0.06] text-slate-300 border-white/10',
  gold: 'bg-gold/12 text-gold-bright border-gold/35',
  danger: 'bg-danger/12 text-danger border-danger/35',
  violet: 'bg-violet-glow/12 text-violet-glow border-violet-glow/35',
};

export function Badge({
  tone = 'neutral',
  children,
  className,
}: {
  tone?: Tone;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1',
        'text-[10px] font-semibold uppercase tracking-[0.14em]',
        tones[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

const statusTone: Record<AuctionStatus, Tone> = {
  LIVE: 'live',
  ENDING: 'danger',
  SCHEDULED: 'upcoming',
  PENDING_APPROVAL: 'gold',
  REJECTED: 'danger',
  CLOSED: 'neutral',
  SETTLED: 'violet',
  ARCHIVED: 'neutral',
  CANCELLED: 'danger',
};

const statusLabel: Record<AuctionStatus, string> = {
  LIVE: 'Live',
  ENDING: 'Ending',
  SCHEDULED: 'Upcoming',
  PENDING_APPROVAL: 'In review',
  REJECTED: 'Rejected',
  CLOSED: 'Closed',
  SETTLED: 'Settled',
  ARCHIVED: 'Archived',
  CANCELLED: 'Cancelled',
};

/** Status chip with a pulsing dot while an auction is actually running. */
export function StatusBadge({ status, className }: { status: AuctionStatus; className?: string }) {
  const isLive = status === 'LIVE' || status === 'ENDING';
  return (
    <Badge tone={statusTone[status]} className={className}>
      {isLive && (
        <span className="relative flex h-1.5 w-1.5">
          <span className="absolute inline-flex h-full w-full animate-pulse-ring rounded-full bg-current" />
          <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-current" />
        </span>
      )}
      {statusLabel[status]}
    </Badge>
  );
}
