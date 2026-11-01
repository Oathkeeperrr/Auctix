import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { BidForm } from '../components/auction/BidForm';
import { BidHistory } from '../components/auction/BidHistory';
import { CountdownTimer } from '../components/auction/CountdownTimer';
import { LiveActivityFeed, type ActivityEntry } from '../components/auction/LiveActivityFeed';
import { PriceDisplay } from '../components/auction/PriceDisplay';
import { AuctionCard } from '../components/auction/AuctionCard';
import { ChartFrame } from '../components/charts/ChartFrame';
import { PriceTrend } from '../components/charts/AnalyticsCharts';
import { LotMedia } from '../components/auction/LotMedia';
import { PageShell } from '../components/layout/PageHeader';
import { StatusBadge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Tabs } from '../components/ui/Tabs';
import { useAsync } from '../hooks/useAsync';
import { api } from '../lib/api';
import { cn } from '../lib/cn';
import { dateTime, humanise, money } from '../lib/format';
import { useAuctionChannel } from '../realtime/useAuctionChannel';
import { useAuthStore } from '../store/authStore';
import { toast } from '../store/toastStore';
import type { AuctionSummary, Bid } from '../types';

export function AuctionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const auctionId = Number(id);
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);

  const { data: auction, loading, error, reload, setData } = useAsync(
    () => api.auction(auctionId),
    [auctionId],
  );

  const [activity, setActivity] = useState<ActivityEntry[]>([]);
  const [tab, setTab] = useState('history');
  const [history, setHistory] = useState<Bid[]>([]);
  const [related, setRelated] = useState<AuctionSummary[]>([]);
  const [messaging, setMessaging] = useState(false);

  // The price trend is drawn from the bids already loaded for the ledger, so opening this tab
  // costs no extra request. Oldest first, because a trend read right to left is nonsense.
  const trend = useMemo(
    () =>
      [...history]
        .sort((a, b) => a.serverTimestamp.localeCompare(b.serverTimestamp))
        .map((bid) => ({
          at: new Date(bid.serverTimestamp).toLocaleString(undefined, {
            day: '2-digit',
            month: 'short',
            hour: '2-digit',
            minute: '2-digit',
          }),
          amount: bid.amount,
        })),
    [history],
  );

  useEffect(() => {
    if (auction) setHistory(auction.recentBids);
  }, [auction]);

  // Other lots in the same category. A failure here is not worth surfacing: the section simply
  // does not appear, and the page it sits under is unaffected.
  useEffect(() => {
    if (!auction) return undefined;
    let cancelled = false;
    api
      .auctions({ category: auction.category, size: 6 })
      .then((page) => {
        if (!cancelled) {
          setRelated(page.content.filter((item) => item.id !== auction.id).slice(0, 4));
        }
      })
      .catch(() => {
        if (!cancelled) setRelated([]);
      });
    return () => {
      cancelled = true;
    };
  }, [auction]);

  const channel = useAuctionChannel(Number.isNaN(auctionId) ? undefined : auctionId, {
    initialState: null,
  });

  // Every server event becomes a line in the live feed and, for bids, a row in the ledger.
  useEffect(() => {
    const event = channel.lastEvent;
    if (!event) return;

    const text =
      event.type === 'BID_PLACED' && event.bid
        ? `${event.bid.automatic ? 'Automatic bid' : 'New bid'} ${money(event.bid.amount, true)} by ${
            event.bid.bidderMasked
          }`
        : event.message ?? humanise(event.type);

    setActivity((current) =>
      [
        {
          id: `${event.type}-${event.serverTime}-${event.bid?.id ?? Math.random().toString(36).slice(2)}`,
          type: event.type,
          text,
          at: event.serverTime,
        },
        ...current,
      ].slice(0, 30),
    );

    if (event.type === 'BID_PLACED' && event.bid) {
      const incoming = event.bid;
      setHistory((current) =>
        current.some((bid) => bid.id === incoming.id) ? current : [incoming, ...current].slice(0, 60),
      );
    }

    if (event.type === 'AUCTION_EXTENDED') {
      toast.warning('Auction extended', event.message ?? 'A late bid pushed the finish line out.');
    }

    if (event.type === 'AUCTION_CLOSED') {
      toast.info('Auction closed', event.message ?? 'Bidding has finished.');
      // Pull the settled record so the winner block is authoritative.
      window.setTimeout(reload, 800);
    }
  }, [channel.lastEvent, reload]);

  const live = channel.state;
  const status = live?.status ?? auction?.status ?? 'SCHEDULED';
  const currentPrice = live?.currentPrice ?? auction?.currentPrice ?? 0;
  const nextMinimum = live?.nextMinimumBid ?? auction?.nextMinimumBid ?? 0;
  const bidCount = live?.bidCount ?? auction?.bidCount ?? 0;
  const bidders = live?.distinctBidders ?? auction?.distinctBidders ?? 0;
  const endTime = live?.endTime ?? auction?.endTime;
  const highestBidderId = live?.highestBidderId ?? auction?.highestBidderId ?? null;
  const extensions = live?.extensionCount ?? auction?.extensionCount ?? 0;

  /**
   * Opens the thread with this seller about this lot, or returns the existing one, then goes
   * to it. The server decides which conversation that is, so repeat clicks never create
   * duplicates.
   */
  const messageSeller = useCallback(async () => {
    if (!auction) return;
    setMessaging(true);
    try {
      const conversation = await api.startConversation(auction.sellerId, auction.id);
      navigate(`/messages?c=${conversation.id}`);
    } catch {
      toast.error('That conversation could not be opened.');
    } finally {
      setMessaging(false);
    }
  }, [auction, navigate]);

  const onBidPlaced = useCallback(() => {
    void api
      .bidHistory(auctionId, 0, 40)
      .then((page) => setHistory(page.content))
      .catch(() => undefined);
  }, [auctionId]);

  const toggleWatch = useCallback(async () => {
    if (!user) {
      navigate('/login');
      return;
    }
    const result = await api.toggleWatch(auctionId).catch(() => null);
    if (result && auction) {
      setData({ ...auction, watched: result.watched });
    }
  }, [auctionId, auction, navigate, setData, user]);

  const tabs = useMemo(
    () => [
      { id: 'history', label: 'Bid history', count: history.length },
      { id: 'activity', label: 'Live activity', count: activity.length },
      { id: 'details', label: 'Details' },
      { id: 'rules', label: 'Auction rules' },
    ],
    [history.length, activity.length],
  );

  if (loading) {
    return (
      <PageShell wide>
        <div className="grid gap-6 lg:grid-cols-[1.25fr_1fr]">
          <div className="skeleton aspect-[4/3] w-full rounded-2xl" />
          <div className="space-y-4">
            <div className="skeleton h-6 w-32" />
            <div className="skeleton h-10 w-3/4" />
            <div className="skeleton h-28 w-full rounded-xl" />
            <div className="skeleton h-12 w-full rounded-xl" />
          </div>
        </div>
      </PageShell>
    );
  }

  if (error || !auction) {
    return (
      <PageShell>
        <div className="glass px-6 py-16 text-center">
          <h1 className="text-xl font-semibold">Auction unavailable</h1>
          <p className="mt-2 text-sm text-slate-400">{error ?? 'This lot could not be found.'}</p>
          <Button className="mt-5" onClick={() => navigate('/auctions')}>
            Browse auctions
          </Button>
        </div>
      </PageShell>
    );
  }

  const settled = status === 'CLOSED' || status === 'SETTLED' || status === 'ARCHIVED';
  const isOwner = user?.id === auction.sellerId;

  return (
    <PageShell wide>
      <nav aria-label="Breadcrumb" className="mb-5 flex items-center gap-2 text-[12px] text-slate-500">
        <Link to="/" className="transition-colors hover:text-slate-300">
          Home
        </Link>
        <span aria-hidden>/</span>
        <Link to="/auctions" className="transition-colors hover:text-slate-300">
          Auctions
        </Link>
        <span aria-hidden>/</span>
        <span className="truncate text-slate-400">{auction.title}</span>
      </nav>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
        {/* ------------------------------------------------- photographs and 3D view */}
        <div>
          <LotMedia
            images={auction.images ?? []}
            kind={auction.modelKind}
            title={auction.title}
            status={status}
          />

          {/* Tabs: history, live feed, description, rules */}
          <div className="mt-5">
            <Tabs items={tabs} active={tab} onChange={setTab} />

            <div className="glass mt-4 p-5">
              {tab === 'history' && (
                <>
                  <p className="mb-3 text-[12px] text-slate-500">
                    Bidder identities are masked. Timestamps are the server's, recorded inside the
                    bid transaction.
                  </p>
                  <ChartFrame
                    title="Price trend"
                    caption="Every accepted bid on this lot, in the order the server recorded them"
                    empty={trend.length === 0}
                    emptyMessage="No bids yet, so there is nothing to plot."
                    height={200}
                  >
                    <PriceTrend points={trend} />
                  </ChartFrame>

                  <div className="mt-4">
                    <BidHistory bids={history} currentUserId={user?.id} maxHeight="max-h-96" />
                  </div>
                </>
              )}

              {tab === 'activity' && <LiveActivityFeed entries={activity} />}

              {tab === 'details' && (
                <div className="space-y-4">
                  <p className="whitespace-pre-line text-sm leading-relaxed text-slate-300">
                    {auction.description}
                  </p>
                  <dl className="grid gap-x-6 gap-y-3 border-t border-white/[0.07] pt-4 sm:grid-cols-2">
                    {[
                      ['Category', auction.categoryLabel],
                      ['Brand', auction.brand ?? '—'],
                      ['Condition', auction.conditionLabel ?? '—'],
                      ['Starting price', money(auction.startingPrice, true)],
                      ['Minimum increment', money(auction.minIncrement, true)],
                      ['Opened', dateTime(auction.startTime)],
                      ['Scheduled close', dateTime(auction.originalEndTime)],
                      ['Current close', dateTime(endTime)],
                      ...(auction.reservePrice != null
                        ? [['Reserve (private)', money(auction.reservePrice, true)] as [string, string]]
                        : []),
                    ].map(([label, value]) => (
                      <div key={label}>
                        <dt className="text-[11px] uppercase tracking-[0.14em] text-slate-500">{label}</dt>
                        <dd className="mt-0.5 text-[13px] text-slate-200">{value}</dd>
                      </div>
                    ))}
                  </dl>

                  <div className="border-t border-white/[0.07] pt-4">
                    <p className="text-[11px] uppercase tracking-[0.14em] text-slate-500">Seller</p>
                    <div className="mt-2 flex items-center gap-3">
                      <span className="flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br from-accent to-violet-glow text-sm font-semibold text-white">
                        {auction.sellerName.slice(0, 1)}
                      </span>
                      <div>
                        <p className="text-sm text-slate-200">{auction.sellerName}</p>
                        <p className="text-[11px] text-slate-500">
                          On AuctiX since {dateTime(auction.sellerJoinedAt)}
                        </p>
                      </div>
                    </div>

                    {user && !isOwner && (
                      <Button
                        variant="secondary"
                        className="mt-4"
                        loading={messaging}
                        onClick={() => void messageSeller()}
                      >
                        Message seller
                      </Button>
                    )}
                    {!user && (
                      <p className="mt-4 text-[12px] text-slate-500">
                        <Link to="/login" className="text-accent-soft underline underline-offset-2">
                          Sign in
                        </Link>{' '}
                        to message the seller.
                      </p>
                    )}
                  </div>
                </div>
              )}

              {tab === 'rules' && (
                <ul className="space-y-3 text-[13px] leading-relaxed text-slate-400">
                  <li>
                    <strong className="text-slate-200">The server decides.</strong> Every bid is
                    validated, ordered and timestamped by the backend inside a locked transaction.
                    Your browser's clock never affects the outcome.
                  </li>
                  <li>
                    <strong className="text-slate-200">Minimum increment.</strong> Each bid must beat
                    the current price by at least {money(auction.minIncrement, true)}.
                  </li>
                  <li>
                    <strong className="text-slate-200">No self-bidding.</strong> You cannot bid while
                    you already hold the highest bid, and sellers cannot bid on their own lots.
                  </li>
                  {auction.antiSnipeEnabled && (
                    <li>
                      <strong className="text-slate-200">Anti-sniping.</strong> A valid bid inside the
                      final {auction.antiSnipeWindowSeconds} seconds extends the auction by{' '}
                      {auction.antiSnipeExtensionSeconds} seconds.
                      {extensions > 0 && ` This lot has been extended ${extensions} time(s).`}
                    </li>
                  )}
                  <li>
                    <strong className="text-slate-200">Reserve price.</strong> If a hidden reserve is
                    not met, the lot closes unsold.
                  </li>
                  <li>
                    <strong className="text-slate-200">Closing.</strong> When the server clock reaches
                    the end time the auction closes automatically and the highest valid bid wins.
                  </li>
                </ul>
              )}
            </div>
          </div>
        </div>

        {/* ------------------------------------------------- bidding column */}
        <div className="space-y-4 lg:sticky lg:top-24 lg:self-start">
          <div className="glass edge-glow is-active space-y-4 p-5">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="label-eyebrow">{auction.categoryLabel}</p>
                <h1 className="mt-1 font-display text-xl font-semibold leading-tight text-white sm:text-2xl">
                  {auction.title}
                </h1>
              </div>
              <button
                type="button"
                onClick={toggleWatch}
                aria-pressed={auction.watched}
                aria-label={auction.watched ? 'Remove from watchlist' : 'Add to watchlist'}
                className={cn(
                  'flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-lg transition-all',
                  auction.watched
                    ? 'bg-danger/15 text-danger ring-1 ring-inset ring-danger/30'
                    : 'bg-white/[0.05] text-slate-500 hover:bg-white/[0.1] hover:text-slate-200',
                )}
              >
                {auction.watched ? '♥' : '♡'}
              </button>
            </div>

            <div className="flex items-center gap-3 border-y border-white/[0.07] py-3">
              <StatusBadge status={status} />
              <span className="text-[12px] text-slate-500">
                {bidCount} bid{bidCount === 1 ? '' : 's'} · {bidders} bidder{bidders === 1 ? '' : 's'}
              </span>
            </div>

            <PriceDisplay amount={currentPrice} tick={channel.priceTick} size="xl" />

            <div className="flex items-center justify-between rounded-lg bg-white/[0.03] px-3.5 py-2.5">
              <span className="text-[12px] text-slate-500">Next minimum bid</span>
              <span className="font-display text-base font-semibold tabular-nums text-accent-soft">
                {money(nextMinimum, true)}
              </span>
            </div>

            <div>
              <p className="label-eyebrow mb-2">
                {status === 'SCHEDULED' ? 'Starts in' : settled ? 'Closed' : 'Time remaining'}
              </p>
              <CountdownTimer
                endTime={status === 'SCHEDULED' ? auction.startTime : endTime ?? auction.endTime}
                running={!settled}
                size="md"
              />
            </div>

            {settled ? (
              <div className="rounded-xl bg-violet-glow/[0.08] p-4 ring-1 ring-inset ring-violet-glow/25">
                <p className="label-eyebrow">Result</p>
                {auction.winnerMasked ? (
                  <p className="mt-1 text-sm text-slate-200">
                    Won by <span className="font-semibold text-white">{auction.winnerMasked}</span> at{' '}
                    <span className="font-semibold text-white">{money(auction.winningAmount, true)}</span>
                  </p>
                ) : (
                  <p className="mt-1 text-sm text-slate-300">
                    Closed without a sale — {bidCount === 0 ? 'no bids were placed' : 'the reserve was not met'}.
                  </p>
                )}
              </div>
            ) : (
              <BidForm
                auctionId={auction.id}
                status={status}
                currentPrice={currentPrice}
                nextMinimumBid={nextMinimum}
                minIncrement={auction.minIncrement}
                sellerId={auction.sellerId}
                highestBidderId={highestBidderId}
                onPlaced={onBidPlaced}
              />
            )}

            {isOwner && (
              <p className="rounded-lg bg-gold/[0.08] px-3 py-2 text-[12px] text-gold-bright">
                You are the seller of this lot.{' '}
                <Link to="/seller" className="underline underline-offset-2">
                  Open seller dashboard
                </Link>
              </p>
            )}
          </div>

          {/* Compact live ledger alongside the bid form */}
          <div className="glass p-5">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-[13px] font-semibold text-white">Latest bids</h2>
              <span className="flex items-center gap-1.5 text-[10px] uppercase tracking-[0.18em] text-slate-500">
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-live" />
                live
              </span>
            </div>
            <BidHistory bids={history.slice(0, 6)} currentUserId={user?.id} maxHeight="max-h-72" />
          </div>
        </div>
      </div>

      {related.length > 0 && (
        <section className="mt-10" aria-labelledby="related-heading">
          <div className="mb-4 flex items-baseline justify-between gap-4">
            <h2 id="related-heading" className="font-display text-lg font-semibold text-white">
              More in {auction.categoryLabel}
            </h2>
            <Link
              to={`/auctions?category=${auction.category}`}
              className="text-[12px] text-slate-400 transition-colors hover:text-white"
            >
              View all →
            </Link>
          </div>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {related.map((item) => (
              <AuctionCard key={item.id} auction={item} />
            ))}
          </div>
        </section>
      )}
    </PageShell>
  );
}
