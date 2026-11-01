import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader, PageShell } from '../components/layout/PageHeader';
import { Button } from '../components/ui/Button';
import { EmptyState } from '../components/ui/EmptyState';
import { useAsync } from '../hooks/useAsync';
import { api } from '../lib/api';
import { cn } from '../lib/cn';
import { dateTime, relativeTime } from '../lib/format';
import { useStomp } from '../realtime/StompProvider';
import type { AppNotification, NotificationType } from '../types';

const TONE: Partial<Record<NotificationType, string>> = {
  OUTBID: 'border-danger/35 bg-danger/[0.06]',
  AUCTION_WON: 'border-gold/35 bg-gold/[0.06]',
  BID_ACCEPTED: 'border-live/30 bg-live/[0.05]',
  LISTING_APPROVED: 'border-live/30 bg-live/[0.05]',
  LISTING_REJECTED: 'border-danger/35 bg-danger/[0.06]',
  AUCTION_ENDING: 'border-gold/30 bg-gold/[0.05]',
};

export function NotificationsPage() {
  const { data, loading, reload } = useAsync(() => api.notifications(0, 60), []);
  const [items, setItems] = useState<AppNotification[]>([]);
  const { subscribePrivate } = useStomp();

  useEffect(() => {
    if (data) setItems(data.content);
  }, [data]);

  useEffect(
    () =>
      subscribePrivate((event) => {
        if (!event.notification) return;
        const incoming = event.notification;
        setItems((current) => [incoming, ...current.filter((item) => item.id !== incoming.id)]);
      }),
    [subscribePrivate],
  );

  async function markAll() {
    await api.markAllNotificationsRead().catch(() => undefined);
    setItems((current) => current.map((item) => ({ ...item, read: true })));
  }

  async function markOne(notification: AppNotification) {
    if (notification.read) return;
    await api.markNotificationRead(notification.id).catch(() => undefined);
    setItems((current) =>
      current.map((item) => (item.id === notification.id ? { ...item, read: true } : item)),
    );
  }

  const unread = items.filter((item) => !item.read).length;

  return (
    <PageShell>
      <PageHeader
        eyebrow="Alerts"
        title="Notifications"
        description="Outbid alerts, auction results and listing decisions, delivered the moment they happen."
        actions={
          <div className="flex gap-2">
            <Button size="sm" variant="secondary" onClick={reload}>
              Refresh
            </Button>
            {unread > 0 && (
              <Button size="sm" onClick={markAll}>
                Mark all read ({unread})
              </Button>
            )}
          </div>
        }
      />

      {loading && (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, index) => (
            <div key={index} className="skeleton h-20 w-full rounded-xl" />
          ))}
        </div>
      )}

      {!loading && items.length === 0 && (
        <EmptyState
          title="No notifications yet"
          message="Place a bid or add a lot to your watchlist and alerts will start arriving here."
          action={
            <Link to="/auctions">
              <Button size="sm" variant="secondary">
                Browse auctions
              </Button>
            </Link>
          }
        />
      )}

      <ul className="space-y-2">
        {items.map((notification) => {
          const body = (
            <>
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <p className="flex items-center gap-2 text-[14px] font-semibold text-white">
                    {!notification.read && <span className="h-1.5 w-1.5 rounded-full bg-accent" />}
                    {notification.title}
                  </p>
                  <p className="mt-1 text-[13px] leading-relaxed text-slate-400">{notification.message}</p>
                </div>
                <span className="shrink-0 text-right">
                  <span className="block text-[11px] text-slate-500">
                    {relativeTime(notification.createdAt)}
                  </span>
                  <span className="block text-[10px] text-slate-600">
                    {dateTime(notification.createdAt)}
                  </span>
                </span>
              </div>
            </>
          );

          return (
            <li key={notification.id}>
              {notification.auctionId ? (
                <Link
                  to={`/auctions/${notification.auctionId}`}
                  onClick={() => markOne(notification)}
                  className={cn(
                    'glass block border p-4 transition-colors hover:bg-white/[0.05]',
                    TONE[notification.type] ?? 'border-white/[0.07]',
                    notification.read && 'opacity-70',
                  )}
                >
                  {body}
                </Link>
              ) : (
                <button
                  type="button"
                  onClick={() => markOne(notification)}
                  className={cn(
                    'glass block w-full border p-4 text-left transition-colors hover:bg-white/[0.05]',
                    TONE[notification.type] ?? 'border-white/[0.07]',
                    notification.read && 'opacity-70',
                  )}
                >
                  {body}
                </button>
              )}
            </li>
          );
        })}
      </ul>
    </PageShell>
  );
}
