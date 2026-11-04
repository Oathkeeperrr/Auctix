import { Link } from 'react-router-dom';
import { cn } from '../../lib/cn';
import { useToastStore, type ToastTone } from '../../store/toastStore';

const tones: Record<ToastTone, { ring: string; dot: string }> = {
  info: { ring: 'border-accent/35', dot: 'bg-accent' },
  success: { ring: 'border-live/40', dot: 'bg-live' },
  warning: { ring: 'border-gold/40', dot: 'bg-gold' },
  danger: { ring: 'border-danger/45', dot: 'bg-danger' },
  outbid: { ring: 'border-danger/60', dot: 'bg-danger' },
};

/** Live region so screen readers announce outbid alerts as they arrive. */
export function Toasts() {
  const toasts = useToastStore((state) => state.toasts);
  const dismiss = useToastStore((state) => state.dismiss);

  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed right-4 top-20 z-[100] flex w-[min(92vw,22rem)] flex-col gap-2"
    >
      {toasts.map((item) => {
        const tone = tones[item.tone];
        const body = (
          <>
            <span className={cn('mt-1.5 h-2 w-2 shrink-0 rounded-full', tone.dot)} />
            <span className="min-w-0 flex-1">
              <span className="block text-[13px] font-semibold text-white">{item.title}</span>
              {item.message && (
                <span className="mt-0.5 block text-[12px] leading-snug text-slate-400">{item.message}</span>
              )}
            </span>
          </>
        );

        return (
          <div
            key={item.id}
            className={cn(
              'glass pointer-events-auto animate-slide-in border p-3.5 pr-9',
              tone.ring,
              item.tone === 'outbid' && 'shadow-[0_0_38px_-10px_rgba(255,95,109,0.6)]',
            )}
          >
            {item.href ? (
              <Link to={item.href} className="flex gap-3" onClick={() => dismiss(item.id)}>
                {body}
              </Link>
            ) : (
              <div className="flex gap-3">{body}</div>
            )}
            <button
              type="button"
              onClick={() => dismiss(item.id)}
              aria-label="Dismiss notification"
              className="absolute right-2 top-2 rounded p-1 text-slate-500 transition-colors hover:text-white"
            >
              ✕
            </button>
          </div>
        );
      })}
    </div>
  );
}
