import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import { cn } from '../../lib/cn';
import { relativeTime } from '../../lib/format';
import { useStomp } from '../../realtime/StompProvider';
import { useAuthStore } from '../../store/authStore';
import { toast } from '../../store/toastStore';
import type { AppNotification, MessagingEvent } from '../../types';
import { useMessageChannel } from '../../realtime/useMessageChannel';
import { Avatar } from '../ui/Avatar';

const NAV_ITEMS = [
  { index: '01', label: 'Home', to: '/' },
  { index: '02', label: 'Auctions', to: '/auctions' },
  { index: '03', label: 'Categories', to: '/auctions?view=categories' },
  { index: '04', label: 'About', to: '/about' },
  { index: '05', label: 'Contact', to: '/contact' },
];

function ConnectionDot() {
  const { state } = useStomp();
  const config = {
    connected: { color: 'bg-live', label: 'Live connection active' },
    connecting: { color: 'bg-gold animate-pulse', label: 'Connecting to live feed' },
    disconnected: { color: 'bg-danger', label: 'Live feed offline, retrying' },
  }[state];

  return (
    <span className="flex items-center gap-1.5" title={config.label}>
      <span className={cn('h-1.5 w-1.5 rounded-full', config.color)} />
      <span className="sr-only">{config.label}</span>
    </span>
  );
}

/**
 * Unread message count in the navbar.
 *
 * Seeded once from the server, then kept current by the same private queue the messages page
 * listens on, so the badge does not need polling.
 */
