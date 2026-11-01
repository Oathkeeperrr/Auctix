import gsap from 'gsap';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CountdownTimer, InlineCountdown } from '../components/auction/CountdownTimer';
import { AuctionCard } from '../components/auction/AuctionCard';
import { ItemThumbnail } from '../components/auction/ItemThumbnail';
import { HeroCanvas } from '../components/three/HeroCanvas';
import { StatusBadge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { CardSkeleton } from '../components/ui/Skeleton';
import { useAsync } from '../hooks/useAsync';
import { api } from '../lib/api';
import { cn } from '../lib/cn';
import { money } from '../lib/format';
import { useAuctionChannel, useGlobalAuctionFeed } from '../realtime/useAuctionChannel';
import { useAuthStore } from '../store/authStore';
import type { AuctionSummary } from '../types';

const BENEFITS = [
  {
    icon: '⚡',
    title: 'Real-time Bidding',
    detail: 'Every bid broadcast over WebSockets the instant the server accepts it.',
  },
  {
    icon: '⛨',
    title: 'Secure & Trusted',
    detail: 'JWT auth, BCrypt hashing and a transparent, timestamped audit trail.',
  },
  {
    icon: '⬢',
    title: 'Containerized & Scalable',
    detail: 'Docker Compose brings up the whole stack; Redis fans events across instances.',
  },
];

const HOW_IT_WORKS = [
  {
    step: '01',
    title: 'Seller lists a lot',
    detail: 'Item details, starting price, reserve, increment and the exact window it runs for.',
  },
  {
    step: '02',
    title: 'Admin approves',
    detail: 'Moderation gates every listing before it can be scheduled and go live.',
  },
  {
    step: '03',
    title: 'Bidding opens',
    detail: 'The server clock starts the auction. Bids are locked, ordered and broadcast.',
  },
  {
    step: '04',
    title: 'Highest valid bid wins',
    detail: 'At the server deadline the auction closes itself and settles the winner.',
  },
];

export function LandingPage() {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const heroRef = useRef<HTMLDivElement>(null);
  const [selectedIndex, setSelectedIndex] = useState(0);

  const { data: featured, loading } = useAsync(() => api.featured(10), []);
  const [auctions, setAuctions] = useState<AuctionSummary[]>([]);

  useEffect(() => {
    if (featured) setAuctions(featured);
  }, [featured]);

  const selected = auctions[selectedIndex];

  // The hero panel is driven by the same live channel as the auction page,
  // so the price on the landing page moves the moment somebody bids.
  const channel = useAuctionChannel(selected?.id, {
    initialState: null,
  });

  // Prices across the whole catalogue stay fresh from the global feed.
  useGlobalAuctionFeed((event) => {
    if (!event.state || !event.auctionId) return;
    const { auctionId, state } = event;
    setAuctions((current) =>
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

  useEffect(() => {
    if (!heroRef.current) return;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced) return;

    const context = gsap.context(() => {
      gsap.from('[data-hero-stagger]', {
        y: 26,
        opacity: 0,
        duration: 0.9,
        ease: 'power3.out',
        stagger: 0.09,
        delay: 0.15,
      });
      gsap.from('[data-hero-panel]', {
        x: 34,
        opacity: 0,
        duration: 1,
        ease: 'power3.out',
        delay: 0.45,
      });
    }, heroRef);

    return () => context.revert();
  }, []);

  const liveState = channel.state;
  const currentPrice = liveState?.currentPrice ?? selected?.currentPrice ?? 0;
  const nextMinimum = liveState?.nextMinimumBid ?? selected?.nextMinimumBid ?? 0;
  const status = liveState?.status ?? selected?.status ?? 'SCHEDULED';
  const endTime = liveState?.endTime ?? selected?.endTime;

  const liveCount = useMemo(
    () => auctions.filter((auction) => auction.status === 'LIVE' || auction.status === 'ENDING').length,
    [auctions],
  );

  function cycle(direction: 1 | -1) {
    if (auctions.length === 0) return;
    setSelectedIndex((index) => (index + direction + auctions.length) % auctions.length);
  }

  async function toggleWatch(auction: AuctionSummary) {
    if (!user) {
      navigate('/login');
      return;
    }
    const result = await api.toggleWatch(auction.id).catch(() => null);
    if (!result) return;
    setAuctions((current) =>
      current.map((item) => (item.id === auction.id ? { ...item, watched: result.watched } : item)),
    );
  }

  return (
    <>
      {/* ----------------------------------------------------------- HERO */}
      <section
        ref={heroRef}
        className="relative flex min-h-[100svh] items-center overflow-hidden pt-20"
        aria-label="Featured live auction"
      >
        <div className="pointer-events-none absolute inset-0 bg-grid-faint [background-size:64px_64px] opacity-[0.55]" />
        <div className="absolute inset-0">
          {selected ? (
            <HeroCanvas kind={selected.modelKind} />
          ) : (
            <div className="absolute inset-0 flex items-center justify-center">
              <span className="h-10 w-10 animate-spin rounded-full border-2 border-accent/60 border-t-transparent" />
            </div>
          )}
        </div>
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-40 bg-gradient-to-t from-ink-950 to-transparent" />

        <div className="relative z-10 mx-auto grid w-full max-w-[1500px] gap-10 px-4 pb-24 sm:px-6 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center lg:gap-6 lg:px-8">
          {/* Headline column */}
          <div className="max-w-xl">
            <div
              data-hero-stagger
              className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.04] px-3 py-1.5 backdrop-blur-sm"
            >
              <span className="relative flex h-1.5 w-1.5">
                <span className="absolute inline-flex h-full w-full animate-pulse-ring rounded-full bg-live" />
                <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-live" />
              </span>
              <span className="text-[10px] font-semibold uppercase tracking-[0.22em] text-slate-300">
                Live auctions
              </span>
              <span className="text-slate-600">•</span>
              <span className="text-[10px] font-semibold uppercase tracking-[0.22em] text-slate-300">
                Real time bidding
              </span>
            </div>

            <h1
              data-hero-stagger
              className="mt-6 font-display text-[2.6rem] font-bold leading-[1.04] tracking-tight text-balance sm:text-6xl lg:text-[4.2rem]"
            >
              Bid. Own.
              <br />
              Experience{' '}
              <span className="bg-gradient-to-r from-accent via-violet-glow to-accent-soft bg-clip-text text-transparent">
                More.
              </span>
            </h1>

            <p data-hero-stagger className="mt-5 max-w-lg text-[15px] leading-relaxed text-slate-400">
              AuctiX is a next-generation, real-time auction platform where rare items, unique
              collectibles and exclusive finds meet passionate bidders — all in one place.
            </p>

            <div data-hero-stagger className="mt-7 flex flex-wrap items-center gap-3">
              <Button size="lg" onClick={() => navigate('/auctions')}>
                Explore Auctions <span aria-hidden>→</span>
              </Button>
              <a
                href="#how-it-works"
                className="inline-flex h-12 items-center gap-2.5 rounded-xl border border-white/10 bg-white/[0.03] px-5 py-3.5 text-[15px] text-slate-200 backdrop-blur-sm transition-colors hover:bg-white/[0.08]"
              >
                <span className="flex h-6 w-6 items-center justify-center rounded-full border border-white/20 text-[9px]">
                  ▶
                </span>
                How It Works
              </a>
            </div>

            <dl
              data-hero-stagger
              className="mt-10 grid gap-4 border-t border-white/[0.07] pt-6 sm:grid-cols-3"
            >
              {BENEFITS.map((benefit) => (
                <div key={benefit.title} className="flex items-start gap-2.5">
                  <span aria-hidden className="mt-0.5 text-base text-accent">
                    {benefit.icon}
                  </span>
                  <div className="min-w-0">
                    <dt className="text-[13px] font-medium text-slate-200">{benefit.title}</dt>
                    <dd className="mt-0.5 text-[11px] leading-snug text-slate-500">{benefit.detail}</dd>
                  </div>
                </div>
              ))}
            </dl>
          </div>

          {/* Live auction panel */}
          <aside data-hero-panel className="w-full lg:w-[21rem]">
            {selected ? (
              <div className="glass edge-glow is-active space-y-4 p-5">
                <div className="flex items-center justify-between">
                  <StatusBadge status={status} />
                  <span className="text-[10px] uppercase tracking-[0.2em] text-slate-500">
                    {liveCount} live now
                  </span>
                </div>

                <CountdownTimer
                  endTime={endTime ?? selected.endTime}
                  running={status === 'LIVE' || status === 'ENDING' || status === 'SCHEDULED'}
                  size="md"
                />

                <div className="border-t border-white/[0.07] pt-3">
                  <h2 className="line-clamp-1 font-display text-lg font-semibold text-white">
                    {selected.title}
                  </h2>
                  <p className="text-[12px] text-slate-500">{selected.categoryLabel}</p>
                </div>

                <div className="rounded-xl bg-white/[0.03] p-3.5">
                  <p className="label-eyebrow">Current bid</p>
                  <div className="mt-1 flex items-baseline gap-2">
                    <span
                      key={`${selected.id}-${currentPrice}`}
                      className="animate-price-pop font-display text-3xl font-semibold tabular-nums text-white"
                    >
                      {money(currentPrice, false)}
                    </span>
                    {channel.priceTick > 0 && <span className="text-[11px] text-live">▲ live</span>}
                  </div>
                  <div className="mt-3 flex items-center justify-between border-t border-white/[0.06] pt-2.5">
                    <span className="text-[11px] text-slate-500">Next minimum bid</span>
                    <span className="font-display text-sm font-medium tabular-nums text-slate-200">
                      {money(nextMinimum, false)}
                    </span>
                  </div>
                </div>

                <Button fullWidth onClick={() => navigate(`/auctions/${selected.id}`)}>
                  Place Bid <span aria-hidden>→</span>
                </Button>

                <p className="text-center text-[10px] text-slate-600">
                  {selected.bidCount} bids · closes on server time
                </p>
              </div>
            ) : (
              <div className="glass h-[22rem] animate-pulse" />
            )}

            {/* Featured item switcher */}
            {auctions.length > 1 && (
              <div className="mt-4 flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => cycle(-1)}
                  aria-label="Previous featured lot"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-white/10 bg-white/[0.04] text-slate-300 transition-colors hover:bg-white/[0.1]"
                >
                  ‹
                </button>
                <div className="no-scrollbar flex flex-1 gap-2 overflow-x-auto">
                  {auctions.slice(0, 6).map((auction, index) => (
                    <button
                      key={auction.id}
                      type="button"
                      onClick={() => setSelectedIndex(index)}
                      aria-label={`Show ${auction.title}`}
                      aria-current={index === selectedIndex}
                      className={cn(
                        'relative h-12 w-12 shrink-0 overflow-hidden rounded-xl transition-all',
                        index === selectedIndex
                          ? 'ring-2 ring-accent ring-offset-2 ring-offset-ink-950'
                          : 'opacity-55 hover:opacity-100',
                      )}
                    >
                      <ItemThumbnail
                        kind={auction.modelKind}
                        imageUrl={auction.imageUrl}
                        title={auction.title}
                      />
                    </button>
                  ))}
                </div>
                <button
                  type="button"
                  onClick={() => cycle(1)}
                  aria-label="Next featured lot"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-white/10 bg-white/[0.04] text-slate-300 transition-colors hover:bg-white/[0.1]"
                >
                  ›
                </button>
              </div>
            )}
          </aside>
        </div>
      </section>

      {/* ----------------------------------------------------------- FEATURED */}
      <section className="mx-auto w-full max-w-[1500px] px-4 py-16 sm:px-6 lg:px-8" id="featured">
        <div className="mb-7 flex items-end justify-between gap-4">
          <div>
            <p className="label-eyebrow">Featured auctions</p>
            <h2 className="mt-1.5 font-display text-2xl font-semibold tracking-tight sm:text-3xl">
              Live &amp; Upcoming
            </h2>
          </div>
          <Link
            to="/auctions"
            className="shrink-0 text-[13px] text-accent-soft transition-colors hover:text-white"
          >
            View all <span aria-hidden>→</span>
          </Link>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {loading && Array.from({ length: 4 }).map((_, index) => <CardSkeleton key={index} />)}
          {!loading &&
            auctions
              .slice(0, 8)
              .map((auction) => (
                <AuctionCard key={auction.id} auction={auction} onToggleWatch={toggleWatch} />
              ))}
        </div>
      </section>

      {/* ----------------------------------------------------------- HOW IT WORKS */}
      <section
        id="how-it-works"
        className="mx-auto w-full max-w-[1500px] scroll-mt-24 px-4 py-16 sm:px-6 lg:px-8"
      >
        <p className="label-eyebrow">How it works</p>
        <h2 className="mt-1.5 max-w-2xl font-display text-2xl font-semibold tracking-tight text-balance sm:text-3xl">
          Four steps from listing to settlement, every one of them decided by the server.
        </h2>

        <ol className="mt-8 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          {HOW_IT_WORKS.map((item) => (
            <li key={item.step} className="glass relative overflow-hidden p-5">
              <span className="absolute -right-3 -top-4 font-display text-6xl font-bold text-white/[0.04]">
                {item.step}
              </span>
              <span className="nav-index">{item.step}</span>
              <h3 className="mt-2 text-[15px] font-semibold text-white">{item.title}</h3>
              <p className="mt-1.5 text-[13px] leading-relaxed text-slate-500">{item.detail}</p>
            </li>
          ))}
        </ol>
      </section>

      {/* ----------------------------------------------------------- ENDING SOON STRIP */}
      {auctions.length > 0 && (
        <section className="mx-auto w-full max-w-[1500px] px-4 pb-8 sm:px-6 lg:px-8">
          <div className="glass flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="label-eyebrow">Closing next</p>
              <p className="mt-1 text-sm text-slate-400">
                Countdowns below run on the backend clock, not your device's.
              </p>
            </div>
            <div className="no-scrollbar flex gap-3 overflow-x-auto">
              {auctions.slice(0, 4).map((auction) => (
                <Link
                  key={auction.id}
                  to={`/auctions/${auction.id}`}
                  className="flex shrink-0 items-center gap-3 rounded-xl bg-white/[0.035] px-3 py-2 transition-colors hover:bg-white/[0.07]"
                >
                  <div className="h-9 w-9 overflow-hidden rounded-lg">
                    <ItemThumbnail
                      kind={auction.modelKind}
                      imageUrl={auction.imageUrl}
                      title={auction.title}
                    />
                  </div>
                  <div className="min-w-0">
                    <p className="max-w-[9rem] truncate text-[12px] text-slate-200">{auction.title}</p>
                    <InlineCountdown endTime={auction.endTime} className="text-[11px]" />
                  </div>
                </Link>
              ))}
            </div>
          </div>
        </section>
      )}
    </>
  );
}
