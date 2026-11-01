import { create } from 'zustand';
import { api, ApiError, readToken, setUnauthenticatedHandler, writeToken } from '../lib/api';
import type { Role, User } from '../types';

interface AuthState {
  user: User | null;
  token: string | null;
  status: 'idle' | 'loading' | 'ready';
  error: string | null;
  login: (email: string, password: string) => Promise<User>;
  register: (payload: {
    fullName: string;
    email: string;
    password: string;
    role: Role;
  }) => Promise<User>;
  logout: () => void;
  restore: () => Promise<void>;
  setUser: (user: User) => void;
  clearError: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: readToken(),
  status: 'idle',
  error: null,

  async login(email, password) {
    set({ status: 'loading', error: null });
    try {
      const response = await api.login(email, password);
      writeToken(response.token);
      set({ user: response.user, token: response.token, status: 'ready', error: null });
      return response.user;
    } catch (error) {
      const message = error instanceof ApiError ? error.message : 'Unable to sign in.';
      set({ status: 'ready', error: message });
      throw error;
    }
  },

  async register(payload) {
    set({ status: 'loading', error: null });
    try {
      const response = await api.register(payload);
      writeToken(response.token);
      set({ user: response.user, token: response.token, status: 'ready', error: null });
      return response.user;
    } catch (error) {
      const message = error instanceof ApiError ? error.message : 'Unable to create your account.';
      set({ status: 'ready', error: message });
      throw error;
    }
  },

  logout() {
    writeToken(null);
    set({ user: null, token: null, status: 'ready', error: null });
  },

  /** Validates a stored token on start-up: an expired one is discarded silently. */
  async restore() {
    const token = readToken();
    if (!token) {
      set({ status: 'ready', user: null, token: null });
      return;
    }
    set({ status: 'loading' });
    try {
      const user = await api.me();
      set({ user, token, status: 'ready' });
    } catch {
      writeToken(null);
      set({ user: null, token: null, status: 'ready' });
    }
  },

  setUser(user) {
    set({ user });
  },

  clearError() {
    set({ error: null });
  },
}));

// A 401 anywhere in the app drops the session immediately.
setUnauthenticatedHandler(() => {
  useAuthStore.setState({ user: null, token: null, status: 'ready' });
});

export function hasRole(user: User | null, ...roles: Role[]): boolean {
  return !!user && roles.includes(user.role);
}
