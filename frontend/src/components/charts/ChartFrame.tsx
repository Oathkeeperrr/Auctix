import type { ReactNode } from 'react';
import { Skeleton } from '../ui/Skeleton';

/**
 * The shell every chart sits in: title, one line of context, and the three states a chart
 * can legitimately be in.
 *
 * <p>Empty is treated as a real answer rather than an error. A platform with no bids yet
 * should say so plainly; drawing an axis with nothing on it invites the reader to assume the
 * data failed to load.</p>
 */
export function ChartFrame({
  title,
  caption,
  loading,
  error,
  empty,
  emptyMessage = 'No data for this period yet.',
  height = 240,
  children,
}: {
  title: string;
  caption?: string;
  loading?: boolean;
  error?: boolean;
  empty?: boolean;
  emptyMessage?: string;
  height?: number;
  children: ReactNode;
}) {
  return (
    <section className="glass p-5">
      <header className="mb-4">
        <h3 className="text-[13px] font-semibold text-white">{title}</h3>
        {caption && <p className="mt-0.5 text-[11px] text-slate-500">{caption}</p>}
      </header>

      <div style={{ height }}>
        {loading ? (
          <Skeleton className="h-full w-full rounded-xl" />
        ) : error ? (
          <div className="flex h-full items-center justify-center text-center">
            <p className="text-[12px] text-danger">
              This chart could not be loaded.
              <br />
              <span className="text-slate-500">The figures are not shown rather than guessed at.</span>
            </p>
          </div>
        ) : empty ? (
          <div className="flex h-full items-center justify-center">
            <p className="text-[12px] text-slate-500">{emptyMessage}</p>
          </div>
        ) : (
          children
        )}
      </div>
    </section>
  );
}
