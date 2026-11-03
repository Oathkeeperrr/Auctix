import type { ReactNode } from 'react';

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        {eyebrow && <p className="label-eyebrow">{eyebrow}</p>}
        <h1 className="mt-1.5 font-display text-2xl font-semibold tracking-tight sm:text-3xl">{title}</h1>
        {description && <p className="mt-2 max-w-2xl text-sm leading-relaxed text-slate-400">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

/** Standard page shell: consistent gutters and max width across every screen. */
export function PageShell({ children, wide }: { children: ReactNode; wide?: boolean }) {
  return (
    <div className={`mx-auto w-full px-4 pb-16 sm:px-6 lg:px-8 ${wide ? 'max-w-[1500px]' : 'max-w-[1240px]'}`}>
      {children}
    </div>
  );
}
