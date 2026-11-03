import { Link } from 'react-router-dom';

const columns = [
  {
    title: 'Marketplace',
    links: [
      { label: 'All auctions', to: '/auctions' },
      { label: 'Live now', to: '/auctions?status=LIVE' },
      { label: 'Ending soon', to: '/auctions?sort=ENDING_SOON' },
      { label: 'Upcoming', to: '/auctions?status=SCHEDULED' },
    ],
  },
  {
    title: 'Account',
    links: [
      { label: 'My dashboard', to: '/dashboard' },
      { label: 'Sell with us', to: '/seller' },
      { label: 'Notifications', to: '/notifications' },
      { label: 'Profile', to: '/profile' },
    ],
  },
  {
    title: 'Platform',
    links: [
      { label: 'About', to: '/about' },
      { label: 'Contact', to: '/contact' },
      { label: 'How it works', to: '/#how-it-works' },
    ],
  },
];

export function Footer() {
  return (
    <footer className="mt-24 border-t border-white/[0.06] bg-ink-950/60">
      <div className="mx-auto grid max-w-[1400px] gap-10 px-4 py-12 sm:px-6 lg:grid-cols-[1.4fr_repeat(3,1fr)] lg:px-8">
        <div>
          <span className="font-display text-xl font-bold text-white">
            Aucti<span className="text-accent">X</span>
          </span>
          <p className="mt-3 max-w-xs text-[13px] leading-relaxed text-slate-500">
            A containerized real-time live bidding and auction management system. Every bid is
            validated, ordered and timestamped on the server.
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            {['Spring Boot', 'PostgreSQL', 'Redis', 'WebSocket', 'Docker'].map((tech) => (
              <span
                key={tech}
                className="rounded-md bg-white/[0.04] px-2 py-1 text-[10px] uppercase tracking-wider text-slate-500"
              >
                {tech}
              </span>
            ))}
          </div>
        </div>

        {columns.map((column) => (
          <div key={column.title}>
            <h3 className="text-[12px] font-semibold uppercase tracking-[0.18em] text-slate-400">
              {column.title}
            </h3>
            <ul className="mt-3 space-y-2">
              {column.links.map((link) => (
                <li key={link.label}>
                  <Link
                    to={link.to}
                    className="text-[13px] text-slate-500 transition-colors hover:text-slate-200"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>

      <div className="border-t border-white/[0.05] px-4 py-5 sm:px-6 lg:px-8">
        <p className="mx-auto max-w-[1400px] text-[12px] text-slate-600">
          AuctiX · Academic project build · Server time governs every auction close.
        </p>
      </div>
    </footer>
  );
}
