import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError, api } from '../../lib/api';
import { cn } from '../../lib/cn';
import { money } from '../../lib/format';
import { useAuthStore } from '../../store/authStore';
import { toast } from '../../store/toastStore';
import type { AuctionStatus } from '../../types';
import { Button } from '../ui/Button';

interface BidFormProps {
  auctionId: number;
  status: AuctionStatus;
  currentPrice: number;
  nextMinimumBid: number;
  minIncrement: number;
  sellerId: number;
  highestBidderId?: number | null;
  onPlaced?: () => void;
}

/**
 * Bid entry.
 *
 * The client validates only to give fast feedback; the backend re-checks everything and is the
 * sole authority on whether a bid is accepted. Every rejection is surfaced with the server's
 * own message and error code.
 */
export function BidForm({
  auctionId,
  status,
  currentPrice,
  nextMinimumBid,
  minIncrement,
  sellerId,
  highestBidderId,
  onPlaced,
}: BidFormProps) {
  const user = useAuthStore((state) => state.user);
  const [amount, setAmount] = useState<string>(() => nextMinimumBid.toFixed(2));
  const [maxAmount, setMaxAmount] = useState<string>('');
  const [useProxy, setUseProxy] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Follow the server's minimum as other people's bids land.
  useEffect(() => {
    setAmount((current) => {
      const parsed = Number(current);
      return Number.isNaN(parsed) || parsed < nextMinimumBid ? nextMinimumBid.toFixed(2) : current;
    });
  }, [nextMinimumBid]);

  const quickSteps = useMemo(
    () => [0, 1, 2, 5].map((multiplier) => nextMinimumBid + multiplier * minIncrement),
    [nextMinimumBid, minIncrement],
  );

  const open = status === 'LIVE' || status === 'ENDING';
  const isSeller = user?.id === sellerId;
  const isLeading = !!user && highestBidderId === user.id;

  if (!user) {
    return (
      <div className="glass-soft space-y-3 p-4 text-center">
        <p className="text-sm text-slate-400">Sign in to place a bid on this lot.</p>
        <div className="flex gap-2">
          <Link
            to="/login"
            className="inline-flex h-11 flex-1 items-center justify-center rounded-xl bg-gradient-to-r from-accent-deep via-accent to-violet-glow text-sm font-medium text-white shadow-glow transition-all hover:brightness-110"
          >
            Sign in
          </Link>
          <Link
            to="/register"
            className="inline-flex h-11 flex-1 items-center justify-center rounded-xl bg-white/[0.06] text-sm font-medium text-slate-100 hairline transition-colors hover:bg-white/[0.11]"
          >
            Create account
          </Link>
        </div>
      </div>
    );
  }

  if (isSeller) {
    return (
      <div className="glass-soft p-4 text-center text-sm text-slate-400">
        This is your listing. Sellers cannot bid on their own auctions.
      </div>
    );
  }

  if (!open) {
    return (
      <div className="glass-soft p-4 text-center text-sm text-slate-400">
        {status === 'SCHEDULED'
          ? 'Bidding opens when this auction starts.'
          : 'Bidding on this auction has closed.'}
      </div>
    );
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);

    const value = Number(amount);
    if (Number.isNaN(value) || value <= 0) {
      setError('Enter a valid amount.');
      return;
    }
    if (!isLeading && value < nextMinimumBid) {
      setError(`The minimum bid right now is ${money(nextMinimumBid, true)}.`);
      return;
    }

    let ceiling: number | null = null;
    if (useProxy) {
      const parsedCeiling = Number(maxAmount);
      if (Number.isNaN(parsedCeiling) || parsedCeiling < value) {
        setError('Your maximum must be at least the amount you are bidding.');
        return;
      }
      ceiling = parsedCeiling;
    }

    setSubmitting(true);
    try {
      const result = await api.placeBid(auctionId, value, ceiling);
      toast.success(
        result.bid ? 'Bid accepted' : 'Maximum updated',
        result.bid
          ? `You lead at ${money(result.bid.amount, true)}.`
          : 'Your automatic bidding ceiling was raised.',
      );
      setMaxAmount('');
      onPlaced?.();
    } catch (caught) {
      const message =
        caught instanceof ApiError ? caught.message : 'Your bid could not be placed. Please try again.';
      setError(message);
      toast.error('Bid rejected', message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      {isLeading && (
        <p className="rounded-lg bg-live/[0.08] px-3 py-2 text-[12px] text-live ring-1 ring-inset ring-live/25">
          You are the highest bidder. You can still raise your automatic maximum.
        </p>
      )}

      <div className="flex flex-wrap gap-2">
        {quickSteps.map((step) => (
          <button
            key={step}
            type="button"
            onClick={() => setAmount(step.toFixed(2))}
            className={cn(
              'rounded-lg px-2.5 py-1.5 text-[12px] font-medium transition-colors',
              Number(amount) === step
                ? 'bg-accent/25 text-white ring-1 ring-inset ring-accent/40'
                : 'bg-white/[0.05] text-slate-400 hover:bg-white/[0.09] hover:text-slate-200',
            )}
          >
            {money(step)}
          </button>
        ))}
      </div>

      <div className="flex gap-2">
        <div className="relative flex-1">
          <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-sm text-slate-500">
            $
          </span>
          <input
            type="number"
            inputMode="decimal"
            step="0.01"
            min={0}
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            aria-label="Your bid amount"
            className="h-12 w-full rounded-xl border border-white/[0.09] bg-ink-800/80 pl-7 pr-3 font-display text-lg tabular-nums text-white outline-none transition-colors focus:border-accent/60"
          />
        </div>
        <Button type="submit" size="lg" loading={submitting} className="shrink-0 px-6">
          Place Bid
        </Button>
      </div>

      <label className="flex cursor-pointer items-center gap-2 text-[12px] text-slate-400">
        <input
          type="checkbox"
          checked={useProxy}
          onChange={(event) => setUseProxy(event.target.checked)}
          className="h-3.5 w-3.5 rounded border-white/20 bg-ink-800 accent-accent"
        />
        Bid automatically for me up to a maximum
      </label>

      {useProxy && (
        <div className="relative">
          <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-sm text-slate-500">
            $
          </span>
          <input
            type="number"
            inputMode="decimal"
            step="0.01"
            min={0}
            value={maxAmount}
            placeholder={(nextMinimumBid + minIncrement * 10).toFixed(2)}
            onChange={(event) => setMaxAmount(event.target.value)}
            aria-label="Maximum automatic bid"
            className="h-11 w-full rounded-xl border border-white/[0.09] bg-ink-800/80 pl-7 pr-3 text-sm tabular-nums text-white outline-none transition-colors focus:border-accent/60"
          />
          <p className="mt-1.5 text-[11px] leading-relaxed text-slate-500">
            AuctiX will raise your bid by the minimum increment whenever someone outbids you, never
            going above this figure.
          </p>
        </div>
      )}

      {error && (
        <p role="alert" className="rounded-lg bg-danger/[0.1] px-3 py-2 text-[12px] text-danger">
          {error}
        </p>
      )}

      <p className="text-[11px] text-slate-500">
        Current price {money(currentPrice, true)} · minimum next bid{' '}
        <span className="text-slate-300">{money(nextMinimumBid, true)}</span> · increment{' '}
        {money(minIncrement, true)}
      </p>
    </form>
  );
}
