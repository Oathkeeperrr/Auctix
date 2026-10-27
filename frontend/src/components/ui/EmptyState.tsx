import type { ReactNode } from 'react';

export function EmptyState({
  title,
  message,
  action,
  icon = '◇',
}: {
  title: string;
  message?: string;
  action?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <div className="glass flex flex-col items-center justify-center gap-3 px-6 py-14 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-white/[0.05] text-xl text-slate-500">
        {icon}
      </div>
      <h3 className="text-base font-semibold text-slate-200">{title}</h3>
      {message && <p className="max-w-sm text-sm leading-relaxed text-slate-500">{message}</p>}
      {action}
    </div>
  );
}
