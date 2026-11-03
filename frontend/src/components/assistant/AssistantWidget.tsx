import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { api } from '../../lib/api';
import { cn } from '../../lib/cn';
import { money } from '../../lib/format';
import type { AssistantReference } from '../../types';

const STORAGE_KEY = 'auctix.assistant.history';
const MAX_HISTORY = 40;

interface Turn {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  at: string;
  references?: AssistantReference[];
  failed?: boolean;
}

const OPENING: string[] = [
  'What is ending soon?',
  'How does bidding work?',
  'Show me luxury watches',
  'What is anti-sniping?',
];

/** Browser storage is per-viewer and can throw, so every access is guarded. */
function readHistory(): Turn[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Turn[]) : [];
  } catch {
    return [];
  }
}

function writeHistory(turns: Turn[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(turns.slice(-MAX_HISTORY)));
  } catch {
    // A private window or blocked storage simply loses the transcript. Not worth surfacing.
  }
}

function clock(iso: string) {
  try {
    return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '';
  }
}

/**
 * The AuctiX assistant, available on every page.
 *
 * <p>It is a rule engine on the server, and the panel says so in its header rather than
 * implying a language model. The lot currently being viewed is passed along, so "what is the
 * current bid" works without the person naming the item.</p>
 */
export function AssistantWidget() {
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const [turns, setTurns] = useState<Turn[]>(readHistory);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [engineLabel, setEngineLabel] = useState('Rule-based assistant');
  const [suggestions, setSuggestions] = useState<string[]>(OPENING);
  const endRef = useRef<HTMLDivElement>(null);

  // The lot being viewed, so questions about "this lot" resolve.
  const match = /^\/auctions\/(\d+)/.exec(location.pathname);
  const auctionId = match ? Number(match[1]) : null;

  useEffect(() => {
    if (!open) return;
    api
      .assistantCapabilities()
      .then((caps) => {
        setEngineLabel(caps.engineLabel);
        if (caps.suggestions?.length) setSuggestions(caps.suggestions);
      })
      .catch(() => undefined);
  }, [open]);

  useEffect(() => {
    writeHistory(turns);
  }, [turns]);

  useEffect(() => {
    if (open) endRef.current?.scrollIntoView({ block: 'end' });
  }, [turns.length, open, busy]);

  const ask = useCallback(
    async (question: string) => {
      const text = question.trim();
      if (!text || busy) return;
      const now = new Date().toISOString();
      setTurns((current) => [
        ...current,
        { id: `${now}-q`, role: 'user', text, at: now },
      ]);
      setDraft('');
      setBusy(true);
      try {
        const reply = await api.askAssistant(text, auctionId);
        setTurns((current) => [
          ...current,
          {
            id: `${new Date().toISOString()}-a`,
            role: 'assistant',
            text: reply.answer,
            at: new Date().toISOString(),
            references: reply.references,
          },
        ]);
        if (reply.suggestions?.length) setSuggestions(reply.suggestions);
      } catch {
        setTurns((current) => [
          ...current,
          {
            id: `${new Date().toISOString()}-e`,
            role: 'assistant',
            text: 'I could not reach the server. Check your connection and try again.',
            at: new Date().toISOString(),
            failed: true,
          },
        ]);
      } finally {
        setBusy(false);
      }
    },
    [auctionId, busy],
  );

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-label={open ? 'Close the assistant' : 'Open the assistant'}
        className={cn(
          'fixed bottom-5 right-5 z-40 flex h-12 w-12 items-center justify-center rounded-full',
          'bg-accent text-white shadow-lg shadow-accent/25 transition-transform hover:scale-105',
        )}
      >
        <span aria-hidden className="text-[18px]">{open ? '×' : '✦'}</span>
      </button>

      {open && (
        <section
          aria-label="AuctiX assistant"
          className="glass fixed bottom-20 right-5 z-40 flex h-[min(560px,75vh)] w-[min(380px,calc(100vw-2.5rem))] flex-col overflow-hidden p-0"
        >
          <header className="border-b border-white/[0.07] px-4 py-3">
            <p className="text-[13px] font-semibold text-white">AuctiX assistant</p>
            <p className="text-[10px] leading-relaxed text-slate-500">{engineLabel}</p>
          </header>

          <div className="flex-1 space-y-3 overflow-y-auto px-4 py-3">
            {turns.length === 0 && (
              <p className="py-6 text-center text-[12px] leading-relaxed text-slate-500">
                Ask about a lot, a category, or how bidding works.
                <br />
                I read public listings only — never accounts or messages.
              </p>
            )}

            {turns.map((turn) => (
              <div key={turn.id} className={cn('flex', turn.role === 'user' ? 'justify-end' : 'justify-start')}>
                <div
                  className={cn(
                    'max-w-[85%] rounded-2xl px-3 py-2',
                    turn.role === 'user'
                      ? 'rounded-br-md bg-accent/90 text-white'
                      : turn.failed
                        ? 'rounded-bl-md bg-danger/[0.12] text-danger'
                        : 'rounded-bl-md bg-white/[0.07] text-slate-100',
                  )}
                >
                  <p className="whitespace-pre-wrap text-[12.5px] leading-relaxed">{turn.text}</p>

                  {turn.references && turn.references.length > 0 && (
                    <ul className="mt-2 space-y-1.5 border-t border-white/10 pt-2">
                      {turn.references.map((ref) => (
                        <li key={ref.auctionId}>
                          <Link
                            to={`/auctions/${ref.auctionId}`}
                            onClick={() => setOpen(false)}
                            className="block rounded-lg bg-white/[0.05] px-2 py-1.5 transition-colors hover:bg-white/[0.1]"
                          >
                            <span className="block truncate text-[12px] font-medium text-white">
                              {ref.title}
                            </span>
                            <span className="text-[10px] text-slate-400">
                              {ref.categoryLabel} · {money(ref.currentPrice, false)} · {ref.bidCount}{' '}
                              {ref.bidCount === 1 ? 'bid' : 'bids'}
                            </span>
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}

                  <time className="mt-1 block text-right text-[9.5px] opacity-60" dateTime={turn.at}>
                    {clock(turn.at)}
                  </time>
                </div>
              </div>
            ))}

            {busy && (
              <div className="flex justify-start">
                <div className="flex items-center gap-1.5 rounded-2xl rounded-bl-md bg-white/[0.07] px-3 py-2.5">
                  <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-slate-400 [animation-delay:-0.2s]" />
                  <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-slate-400 [animation-delay:-0.1s]" />
                  <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-slate-400" />
                </div>
              </div>
            )}
            <div ref={endRef} />
          </div>

          {!busy && suggestions.length > 0 && (
            <div className="flex flex-wrap gap-1.5 border-t border-white/[0.07] px-4 py-2">
              {suggestions.slice(0, 3).map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  onClick={() => void ask(suggestion)}
                  className="rounded-full bg-white/[0.06] px-2.5 py-1 text-[11px] text-slate-300 transition-colors hover:bg-accent/20 hover:text-accent-soft"
                >
                  {suggestion}
                </button>
              ))}
            </div>
          )}

          <form
            onSubmit={(event) => {
              event.preventDefault();
              void ask(draft);
            }}
            className="flex items-center gap-2 border-t border-white/[0.07] p-3"
          >
            <input
              value={draft}
              onChange={(event) => setDraft(event.target.value.slice(0, 500))}
              placeholder="Ask a question…"
              aria-label="Ask the assistant"
              className="h-9 flex-1 rounded-lg border border-white/[0.08] bg-white/[0.04] px-3 text-[12.5px] text-slate-100 placeholder:text-slate-600 outline-none focus:border-accent/50"
            />
            <button
              type="submit"
              disabled={busy || draft.trim().length === 0}
              className="h-9 shrink-0 rounded-lg bg-accent px-3 text-[12px] font-medium text-white transition-opacity disabled:opacity-40"
            >
              Ask
            </button>
          </form>
        </section>
      )}
    </>
  );
}
