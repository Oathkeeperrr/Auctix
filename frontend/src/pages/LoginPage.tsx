import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Field';
import { useAuthStore } from '../store/authStore';
import { AuthAside } from './AuthAside';

const DEMO_ACCOUNTS = [
  { label: 'Bidder', email: 'kalpesh@auctix.io' },
  { label: 'Seller', email: 'seller@auctix.io' },
  { label: 'Admin', email: 'admin@auctix.io' },
];

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const login = useAuthStore((state) => state.login);
  const error = useAuthStore((state) => state.error);
  const clearError = useAuthStore((state) => state.clearError);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const from = (location.state as { from?: string } | null)?.from;

  async function submit(event: FormEvent) {
    event.preventDefault();
    clearError();
    setSubmitting(true);
    try {
      const user = await login(email, password);
      const fallback = user.role === 'ADMIN' ? '/admin' : user.role === 'SELLER' ? '/seller' : '/dashboard';
      navigate(from ?? fallback, { replace: true });
    } catch {
      /* The store already holds the message. */
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto grid w-full max-w-[1240px] gap-10 px-4 pb-20 sm:px-6 lg:grid-cols-2 lg:items-center lg:px-8">
      <AuthAside
        eyebrow="Welcome back"
        title="Step back into the gallery"
        points={[
          'Your watchlist, active bids and wins in one place',
          'Instant outbid alerts over a live connection',
          'Every bid you place is timestamped by the server',
        ]}
      />

      <div className="glass edge-glow is-active mx-auto w-full max-w-md p-6 sm:p-8">
        <h1 className="font-display text-2xl font-semibold">Sign in</h1>
        <p className="mt-1.5 text-sm text-slate-400">Use your AuctiX account to continue bidding.</p>

        <form onSubmit={submit} className="mt-6 space-y-4">
          <Input
            label="E-mail"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="you@example.com"
          />
          <Input
            label="Password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="••••••••"
          />

          {error && (
            <p role="alert" className="rounded-lg bg-danger/[0.1] px-3 py-2 text-[12px] text-danger">
              {error}
            </p>
          )}

          <Button type="submit" fullWidth size="lg" loading={submitting}>
            Sign in
          </Button>
        </form>

        <div className="mt-6 border-t border-white/[0.07] pt-5">
          <p className="text-[11px] uppercase tracking-[0.18em] text-slate-500">Demo accounts</p>
          <div className="mt-2.5 flex flex-wrap gap-2">
            {DEMO_ACCOUNTS.map((account) => (
              <button
                key={account.email}
                type="button"
                onClick={() => {
                  setEmail(account.email);
                  setPassword('Password123!');
                }}
                className="rounded-lg bg-white/[0.05] px-2.5 py-1.5 text-[12px] text-slate-300 transition-colors hover:bg-white/[0.1] hover:text-white"
              >
                {account.label}
              </button>
            ))}
          </div>
          <p className="mt-2 text-[11px] text-slate-600">
            Seeded accounts all use the password <code className="text-slate-400">Password123!</code>
          </p>
        </div>

        <p className="mt-6 text-center text-[13px] text-slate-500">
          New to AuctiX?{' '}
          <Link to="/register" className="text-accent-soft transition-colors hover:text-white">
            Create an account
          </Link>
        </p>
      </div>
    </div>
  );
}
