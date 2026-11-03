import { useEffect, useRef, useState } from 'react';
import { cn } from '../../lib/cn';
import { dateTime, relativeTime } from '../../lib/format';
import type { ChatMessage, Conversation } from '../../types';
import { Button } from '../ui/Button';
import { Skeleton } from '../ui/Skeleton';

const MAX = 2000;

/**
 * One thread: history, a composer, and read receipts.
 *
 * <p>A tick means delivered and stored; two ticks mean the other person has opened the thread
 * since. Both come from the database — nothing here is simulated.</p>
 */
export function MessageThread({
  conversation,
  messages,
  loading,
  sending,
  onSend,
}: {
  conversation: Conversation;
  messages: ChatMessage[];
  loading: boolean;
  sending: boolean;
  onSend: (body: string) => void;
}) {
  const [draft, setDraft] = useState('');
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' });
  }, [messages.length, conversation.id]);

  // Accepts both the form's submit event and the textarea's keydown, which are sibling types.
  function submit(event: { preventDefault: () => void }) {
    event.preventDefault();
    const body = draft.trim();
    if (!body || sending) return;
    onSend(body);
    setDraft('');
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between gap-3 border-b border-white/[0.07] px-5 py-3">
        <div className="min-w-0">
          <p className="truncate text-[14px] font-semibold text-white">{conversation.otherPartyName}</p>
          <p className="truncate text-[11px] text-slate-500">
            {conversation.auctionTitle
              ? `About ${conversation.auctionTitle}`
              : conversation.otherPartyLastSeen
                ? `Last seen ${relativeTime(conversation.otherPartyLastSeen)}`
                : 'General enquiry'}
          </p>
        </div>
      </header>

      <div className="flex-1 space-y-3 overflow-y-auto px-5 py-4">
        {loading ? (
          <div className="space-y-3">
            <Skeleton className="h-12 w-2/3 rounded-xl" />
            <Skeleton className="ml-auto h-12 w-1/2 rounded-xl" />
            <Skeleton className="h-12 w-3/5 rounded-xl" />
          </div>
        ) : messages.length === 0 ? (
          <p className="py-8 text-center text-[12px] text-slate-500">
            No messages yet. Say hello.
          </p>
        ) : (
          messages.map((message) => (
            <div key={message.id} className={cn('flex', message.mine ? 'justify-end' : 'justify-start')}>
              <div
                className={cn(
                  'max-w-[78%] rounded-2xl px-3.5 py-2',
                  message.mine
                    ? 'rounded-br-md bg-accent/90 text-white'
                    : 'rounded-bl-md bg-white/[0.07] text-slate-100',
                )}
              >
                <p className="whitespace-pre-wrap break-words text-[13px] leading-relaxed">{message.body}</p>
                <div
                  className={cn(
                    'mt-1 flex items-center justify-end gap-1 text-[10px]',
                    message.mine ? 'text-white/70' : 'text-slate-500',
                  )}
                >
                  <time dateTime={message.sentAt} title={dateTime(message.sentAt)}>
                    {relativeTime(message.sentAt)}
                  </time>
                  {message.mine && (
                    <span
                      aria-label={message.readAt ? 'Read' : 'Sent'}
                      title={message.readAt ? `Read ${relativeTime(message.readAt)}` : 'Sent'}
                    >
                      {message.readAt ? '✓✓' : '✓'}
                    </span>
                  )}
                </div>
              </div>
            </div>
          ))
        )}
        <div ref={endRef} />
      </div>

      <form onSubmit={submit} className="border-t border-white/[0.07] p-3">
        <div className="flex items-end gap-2">
          <textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value.slice(0, MAX))}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) submit(event);
            }}
            rows={1}
            placeholder="Write a message…  (Enter to send, Shift+Enter for a new line)"
            aria-label="Message"
            className="max-h-32 min-h-[40px] flex-1 resize-y rounded-xl border border-white/[0.08] bg-white/[0.04] px-3 py-2 text-[13px] text-slate-100 placeholder:text-slate-600 outline-none focus:border-accent/50"
          />
          <Button type="submit" disabled={sending || draft.trim().length === 0}>
            {sending ? 'Sending…' : 'Send'}
          </Button>
        </div>
        {draft.length > MAX - 200 && (
          <p className="mt-1 text-right text-[10px] text-slate-500">{draft.length}/{MAX}</p>
        )}
      </form>
    </div>
  );
}
