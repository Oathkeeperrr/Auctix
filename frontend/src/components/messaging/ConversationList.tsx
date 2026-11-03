import { cn } from '../../lib/cn';
import { relativeTime } from '../../lib/format';
import type { Conversation } from '../../types';
import { Avatar } from '../ui/Avatar';
import { EmptyState } from '../ui/EmptyState';

/** The left rail: who you are talking to, what was last said, and what is unread. */
export function ConversationList({
  conversations,
  activeId,
  onSelect,
}: {
  conversations: Conversation[];
  activeId: number | null;
  onSelect: (conversation: Conversation) => void;
}) {
  if (conversations.length === 0) {
    return (
      <EmptyState
        title="No conversations yet"
        message="Open a lot and use the Message seller button to start one."
      />
    );
  }

  return (
    <ul className="divide-y divide-white/[0.06]">
      {conversations.map((conversation) => {
        const active = conversation.id === activeId;
        const unread = conversation.unreadCount > 0;
        return (
          <li key={conversation.id}>
            <button
              type="button"
              onClick={() => onSelect(conversation)}
              aria-current={active}
              className={cn(
                'flex w-full items-start gap-3 px-4 py-3 text-left transition-colors',
                active ? 'bg-white/[0.07]' : 'hover:bg-white/[0.04]',
              )}
            >
              <Avatar name={conversation.otherPartyName} src={conversation.otherPartyAvatarUrl} size={38} />

              <div className="min-w-0 flex-1">
                <div className="flex items-baseline justify-between gap-2">
                  <p className={cn('truncate text-[13px]', unread ? 'font-semibold text-white' : 'text-slate-200')}>
                    {conversation.otherPartyName}
                  </p>
                  <span className="shrink-0 text-[10px] text-slate-500">
                    {relativeTime(conversation.lastMessageAt)}
                  </span>
                </div>

                {conversation.auctionTitle && (
                  <p className="truncate text-[11px] text-accent-soft">{conversation.auctionTitle}</p>
                )}

                <div className="mt-0.5 flex items-center justify-between gap-2">
                  <p className={cn('truncate text-[12px]', unread ? 'text-slate-200' : 'text-slate-500')}>
                    {conversation.lastMessage ?? 'No messages yet'}
                  </p>
                  {unread && (
                    <span className="flex h-[18px] min-w-[18px] shrink-0 items-center justify-center rounded-full bg-accent px-1.5 text-[10px] font-semibold text-white">
                      {conversation.unreadCount > 9 ? '9+' : conversation.unreadCount}
                    </span>
                  )}
                </div>
              </div>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
