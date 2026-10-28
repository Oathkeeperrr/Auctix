import { useEffect, useRef, useState } from 'react';
import { useStomp } from './StompProvider';
import type { AuctionEvent, AuctionState, Bid } from '../types';

export interface AuctionChannel {
  state: AuctionState | null;
  /** Newest first, capped so a long-running page cannot grow without bound. */
  liveBids: Bid[];
  lastEvent: AuctionEvent | null;
  /** Increments whenever the price changes, so the UI can replay its pulse animation. */
  priceTick: number;
}

const MAX_LIVE_BIDS = 40;

/**
 * Subscribes to one auction's public topic and keeps its live state.
 * Everything here is pushed by the server; nothing is derived or guessed on the client.
 */
export function useAuctionChannel(
  auctionId: number | undefined,
  options: { initialState?: AuctionState | null; initialBids?: Bid[] } = {},
): AuctionChannel {
  const { subscribe } = useStomp();
  const [state, setState] = useState<AuctionState | null>(options.initialState ?? null);
  const [liveBids, setLiveBids] = useState<Bid[]>(options.initialBids ?? []);
  const [lastEvent, setLastEvent] = useState<AuctionEvent | null>(null);
  const [priceTick, setPriceTick] = useState(0);
  const priceRef = useRef<number | null>(options.initialState?.currentPrice ?? null);

  useEffect(() => {
    if (!auctionId) return;
    return subscribe(`/topics/auction/${auctionId}`, (event) => {
      setLastEvent(event);

      if (event.state) {
        setState(event.state);
        if (priceRef.current !== event.state.currentPrice) {
          priceRef.current = event.state.currentPrice;
          setPriceTick((tick) => tick + 1);
        }
      }

      if (event.type === 'BID_PLACED' && event.bid) {
        const incoming = event.bid;
        setLiveBids((current) => {
          if (current.some((bid) => bid.id === incoming.id)) return current;
          return [incoming, ...current].slice(0, MAX_LIVE_BIDS);
        });
      }
    });
  }, [auctionId, subscribe]);

  return { state, liveBids, lastEvent, priceTick };
}

/**
 * The global feed. Used by the landing page and the browser so prices move
 * even when the visitor is not on a specific auction page.
 */
export function useGlobalAuctionFeed(handler: (event: AuctionEvent) => void): void {
  const { subscribe } = useStomp();
  const handlerRef = useRef(handler);
  handlerRef.current = handler;

  useEffect(() => subscribe('/topics/auctions', (event) => handlerRef.current(event)), [subscribe]);
}
