import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { InlineCountdown } from '../components/auction/CountdownTimer';
import { PageHeader, PageShell } from '../components/layout/PageHeader';
import { Badge, StatusBadge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { DataTable, type Column } from '../components/ui/DataTable';
import { Modal } from '../components/ui/Modal';
import { StatCard } from '../components/ui/StatCard';
import { Tabs } from '../components/ui/Tabs';
import { Textarea } from '../components/ui/Field';
import { useAsync } from '../hooks/useAsync';
import { ChartFrame } from '../components/charts/ChartFrame';
import { DailySeries, Distribution, PALETTE, StatusDonut } from '../components/charts/AnalyticsCharts';
import { api } from '../lib/api';
import { cn } from '../lib/cn';
import { dateTime, humanise, money, relativeTime } from '../lib/format';
import { toast } from '../store/toastStore';
import type { AdminUser, AuctionStatus, AuctionSummary, AuditEvent, Role } from '../types';

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'approvals', label: 'Pending approvals' },
  { id: 'auctions', label: 'Auctions' },
  { id: 'users', label: 'Users' },
  { id: 'analytics', label: 'Analytics' },
  { id: 'reports', label: 'Reports' },
  { id: 'audit', label: 'Audit trail' },
];

export function AdminPage() {
  const [tab, setTab] = useState('overview');
  const [rejecting, setRejecting] = useState<AuctionSummary | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const { data: stats, reload: reloadStats } = useAsync(() => api.adminStats(), []);
  const {
    data: analytics,
    loading: loadingAnalytics,
    error: analyticsError,
  } = useAsync(() => api.platformAnalytics(30), []);
  const { data: pending, loading: loadingPending, reload: reloadPending } = useAsync(
    () => api.adminPending(0, 50),
    [],
  );

  const [auctionStatus, setAuctionStatus] = useState<AuctionStatus | ''>('');
  const { data: auctions, loading: loadingAuctions, reload: reloadAuctions } = useAsync(
    () => api.adminAuctions({ status: auctionStatus || undefined, page: 0, size: 50 }),
    [auctionStatus],
  );

  const [roleFilter, setRoleFilter] = useState<Role | ''>('');
  const [userQuery, setUserQuery] = useState('');
  const [userSearch, setUserSearch] = useState('');
  const { data: users, loading: loadingUsers, reload: reloadUsers } = useAsync(
    () => api.adminUsers({ role: roleFilter || undefined, query: userSearch || undefined, size: 50 }),
    [roleFilter, userSearch],
  );

  const { data: audit, loading: loadingAudit, reload: reloadAudit } = useAsync(
    () => api.adminAudit({ page: 0, size: 80 }),
    [],
  );
  const { data: report } = useAsync(() => api.adminActivityReport(), []);

  useEffect(() => {
    const handle = window.setTimeout(() => setUserSearch(userQuery), 350);
    return () => window.clearTimeout(handle);
  }, [userQuery]);

  const approve = useCallback(
    async (auction: AuctionSummary) => {
      try {
        await api.adminApprove(auction.id);
        toast.success('Listing approved', auction.title);
        reloadPending();
        reloadStats();
        reloadAudit();
      } catch (error) {
        toast.error('Approval failed', error instanceof Error ? error.message : undefined);
      }
    },
    [reloadPending, reloadStats, reloadAudit],
  );

  const reject = useCallback(async () => {
    if (!rejecting) return;
    try {
      await api.adminReject(rejecting.id, rejectReason.trim() || 'Did not meet listing guidelines.');
      toast.info('Listing rejected', rejecting.title);
      setRejecting(null);
      setRejectReason('');
      reloadPending();
      reloadStats();
      reloadAudit();
    } catch (error) {
      toast.error('Rejection failed', error instanceof Error ? error.message : undefined);
    }
  }, [rejecting, rejectReason, reloadPending, reloadStats, reloadAudit]);

  const setUserStatus = useCallback(
    async (user: AdminUser, status: 'ACTIVE' | 'SUSPENDED') => {
      try {
        await api.adminSetUserStatus(user.id, status, status === 'SUSPENDED' ? 'Suspended by admin' : undefined);
        toast.success(status === 'SUSPENDED' ? 'User suspended' : 'User reinstated', user.email);
        reloadUsers();
        reloadStats();
      } catch (error) {
        toast.error('Could not update user', error instanceof Error ? error.message : undefined);
      }
    },
    [reloadUsers, reloadStats],
  );

  const pendingColumns: Column<AuctionSummary>[] = [
    {
      key: 'title',
      header: 'Lot',
      render: (auction) => (
        <div className="min-w-0">
          <Link
            to={`/auctions/${auction.id}`}
            className="block max-w-[20rem] truncate text-slate-100 hover:text-accent-soft"
          >
            {auction.title}
          </Link>
          <span className="text-[11px] text-slate-500">
            {auction.categoryLabel} · {auction.sellerName}
          </span>
        </div>
      ),
    },
    {
      key: 'start',
      header: 'Starting price',
      align: 'right',
      render: (auction) => (
        <span className="tabular-nums text-slate-300">{money(auction.startingPrice, true)}</span>
      ),
    },
    {
      key: 'window',
      header: 'Window',
      render: (auction) => (
        <span className="text-[12px] text-slate-500">
          {dateTime(auction.startTime)} → {dateTime(auction.endTime)}
        </span>
      ),
    },
    {
      key: 'actions',
      header: '',
      align: 'right',
      render: (auction) => (
        <div className="flex justify-end gap-2">
          <Button size="sm" onClick={() => approve(auction)}>
            Approve
          </Button>
          <Button size="sm" variant="secondary" onClick={() => setRejecting(auction)}>
            Reject
          </Button>
        </div>
      ),
    },
  ];

  const auctionColumns: Column<AuctionSummary>[] = [
    {
      key: 'title',
      header: 'Lot',
      render: (auction) => (
        <Link
          to={`/auctions/${auction.id}`}
          className="block max-w-[20rem] truncate text-slate-100 hover:text-accent-soft"
        >
          {auction.title}
        </Link>
      ),
    },
    { key: 'status', header: 'Status', render: (auction) => <StatusBadge status={auction.status} /> },
    { key: 'seller', header: 'Seller', render: (auction) => <span className="text-[12px] text-slate-400">{auction.sellerName}</span> },
    {
      key: 'price',
      header: 'Price',
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
        <InlineCountdown
          endTime={auction.endTime}
          running={auction.status === 'LIVE' || auction.status === 'ENDING'}
          className="text-[12px]"
        />
      ),
    },
    {
      key: 'actions',
      header: '',
      align: 'right',
      render: (auction) =>
        ['CLOSED', 'SETTLED', 'ARCHIVED', 'CANCELLED'].includes(auction.status) ? null : (
          <button
            type="button"
            onClick={async () => {
              await api.adminCancel(auction.id).catch(() => toast.error('Could not cancel'));
              reloadAuctions();
              reloadStats();
            }}
            className="rounded-md px-2 py-1 text-[12px] text-slate-500 transition-colors hover:bg-white/[0.06] hover:text-danger"
          >
            Cancel
          </button>
        ),
    },
  ];

  const userColumns: Column<AdminUser>[] = [
    {
      key: 'name',
      header: 'User',
      render: (user) => (
        <div className="min-w-0">
          <span className="block truncate text-slate-100">{user.fullName}</span>
          <span className="block truncate text-[11px] text-slate-500">{user.email}</span>
        </div>
      ),
    },
    {
      key: 'role',
      header: 'Role',
      render: (user) => (
        <select
          value={user.role}
          onChange={async (event) => {
            await api
              .adminSetUserRole(user.id, event.target.value as Role)
              .catch(() => toast.error('Could not change role'));
            reloadUsers();
            reloadStats();
          }}
          aria-label={`Role for ${user.email}`}
          className="rounded-md border border-white/[0.08] bg-ink-800/70 px-2 py-1 text-[12px] text-slate-200 outline-none focus:border-accent/60"
        >
          {(['BIDDER', 'SELLER', 'ADMIN'] as Role[]).map((role) => (
            <option key={role} value={role}>
              {humanise(role)}
            </option>
          ))}
        </select>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (user) => (
        <Badge tone={user.status === 'ACTIVE' ? 'live' : 'danger'}>{humanise(user.status)}</Badge>
      ),
    },
    {
      key: 'activity',
      header: 'Activity',
      align: 'right',
      render: (user) => (
        <span className="text-[12px] text-slate-500">
          {user.auctionsCreated} lots · {user.bidsPlaced} bids
        </span>
      ),
    },
    {
      key: 'last',
      header: 'Last seen',
      align: 'right',
      render: (user) => (
        <span className="text-[12px] text-slate-500">
          {user.lastLoginAt ? relativeTime(user.lastLoginAt) : 'never'}
        </span>
      ),
    },
    {
      key: 'actions',
      header: '',
      align: 'right',
      render: (user) =>
        user.role === 'ADMIN' ? null : (
          <button
            type="button"
            onClick={() => setUserStatus(user, user.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE')}
            className={cn(
              'rounded-md px-2 py-1 text-[12px] transition-colors hover:bg-white/[0.06]',
              user.status === 'ACTIVE' ? 'text-slate-500 hover:text-danger' : 'text-live',
            )}
          >
            {user.status === 'ACTIVE' ? 'Suspend' : 'Reinstate'}
          </button>
        ),
    },
  ];

  const auditColumns: Column<AuditEvent>[] = [
    {
      key: 'action',
      header: 'Action',
      render: (event) => <span className="font-mono text-[11px] text-accent-soft">{event.action}</span>,
    },
    {
      key: 'actor',
      header: 'Actor',
      render: (event) => (
        <span className="text-[12px] text-slate-400">{event.actorEmail ?? 'system'}</span>
      ),
    },
    {
      key: 'entity',
      header: 'Entity',
      render: (event) => (
        <span className="text-[12px] text-slate-500">
          {event.entityType}
          {event.entityId != null && ` #${event.entityId}`}
        </span>
      ),
    },
    {
      key: 'details',
      header: 'Details',
      render: (event) => (
        <span className="block max-w-[26rem] truncate text-[12px] text-slate-500">
          {event.details ?? '—'}
        </span>
      ),
    },
    {
      key: 'at',
      header: 'Server time',
      align: 'right',
      render: (event) => <span className="text-[12px] text-slate-500">{dateTime(event.createdAt)}</span>,
    },
  ];

  const maxReportCount = Math.max(1, ...(report?.map((row) => Number(row.count)) ?? [1]));

  return (
    <PageShell wide>
      <PageHeader
        eyebrow="Administration"
        title="Platform control"
        description="Moderate listings, manage accounts and review everything the platform has recorded."
        actions={
          <Button size="sm" variant="secondary" onClick={() => { reloadStats(); reloadPending(); reloadAuctions(); reloadUsers(); reloadAudit(); }}>
            Refresh
          </Button>
        }
      />

      <div className="mb-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          label="Pending approvals"
          value={stats?.pendingApprovals ?? '—'}
          tone="gold"
          sub="Awaiting moderation"
          icon="⏳"
        />
        <StatCard label="Live auctions" value={stats?.liveAuctions ?? '—'} tone="live" icon="◉" />
        <StatCard
          label="Users"
          value={stats?.totalUsers ?? '—'}
          sub={stats ? `${stats.suspendedUsers} suspended` : undefined}
          icon="☰"
        />
        <StatCard
          label="Settled value"
          value={stats ? money(stats.settledValue) : '—'}
          sub={stats ? `${stats.totalBids} bids all-time` : undefined}
          icon="$"
        />
      </div>

      <Tabs
        items={TABS.map((item) =>
          item.id === 'approvals' ? { ...item, count: stats?.pendingApprovals } : item,
        )}
        active={tab}
        onChange={setTab}
        className="mb-5 max-w-fit"
      />

      {tab === 'overview' && (
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="glass p-5">
            <h2 className="text-[13px] font-semibold uppercase tracking-[0.16em] text-slate-400">
              Auction pipeline
            </h2>
            <dl className="mt-4 space-y-3">
              {[
                ['Pending approval', stats?.pendingApprovals, 'text-gold'],
                ['Scheduled', stats?.scheduledAuctions, 'text-accent-soft'],
                ['Live', stats?.liveAuctions, 'text-live'],
                ['Closed', stats?.closedAuctions, 'text-slate-400'],
                ['Settled', stats?.settledAuctions, 'text-violet-glow'],
              ].map(([label, value, tone]) => (
                <div key={String(label)} className="flex items-center justify-between">
                  <dt className="text-[13px] text-slate-400">{label}</dt>
                  <dd className={cn('font-display text-base font-semibold tabular-nums', tone as string)}>
                    {value ?? '—'}
                  </dd>
                </div>
              ))}
            </dl>
          </div>

          <div className="glass p-5">
            <h2 className="text-[13px] font-semibold uppercase tracking-[0.16em] text-slate-400">
              Community
            </h2>
            <dl className="mt-4 space-y-3">
              {[
                ['Bidders', stats?.bidders],
                ['Sellers', stats?.sellers],
                ['Administrators', stats?.admins],
                ['Suspended', stats?.suspendedUsers],
                ['Total bids placed', stats?.totalBids],
              ].map(([label, value]) => (
                <div key={String(label)} className="flex items-center justify-between">
                  <dt className="text-[13px] text-slate-400">{label}</dt>
                  <dd className="font-display text-base font-semibold tabular-nums text-white">
                    {value ?? '—'}
                  </dd>
                </div>
              ))}
            </dl>
          </div>
        </div>
      )}

      {tab === 'approvals' && (
        <DataTable
          columns={pendingColumns}
          rows={pending?.content ?? []}
          rowKey={(auction) => auction.id}
          loading={loadingPending}
          empty="No listings are waiting for review."
        />
      )}

      {tab === 'auctions' && (
        <>
          <div className="mb-4 flex flex-wrap gap-2">
            {(['', 'PENDING_APPROVAL', 'SCHEDULED', 'LIVE', 'SETTLED', 'CANCELLED'] as const).map(
              (value) => (
                <button
                  key={value || 'all'}
                  type="button"
                  onClick={() => setAuctionStatus(value as AuctionStatus | '')}
                  className={cn(
                    'rounded-lg px-3 py-1.5 text-[12px] font-medium transition-colors',
                    auctionStatus === value
                      ? 'bg-accent/20 text-white ring-1 ring-inset ring-accent/40'
                      : 'bg-white/[0.04] text-slate-400 hover:bg-white/[0.08]',
                  )}
                >
                  {value ? humanise(value) : 'All'}
                </button>
              ),
            )}
          </div>
          <DataTable
            columns={auctionColumns}
            rows={auctions?.content ?? []}
            rowKey={(auction) => auction.id}
            loading={loadingAuctions}
            empty="No auctions match this filter."
          />
        </>
      )}

      {tab === 'users' && (
        <>
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <input
              type="search"
              value={userQuery}
              onChange={(event) => setUserQuery(event.target.value)}
              placeholder="Search name or e-mail"
              aria-label="Search users"
              className="h-9 w-64 rounded-lg border border-white/[0.08] bg-ink-800/70 px-3 text-[13px] text-slate-200 outline-none focus:border-accent/60"
            />
            {(['', 'BIDDER', 'SELLER', 'ADMIN'] as const).map((value) => (
              <button
                key={value || 'all'}
                type="button"
                onClick={() => setRoleFilter(value as Role | '')}
                className={cn(
                  'rounded-lg px-3 py-1.5 text-[12px] font-medium transition-colors',
                  roleFilter === value
                    ? 'bg-accent/20 text-white ring-1 ring-inset ring-accent/40'
                    : 'bg-white/[0.04] text-slate-400 hover:bg-white/[0.08]',
                )}
              >
                {value ? humanise(value) : 'All roles'}
              </button>
            ))}
          </div>
          <DataTable
            columns={userColumns}
            rows={users?.content ?? []}
            rowKey={(user) => user.id}
            loading={loadingUsers}
            empty="No users match this filter."
          />
        </>
      )}

      {tab === 'analytics' && (
        <div className="space-y-4">
          <p className="text-[11px] text-slate-500">
            Counted from the database over the last {analytics?.days ?? 30} days. Days with no
            activity are shown as zero rather than omitted, and nothing here is projected.
          </p>

          <div className="grid gap-4 lg:grid-cols-2">
            <ChartFrame
              title="Bid volume"
              caption="Bids placed per day"
              loading={loadingAnalytics}
              error={!!analyticsError}
              empty={!!analytics && analytics.bidsPerDay.every((p) => p.count === 0)}
              emptyMessage="No bids have been placed in this period."
            >
              {analytics && <DailySeries data={analytics.bidsPerDay} label="Bids" color={PALETTE[0]} />}
            </ChartFrame>

            <ChartFrame
              title="Listings created"
              caption="New lots per day"
              loading={loadingAnalytics}
              error={!!analyticsError}
              empty={!!analytics && analytics.auctionsPerDay.every((p) => p.count === 0)}
              emptyMessage="No listings were created in this period."
            >
              {analytics && (
                <DailySeries data={analytics.auctionsPerDay} label="Listings" color={PALETTE[1]} />
              )}
            </ChartFrame>

            <ChartFrame
              title="Lots by category"
              caption="Across the whole catalogue"
              loading={loadingAnalytics}
              error={!!analyticsError}
              empty={!!analytics && analytics.byCategory.length === 0}
            >
              {analytics && <Distribution data={analytics.byCategory} label="Lots" />}
            </ChartFrame>

            <ChartFrame
              title="Lots by status"
              caption="Where everything stands right now"
              loading={loadingAnalytics}
              error={!!analyticsError}
              empty={!!analytics && analytics.byStatus.length === 0}
              height={264}
            >
              {analytics && <StatusDonut data={analytics.byStatus} />}
            </ChartFrame>

            <ChartFrame
              title="Registrations"
              caption="New accounts per day"
              loading={loadingAnalytics}
              error={!!analyticsError}
              empty={!!analytics && analytics.signUpsPerDay.every((p) => p.count === 0)}
              emptyMessage="No accounts were created in this period."
            >
              {analytics && (
                <DailySeries data={analytics.signUpsPerDay} label="Sign-ups" color={PALETTE[4]} />
              )}
            </ChartFrame>

            <ChartFrame
              title="Seller performance"
              caption="Busiest sellers, with settled value"
              loading={loadingAnalytics}
              error={!!analyticsError}
              empty={!!analytics && analytics.topSellers.length === 0}
            >
              <div className="h-full overflow-y-auto">
                <table className="w-full text-[12px]">
                  <thead className="text-[10px] uppercase tracking-[0.14em] text-slate-500">
                    <tr>
                      <th className="pb-2 text-left font-medium">Seller</th>
                      <th className="pb-2 text-right font-medium">Lots</th>
                      <th className="pb-2 text-right font-medium">Sold</th>
                      <th className="pb-2 text-right font-medium">Settled value</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/[0.06]">
                    {(analytics?.topSellers ?? []).map((row) => (
                      <tr key={row.sellerId}>
                        <td className="py-2 pr-2 text-slate-200">{row.name}</td>
                        <td className="py-2 text-right tabular-nums text-slate-300">{row.lots}</td>
                        <td className="py-2 text-right tabular-nums text-slate-300">{row.settled}</td>
                        <td className="py-2 text-right tabular-nums text-white">
                          {money(row.revenue, false)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ChartFrame>
          </div>
        </div>
      )}

      {tab === 'reports' && (
        <div className="glass p-5">
          <h2 className="text-[13px] font-semibold uppercase tracking-[0.16em] text-slate-400">
            Recorded activity by type
          </h2>
          <ul className="mt-5 space-y-2.5">
            {(report ?? []).map((row) => (
              <li key={row.action} className="flex items-center gap-3">
                <span className="w-48 shrink-0 truncate font-mono text-[11px] text-slate-400">
                  {row.action}
                </span>
                <span className="h-2 flex-1 overflow-hidden rounded-full bg-white/[0.05]">
                  <span
                    className="block h-full rounded-full bg-gradient-to-r from-accent to-violet-glow"
                    style={{ width: `${(Number(row.count) / maxReportCount) * 100}%` }}
                  />
                </span>
                <span className="w-12 shrink-0 text-right font-display text-[13px] tabular-nums text-slate-300">
                  {row.count}
                </span>
              </li>
            ))}
            {(!report || report.length === 0) && (
              <li className="py-6 text-center text-sm text-slate-500">No activity recorded yet.</li>
            )}
          </ul>
        </div>
      )}

      {tab === 'audit' && (
        <DataTable
          columns={auditColumns}
          rows={audit?.content ?? []}
          rowKey={(event) => event.id}
          loading={loadingAudit}
          empty="The audit trail is empty."
        />
      )}

      <Modal
        open={rejecting != null}
        onClose={() => setRejecting(null)}
        title="Reject this listing"
        footer={
          <>
            <Button variant="ghost" onClick={() => setRejecting(null)}>
              Cancel
            </Button>
            <Button variant="danger" onClick={reject}>
              Reject listing
            </Button>
          </>
        }
      >
        <p className="text-sm text-slate-400">
          The seller is notified with your reason and can edit and resubmit the listing.
        </p>
        <Textarea
          label="Reason"
          value={rejectReason}
          onChange={(event) => setRejectReason(event.target.value)}
          placeholder="Photos are unclear and the condition description is incomplete."
        />
      </Modal>
    </PageShell>
  );
}
