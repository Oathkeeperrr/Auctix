import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { InlineCountdown } from '../components/auction/CountdownTimer';
import { PageHeader, PageShell } from '../components/layout/PageHeader';
import { StatusBadge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { DataTable, type Column } from '../components/ui/DataTable';
import { Modal } from '../components/ui/Modal';
import { StatCard } from '../components/ui/StatCard';
import { Tabs } from '../components/ui/Tabs';
import { useAsync } from '../hooks/useAsync';
import { api } from '../lib/api';
import { dateTime, money } from '../lib/format';
import { useGlobalAuctionFeed } from '../realtime/useAuctionChannel';
import { toast } from '../store/toastStore';
import type { AuctionAnalytics, AuctionSummary } from '../types';

const GROUPS = [
  { id: 'all', label: 'All' },
  { id: 'pending', label: 'In review' },
  { id: 'scheduled', label: 'Scheduled' },
  { id: 'live', label: 'Live' },
  { id: 'completed', label: 'Completed' },
];

function matchesGroup(auction: AuctionSummary, group: string): boolean {
  switch (group) {
    case 'pending':
      return auction.status === 'PENDING_APPROVAL' || auction.status === 'REJECTED';
    case 'scheduled':
      return auction.status === 'SCHEDULED';
    case 'live':
      return auction.status === 'LIVE' || auction.status === 'ENDING';
    case 'completed':
      return ['CLOSED', 'SETTLED', 'ARCHIVED', 'CANCELLED'].includes(auction.status);
    default:
      return true;
  }
}

/** Tiny inline sparkline: avoids pulling a charting library for one panel. */
function BidSparkline({ analytics }: { analytics: AuctionAnalytics }) {
  const points = analytics.timeline;
  if (points.length < 2) {
    return <p className="text-[12px] text-slate-500">Not enough bids yet to draw a curve.</p>;
  }

  const amounts = points.map((point) => point.amount);
  const min = Math.min(...amounts);
  const max = Math.max(...amounts);
  const span = max - min || 1;

  const path = points
    .map((point, index) => {
      const x = (index / (points.length - 1)) * 100;
      const y = 100 - ((point.amount - min) / span) * 88 - 6;
      return `${index === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(' ');

  return (
    <svg viewBox="0 0 100 100" preserveAspectRatio="none" className="h-28 w-full" role="img" aria-label="Bid progression">
      <defs>
        <linearGradient id="bid-fill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#4c7dff" stopOpacity="0.45" />
          <stop offset="100%" stopColor="#4c7dff" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={`${path} L100,100 L0,100 Z`} fill="url(#bid-fill)" />
      <path d={path} fill="none" stroke="#7aa0ff" strokeWidth="1.6" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

export function SellerPage() {
  const [group, setGroup] = useState('all');
  const [analyticsFor, setAnalyticsFor] = useState<number | null>(null);
  const [analytics, setAnalytics] = useState<AuctionAnalytics | null>(null);
  const [auctions, setAuctions] = useState<AuctionSummary[]>([]);

  const { data: stats, reload: reloadStats } = useAsync(() => api.sellerStats(), []);
  const { data: page, loading, reload } = useAsync(() => api.sellerAuctions(0, 50), []);

  useEffect(() => {
    if (page) setAuctions(page.content);
  }, [page]);

  useGlobalAuctionFeed((event) => {
    if (!event.state || !event.auctionId) return;
    const { auctionId, state } = event;
    setAuctions((current) =>
      current.map((auction) =>
        auction.id === auctionId
          ? {
              ...auction,
              currentPrice: state.currentPrice,
              bidCount: state.bidCount,
              status: state.status,
              endTime: state.endTime,
            }
          : auction,
      ),
    );
    if (event.type === 'AUCTION_CLOSED' || event.type === 'AUCTION_APPROVED') reloadStats();
  });

  useEffect(() => {
    if (analyticsFor == null) {
      setAnalytics(null);
      return;
    }
    let cancelled = false;
    void api
      .auctionAnalytics(analyticsFor)
      .then((result) => {
        if (!cancelled) setAnalytics(result);
      })
      .catch(() => toast.error('Could not load analytics'));
    return () => {
      cancelled = true;
    };
  }, [analyticsFor]);

  const filtered = useMemo(
    () => auctions.filter((auction) => matchesGroup(auction, group)),
    [auctions, group],
  );

  async function cancelAuction(auction: AuctionSummary) {
    try {
      await api.cancelAuction(auction.id);
      toast.success('Auction cancelled', auction.title);
      reload();
      reloadStats();
    } catch (error) {
      toast.error('Could not cancel', error instanceof Error ? error.message : undefined);
    }
  }

  const columns: Column<AuctionSummary>[] = [
    {
      key: 'title',
      header: 'Lot',
      render: (auction) => (
        <div className="min-w-0">
          <Link
            to={`/auctions/${auction.id}`}
            className="block max-w-[18rem] truncate text-slate-100 hover:text-accent-soft"
          >
            {auction.title}
          </Link>
          <span className="text-[11px] text-slate-500">{auction.categoryLabel}</span>
        </div>
      ),
    },
    { key: 'status', header: 'Status', render: (auction) => <StatusBadge status={auction.status} /> },
    {
      key: 'price',
      header: 'Current',
      align: 'right',
      render: (auction) => (
        <span className="tabular-nums text-slate-200">{money(auction.currentPrice, true)}</span>
      ),
    },
    {
      key: 'bids',
      header: 'Bids',
      align: 'right',
      render: (auction) => <span className="tabular-nums text-slate-400">{auction.bidCount}</span>,
    },
    {
      key: 'ends',
      header: 'Ends',
      render: (auction) => (
        <div>
          <InlineCountdown
            endTime={auction.endTime}
            running={auction.status === 'LIVE' || auction.status === 'ENDING'}
            className="text-[12px]"
          />
          <span className="block text-[10px] text-slate-600">{dateTime(auction.endTime)}</span>
        </div>
      ),
    },
    {
      key: 'actions',
      header: '',
      align: 'right',
      render: (auction) => (
        <div className="flex justify-end gap-1.5">
          <button
            type="button"
            onClick={() => setAnalyticsFor(auction.id)}
            className="rounded-md px-2 py-1 text-[12px] text-accent-soft transition-colors hover:bg-white/[0.06]"
          >
            Analytics
          </button>
          {!['CLOSED', 'SETTLED', 'ARCHIVED', 'CANCELLED'].includes(auction.status) && (
            <button
              type="button"
              onClick={() => cancelAuction(auction)}
              className="rounded-md px-2 py-1 text-[12px] text-slate-500 transition-colors hover:bg-white/[0.06] hover:text-danger"
            >
              Cancel
            </button>
          )}
        </div>
      ),
    },
  ];

  return (
    <PageShell wide>
      <PageHeader
        eyebrow="Seller dashboard"
        title="Your listings"
        description="Create lots, follow the bidding as it happens and review how each auction performed."
        actions={
          <Link to="/seller/auctions/create">
            <Button size="sm">Create auction</Button>
          </Link>
        }
      />

      <div className="mb-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Total lots" value={stats?.totalAuctions ?? '—'} icon="▤" />
        <StatCard label="Live now" value={stats?.live ?? '—'} tone="live" icon="◉" />
        <StatCard
          label="Awaiting approval"
          value={stats?.pendingApproval ?? '—'}
          tone="gold"
          sub={stats?.rejected ? `${stats.rejected} rejected` : undefined}
          icon="⏳"
        />
        <StatCard
          label="Revenue settled"
          value={stats ? money(stats.totalRevenue) : '—'}
          sub={stats ? `${stats.totalBidsReceived} bids received` : undefined}
          icon="$"
        />
      </div>

      <Tabs items={GROUPS} active={group} onChange={setGroup} className="mb-5 max-w-fit" />

      <DataTable
        columns={columns}
        rows={filtered}
        rowKey={(auction) => auction.id}
        loading={loading}
        empty="No lots in this group yet."
      />

      <Modal
        open={analyticsFor != null}
        onClose={() => setAnalyticsFor(null)}
        title={analytics?.title ?? 'Auction analytics'}
        width="max-w-xl"
      >
        {analytics ? (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <StatCard label="Bids" value={analytics.bidCount} />
              <StatCard label="Bidders" value={analytics.distinctBidders} />
              <StatCard label="Start" value={money(analytics.startingPrice)} />
              <StatCard label="Current" value={money(analytics.currentPrice)} tone="live" />
            </div>
            <div className="glass-soft p-4">
              <p className="label-eyebrow mb-2">Price progression</p>
              <BidSparkline analytics={analytics} />
              <p className="mt-2 text-[12px] text-slate-500">
                Uplift of {money(analytics.uplift, true)} over the starting price.
              </p>
            </div>
          </div>
        ) : (
          <div className="skeleton h-40 w-full rounded-xl" />
        )}
      </Modal>
    </PageShell>
  );
}
