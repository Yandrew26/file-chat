import { clsx } from 'clsx'
import { BookOpen, Search, ScrollText, X } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { searchDocument, type Passage } from '../../lib/api'
import { promptStore } from '../../lib/storage'
import { useStore } from '../../lib/store'
import { Button, IconButton } from '../ui/Button'
import { Spinner } from '../ui/Spinner'
import { PassageCard } from './PassageCard'

export type InspectorTab = 'sources' | 'search' | 'prompt'

export interface SourcesSelection {
  question?: string
  sources?: Passage[]
  cited: number[]
  /** Passage to scroll to and flash, with a nonce so repeated clicks flash again */
  focus?: { n: number; nonce: number }
}

interface InspectorProps {
  tab: InspectorTab
  onTabChange: (tab: InspectorTab) => void
  onClose: () => void
  selection: SourcesSelection | null
  conversationId: string
}

const TABS: { id: InspectorTab; label: string; icon: typeof BookOpen }[] = [
  { id: 'sources', label: 'Sources', icon: BookOpen },
  { id: 'search', label: 'Search', icon: Search },
  { id: 'prompt', label: 'Prompt', icon: ScrollText },
]

export function Inspector({ tab, onTabChange, onClose, selection, conversationId }: InspectorProps) {
  return (
    <div className="flex h-full flex-col bg-paper">
      <div className="flex h-14 shrink-0 items-center gap-1 border-b border-line px-3">
        <div role="tablist" aria-label="Inspector" className="flex flex-1 gap-0.5">
          {TABS.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              role="tab"
              id={`tab-${id}`}
              aria-selected={tab === id}
              aria-controls={`panel-${id}`}
              onClick={() => onTabChange(id)}
              className={clsx(
                'flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-[13px] transition-colors',
                tab === id ? 'bg-hover font-medium text-fg' : 'text-muted hover:text-fg',
              )}
            >
              <Icon className="size-3.5" />
              {label}
            </button>
          ))}
        </div>
        <IconButton label="Close panel" size="sm" onClick={onClose}>
          <X className="size-4" />
        </IconButton>
      </div>

      <div
        role="tabpanel"
        id={`panel-${tab}`}
        aria-labelledby={`tab-${tab}`}
        className="min-h-0 flex-1 overflow-y-auto"
      >
        {tab === 'sources' && <SourcesTab selection={selection} />}
        {tab === 'search' && <SearchTab conversationId={conversationId} />}
        {tab === 'prompt' && <PromptTab />}
      </div>
    </div>
  )
}

function SourcesTab({ selection }: { selection: SourcesSelection | null }) {
  const cards = useRef(new Map<number, HTMLElement>())
  const focus = selection?.focus

  useEffect(() => {
    if (focus) cards.current.get(focus.n)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [focus])

  if (!selection?.sources) {
    return (
      <EmptyState
        icon={<BookOpen className="size-5" />}
        title="No sources selected"
        body={
          selection
            ? 'The passages for this answer weren’t saved in this browser.'
            : 'Ask a question and the passages FileChat retrieved to answer it appear here.'
        }
      />
    )
  }
  if (selection.sources.length === 0) {
    return (
      <EmptyState
        icon={<BookOpen className="size-5" />}
        title="Nothing matched"
        body="No passages in the document were close enough to this question, so the answer isn’t grounded in it."
      />
    )
  }

  return (
    <div className="space-y-3 p-4">
      <div className="mb-4">
        <p className="text-xs font-medium text-subtle">Passages retrieved for</p>
        <p className="mt-1 font-serif text-[17px] leading-snug">“{selection.question}”</p>
      </div>
      {selection.sources.map((passage, index) => (
        <PassageCard
          key={focus?.n === index + 1 ? `${passage.id}-${focus.nonce}` : passage.id}
          ref={(node) => {
            if (node) cards.current.set(index + 1, node)
            else cards.current.delete(index + 1)
          }}
          passage={passage}
          number={index + 1}
          query={selection.question}
          cited={selection.cited.includes(index + 1)}
          flash={focus?.n === index + 1}
        />
      ))}
    </div>
  )
}

function SearchTab({ conversationId }: { conversationId: string }) {
  const [query, setQuery] = useState('')
  const [submitted, setSubmitted] = useState('')
  const [results, setResults] = useState<Passage[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const controller = useRef<AbortController | null>(null)

  useEffect(() => () => controller.current?.abort(), [])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const value = query.trim()
    if (!value) return
    controller.current?.abort()
    controller.current = new AbortController()
    setPending(true)
    setError(null)
    try {
      setResults(await searchDocument(conversationId, value, controller.current.signal))
      setSubmitted(value)
    } catch (e) {
      if ((e as Error).name !== 'AbortError') setError((e as Error).message)
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="p-4">
      <p className="text-[13px] text-muted">Find passages by meaning, without asking the assistant.</p>
      <form onSubmit={onSubmit} className="mt-3 flex gap-2">
        <label className="relative flex-1">
          <span className="sr-only">Search this conversation’s documents</span>
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-subtle" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="e.g. termination clause"
            className="h-9 w-full rounded-lg border border-line bg-surface pr-3 pl-9 text-sm placeholder:text-subtle focus:border-line-strong focus:outline-none"
          />
        </label>
        <Button type="submit" disabled={pending || !query.trim()}>
          {pending ? <Spinner /> : 'Search'}
        </Button>
      </form>

      {error && (
        <p role="alert" className="mt-4 rounded-lg bg-danger-soft px-3 py-2 text-[13px] text-danger">
          {error}
        </p>
      )}

      {results && !error && (
        <div className="mt-5 space-y-3" aria-live="polite">
          <p className="text-xs text-subtle">
            {results.length === 0
              ? 'No passages matched.'
              : `${results.length} passage${results.length === 1 ? '' : 's'}, best match first`}
          </p>
          {results.map((passage) => (
            <PassageCard key={passage.id} passage={passage} query={submitted} />
          ))}
        </div>
      )}
    </div>
  )
}

function PromptTab() {
  const prompt = useStore(promptStore)
  if (!prompt) {
    return (
      <EmptyState
        icon={<ScrollText className="size-5" />}
        title="Prompt not loaded yet"
        body="The system prompt is shown after your first streamed answer."
      />
    )
  }
  return (
    <div className="p-4">
      <p className="text-[13px] text-muted">
        Sent with every question. Retrieved passages replace{' '}
        <code className="rounded bg-sunken px-1 font-mono text-[12px]">{'{elasticsearch_results}'}</code>.
      </p>
      <pre className="mt-3 rounded-xl border border-line bg-surface p-4 font-mono text-[12px] leading-relaxed whitespace-pre-wrap text-muted">
        {prompt}
      </pre>
    </div>
  )
}

function EmptyState({ icon, title, body }: { icon: ReactNode; title: string; body: string }) {
  return (
    <div className="flex flex-col items-center px-8 py-16 text-center">
      <div className="flex size-10 items-center justify-center rounded-xl bg-sunken text-subtle">{icon}</div>
      <p className="mt-3 text-sm font-medium">{title}</p>
      <p className="mt-1 max-w-[260px] text-[13px] text-muted">{body}</p>
    </div>
  )
}
