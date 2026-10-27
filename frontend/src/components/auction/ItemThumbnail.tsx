import { useEffect, useRef, useState } from 'react';
import { cn } from '../../lib/cn';
import type { ModelKind } from '../../types';

const palettes: Record<ModelKind, { from: string; to: string; glyph: string }> = {
  WATCH: { from: 'from-gold/30', to: 'to-accent/10', glyph: '◴' },
  CAR: { from: 'from-accent/35', to: 'to-violet-glow/10', glyph: '◰' },
  SNEAKER: { from: 'from-danger/28', to: 'to-gold/10', glyph: '◧' },
  ARTWORK: { from: 'from-violet-glow/32', to: 'to-accent/10', glyph: '◩' },
  COLLECTIBLE: { from: 'from-violet-glow/28', to: 'to-gold/12', glyph: '◆' },
};

type Phase = 'loading' | 'ready' | 'failed';

/**
 * Card artwork. Shows the lot's photograph when there is one, a shimmer while it loads, and a
 * category-tinted glyph when there is none or it fails — so a catalogue never looks broken.
 */
export function ItemThumbnail({
  kind,
  imageUrl,
  title,
  className,
}: {
  kind: ModelKind;
  imageUrl?: string | null;
  title: string;
  className?: string;
}) {
  const [phase, setPhase] = useState<Phase>(imageUrl ? 'loading' : 'failed');
  const ref = useRef<HTMLImageElement>(null);
  const palette = palettes[kind] ?? palettes.COLLECTIBLE;

  // A changed URL deserves a fresh attempt rather than inheriting the previous verdict.
  useEffect(() => {
    setPhase(imageUrl ? 'loading' : 'failed');
  }, [imageUrl]);

  // An image already in cache can finish before React attaches onLoad, which would otherwise
  // leave the shimmer up for ever.
  useEffect(() => {
    const el = ref.current;
    if (el && el.complete && el.naturalWidth > 0) {
      setPhase('ready');
    }
  }, [imageUrl]);

  if (!imageUrl || phase === 'failed') {
    return (
      <div
        className={cn(
          'relative flex h-full w-full items-center justify-center overflow-hidden bg-gradient-to-br',
          palette.from,
          palette.to,
          className,
        )}
        role="img"
        aria-label={`${title} — no photograph available`}
      >
        <div className="absolute inset-0 bg-grid-faint [background-size:22px_22px] opacity-40" />
        <span className="relative text-5xl text-white/40" aria-hidden>
          {palette.glyph}
        </span>
      </div>
    );
  }

  return (
    <>
      {phase === 'loading' && <div className="skeleton absolute inset-0" aria-hidden />}
      <img
        ref={ref}
        src={imageUrl}
        alt={title}
        loading="lazy"
        decoding="async"
        onLoad={() => setPhase('ready')}
        onError={() => setPhase('failed')}
        className={cn(
          'h-full w-full object-cover transition-opacity duration-300',
          phase === 'ready' ? 'opacity-100' : 'opacity-0',
          className,
        )}
      />
    </>
  );
}
