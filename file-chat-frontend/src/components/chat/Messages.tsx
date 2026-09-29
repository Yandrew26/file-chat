import { clsx } from 'clsx'
import { AlertCircle, BookOpen, Check, Copy, RotateCcw } from 'lucide-react'
import { memo, useCallback, useState, type ReactNode } from 'react'
import type { Passage, RelatedWork } from '../../lib/api'
import { formatFullDate, formatTime } from '../../lib/format'
import { LogoMark } from '../Logo'
import { Spinner } from '../ui/Spinner'
import { Markdown } from './Markdown'

export type AnswerStatus = 'retrieving' | 'writing' | 'done' | 'stopped' | 'error'

export type ChatItem =
  | { kind: 'user'; key: string; text: string; date: Date | null }
  | {
      kind: 'assistant'
      key: string
      text: string
      date: Date | null
      question: string
      status: AnswerStatus
      sources?: Passage[]
      related?: RelatedWork[]
      error?: string
      /** Present on answers from this session that can be asked again */
      retryId?: string
    }
  | { kind: 'no-answer'; key: string }

export function UserMessage({ text }: { text: string }) {
  return (
    <div className="flex justify-end">
      <div className="max-w-[85%] rounded-2xl rounded-br-md border border-line bg-surface px-4 py-2.5 text-[15px] leading-relaxed whitespace-pre-wrap shadow-card sm:max-w-[75%]">
        {text}
      </div>
    </div>
  )
}

export function MissingAnswer() {
  return <p className="pl-9 text-[13px] text-subtle italic">No answer was saved for this question.</p>
}

interface AssistantMessageProps {
  item: Extract<ChatItem, { kind: 'assistant' }>
  selected: boolean
  onCite: (key: string, n: number) => void
  onShowSources: (key: string) => void
  onRetry: (retryId: string) => void
}

export const AssistantMessage = memo(function AssistantMessage({
  item,
  selected,
  onCite,
  onShowSources,
  onRetry,
}: AssistantMessageProps) {
  const pending = item.status === 'retrieving' || item.status === 'writing'
  // Stable so the memoized Markdown is not re-parsed for every earlier answer while a new one streams
  const cite = useCallback((n: number) => onCite(item.key, n), [onCite, item.key])
  const sourceCount = item.sources?.length ?? 0

  return (
    <div className="group flex gap-3">
      <LogoMark className="mt-0.5 size-6 shrink-0" />
      <div className="min-w-0 flex-1">
        {item.status === 'retrieving' && <StatusLine>Searching your documents</StatusLine>}
        {item.status === 'writing' && !item.text && (
          <StatusLine>
            {sourceCount > 0
              ? `Found ${sourceCount} passage${sourceCount === 1 ? '' : 's'} · writing an answer`
              : 'Writing an answer'}
          </StatusLine>
        )}

        {item.text ? (
          <Markdown content={item.text} streaming={item.status === 'writing'} sources={item.sources} onCite={cite} />
        ) : (
          item.status === 'done' && <p className="text-[15px] text-muted">No answer was returned.</p>
        )}

        {item.status === 'error' && (
          <div
            role="alert"
            className="mt-2 flex items-start gap-2 rounded-lg bg-danger-soft px-3 py-2.5 text-[13px] text-danger"
          >
            <AlertCircle className="mt-px size-4 shrink-0" />
            <span className="flex-1">{item.error ?? 'The answer could not be generated.'}</span>
            {item.retryId && (
              <button
                type="button"
                onClick={() => onRetry(item.retryId!)}
                className="inline-flex shrink-0 items-center gap-1 font-medium hover:underline"
              >
                <RotateCcw className="size-3.5" /> Retry
              </button>
            )}
          </div>
        )}

        {!pending && item.status !== 'error' && (
          <div className="mt-2 flex items-center gap-1 text-subtle">
            {item.status === 'stopped' && (
              <span className="mr-1 rounded-full bg-sunken px-2 py-0.5 text-[11px] font-medium text-muted">
                Stopped
              </span>
            )}
            {item.text && <CopyButton text={item.text} />}
            {item.sources && (
              <button
                type="button"
                onClick={() => onShowSources(item.key)}
                aria-pressed={selected}
                className={clsx(
                  'inline-flex h-7 items-center gap-1.5 rounded-md px-2 text-xs transition-colors',
                  selected ? 'bg-hover text-fg' : 'hover:bg-hover hover:text-fg',
                )}
              >
                <BookOpen className="size-3.5" />
                {sourceCount} source{sourceCount === 1 ? '' : 's'}
              </button>
            )}
            {item.status === 'stopped' && item.retryId && (
              <button
                type="button"
                onClick={() => onRetry(item.retryId!)}
                className="inline-flex h-7 items-center gap-1.5 rounded-md px-2 text-xs transition-colors hover:bg-hover hover:text-fg"
              >
                <RotateCcw className="size-3.5" /> Ask again
              </button>
            )}
            {item.date && (
              <time className="ml-1 text-[11.5px] tabular-nums" title={formatFullDate(item.date)}>
                {formatTime(item.date)}
              </time>
            )}
          </div>
        )}
      </div>
    </div>
  )
})

function StatusLine({ children }: { children: ReactNode }) {
  return (
    <p className="flex h-7 items-center gap-2 text-[13.5px] text-muted" aria-live="polite">
      <Spinner className="size-3.5 text-ink" />
      {children}
    </p>
  )
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      aria-label={copied ? 'Copied' : 'Copy answer'}
      title={copied ? 'Copied' : 'Copy answer'}
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(text)
          setCopied(true)
          setTimeout(() => setCopied(false), 1500)
        } catch {
          // clipboard unavailable (insecure context)
        }
      }}
      className="inline-flex size-7 items-center justify-center rounded-md transition-colors hover:bg-hover hover:text-fg"
    >
      {copied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
    </button>
  )
}
