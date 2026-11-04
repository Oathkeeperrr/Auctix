import { useState } from 'react';
import { cn } from '../../lib/cn';

/**
 * Round user avatar. Renders the uploaded image when there is one and falls back to the
 * person's initial — including when the URL is broken or unreachable, so a bad link never
 * leaves an empty hole in the header.
 */
export function Avatar({
  name,
  src,
  size = 36,
  className,
}: {
  name: string;
  src?: string | null;
  size?: number;
  className?: string;
}) {
  const [failed, setFailed] = useState(false);
  const trimmed = name?.trim() ?? '';
  const initial = trimmed.length > 0 ? trimmed.charAt(0).toUpperCase() : '?';
  const showImage = !!src && !failed;

  return (
    <span
      title={name}
      style={{ width: size, height: size, fontSize: Math.round(size * 0.4) }}
      className={cn(
        'relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full',
        'bg-gradient-to-br from-accent to-violet-glow font-semibold text-white',
        className,
      )}
    >
      {showImage ? (
        <img
          src={src ?? undefined}
          alt=""
          loading="lazy"
          decoding="async"
          onError={() => setFailed(true)}
          className="h-full w-full object-cover"
        />
      ) : (
        initial
      )}
    </span>
  );
}
