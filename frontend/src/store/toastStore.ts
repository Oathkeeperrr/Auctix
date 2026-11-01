import { create } from 'zustand';

export type ToastTone = 'info' | 'success' | 'warning' | 'danger' | 'outbid';

export interface Toast {
  id: number;
  tone: ToastTone;
  title: string;
  message?: string;
  href?: string;
  durationMs: number;
}

interface ToastState {
  toasts: Toast[];
  push: (toast: Omit<Toast, 'id' | 'durationMs'> & { durationMs?: number }) => number;
  dismiss: (id: number) => void;
  clear: () => void;
}

let sequence = 0;

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],

  push(toast) {
    const id = ++sequence;
    const entry: Toast = { id, durationMs: toast.durationMs ?? 6000, ...toast };
    set((state) => ({ toasts: [...state.toasts, entry].slice(-5) }));
    if (entry.durationMs > 0) {
      window.setTimeout(() => {
        set((state) => ({ toasts: state.toasts.filter((item) => item.id !== id) }));
      }, entry.durationMs);
    }
    return id;
  },

  dismiss(id) {
    set((state) => ({ toasts: state.toasts.filter((toast) => toast.id !== id) }));
  },

  clear() {
    set({ toasts: [] });
  },
}));

export const toast = {
  info: (title: string, message?: string) => useToastStore.getState().push({ tone: 'info', title, message }),
  success: (title: string, message?: string) =>
    useToastStore.getState().push({ tone: 'success', title, message }),
  warning: (title: string, message?: string) =>
    useToastStore.getState().push({ tone: 'warning', title, message }),
  error: (title: string, message?: string) =>
    useToastStore.getState().push({ tone: 'danger', title, message }),
  outbid: (title: string, message?: string, href?: string) =>
    useToastStore.getState().push({ tone: 'outbid', title, message, href, durationMs: 9000 }),
};
