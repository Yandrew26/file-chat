import { clsx } from 'clsx'
import { FileText } from 'lucide-react'
import { forwardRef, useState, type ReactNode } from 'react'
import type { Passage } from '../../lib/api'

const STOP_WORDS = new Set([
  'about',
  'after',
  'also',
  'been',
  'does',
  'from',
  'have',
  'into',
  'more',
  'than',
  'that',
  'their',
  'there',
  'these',
  'they',
  'this',
  'what',
  'when',
  'where',
  'which',
  'while',
  'with',
  'would',
  'your',
  'could',
  'should',
  'were',
])

/** Wraps words from the question that appear in the passage in <mark>, to show why it matched. */
function highlight(text: string, query?: string): ReactNode {
  const terms = (query ?? '')
    .toLowerCase()
    .split(/[^\p{L}\p{N}]+/u)
    .filter((term) => term.length >= 4 && !STOP_WORDS.has(term))
  if (terms.length === 0) return text
  const pattern = new RegExp(`(${terms.map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})`, 'giu')
  return text.split(pattern).map((part, index) =>
    index % 2 === 1 ? (
      <mark key={index} className="rounded-[3px] bg-mark px-0.5 text-fg">
        {part}
      </mark>
    ) : (
      part
    ),
  )
}

interface PassageCardProps {
  passage: Passage
  number?: number
  query?: string
  cited?: boolean
  flash?: boolean
}

export const PassageCard = forwardRef<HTMLElement, PassageCardProps>(function PassageCard(
  { passage, number, query, cited, flash },
  ref,
) {
  const [expanded, setExpanded] = useState(false)
  const long = passage.text.length > 420
  const match = passage.score == null ? null : Math.round(Math.max(0, Math.min(1, passage.score)) * 100)

  return (
    <article
      ref={ref}
      id={number ? `passage-${number}` : undefined}
      className={clsx(
        'scroll-mt-4 rounded-xl border bg-surface p-4 shadow-card transition-colors',
        cited ? 'border-line-strong' : 'border-line',
        flash && 'animate-flash',
      )}
    >
      <header className="flex items-center gap-2">
        {number != null && (
          <span
            className={clsx(
              'flex h-5 min-w-5 items-center justify-center rounded-[5px] px-1 text-[11px] font-semibold tabular-nums',
              cited ? 'border border-mark bg-mark-soft text-mark-fg' : 'border border-line bg-sunken text-muted',
            )}
          >
            {number}
          </span>
        )}
        <span className="flex min-w-0 items-center gap-1.5 text-xs text-muted">
          <FileText className="size-3.5 shrink-0" />
          <span className="truncate">{passage.fileName ?? 'Document'}</span>
          {passage.pageNumber != null && <span className="shrink-0 text-subtle">· Page {passage.pageNumber}</span>}
        </span>
        {cited && (
          <span className="ml-auto shrink-0 rounded-full bg-sunken px-2 py-0.5 text-[10.5px] font-medium tracking-wide text-muted uppercase">
            Cited
          </span>
        )}
      </header>

      <p
        className={clsx(
          'mt-3 font-serif text-[15px] leading-relaxed whitespace-pre-line text-fg',
          long && !expanded && 'line-clamp-6',
        )}
      >
        {highlight(passage.text, query)}
      </p>

      <footer className="mt-3 flex items-center gap-3">
        {match != null && (
          <span
            className="flex items-center gap-2 text-[11.5px] text-subtle"
            title="Similarity between the question and this passage"
          >
            <span className="h-1 w-16 overflow-hidden rounded-full bg-sunken">
              <span className="block h-full rounded-full bg-ink/70" style={{ width: `${match}%` }} />
            </span>
            <span className="tabular-nums">{match}% match</span>
          </span>
        )}
        {long && (
          <button
            type="button"
            onClick={() => setExpanded((v) => !v)}
            className="ml-auto text-xs font-medium text-ink hover:underline"
          >
            {expanded ? 'Show less' : 'Show more'}
          </button>
        )}
      </footer>
    </article>
  )
})
