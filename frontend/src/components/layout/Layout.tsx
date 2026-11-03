import { Suspense } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { AssistantWidget } from '../assistant/AssistantWidget';
import { Toasts } from '../ui/Toasts';
import { Footer } from './Footer';
import { Navbar } from './Navbar';

function RouteFallback() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <div className="flex flex-col items-center gap-3">
        <span className="h-8 w-8 animate-spin rounded-full border-2 border-accent border-t-transparent" />
        <p className="text-[12px] uppercase tracking-[0.2em] text-slate-500">Loading</p>
      </div>
    </div>
  );
}

export function Layout() {
  const location = useLocation();
  const immersive = location.pathname === '/';

  return (
    <div className="flex min-h-full flex-col">
      <Navbar />
      <main className={immersive ? 'flex-1' : 'flex-1 pt-24'}>
        <Suspense fallback={<RouteFallback />}>
          <Outlet />
        </Suspense>
      </main>
      <Footer />
      <AssistantWidget />
      <Toasts />
    </div>
  );
}
