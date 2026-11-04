import { cn } from '../../lib/cn';

export interface TabItem {
  id: string;
  label: string;
  count?: number;
}

export function Tabs({
  items,
  active,
  onChange,
  className,
}: {
  items: TabItem[];
  active: string;
  onChange: (id: string) => void;
  className?: string;
}) {
  return (
    <div className={cn('no-scrollbar flex gap-1 overflow-x-auto rounded-xl bg-white/[0.04] p-1', className)}>
      {items.map((item) => {
        const selected = item.id === active;
        return (
          <button
            key={item.id}
            type="button"
            onClick={() => onChange(item.id)}
            aria-current={selected}
            className={cn(
              'relative shrink-0 rounded-lg px-3.5 py-2 text-[13px] font-medium transition-all',
              selected
                ? 'bg-gradient-to-r from-accent/25 to-violet-glow/20 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200',
            )}
          >
            {item.label}
            {item.count !== undefined && (
              <span
                className={cn(
                  'ml-2 rounded-full px-1.5 py-0.5 text-[10px] tabular-nums',
                  selected ? 'bg-white/15 text-white' : 'bg-white/[0.06] text-slate-400',
                )}
              >
                {item.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}
