import { useEffect, useRef, useState } from 'react';
import { cn } from '../../lib/cn';
import { money } from '../../lib/format';

/**
 * Shows the current price and pulses whenever the server pushes a new one, so a bid landing
 * from another user is visible even out of the corner of the eye.
 */
export function PriceDisplay({
  amount,
  label = 'Current bid',
  tick = 0,
  size = 'lg',
  className,
}: {
  amount: number;
  label?: string;
  tick?: number;
  size?: 'md' | 'lg' | 'xl';
  className?: string;
}) {
  const [pulse, setPulse] = useState(false);
  const previous = useRef(amount);
  const [direction, setDirection] = useState<'up' | 'none'>('none');

  useEffect(() => {
    if (previous.current === amount) return;
    setDirection(amount > previous.current ? 'up' : 'none');
    previous.current = amount;
    setPulse(true);
    const timer = window.setTimeout(() => setPulse(false), 650);
    return () => window.clearTimeout(timer);
  }, [amount, tick]);

  const sizeClass = {
    md: 'text-xl',
    lg: 'text-3xl',
    xl: 'text-4xl sm:text-5xl',
  }[size];

  return (
    <div className={className}>
      <p className="label-eyebrow">{label}</p>
      <p className="mt-1 flex items-baseline gap-2">
        <span
          className={cn(
            'font-display font-semibold tabular-nums text-white',
            sizeClass,
            pulse && 'animate-price-pop',
          )}
        >
          {money(amount, true)}
        </span>
        {pulse && direction === 'up' && (
          <span className="animate-fade-up text-xs font-medium text-live">▲ new bid</span>
        )}
      </p>
    </div>
  );
}
