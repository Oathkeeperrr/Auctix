import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ConversationList } from '../components/messaging/ConversationList';
import { MessageThread } from '../components/messaging/MessageThread';
import { PageHeader, PageShell } from '../components/layout/PageHeader';
import { EmptyState } from '../components/ui/EmptyState';
import { Skeleton } from '../components/ui/Skeleton';
import { api } from '../lib/api';
import { cn } from '../lib/cn';
import { useMessageChannel } from '../realtime/useMessageChannel';
import { toast } from '../store/toastStore';
import type { ChatMessage, Conversation, MessagingEvent } from '../types';

/**
 * The messaging screen: conversations on the left, the open thread on the right.
 *
 * <p>Live updates arrive on the signed-in user's private STOMP queue. A message for the thread
 * already on screen is appended and immediately marked read; one for any other thread bumps
 * that row's unread count instead, so the list stays truthful without a refresh.</p>
 */
export function MessagesPage() {
  const [params, setParams] = useSearchParams();
  const openId = params.get('c') ? Number(params.get('c')) : null;

  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [listLoading, setListLoading] = useState(true);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [threadLoading, setThreadLoading] = useState(false);
  const [sending, setSending] = useState(false);

  const active = conversations.find((item) => item.id === openId) ?? null;
  // Read inside the socket handler without making it a dependency, which would resubscribe
  // on every message.
  const openIdRef = useRef<number | null>(openId);
  openIdRef.current = openId;

  const loadConversations = useCallback(async () => {
    try {
      setConversations(await api.conversations());
    } catch {
      toast.error('Conversations could not be loaded.');
    } finally {
      setListLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadConversations();
  }, [loadConversations]);

  // Opening a thread loads it and clears its unread count.
  useEffect(() => {
    if (openId == null) {
      setMessages([]);
      return;
    }
    let cancelled = false;
    setThreadLoading(true);
    api
      .messages(openId)
      .then(async (thread) => {
        if (cancelled) return;
        setMessages(thread);
        await api.markConversationRead(openId).catch(() => undefined);
        if (!cancelled) {
          setConversations((current) =>
            current.map((item) => (item.id === openId ? { ...item, unreadCount: 0 } : item)),
          );
        }
      })
      .catch(() => {
        if (!cancelled) toast.error('That conversation could not be opened.');
      })
      .finally(() => {
        if (!cancelled) setThreadLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [openId]);

  const onEvent = useCallback(
    (event: MessagingEvent) => {
      if (event.type === 'READ') {
        // The other side opened the thread, so our sent messages are now read.
        setMessages((current) =>
          current.map((message) =>
            message.mine && !message.readAt ? { ...message, readAt: event.at } : message,
          ),
        );
        return;
      }

      const incoming = event.message;
      if (!incoming) return;

      if (incoming.conversationId === openIdRef.current) {
        setMessages((current) =>
          current.some((m) => m.id === incoming.id) ? current : [...current, incoming],
        );
        void api.markConversationRead(incoming.conversationId).catch(() => undefined);
      }

      setConversations((current) => {
        const known = current.some((item) => item.id === incoming.conversationId);
        if (!known) {
          // First message of a thread opened by someone else: refetch rather than invent a row.
          void loadConversations();
          return current;
        }
        return current
          .map((item) =>
            item.id === incoming.conversationId
              ? {
                  ...item,
                  lastMessage: incoming.body,
                  lastMessageAt: incoming.sentAt,
                  lastMessageSenderId: incoming.senderId,
                  unreadCount:
                    incoming.conversationId === openIdRef.current ? 0 : item.unreadCount + 1,
                }
              : item,
          )
          .sort((a, b) => b.lastMessageAt.localeCompare(a.lastMessageAt));
      });
    },
    [loadConversations],
  );

  useMessageChannel(onEvent);

  async function send(body: string) {
    if (openId == null) return;
    setSending(true);
    try {
      const sent = await api.sendMessage(openId, body);
      setMessages((current) => [...current, sent]);
      setConversations((current) =>
        current
          .map((item) =>
            item.id === openId
              ? {
                  ...item,
                  lastMessage: sent.body,
                  lastMessageAt: sent.sentAt,
                  lastMessageSenderId: sent.senderId,
                }
              : item,
          )
          .sort((a, b) => b.lastMessageAt.localeCompare(a.lastMessageAt)),
      );
    } catch {
      toast.error('The message was not sent.');
    } finally {
      setSending(false);
    }
  }

  return (
    <PageShell>
      <PageHeader
        eyebrow="Inbox"
        title="Messages"
        description="Private conversations with buyers and sellers. Only the two of you can read a thread."
      />

      <div className="glass grid min-h-[560px] grid-cols-1 overflow-hidden p-0 lg:grid-cols-[320px_minmax(0,1fr)]">
        <aside
          className={cn(
            'border-white/[0.07] lg:border-r',
            active ? 'hidden lg:block' : 'block',
          )}
        >
          {listLoading ? (
            <div className="space-y-3 p-4">
              <Skeleton className="h-14 w-full rounded-xl" />
              <Skeleton className="h-14 w-full rounded-xl" />
              <Skeleton className="h-14 w-full rounded-xl" />
            </div>
          ) : (
            <ConversationList
              conversations={conversations}
              activeId={openId}
              onSelect={(conversation) => setParams({ c: String(conversation.id) })}
            />
          )}
        </aside>

        <section className={cn(active ? 'block' : 'hidden lg:block')}>
          {active ? (
            <>
              <button
                type="button"
                onClick={() => setParams({})}
                className="px-5 pt-3 text-[12px] text-slate-400 hover:text-white lg:hidden"
              >
                ← All conversations
              </button>
              <MessageThread
                conversation={active}
                messages={messages}
                loading={threadLoading}
                sending={sending}
                onSend={(body) => void send(body)}
              />
            </>
          ) : (
            <div className="flex h-full items-center justify-center p-8">
              <EmptyState
                title="Choose a conversation"
                message="Pick a thread on the left, or start one from a lot's page."
              />
            </div>
          )}
        </section>
      </div>
    </PageShell>
  );
}
