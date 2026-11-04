import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '../../lib/cn';

interface GlassPanelProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  glow?: boolean;
  padded?: boolean;
}

export function GlassPanel({ children, glow, padded = true, className, ...rest }: GlassPanelProps) {
  return (
    <div className={cn('glass', glow && 'edge-glow', padded && 'p-5 sm:p-6', className)} {...rest}>
      {children}
    </div>
  );
}
