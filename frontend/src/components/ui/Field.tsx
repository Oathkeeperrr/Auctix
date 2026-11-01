import {
  forwardRef,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';
import { cn } from '../../lib/cn';

const base =
  'w-full rounded-xl bg-ink-800/70 px-3.5 py-2.5 text-sm text-slate-100 placeholder:text-slate-500 ' +
  'border border-white/[0.08] transition-colors focus:border-accent/60 focus:bg-ink-800 ' +
  'focus:outline-none disabled:opacity-50';

export function FieldShell({
  label,
  hint,
  error,
  required,
  children,
  className,
}: {
  label?: string;
  hint?: string;
  error?: string | null;
  required?: boolean;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label className={cn('block space-y-1.5', className)}>
      {label && (
        <span className="flex items-baseline justify-between">
          <span className="text-[12px] font-medium text-slate-300">
            {label}
            {required && <span className="ml-0.5 text-accent">*</span>}
          </span>
          {hint && <span className="text-[11px] text-slate-500">{hint}</span>}
        </span>
      )}
      {children}
      {error && <span className="block text-[11px] text-danger">{error}</span>}
    </label>
  );
}

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  hint?: string;
  error?: string | null;
  prefix?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, hint, error, prefix, className, required, ...rest },
  ref,
) {
  return (
    <FieldShell label={label} hint={hint} error={error} required={required}>
      <span className="relative block">
        {prefix && (
          <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-sm text-slate-500">
            {prefix}
          </span>
        )}
        <input
          ref={ref}
          required={required}
          className={cn(base, prefix && 'pl-7', error && 'border-danger/60', className)}
          {...rest}
        />
      </span>
    </FieldShell>
  );
});

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  hint?: string;
  error?: string | null;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { label, hint, error, className, required, ...rest },
  ref,
) {
  return (
    <FieldShell label={label} hint={hint} error={error} required={required}>
      <textarea
        ref={ref}
        required={required}
        className={cn(base, 'min-h-[120px] resize-y leading-relaxed', error && 'border-danger/60', className)}
        {...rest}
      />
    </FieldShell>
  );
});

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  hint?: string;
  error?: string | null;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, hint, error, className, required, children, ...rest },
  ref,
) {
  return (
    <FieldShell label={label} hint={hint} error={error} required={required}>
      <select
        ref={ref}
        required={required}
        className={cn(base, 'appearance-none pr-9', error && 'border-danger/60', className)}
        {...rest}
      >
        {children}
      </select>
    </FieldShell>
  );
});
