import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Field';
import { cn } from '../lib/cn';
import { useAuthStore } from '../store/authStore';
import type { Role } from '../types';
import { AuthAside } from './AuthAside';

const ROLE_OPTIONS: { id: Role; title: string; detail: string }[] = [
  { id: 'BIDDER', title: 'Bidder', detail: 'Browse lots, place bids and track what you win.' },
  { id: 'SELLER', title: 'Seller', detail: 'List items for auction and watch the bidding live.' },
];

export function RegisterPage() {
  const navigate = useNavigate();
  const register = useAuthStore((state) => state.register);
  const error = useAuthStore((state) => state.error);
  const clearError = useAuthStore((state) => state.clearError);

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [role, setRole] = useState<Role>('BIDDER');
  const [submitting, setSubmitting] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    clearError();
    setLocalError(null);

    if (password !== confirm) {
      setLocalError('The two passwords do not match.');
      return;
    }
    if (password.length < 8 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) {
      setLocalError('Use at least 8 characters, including a letter and a digit.');
      return;
    }

    setSubmitting(true);
    try {
      const user = await register({ fullName, email, password, role });
      navigate(user.role === 'SELLER' ? '/seller' : '/dashboard', { replace: true });
    } catch {
      /* Message is held in the store. */
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto grid w-full max-w-[1240px] gap-10 px-4 pb-20 sm:px-6 lg:grid-cols-2 lg:items-center lg:px-8">
      <AuthAside
        eyebrow="Join AuctiX"
        title="Bid on the things that rarely come up twice"
        points={[
          'Live bidding with server-ordered, race-free results',
          'Proxy bidding so you never miss a late move',
          'Sell your own lots once an administrator approves them',
        ]}
      />

      <div className="glass edge-glow is-active mx-auto w-full max-w-md p-6 sm:p-8">
        <h1 className="font-display text-2xl font-semibold">Create your account</h1>
        <p className="mt-1.5 text-sm text-slate-400">It takes less than a minute.</p>

        <form onSubmit={submit} className="mt-6 space-y-4">
          <Input
            label="Full name"
            required
            autoComplete="name"
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            placeholder="Alex Carter"
          />
          <Input
            label="E-mail"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="you@example.com"
          />
          <Input
            label="Password"
            type="password"
            required
            autoComplete="new-password"
            hint="8+ characters, a letter and a digit"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <Input
            label="Confirm password"
            type="password"
            required
            autoComplete="new-password"
            value={confirm}
            onChange={(event) => setConfirm(event.target.value)}
          />

          <fieldset>
            <legend className="mb-2 text-[12px] font-medium text-slate-300">I want to</legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {ROLE_OPTIONS.map((option) => (
                <button
                  key={option.id}
                  type="button"
                  onClick={() => setRole(option.id)}
                  aria-pressed={role === option.id}
                  className={cn(
                    'rounded-xl border p-3 text-left transition-all',
                    role === option.id
                      ? 'border-accent/50 bg-accent/[0.08]'
                      : 'border-white/[0.08] bg-white/[0.02] hover:bg-white/[0.05]',
                  )}
                >
                  <span className="block text-[13px] font-semibold text-white">{option.title}</span>
                  <span className="mt-0.5 block text-[11px] leading-snug text-slate-500">
                    {option.detail}
                  </span>
                </button>
              ))}
            </div>
          </fieldset>

          {(localError || error) && (
            <p role="alert" className="rounded-lg bg-danger/[0.1] px-3 py-2 text-[12px] text-danger">
              {localError ?? error}
            </p>
          )}

          <Button type="submit" fullWidth size="lg" loading={submitting}>
            Create account
          </Button>
        </form>

        <p className="mt-6 text-center text-[13px] text-slate-500">
          Already have an account?{' '}
          <Link to="/login" className="text-accent-soft transition-colors hover:text-white">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
