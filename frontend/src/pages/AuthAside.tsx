/** Shared marketing column beside the sign-in and registration forms. */
export function AuthAside({
  eyebrow,
  title,
  points,
}: {
  eyebrow: string;
  title: string;
  points: string[];
}) {
  return (
    <div className="hidden lg:block">
      <p className="label-eyebrow">{eyebrow}</p>
      <h2 className="mt-2 max-w-md font-display text-4xl font-bold leading-[1.08] tracking-tight text-balance">
        {title}
      </h2>
      <ul className="mt-8 space-y-3">
        {points.map((point) => (
          <li key={point} className="flex items-start gap-3 text-sm text-slate-400">
            <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-accent" />
            {point}
          </li>
        ))}
      </ul>
      <div className="mt-10 flex gap-3">
        {['Real-time', 'Secure', 'Containerized'].map((tag) => (
          <span
            key={tag}
            className="rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-[11px] uppercase tracking-[0.16em] text-slate-500"
          >
            {tag}
          </span>
        ))}
      </div>
    </div>
  );
}
