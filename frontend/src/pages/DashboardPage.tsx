import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AuctionCard } from '../components/auction/AuctionCard';
import { PageHeader, PageShell } from '../components/layout/PageHeader';
import { Button } from '../components/ui/Button';
import { DataTable, type Column } from '../components/ui/DataTable';
import { EmptyState } from '../components/ui/EmptyState';
import { CardSkeleton } from '../components/ui/Skeleton';
import { StatCard } from '../components/ui/StatCard';
import { Tabs } from '../components/ui/Tabs';
import { useAsync } from '../hooks/useAsync';
import { api } from '../lib/api';
import { cn } from '../lib/cn';
import { dateTime, humanise, money } from '../lib/format';
import { useGlobalAuctionFeed } from '../realtime/useAuctionChannel';
import { useAuthStore } from '../store/authStore';
import type { AuctionSummary, Bid } from '../types';

export function DashboardPage() {
  const user = useAuthStore((state) => state.user);
  const [tab, setTab] = useState('active');

  const { data: stats, reload: reloadStats } = useAsync(() => api.dashboard(), []);
  const { data: active, loading: loadingActive } = useAsync(() => api.myActiveAuctions(), []);
  const { data: wins } = useAsync(() => api.myWins(), []);
  const { data: losses } = useAsync(() => api.myLosses(), []);
  const { data: watchlist } = useAsync(() => api.watchlist(), []);
  const { data: bidPage } = useAsync(() => api.myBids(0, 40), []);

  const [activeAuctions, setActiveAuctions] = useState<AuctionSummary[]>([]);
  const [watched, setWatched] = useState<AuctionSummary[]>([]);

  useEffect(() => {
    if (active) setActiveAuctions(active);
  }, [active]);
  useEffect(() => {
    if (watchlist) setWatched(watchlist);
  }, [watchlist]);

  // Keep the bidder's own live auctions current without polling.
  useGlobalAuctionFeed((event) => {
    if (!event.state || !event.auctionId) return;
    const { auctionId, state } = event;
    const patch = (list: AuctionSummary[]) =>
      list.map((auction) =>
        auction.id === auctionId
          ? {
              ...auction,
              currentPrice: state.currentPrice,
              bidCount: state.bidCount,
              status: state.status,
              endTime: state.endTime,
            }
          : auction,
      );
    setActiveAuctions(patch);
    setWatched(patch);
    if (event.type === 'AUCTION_CLOSED') reloadStats();
  });

  const toggleWatch = useCallback(async (auction: AuctionSummary) => {
    const result = await api.toggleWatch(auction.id).catch(() => null);
    if (!result) return;
    setWatched((current) =>
      result.watched ? current : current.filter((item) => item.id !== auction.id),
    );
    setActiveAuctions((current) =>
      current.map((item) => (item.id === auction.id ? { ...item, watched: result.watched } : item)),
    );
  }, []);

  const bidColumns: Column<Bid>[] = [
    {
      key: 'auction',
      header: 'Auction',
      render: (bid) => (
        <Link to={`/auctions/${bid.auctionId}`} className="text-accent-soft hover:text-white">
          Lot #{bid.auctionId}
        </Link>
      ),
    },
    {
      key: 'amount',
      header: 'Amount',
      align: 'right',
      render: (bid) => <span className="tabular-nums text-slate-200">{money(bid.amount, true)}</span>,
    },
    {
      key: 'type',
      header: 'Type',
      render: (bid) => (
        <span className="text-[12px] text-slate-500">{bid.automatic ? 'Automatic' : 'Manual'}</span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (bid) => (
        <span
          className={cn(
            'text-[12px] font-medium',
            bid.status === 'WINNING' && 'text-live',
            bid.status === 'WON' && 'text-gold',
            bid.status === 'OUTBID' && 'text-danger',
            bid.status === 'LOST' && 'text-slate-500',
          )}
        >
          {humanise(bid.status)}
        </span>
      ),
    },
    {
      key: 'time',
      header: 'Placed (server time)',
      align: 'right',
      render: (bid) => <span className="text-[12px] text-slate-500">{dateTime(bid.serverTimestamp)}</span>,
    },
  ];

  const tabs = [
    { id: 'active', label: 'Active bids', count: activeAuctions.length },
    { id: 'won', label: 'Won', count: wins?.length ?? 0 },
    { id: 'lost', label: 'Lost', count: losses?.length ?? 0 },
    { id: 'watchlist', label: 'Watchlist', count: watched.length },
    { id: 'history', label: 'Bid history', count: bidPage?.totalElements ?? 0 },
  ];

  const grid = (list: AuctionSummary[], emptyTitle: string, emptyMessage: string) =>
    list.length === 0 ? (
      <EmptyState
        title={emptyTitle}
        message={emptyMessage}
        action={
          <Link to="/auctions">
            <Button size="sm" variant="secondary">
              Browse auctions
            </Button>
          </Link>
        }
      />
    ) : (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {list.map((auction) => (
          <AuctionCard key={auction.id} auction={auction} onToggleWatch={toggleWatch} />
        ))}
      </div>
    );

  return (
    <PageShell wide>
      <PageHeader
        eyebrow="Bidder dashboard"
        title={`Welcome back, ${user?.fullName.split(' ')[0] ?? 'bidder'}`}
        description="Everything you are bidding on, watching and have won, updated live."
        actions={
          <Link to="/auctions">
            <Button size="sm">Find more lots</Button>
          </Link>
        }
      />

      <div className="mb-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Active bids" value={stats?.activeBids ?? '—'} tone="live" icon="◉" />
        <StatCard
          label="Auctions won"
          value={stats?.auctionsWon ?? '—'}
          sub={stats ? `${money(stats.totalWinningValue)} total` : undefined}
          tone="gold"
          icon="★"
        />
        <StatCard label="Watchlist" value={stats?.watchlistSize ?? '—'} icon="♡" />
        <StatCard
          label="Total bids placed"
          value={stats?.totalBids ?? '—'}
          sub={stats ? `${stats.unreadNotifications} unread alerts` : undefined}
          icon="⇅"
        />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} className="mb-5 max-w-fit" />

      {tab === 'active' &&
        (loadingActive ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <CardSkeleton key={index} />
            ))}
          </div>
        ) : (
          grid(
            activeAuctions,
            'No active bids',
            'When you bid on a running auction it will appear here with a live countdown.',
          )
        ))}

      {tab === 'won' &&
        grid(wins ?? [], 'Nothing won yet', 'Lots you win will be collected here with the final price.')}

      {tab === 'lost' &&
        grid(
          losses ?? [],
          'No closed auctions',
          'Auctions you bid on but did not win will be listed here.',
        )}

      {tab === 'watchlist' &&
        grid(
          watched,
          'Your watchlist is empty',
          'Tap the heart on any lot to follow it and get alerts before it closes.',
        )}

      {tab === 'history' && (
        <DataTable
          columns={bidColumns}
          rows={bidPage?.content ?? []}
          rowKey={(bid) => bid.id}
          empty="You have not placed any bids yet."
        />
      )}
    </PageShell>
  );
}
