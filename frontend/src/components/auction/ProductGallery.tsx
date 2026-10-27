import { useCallback, useEffect, useState } from 'react';
import { cn } from '../../lib/cn';
import type { ItemImage } from '../../types';
import { Modal } from '../ui/Modal';

/**
 * The photograph gallery on a lot's detail page: one large frame, a thumbnail strip, and a
 * click-to-zoom overlay.
 *
 * <p>Arrow keys move between frames whether or not the overlay is open, so the gallery can be
 * driven from the keyboard alone.</p>
 */
export function ProductGallery({ images, title }: { images: ItemImage[]; title: string }) {
  const [index, setIndex] = useState(0);
  const [zoomed, setZoomed] = useState(false);
  const [loaded, setLoaded] = useState(false);

  const count = images.length;
  const active = images[Math.min(index, count - 1)];

  const step = useCallback(
    (delta: number) => {
      setLoaded(false);
      setIndex((current) => (current + delta + count) % count);
    },
    [count],
  );

  useEffect(() => {
    if (count < 2) return undefined;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'ArrowRight') step(1);
      if (event.key === 'ArrowLeft') step(-1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [count, step]);

  if (count === 0 || !active) {
    return null;
  }

  return (
    <div>
      <button
        type="button"
        onClick={() => setZoomed(true)}
        aria-label={`Enlarge photograph ${index + 1} of ${count}`}
        className="group relative block w-full cursor-zoom-in overflow-hidden rounded-xl bg-white/[0.03]"
      >
        <div className="aspect-[4/3] w-full sm:aspect-[16/11]">
          {!loaded && <div className="skeleton absolute inset-0" aria-hidden />}
          <img
            key={active.url}
            src={active.url}
            alt={active.caption ? `${title} — ${active.caption}` : title}
            onLoad={() => setLoaded(true)}
            className={cn(
              'h-full w-full object-cover transition-all duration-500',
              loaded ? 'opacity-100' : 'opacity-0',
              'group-hover:scale-[1.03]',
            )}
          />
        </div>
        {active.caption && (
          <span className="pointer-events-none absolute bottom-3 right-3 rounded-lg bg-ink-950/75 px-2.5 py-1.5 text-[11px] text-slate-300 backdrop-blur-sm">
            {active.caption}
          </span>
        )}
      </button>

      {count > 1 && (
        <div className="mt-3 flex gap-2" role="group" aria-label="Choose a photograph">
          {images.map((image, position) => (
            <button
              key={image.url}
              type="button"
              onClick={() => {
                setLoaded(false);
                setIndex(position);
              }}
              aria-label={image.caption ?? `Photograph ${position + 1}`}
              aria-current={position === index}
              className={cn(
                'relative h-16 w-20 shrink-0 overflow-hidden rounded-lg transition-all',
                position === index
                  ? 'ring-2 ring-accent ring-offset-2 ring-offset-ink-950'
                  : 'opacity-60 hover:opacity-100',
              )}
            >
              <img src={image.url} alt="" className="h-full w-full object-cover" />
            </button>
          ))}
        </div>
      )}

      <Modal open={zoomed} onClose={() => setZoomed(false)} title={title} width="max-w-5xl">
        <img src={active.url} alt={active.caption ?? title} className="w-full rounded-lg" />
        {count > 1 && (
          <div className="mt-4 flex items-center justify-between text-[12px] text-slate-400">
            <button type="button" onClick={() => step(-1)} className="hover:text-white">
              ← Previous
            </button>
            <span className="tabular-nums">
              {index + 1} of {count}
            </span>
            <button type="button" onClick={() => step(1)} className="hover:text-white">
              Next →
            </button>
          </div>
        )}
      </Modal>
    </div>
  );
}
