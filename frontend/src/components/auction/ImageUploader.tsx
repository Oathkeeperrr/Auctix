import { useRef, useState } from 'react';
import { api } from '../../lib/api';
import { cn } from '../../lib/cn';
import { Button } from '../ui/Button';

const ACCEPT = 'image/jpeg,image/png,image/webp';

/**
 * Photograph picker for a new listing.
 *
 * <p>Files are uploaded as soon as they are chosen, so the seller sees whether an image was
 * accepted before committing to the rest of the form. The component holds URLs rather than
 * File objects: by the time the listing is submitted the images already exist on the server
 * and the request carries nothing but their addresses.</p>
 *
 * <p>The checks here are a courtesy, not a defence. The server validates size and sniffs the
 * leading bytes of every file regardless of what the browser claims.</p>
 */
export function ImageUploader({
  urls,
  onChange,
  max = 6,
  maxBytes = 5 * 1024 * 1024,
}: {
  urls: string[];
  onChange: (next: string[]) => void;
  max?: number;
  maxBytes?: number;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const remaining = max - urls.length;

  async function choose(files: FileList | null) {
    if (!files || files.length === 0) return;
    setError(null);

    const picked = Array.from(files);
    if (picked.length > remaining) {
      setError(`You can add ${remaining} more image${remaining === 1 ? '' : 's'}.`);
      return;
    }
    const tooBig = picked.find((file) => file.size > maxBytes);
    if (tooBig) {
      setError(`"${tooBig.name}" is larger than ${Math.round(maxBytes / (1024 * 1024))} MB.`);
      return;
    }

    setBusy(true);
    try {
      const uploaded = await api.uploadImages(picked);
      onChange([...urls, ...uploaded.map((image) => image.url)]);
    } catch (cause) {
      const message =
        cause && typeof cause === 'object' && 'message' in cause
          ? String((cause as { message: unknown }).message)
          : 'The upload failed.';
      setError(message);
    } finally {
      setBusy(false);
      if (input.current) input.current.value = '';
    }
  }

  function move(from: number, to: number) {
    if (to < 0 || to >= urls.length) return;
    const next = [...urls];
    const [moved] = next.splice(from, 1);
    next.splice(to, 0, moved);
    onChange(next);
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-[13px] font-medium text-slate-200">Photographs</p>
          <p className="text-[11px] text-slate-500">
            Up to {max}. The first is the catalogue cover. JPEG, PNG or WebP, {Math.round(maxBytes / (1024 * 1024))} MB each.
          </p>
        </div>
        <Button
          type="button"
          variant="ghost"
          disabled={busy || remaining <= 0}
          onClick={() => input.current?.click()}
        >
          {busy ? 'Uploading…' : remaining <= 0 ? 'Limit reached' : 'Add images'}
        </Button>
      </div>

      <input
        ref={input}
        type="file"
        accept={ACCEPT}
        multiple
        className="hidden"
        onChange={(event) => void choose(event.target.files)}
      />

      {error && (
        <p role="alert" className="rounded-lg bg-danger/[0.12] px-3 py-2 text-[12px] text-danger">
          {error}
        </p>
      )}

      {urls.length === 0 ? (
        <button
          type="button"
          onClick={() => input.current?.click()}
          className="flex h-28 w-full items-center justify-center rounded-xl border border-dashed border-white/[0.14] text-[12px] text-slate-500 transition-colors hover:border-accent/40 hover:text-slate-300"
        >
          No photographs yet — the 3D view is used instead. Click to add some.
        </button>
      ) : (
        <ul className="grid grid-cols-3 gap-3 sm:grid-cols-4">
          {urls.map((url, index) => (
            <li key={url} className="group relative overflow-hidden rounded-lg bg-white/[0.04]">
              <div className="aspect-[4/3]">
                <img src={url} alt="" className="h-full w-full object-cover" />
              </div>

              {index === 0 && (
                <span className="absolute left-1.5 top-1.5 rounded bg-accent/85 px-1.5 py-0.5 text-[10px] font-medium text-white">
                  Cover
                </span>
              )}

              <div className="absolute inset-x-0 bottom-0 flex justify-between bg-ink-950/80 px-1 py-1 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
                <button
                  type="button"
                  onClick={() => move(index, index - 1)}
                  disabled={index === 0}
                  aria-label="Move earlier"
                  className={cn('px-1.5 text-[12px]', index === 0 ? 'text-slate-600' : 'text-slate-300 hover:text-white')}
                >
                  ←
                </button>
                <button
                  type="button"
                  onClick={() => onChange(urls.filter((_, i) => i !== index))}
                  aria-label="Remove this photograph"
                  className="px-1.5 text-[12px] text-slate-300 hover:text-danger"
                >
                  ✕
                </button>
                <button
                  type="button"
                  onClick={() => move(index, index + 1)}
                  disabled={index === urls.length - 1}
                  aria-label="Move later"
                  className={cn(
                    'px-1.5 text-[12px]',
                    index === urls.length - 1 ? 'text-slate-600' : 'text-slate-300 hover:text-white',
                  )}
                >
                  →
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
