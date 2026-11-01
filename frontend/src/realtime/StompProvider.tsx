import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { readToken } from '../lib/api';
import { observeServerTime } from '../lib/serverTime';
import { useAuthStore } from '../store/authStore';
import type { AuctionEvent } from '../types';

export type ConnectionState = 'connecting' | 'connected' | 'disconnected';

interface StompContextValue {
  state: ConnectionState;
  /** Subscribe to a destination; returns an unsubscribe function. */
  subscribe: (destination: string, handler: (event: AuctionEvent) => void) => () => void;
  /** Subscribe to the signed-in user's private queue. */
  subscribePrivate: (handler: (event: AuctionEvent) => void) => () => void;
  publish: (destination: string, body: unknown) => void;
}

const StompContext = createContext<StompContextValue | null>(null);

function websocketUrl(): string {
  const configured = import.meta.env.VITE_WS_URL;
  if (configured) return configured;
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws`;
}

/**
 * Holds a single STOMP connection for the whole application.
 *
 * Components subscribe through the context rather than opening sockets of their own, so a
 * page with a live auction, a notification bell and a global ticker still uses one connection.
 * The client reconnects automatically and re-subscribes everything that is still mounted.
 */
export function StompProvider({ children }: { children: ReactNode }) {
  const token = useAuthStore((state) => state.token);
  const [state, setState] = useState<ConnectionState>('connecting');
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef(
    new Map<string, { handlers: Set<(event: AuctionEvent) => void>; subscription?: StompSubscription }>(),
  );

  const dispatch = useCallback((destination: string, message: IMessage) => {
    let event: AuctionEvent;
    try {
      event = JSON.parse(message.body) as AuctionEvent;
    } catch {
      return;
    }
    observeServerTime(event.serverTime);
    subscriptionsRef.current.get(destination)?.handlers.forEach((handler) => {
      try {
        handler(event);
      } catch (error) {
        console.error('Auction event handler failed', error);
      }
    });
  }, []);

  useEffect(() => {
    const client = new Client({
      brokerURL: websocketUrl(),
      reconnectDelay: 4000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      debug: () => undefined,
      onConnect: () => {
        setState('connected');
        // Re-establish every destination a mounted component still cares about.
        subscriptionsRef.current.forEach((entry, destination) => {
          entry.subscription = client.subscribe(destination, (message) =>
            dispatch(destination, message),
          );
        });
      },
      onWebSocketClose: () => setState('disconnected'),
      onStompError: () => setState('disconnected'),
    });

    clientRef.current = client;
    setState('connecting');
    client.activate();

    return () => {
      subscriptionsRef.current.forEach((entry) => {
        entry.subscription = undefined;
      });
      void client.deactivate();
      clientRef.current = null;
    };
    // Re-create the connection when the identity changes, so the CONNECT frame
    // carries the right token and private queues resolve to the right user.
  }, [token, dispatch]);

  const subscribe = useCallback(
    (destination: string, handler: (event: AuctionEvent) => void) => {
      const registry = subscriptionsRef.current;
      let entry = registry.get(destination);
      if (!entry) {
        entry = { handlers: new Set() };
        registry.set(destination, entry);
        const client = clientRef.current;
        if (client?.connected) {
          entry.subscription = client.subscribe(destination, (message) => dispatch(destination, message));
        }
      }
      entry.handlers.add(handler);

      return () => {
        const current = registry.get(destination);
        if (!current) return;
        current.handlers.delete(handler);
        if (current.handlers.size === 0) {
          current.subscription?.unsubscribe();
          registry.delete(destination);
        }
      };
    },
    [dispatch],
  );

  const subscribePrivate = useCallback(
    (handler: (event: AuctionEvent) => void) => {
      if (!readToken()) return () => undefined;
      return subscribe('/user/queue/notifications', handler);
    },
    [subscribe],
  );

  const publish = useCallback((destination: string, body: unknown) => {
    const client = clientRef.current;
    if (client?.connected) {
      client.publish({ destination, body: JSON.stringify(body) });
    }
  }, []);

  const value = useMemo<StompContextValue>(
    () => ({ state, subscribe, subscribePrivate, publish }),
    [state, subscribe, subscribePrivate, publish],
  );

  return <StompContext.Provider value={value}>{children}</StompContext.Provider>;
}

export function useStomp(): StompContextValue {
  const context = useContext(StompContext);
  if (!context) {
    throw new Error('useStomp must be used inside <StompProvider>');
  }
  return context;
}
