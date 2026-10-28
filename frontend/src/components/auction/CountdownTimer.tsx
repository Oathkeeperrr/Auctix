import { useCountdown } from '../../hooks/useCountdown';
import { cn } from '../../lib/cn';
import { pad } from '../../lib/format';

interface CountdownProps {
  /** Absolute end time from the backend. The browser clock is never trusted. */
  endTime: string;
  running?: boolean;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
  onElapsed?: () => void;
}

const sizes = {
  sm: { value: 'text-base', label: 'text-[9px]', gap: 'gap-2' },
  md: { value: 'text-2xl', label: 'text-[10px]', gap: 'gap-3' },
  lg: { value: 'text-3xl sm:text-4xl', label: 'text-[10px]', gap: 'gap-3 sm:gap-5' },
};

/**
 * Four-segment countdown driven by the server clock.
 * It turns red inside the final minute and stops cleanly at zero.
 */
export function CountdownTimer({ endTime, running = true, size = 'md', className }: CountdownProps) {
  const { days, hours, minutes, seconds, total } = useCountdown(endTime, running);
  const style = sizes[size];
  const urgent = total > 0 && total <= 60;
  const finished = total <= 0;

  const segments = [
    { value: days, label: 'Days' },
    { value: hours, label: 'Hours' },
    { value: minutes, label: 'Minutes' },
    { value: seconds, label: 'Seconds' },
  ];

  if (finished) {
    return (
      <p className={cn('font-display font-semibold text-slate-400', style.value, className)}>Auction closed</p>
    );
  }

  return (
    <div className={cn('flex', style.gap, className)} role="timer" aria-live="off">
      {segments.map((segment) => (
        <div key={segment.label} className="flex min-w-[2.6rem] flex-col items-center">
          <span
            className={cn(
              'font-display font-semibold tabular-nums transition-colors',
              style.value,
              urgent ? 'text-danger' : 'text-white',
            )}
          >
            {pad(segment.value)}
          </span>
          <span className={cn('mt-0.5 uppercase tracking-[0.16em] text-slate-500', style.label)}>
            {segment.label}
          </span>
        </div>
      ))}
    </div>
  );
}

/** One-line variant for cards and tables. */
export function InlineCountdown({
  endTime,
  running = true,
  className,
}: {
  endTime: string;
  running?: boolean;
  className?: string;
}) {
  const { days, hours, minutes, seconds, total } = useCountdown(endTime, running);
  if (total <= 0) return <span className={cn('text-slate-500', className)}>Ended</span>;

  const text =
    days > 0
      ? `${pad(days)}d ${pad(hours)}h ${pad(minutes)}m`
      : hours > 0
        ? `${pad(hours)}h ${pad(minutes)}m ${pad(seconds)}s`
        : `${pad(minutes)}m ${pad(seconds)}s`;

  return (
    <span className={cn('font-mono tabular-nums', total <= 60 ? 'text-danger' : 'text-slate-300', className)}>
      {text}
    </span>
  );
}
