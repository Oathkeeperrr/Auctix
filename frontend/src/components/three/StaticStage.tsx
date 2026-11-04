import type { ModelKind } from '../../types';

const presentation: Record<ModelKind, { glyph: string; label: string; gradient: string }> = {
  WATCH: { glyph: '◴', label: 'Luxury timepiece', gradient: 'from-gold/25 via-accent/10 to-transparent' },
  CAR: { glyph: '◰', label: 'Performance car', gradient: 'from-accent/25 via-violet-glow/10 to-transparent' },
  SNEAKER: { glyph: '◧', label: 'Collectible sneaker', gradient: 'from-danger/20 via-gold/10 to-transparent' },
  ARTWORK: { glyph: '◩', label: 'Original artwork', gradient: 'from-violet-glow/25 via-accent/10 to-transparent' },
  COLLECTIBLE: { glyph: '◆', label: 'Rare collectible', gradient: 'from-violet-glow/25 via-gold/10 to-transparent' },
};

/**
 * The no-WebGL / reduced-motion presentation. It keeps the same composition as the 3D stage
 * — pedestal, spotlight, floating object — drawn entirely with CSS, so the page still reads
 * as a gallery rather than as a broken canvas.
 */
export function StaticStage({ kind }: { kind: ModelKind }) {
  const item = presentation[kind] ?? presentation.COLLECTIBLE;

  return (
    <div className="absolute inset-0 flex items-center justify-center" role="img" aria-label={item.label}>
      <div className={`absolute inset-x-8 top-10 h-64 rounded-full bg-gradient-to-b ${item.gradient} blur-3xl`} />
      <div className="relative flex flex-col items-center">
        <div className="glass flex h-40 w-40 items-center justify-center rounded-3xl text-6xl text-slate-200 sm:h-52 sm:w-52 sm:text-7xl">
          {item.glyph}
        </div>
        <div className="mt-8 h-2 w-56 rounded-full bg-accent/40 blur-[2px]" />
        <div className="mt-1 h-20 w-64 rounded-b-[50%] bg-gradient-to-b from-white/[0.05] to-transparent" />
        <p className="mt-2 text-[11px] uppercase tracking-[0.28em] text-slate-500">{item.label}</p>
      </div>
    </div>
  );
}
