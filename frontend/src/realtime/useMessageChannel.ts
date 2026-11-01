import { useEffect } from 'react';
import type { MessagingEvent } from '../types';
import { useStomp } from './StompProvider';

/**
 * Listens on the signed-in user's private message queue.
 *
 * <p>The destination is a user queue rather than a topic, so the server decides who receives
 * what — a client cannot subscribe to someone else's conversations by guessing an id.</p>
 *
 * The provider's subscribe is typed for auction events, which is the only payload it knew
 * about; the cast here keeps that signature untouched rather than widening it for every caller.
 */
export function useMessageChannel(handler: (event: MessagingEvent) => void, enabled = true) {
  const { subscribe } = useStomp();

  useEffect(() => {
    if (!enabled) return undefined;
    return subscribe('/user/queue/messages', (event) =>
      handler(event as unknown as MessagingEvent),
    );
  }, [subscribe, handler, enabled]);
}
