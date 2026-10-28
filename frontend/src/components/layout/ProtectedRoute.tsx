import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import type { Role } from '../../types';

/**
 * Route guard. This is a convenience for the user, not a security control:
 * every protected API is independently enforced by Spring Security on the server.
 */
export function ProtectedRoute({ roles }: { roles?: Role[] }) {
  const user = useAuthStore((state) => state.user);
  const status = useAuthStore((state) => state.status);
  const location = useLocation();

  if (status !== 'ready') {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <span className="h-8 w-8 animate-spin rounded-full border-2 border-accent border-t-transparent" />
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  if (roles && !roles.includes(user.role)) {
    return (
      <div className="mx-auto max-w-xl px-4 py-24 text-center">
        <h1 className="text-2xl font-semibold">Not available for your account</h1>
        <p className="mt-3 text-sm leading-relaxed text-slate-400">
          This area is restricted to {roles.map((role) => role.toLowerCase()).join(' and ')} accounts.
          You are signed in as a {user.role.toLowerCase()}.
        </p>
      </div>
    );
  }

  return <Outlet />;
}
