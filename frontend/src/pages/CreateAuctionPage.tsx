import { useMemo, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ProductViewer } from '../components/three/ProductViewer';
import { PageHeader, PageShell } from '../components/layout/PageHeader';
import { ImageUploader } from '../components/auction/ImageUploader';
import { Button } from '../components/ui/Button';
import { Input, Select, Textarea } from '../components/ui/Field';
import { useAsync } from '../hooks/useAsync';
import { ApiError, api } from '../lib/api';
import { money, toLocalInputValue } from '../lib/format';
import { toast } from '../store/toastStore';
import type { ModelKind } from '../types';

const MODEL_OPTIONS: { id: ModelKind; label: string }[] = [
  { id: 'WATCH', label: 'Watch' },
  { id: 'CAR', label: 'Car' },
  { id: 'SNEAKER', label: 'Sneaker' },
  { id: 'ARTWORK', label: 'Artwork' },
  { id: 'COLLECTIBLE', label: 'Collectible' },
];

export function CreateAuctionPage() {
  const navigate = useNavigate();
  const { data: categories } = useAsync(() => api.categories(), []);

  const defaults = useMemo(() => {
    const start = new Date(Date.now() + 60 * 60 * 1000);
    const end = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000);
    return { start: toLocalInputValue(start), end: toLocalInputValue(end) };
  }, []);

  const [form, setForm] = useState({
    title: '',
    description: '',
    category: 'LUXURY_WATCHES',
    modelKind: 'WATCH' as ModelKind,
    brand: '',
    conditionLabel: '',
    startingPrice: '500',
    reservePrice: '',
    minIncrement: '25',
    startTime: defaults.start,
    endTime: defaults.end,
  });
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function set<K extends keyof typeof form>(key: K, value: (typeof form)[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);

    const startingPrice = Number(form.startingPrice);
    const minIncrement = Number(form.minIncrement);
    const reservePrice = form.reservePrice ? Number(form.reservePrice) : null;
    const start = new Date(form.startTime);
    const end = new Date(form.endTime);

    if (Number.isNaN(startingPrice) || startingPrice <= 0) {
      setError('Enter a starting price greater than zero.');
      return;
    }
    if (Number.isNaN(minIncrement) || minIncrement <= 0) {
      setError('Enter a minimum increment greater than zero.');
      return;
    }
    if (reservePrice != null && reservePrice < startingPrice) {
      setError('The reserve price cannot be below the starting price.');
      return;
    }
    if (end <= start) {
      setError('The auction must end after it starts.');
      return;
    }
    if (start <= new Date()) {
      setError('The start time must be in the future.');
      return;
    }

    setSubmitting(true);
    try {
      const created = await api.createAuction({
        title: form.title.trim(),
        description: form.description.trim(),
        category: form.category,
        modelKind: form.modelKind,
        imageUrls: imageUrls.length > 0 ? imageUrls : undefined,
        brand: form.brand.trim() || undefined,
        conditionLabel: form.conditionLabel.trim() || undefined,
        startingPrice,
        reservePrice,
        minIncrement,
        startTime: start.toISOString(),
        endTime: end.toISOString(),
      });
      toast.success('Listing submitted', 'An administrator will review it before it goes live.');
      navigate(`/auctions/${created.id}`);
    } catch (caught) {
      const message = caught instanceof ApiError ? caught.message : 'The listing could not be created.';
      setError(message);
      toast.error('Could not create listing', message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PageShell>
      <PageHeader
        eyebrow="Seller"
        title="Create an auction"
        description="Your lot enters moderation first. Once an administrator approves it, it is scheduled and opens automatically at the start time."
      />

      <div className="grid gap-6 lg:grid-cols-[1.2fr_1fr]">
        <form onSubmit={submit} className="glass space-y-5 p-5 sm:p-6">
          <div className="space-y-4">
            <h2 className="text-[13px] font-semibold uppercase tracking-[0.16em] text-slate-400">Item</h2>
            <Input
              label="Title"
              required
              maxLength={160}
              value={form.title}
              onChange={(event) => set('title', event.target.value)}
              placeholder="Rolex Submariner Date 126610LN"
            />
            <Textarea
              label="Description"
              required
              minLength={10}
              maxLength={4000}
              hint={`${form.description.length}/4000`}
              value={form.description}
              onChange={(event) => set('description', event.target.value)}
              placeholder="Condition, provenance, what is included, anything a bidder should know."
            />

            <div className="grid gap-4 sm:grid-cols-2">
              <Select
                label="Category"
                required
                value={form.category}
                onChange={(event) => {
                  const next = event.target.value;
                  set('category', next);
                  const match = categories?.find((option) => option.name === next);
                  if (match) set('modelKind', match.modelKind);
                }}
              >
                {categories?.map((option) => (
                  <option key={option.name} value={option.name}>
                    {option.label}
                  </option>
                ))}
              </Select>

              <Select
                label="3D presentation"
                hint="How the lot is displayed"
                value={form.modelKind}
                onChange={(event) => set('modelKind', event.target.value as ModelKind)}
              >
                {MODEL_OPTIONS.map((option) => (
                  <option key={option.id} value={option.id}>
                    {option.label}
                  </option>
                ))}
              </Select>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                label="Brand"
                value={form.brand}
                onChange={(event) => set('brand', event.target.value)}
                placeholder="Rolex"
              />
              <Input
                label="Condition"
                value={form.conditionLabel}
                onChange={(event) => set('conditionLabel', event.target.value)}
                placeholder="Unworn, full set"
              />
            </div>

            <ImageUploader urls={imageUrls} onChange={setImageUrls} />
          </div>

          <div className="space-y-4 border-t border-white/[0.07] pt-5">
            <h2 className="text-[13px] font-semibold uppercase tracking-[0.16em] text-slate-400">
              Pricing
            </h2>
            <div className="grid gap-4 sm:grid-cols-3">
              <Input
                label="Starting price"
                type="number"
                step="0.01"
                min="0.01"
                required
                prefix="$"
                value={form.startingPrice}
                onChange={(event) => set('startingPrice', event.target.value)}
              />
              <Input
                label="Reserve price"
                type="number"
                step="0.01"
                min="0"
                prefix="$"
                hint="Optional"
                value={form.reservePrice}
                onChange={(event) => set('reservePrice', event.target.value)}
              />
              <Input
                label="Minimum increment"
                type="number"
                step="0.01"
                min="0.01"
                required
                prefix="$"
                value={form.minIncrement}
                onChange={(event) => set('minIncrement', event.target.value)}
              />
            </div>
            <p className="text-[11px] text-slate-500">
              The first bid must meet {money(Number(form.startingPrice) || 0, true)}. Each later bid must
              clear the current price by at least {money(Number(form.minIncrement) || 0, true)}.
            </p>
          </div>

          <div className="space-y-4 border-t border-white/[0.07] pt-5">
            <h2 className="text-[13px] font-semibold uppercase tracking-[0.16em] text-slate-400">
              Schedule
            </h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                label="Start"
                type="datetime-local"
                required
                value={form.startTime}
                onChange={(event) => set('startTime', event.target.value)}
              />
              <Input
                label="End"
                type="datetime-local"
                required
                value={form.endTime}
                onChange={(event) => set('endTime', event.target.value)}
              />
            </div>
            <p className="text-[11px] text-slate-500">
              Times are entered in your timezone and stored as absolute instants. The backend opens and
              closes the auction on its own clock.
            </p>
          </div>

          {error && (
            <p role="alert" className="rounded-lg bg-danger/[0.1] px-3 py-2 text-[12px] text-danger">
              {error}
            </p>
          )}

          <div className="flex gap-3 border-t border-white/[0.07] pt-5">
            <Button type="submit" loading={submitting} size="lg">
              Submit for approval
            </Button>
            <Button type="button" variant="ghost" size="lg" onClick={() => navigate('/seller')}>
              Cancel
            </Button>
          </div>
        </form>

        {/* Live preview */}
        <aside className="space-y-4 lg:sticky lg:top-24 lg:self-start">
          <div className="glass relative aspect-square overflow-hidden p-0">
            <ProductViewer kind={form.modelKind} />
            <div className="pointer-events-none absolute bottom-3 left-3 rounded-lg bg-ink-950/70 px-2.5 py-1.5 text-[11px] text-slate-400 backdrop-blur-sm">
              Preview of the 3D presentation
            </div>
          </div>

          <div className="glass space-y-3 p-5">
            <p className="label-eyebrow">Card preview</p>
            <h3 className="font-display text-lg font-semibold text-white">
              {form.title || 'Your lot title'}
            </h3>
            <p className="line-clamp-3 text-[13px] leading-relaxed text-slate-500">
              {form.description || 'Your description will appear here.'}
            </p>
            <div className="flex items-end justify-between border-t border-white/[0.07] pt-3">
              <div>
                <p className="text-[10px] uppercase tracking-[0.18em] text-slate-500">Starting bid</p>
                <p className="font-display text-xl font-semibold tabular-nums text-white">
                  {money(Number(form.startingPrice) || 0)}
                </p>
              </div>
              {form.reservePrice && (
                <p className="text-[11px] text-gold">Reserve {money(Number(form.reservePrice))}</p>
              )}
            </div>
          </div>
        </aside>
      </div>
    </PageShell>
  );
}