function MessagesLink({ signedIn }: { signedIn: boolean }) {
  const [unread, setUnread] = useState(0);
  const location = useLocation();

  useEffect(() => {
    if (!signedIn) {
      setUnread(0);
      return;
    }
    let cancelled = false;
    api
      .unreadMessageCount()
      .then((count) => {
        if (!cancelled) setUnread(count);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [signedIn, location.pathname]);

  const onEvent = useCallback((event: MessagingEvent) => {
    if (event.type === 'MESSAGE') setUnread((current) => current + 1);
  }, []);
  useMessageChannel(onEvent, signedIn);

  if (!signedIn) return null;

  return (
    <Link
      to="/messages"
      aria-label={`Messages${unread > 0 ? `, ${unread} unread` : ''}`}
      className="relative flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-white/[0.06] hover:text-white"
    >
      <span aria-hidden className="text-[15px]">✉</span>
      {unread > 0 && (
        <span className="absolute -right-0.5 -top-0.5 flex h-[17px] min-w-[17px] items-center justify-center rounded-full bg-accent px-1 text-[10px] font-semibold text-white">
          {unread > 9 ? '9+' : unread}
        </span>
      )}
    </Link>
  );
}

function NotificationBell() {
  const user = useAuthStore((state) => state.user);
  const { subscribePrivate } = useStomp();
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState<AppNotification[]>([]);
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    void api
      .notifications(0, 8)
      .then((page) => {
        if (!cancelled) setItems(page.content);
      })
      .catch(() => undefined);
    void api
      .unreadCount()
      .then((count) => {
        if (!cancelled) setUnread(count);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [user]);

  // Private queue: outbid alerts, wins, approvals.
  useEffect(() => {
    if (!user) return;
    return subscribePrivate((event) => {
      if (!event.notification) return;
      const incoming = event.notification;
      setItems((current) => [incoming, ...current.filter((item) => item.id !== incoming.id)].slice(0, 12));
      setUnread((count) => count + 1);

      if (event.type === 'OUTBID') {
        toast.outbid(
          "You've been outbid",
          incoming.message,
          incoming.auctionId ? `/auctions/${incoming.auctionId}` : undefined,
        );
      } else if (event.type === 'AUCTION_WON') {
        toast.success(incoming.title, incoming.message);
      } else if (incoming.type !== 'BID_ACCEPTED') {
        // "Your bid was accepted" is already confirmed inline by the bid form.
        toast.info(incoming.title, incoming.message);
      }
    });
  }, [user, subscribePrivate]);

  useEffect(() => {
    if (!open) return;
    const onClick = (event: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [open]);

  if (!user) return null;

  async function markAll() {
    await api.markAllNotificationsRead().catch(() => undefined);
    setUnread(0);
    setItems((current) => current.map((item) => ({ ...item, read: true })));
  }

  return (
    <div className="relative" ref={panelRef}>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label={`Notifications${unread > 0 ? `, ${unread} unread` : ''}`}
        className="relative flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-white/[0.06] hover:text-white"
      >
        <span aria-hidden className="text-base">
          ◔
        </span>
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-[1rem] items-center justify-center rounded-full bg-danger px-1 text-[9px] font-bold text-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="glass absolute right-0 top-11 z-50 w-80 animate-fade-up p-0">
          <div className="flex items-center justify-between border-b border-white/[0.07] px-4 py-3">
            <span className="text-[13px] font-semibold text-white">Notifications</span>
            <button
              type="button"
              onClick={markAll}
              className="text-[11px] text-accent-soft transition-colors hover:text-white"
            >
              Mark all read
            </button>
          </div>
          <div className="no-scrollbar max-h-80 overflow-y-auto">
            {items.length === 0 ? (
              <p className="px-4 py-8 text-center text-[13px] text-slate-500">Nothing yet.</p>
            ) : (
              items.map((item) => (
                <Link
                  key={item.id}
                  to={item.auctionId ? `/auctions/${item.auctionId}` : '/notifications'}
                  onClick={() => setOpen(false)}
                  className={cn(
                    'block border-b border-white/[0.04] px-4 py-3 transition-colors last:border-0 hover:bg-white/[0.04]',
                    !item.read && 'bg-accent/[0.05]',
                  )}
                >
                  <p className="text-[13px] font-medium text-slate-100">{item.title}</p>
                  <p className="mt-0.5 line-clamp-2 text-[12px] leading-snug text-slate-500">{item.message}</p>
                  <p className="mt-1 text-[10px] text-slate-600">{relativeTime(item.createdAt)}</p>
                </Link>
              ))
            )}
          </div>
          <Link
            to="/notifications"
            onClick={() => setOpen(false)}
            className="block border-t border-white/[0.07] px-4 py-2.5 text-center text-[12px] text-accent-soft transition-colors hover:bg-white/[0.04]"
          >
            View all
          </Link>
        </div>
      )}
    </div>
  );
}

export function Navbar() {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  const navigate = useNavigate();
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [search, setSearch] = useState('');

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const dashboardHref = user?.role === 'ADMIN' ? '/admin' : user?.role === 'SELLER' ? '/seller' : '/dashboard';

  return (
    <header
      className={cn(
        'fixed inset-x-0 top-0 z-50 transition-all duration-300',
        scrolled ? 'border-b border-white/[0.07] bg-ink-950/80 backdrop-blur-xl' : 'bg-transparent',
      )}
    >
      <nav className="mx-auto flex h-16 max-w-[1400px] items-center gap-6 px-4 sm:px-6 lg:px-8">
        <Link to="/" className="flex shrink-0 items-center gap-2">
          <span className="font-display text-xl font-bold tracking-tight text-white">
            Aucti<span className="text-accent">X</span>
          </span>
        </Link>

        <ul className="hidden items-center gap-1 lg:flex">
          {NAV_ITEMS.map((item) => (
            <li key={item.index}>
              <NavLink
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) =>
                  cn(
                    'group flex items-center gap-1.5 rounded-lg px-3 py-2 text-[13px] transition-colors',
                    isActive ? 'text-white' : 'text-slate-400 hover:text-slate-100',
                  )
                }
              >
                <span className="nav-index">{item.index}</span>
                <span>{item.label}</span>
              </NavLink>
            </li>
          ))}
        </ul>

        <div className="ml-auto flex items-center gap-2">
          <form
            onSubmit={(event) => {
              event.preventDefault();
              navigate(`/auctions?search=${encodeURIComponent(search)}`);
            }}
            className="hidden md:block"
          >
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search lots"
              aria-label="Search auctions"
              className="h-9 w-44 rounded-lg border border-white/[0.08] bg-white/[0.04] px-3 text-[13px] text-slate-200 placeholder:text-slate-600 outline-none transition-all focus:w-56 focus:border-accent/50"
            />
          </form>

          <ConnectionDot />
          <MessagesLink signedIn={!!user} />
          <NotificationBell />

          {user ? (
            <div className="flex items-center gap-2">
              <Link
                to={dashboardHref}
                className="hidden rounded-lg px-3 py-2 text-[13px] text-slate-300 transition-colors hover:bg-white/[0.06] hover:text-white sm:block"
              >
                {user.role === 'ADMIN' ? 'Admin' : user.role === 'SELLER' ? 'Seller' : 'Dashboard'}
              </Link>
              <Link to="/profile" aria-label="Your profile">
                <Avatar name={user.fullName} src={user.avatarUrl} size={36} />
              </Link>
              <button
                type="button"
                onClick={() => {
                  logout();
                  navigate('/');
                }}
                className="hidden rounded-lg px-2.5 py-2 text-[13px] text-slate-500 transition-colors hover:text-white sm:block"
              >
                Sign out
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Link
                to="/login"
                className="rounded-lg px-3 py-2 text-[13px] text-slate-300 transition-colors hover:bg-white/[0.06] hover:text-white"
              >
                Login
              </Link>
              <Link
                to="/register"
                className="rounded-full bg-gradient-to-r from-accent-deep via-accent to-violet-glow px-4 py-2 text-[13px] font-medium text-white shadow-glow transition-all hover:brightness-110"
              >
                Sign Up
              </Link>
            </div>
          )}

          <button
            type="button"
            onClick={() => setMobileOpen((value) => !value)}
            aria-label="Toggle navigation"
            aria-expanded={mobileOpen}
            className="flex h-9 w-9 items-center justify-center rounded-lg text-slate-300 transition-colors hover:bg-white/[0.06] lg:hidden"
          >
            {mobileOpen ? '✕' : '☰'}
          </button>
        </div>
      </nav>

      {mobileOpen && (
        <div className="border-t border-white/[0.07] bg-ink-950/95 px-4 py-3 backdrop-blur-xl lg:hidden">
          <ul className="space-y-1">
            {NAV_ITEMS.map((item) => (
              <li key={item.index}>
                <NavLink
                  to={item.to}
                  end={item.to === '/'}
                  onClick={() => setMobileOpen(false)}
                  className="flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm text-slate-300 transition-colors hover:bg-white/[0.06] hover:text-white"
                >
                  <span className="nav-index">{item.index}</span>
                  {item.label}
                </NavLink>
              </li>
            ))}
            {user && (
              <li>
                <button
                  type="button"
                  onClick={() => {
                    logout();
                    setMobileOpen(false);
                    navigate('/');
                  }}
                  className="w-full rounded-lg px-3 py-2.5 text-left text-sm text-slate-400 hover:bg-white/[0.06]"
                >
                  Sign out
                </button>
              </li>
            )}
          </ul>
        </div>
      )}
    </header>
  );
}
