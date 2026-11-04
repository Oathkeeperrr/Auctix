import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

export function StatCard({
  label,
  value,
  sub,
  tone = 'default',
  icon,
}: {
  label: string;
  value: ReactNode;
  sub?: string;
  tone?: 'default' | 'live' | 'gold' | 'danger';
  icon?: ReactNode;
}) {
  const accent = {
    default: 'from-accent/18',
    live: 'from-live/18',
    gold: 'from-gold/18',
    danger: 'from-danger/18',
  }[tone];

  return (
    <div className="glass relative overflow-hidden p-4">
      <div className={cn('pointer-events-none absolute -right-8 -top-10 h-24 w-24 rounded-full bg-gradient-to-br to-transparent blur-2xl', accent)} />
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="label-eyebrow truncate">{label}</p>
          <p className="stat-value mt-1.5">{value}</p>
          {sub && <p className="mt-0.5 truncate text-[11px] text-slate-500">{sub}</p>}
        </div>
        {icon && <div className="shrink-0 text-slate-500">{icon}</div>}
      </div>
    </div>
  );
}
