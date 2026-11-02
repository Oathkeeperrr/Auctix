import { lazy, useEffect } from 'react';
import { Route, Routes } from 'react-router-dom';
import { Layout } from './components/layout/Layout';
import { ProtectedRoute } from './components/layout/ProtectedRoute';
import { MessagesPage } from './pages/MessagesPage';
import { api } from './lib/api';
import { LandingPage } from './pages/LandingPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { useAuthStore } from './store/authStore';

// Heavier, authenticated screens are split out of the initial bundle.
const AuctionsPage = lazy(() => import('./pages/AuctionsPage').then((m) => ({ default: m.AuctionsPage })));
const AuctionDetailPage = lazy(() =>
  import('./pages/AuctionDetailPage').then((m) => ({ default: m.AuctionDetailPage })),
);
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const SellerPage = lazy(() => import('./pages/SellerPage').then((m) => ({ default: m.SellerPage })));
const CreateAuctionPage = lazy(() =>
  import('./pages/CreateAuctionPage').then((m) => ({ default: m.CreateAuctionPage })),
);
const AdminPage = lazy(() => import('./pages/AdminPage').then((m) => ({ default: m.AdminPage })));
const ProfilePage = lazy(() => import('./pages/ProfilePage').then((m) => ({ default: m.ProfilePage })));
const NotificationsPage = lazy(() =>
  import('./pages/NotificationsPage').then((m) => ({ default: m.NotificationsPage })),
);
const AboutPage = lazy(() => import('./pages/StaticPages').then((m) => ({ default: m.AboutPage })));
const ContactPage = lazy(() => import('./pages/StaticPages').then((m) => ({ default: m.ContactPage })));
const NotFoundPage = lazy(() => import('./pages/StaticPages').then((m) => ({ default: m.NotFoundPage })));

export default function App() {
  const restore = useAuthStore((state) => state.restore);

  useEffect(() => {
    // Validate any stored token and synchronise with the server clock before rendering prices.
    void restore();
    void api.serverTime().catch(() => undefined);
    const interval = window.setInterval(() => {
      void api.serverTime().catch(() => undefined);
    }, 120_000);
    return () => window.clearInterval(interval);
  }, [restore]);

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<LandingPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="auctions" element={<AuctionsPage />} />
        <Route path="auctions/:id" element={<AuctionDetailPage />} />
        <Route path="about" element={<AboutPage />} />
        <Route path="contact" element={<ContactPage />} />

        <Route element={<ProtectedRoute />}>
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="messages" element={<MessagesPage />} />
        </Route>

        <Route element={<ProtectedRoute roles={['SELLER', 'ADMIN']} />}>
          <Route path="seller" element={<SellerPage />} />
          <Route path="seller/auctions/create" element={<CreateAuctionPage />} />
        </Route>

        <Route element={<ProtectedRoute roles={['ADMIN']} />}>
          <Route path="admin" element={<AdminPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
