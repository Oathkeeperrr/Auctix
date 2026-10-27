import { useState } from 'react';
import { ProductViewer } from '../three/ProductViewer';
import { StatusBadge } from '../ui/Badge';
import { Tabs } from '../ui/Tabs';
import type { AuctionStatus, ItemImage, ModelKind } from '../../types';
import { ProductGallery } from './ProductGallery';

/**
 * The media panel at the top of a lot's detail page.
 *
 * <p>Photographs and the 3D view are two ways of looking at the same lot rather than
 * alternatives, so both are offered whenever both exist. A lot with no photographs opens on the
 * 3D view and shows no chooser, which is why no seller is ever obliged to supply images.</p>
 */
export function LotMedia({
  images,
  kind,
  title,
  status,
}: {
  images: ItemImage[];
  kind: ModelKind;
  title: string;
  status: AuctionStatus;
}) {
  const hasPhotos = images.length > 0;
  const [view, setView] = useState<'photos' | 'model'>(hasPhotos ? 'photos' : 'model');
  const showing = hasPhotos ? view : 'model';

  return (
    <div>
      {hasPhotos && (
        <Tabs
          className="mb-3"
          active={showing}
          onChange={(id) => setView(id as 'photos' | 'model')}
          items={[
            { id: 'photos', label: 'Photographs', count: images.length },
            { id: 'model', label: '3D view' },
          ]}
        />
      )}

      {showing === 'photos' ? (
        <div className="relative">
          <ProductGallery images={images} title={title} />
          <div className="pointer-events-none absolute left-4 top-4">
            <StatusBadge status={status} />
          </div>
        </div>
      ) : (
        <div className="glass relative aspect-[4/3] overflow-hidden p-0 sm:aspect-[16/11]">
          <ProductViewer kind={kind} />
          <div className="pointer-events-none absolute left-4 top-4">
            <StatusBadge status={status} />
          </div>
          <div className="pointer-events-none absolute bottom-4 left-4 rounded-lg bg-ink-950/70 px-2.5 py-1.5 text-[11px] text-slate-400 backdrop-blur-sm">
            Drag to rotate · scroll to zoom
          </div>
        </div>
      )}
    </div>
  );
}
