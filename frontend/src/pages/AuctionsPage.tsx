import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AuctionCard } from '../components/auction/AuctionCard';
import { PageHeader, PageShell } from '../components/layout/PageHeader';
import { Button } from '../components/ui/Button';
import { EmptyState } from '../components/ui/EmptyState';
import { CardSkeleton } from '../components/ui/Skeleton';
import { useAsync } from '../hooks/useAsync';
import { api } from '../lib/api';
import { cn } from '../lib/cn';
import { useGlobalAuctionFeed } from '../realtime/useAuctionChannel';
import { useAuthStore } from '../store/authStore';
import type { AuctionStatus, AuctionSummary, CategoryOption } from '../types';

const SORTS = [
  { id: 'ENDING_SOON', label: 'Ending soon' },
  { id: 'NEWEST', label: 'Newest' },
  { id: 'MOST_BIDS', label: 'Most bids' },
  { id: 'PRICE_ASC', label: 'Price: low to high' },
  { id: 'PRICE_DESC', label: 'Price: high to low' },
];

const STATUSES: { id: AuctionStatus | 'ALL'; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'LIVE', label: 'Live' },
  { id: 'SCHEDULED', label: 'Upcoming' },
  { id: 'SETTLED', label: 'Completed' },
];

export function AuctionsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);

  const search = params.get('search') ?? '';
  const category = params.get('category') ?? '';
  const status = (params.get('status') as AuctionStatus | null) ?? null;
  const sort = params.get('sort') ?? 'ENDING_SOON';
  const minPrice = params.get('minPrice') ?? '';
  const maxPrice = params.get('maxPrice') ?? '';
  const page = Number(params.get('page') ?? '0');

  const [searchDraft, setSearchDraft] = useState(search);
  const [items, setItems] = useState<AuctionSummary[]>([]);

  const { data: categories } = useAsync<CategoryOption[]>(() => api.categories(), []);
  const { data, loading, error } = useAsync(
    () =>
      api.auctions({
        search: search || undefined,
        category: category || undefined,
        status: status ?? undefined,
        minPrice: minPrice ? Number(minPrice) : undefined,
        maxPrice: maxPrice ? Number(maxPrice) : undefined,
        sort,
        page,
        size: 12,
      }),
    [search, category, status, sort, minPrice, maxPrice, page],
  );

  useEffect(() => {
    if (data) setItems(data.content);
  }, [data]);

  useEffect(() => setSearchDraft(search), [search]);

  // Live price updates while browsing.
  useGlobalAuctionFeed((event) => {
    if (!event.state || !event.auctionId) return;
    const { auctionId, state } = event;
    setItems((current) =>
      current.map((auction) =>
        auction.id === auctionId
          ? {
              ...auction,
              currentPrice: state.currentPrice,
              nextMinimumBid: state.nextMinimumBid,
              bidCount: state.bidCount,
              status: state.status,
              endTime: state.endTime,
            }
          : auction,
      ),
    );
  });

  const update = useCallback(
    (patch: Record<string, string | null>) => {
      const next = new URLSearchParams(params);
      Object.entries(patch).forEach(([key, value]) => {
        if (value === null || value === '') next.delete(key);
        else next.set(key, value);
      });
      if (!('page' in patch)) next.delete('page');
      setParams(next, { replace: true });
    },
    [params, setParams],
  );

  const toggleWatch = useCallback(
    async (auction: AuctionSummary) => {
      if (!user) {
        navigate('/login');
        return;
      }
      const result = await api.toggleWatch(auction.id).catch(() => null);
      if (!result) return;
      setItems((current) =>
        current.map((item) => (item.id === auction.id ? { ...item, watched: result.watched } : item)),
      );
    },
    [navigate, user],
  );

  const activeFilters = useMemo(
    () => [search, category, status, minPrice, maxPrice].filter(Boolean).length,
    [search, category, status, minPrice, maxPrice],
  );

  return (
    <PageShell wide>
      <PageHeader
        eyebrow="Marketplace"
        title="Auction browser"
        description="Filter the catalogue by category, status and price. Prices and countdowns update live from the server."
        actions={
          activeFilters > 0 ? (
            <Button variant="secondary" size="sm" onClick={() => setParams(new URLSearchParams())}>
              Clear filters ({activeFilters})
            </Button>
          ) : undefined
        }
      />

      {/* Filter bar */}
      <div className="glass mb-6 space-y-4 p-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
          <form
            className="flex flex-1 gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              update({ search: searchDraft });
            }}
          >
            <input
              type="search"
              value={searchDraft}
              onChange={(event) => setSearchDraft(event.target.value)}
              placeholder="Search by title or description"
              aria-label="Search auctions"
              className="h-10 flex-1 rounded-xl border border-white/[0.08] bg-ink-800/70 px-3.5 text-sm text-slate-100 placeholder:text-slate-600 outline-none transition-colors focus:border-accent/60"
            />
            <Button type="submit" size="sm" className="h-10 px-4">
              Search
            </Button>
          </form>

          <div className="flex gap-2">
            <select
              value={category}
              onChange={(event) => update({ category: event.target.value })}
              aria-label="Filter by category"
              className="h-10 rounded-xl border border-white/[0.08] bg-ink-800/70 px-3 text-[13px] text-slate-200 outline-none focus:border-accent/60"
            >
              <option value="">All categories</option>
              {categories?.map((option) => (
                <option key={option.name} value={option.name}>
                  {option.label}
                </option>
              ))}
            </select>

            <select
              value={sort}
              onChange={(event) => update({ sort: event.target.value })}
              aria-label="Sort auctions"
              className="h-10 rounded-xl border border-white/[0.08] bg-ink-800/70 px-3 text-[13px] text-slate-200 outline-none focus:border-accent/60"
            >
              {SORTS.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {STATUSES.map((option) => {
            const selected = option.id === 'ALL' ? !status : status === option.id;
            return (
              <button
                key={option.id}
                type="button"
                onClick={() => update({ status: option.id === 'ALL' ? null : option.id })}
                className={cn(
                  'rounded-lg px-3 py-1.5 text-[12px] font-medium transition-colors',
                  selected
                    ? 'bg-accent/20 text-white ring-1 ring-inset ring-accent/40'
                    : 'bg-white/[0.04] text-slate-400 hover:bg-white/[0.08] hover:text-slate-200',
                )}
              >
                {option.label}
              </button>
            );
          })}

          <span className="mx-1 h-5 w-px bg-white/10" />

          <input
            type="number"
            value={minPrice}
            placeholder="Min $"
            aria-label="Minimum price"
            onChange={(event) => update({ minPrice: event.target.value })}
            className="h-9 w-24 rounded-lg border border-white/[0.08] bg-ink-800/70 px-2.5 text-[12px] text-slate-200 outline-none focus:border-accent/60"
          />
          <input
            type="number"
            value={maxPrice}
            placeholder="Max $"
            aria-label="Maximum price"
            onChange={(event) => update({ maxPrice: event.target.value })}
            className="h-9 w-24 rounded-lg border border-white/[0.08] bg-ink-800/70 px-2.5 text-[12px] text-slate-200 outline-none focus:border-accent/60"
          />

          {data && (
            <span className="ml-auto text-[12px] text-slate-500">
              {data.totalElements} lot{data.totalElements === 1 ? '' : 's'}
            </span>
          )}
        </div>
      </div>

      {error && (
        <div className="glass mb-6 border-danger/30 p-4 text-sm text-danger">{error}</div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {loading && Array.from({ length: 8 }).map((_, index) => <CardSkeleton key={index} />)}
        {!loading &&
          items.map((auction) => (
            <AuctionCard key={auction.id} auction={auction} onToggleWatch={toggleWatch} />
          ))}
      </div>

      {!loading && items.length === 0 && (
        <EmptyState
          title="No lots match these filters"
          message="Try widening the price range, clearing the category, or searching for something else."
          action={
            <Button variant="secondary" size="sm" onClick={() => setParams(new URLSearchParams())}>
              Reset filters
            </Button>
          }
        />
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-8 flex items-center justify-center gap-3">
          <Button
            variant="secondary"
            size="sm"
            disabled={page === 0}
            onClick={() => update({ page: String(page - 1) })}
          >
            Previous
          </Button>
          <span className="text-[13px] text-slate-500">
            Page {page + 1} of {data.totalPages}
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={data.last}
            onClick={() => update({ page: String(page + 1) })}
          >
            Next
          </Button>
        </div>
      )}
    </PageShell>
  );
}
