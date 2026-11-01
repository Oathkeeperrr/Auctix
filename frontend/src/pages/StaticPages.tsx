import { Link } from 'react-router-dom';
import { PageHeader, PageShell } from '../components/layout/PageHeader';
import { Button } from '../components/ui/Button';

const ARCHITECTURE = [
  { layer: 'Client', detail: 'React 18 + Vite + TypeScript, Three.js via React Three Fiber, Tailwind CSS.' },
  { layer: 'Edge', detail: 'Nginx reverse proxy: /api and /ws to Spring Boot, everything else to the SPA.' },
  { layer: 'Application', detail: 'Java 17 + Spring Boot: REST, Spring Security with JWT, STOMP over WebSocket.' },
  { layer: 'Cache & messaging', detail: 'Redis for pub/sub fan-out between instances and bid rate limiting.' },
  { layer: 'Database', detail: 'PostgreSQL with JPA/Hibernate, row-level locking and optimistic versioning.' },
  { layer: 'Runtime', detail: 'Docker images for every service, orchestrated by one docker-compose.yml.' },
];

export function AboutPage() {
  return (
    <PageShell>
      <PageHeader
        eyebrow="About"
        title="A real auction engine, not a simulation"
        description="AuctiX is a containerized real-time live bidding and auction management system. Every price, countdown and outcome you see is produced by the backend."
      />

      <div className="glass mb-6 space-y-4 p-6">
        <h2 className="font-display text-lg font-semibold">How a bid is decided</h2>
        <ol className="space-y-3 text-[13px] leading-relaxed text-slate-400">
          {[
            'The request is authenticated and the auction row is locked with SELECT ... FOR UPDATE.',
            'Auction state, expiry against the server clock and the minimum increment are all re-checked.',
            'The bid is written, the auction is updated and any standing proxy bids answer back.',
            'A late bid inside the anti-sniping window pushes the end time out.',
            'The transaction commits, then the event is published to Redis.',
            'Every backend instance relays it over STOMP to the auction topic and the affected private queues.',
          ].map((step, index) => (
            <li key={step} className="flex gap-3">
              <span className="font-mono text-[11px] text-accent">{String(index + 1).padStart(2, '0')}</span>
              <span>{step}</span>
            </li>
          ))}
        </ol>
        <p className="border-t border-white/[0.07] pt-4 text-[13px] text-slate-400">
          Because the lock is held by the database, three people bidding in the same millisecond are
          served one at a time, in an order the database decides. A bid that was valid when it was
          typed but is stale by the time it is processed is rejected with a clear error rather than
          silently overwriting a higher one.
        </p>
      </div>

      <div className="glass p-6">
        <h2 className="font-display text-lg font-semibold">Architecture</h2>
        <dl className="mt-4 divide-y divide-white/[0.06]">
          {ARCHITECTURE.map((row) => (
            <div key={row.layer} className="grid gap-1 py-3 sm:grid-cols-[10rem_1fr] sm:gap-4">
              <dt className="text-[12px] font-semibold uppercase tracking-[0.14em] text-slate-400">
                {row.layer}
              </dt>
              <dd className="text-[13px] leading-relaxed text-slate-400">{row.detail}</dd>
            </div>
          ))}
        </dl>
      </div>
    </PageShell>
  );
}

export function ContactPage() {
  return (
    <PageShell>
      <PageHeader
        eyebrow="Contact"
        title="Get in touch"
        description="Questions about a lot, a listing decision or the platform itself."
      />
      <div className="grid gap-4 sm:grid-cols-3">
        {[
          { title: 'Bidding support', detail: 'Problems placing a bid or a disputed result.', email: 'bidding@auctix.io' },
          { title: 'Seller services', detail: 'Listing guidelines, approvals and settlement.', email: 'sellers@auctix.io' },
          { title: 'Platform & security', detail: 'Report a vulnerability or an account issue.', email: 'security@auctix.io' },
        ].map((card) => (
          <div key={card.title} className="glass p-5">
            <h2 className="text-[15px] font-semibold text-white">{card.title}</h2>
            <p className="mt-1.5 text-[13px] leading-relaxed text-slate-500">{card.detail}</p>
            <p className="mt-3 font-mono text-[12px] text-accent-soft">{card.email}</p>
          </div>
        ))}
      </div>
      <p className="mt-6 text-[12px] text-slate-600">
        This is an academic project build; the addresses above are illustrative.
      </p>
    </PageShell>
  );
}

export function NotFoundPage() {
  return (
    <PageShell>
      <div className="glass flex flex-col items-center gap-4 px-6 py-24 text-center">
        <p className="font-display text-6xl font-bold text-white/10">404</p>
        <h1 className="font-display text-2xl font-semibold">That page is not on the floor</h1>
        <p className="max-w-sm text-sm leading-relaxed text-slate-500">
          The link may be stale, or the lot you were looking for has been archived.
        </p>
        <Link to="/auctions">
          <Button>Browse auctions</Button>
        </Link>
      </div>
    </PageShell>
  );
}
