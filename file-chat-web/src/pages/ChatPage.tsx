import { useQueryClient } from '@tanstack/react-query'
import { clsx } from 'clsx'
import { ArrowDown, FileText, PanelRight, Paperclip, RotateCcw } from 'lucide-react'
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router'
import { toast } from 'sonner'
import { addDocument, askOnce, streamAnswer, type Passage } from '../lib/api'
import { citedNumbers } from '../lib/citations'
import { formatBytes, parseServerDate, truncate } from '../lib/format'
import {
  documentsStore,
  promptStore,
  rememberDocument,
  rememberSources,
  settingsStore,
  sourcesStore,
  userStore,
} from '../lib/storage'
import { useStore } from '../lib/store'
import { useMessages } from '../hooks/queries'
import { PageHeader } from '../components/PageHeader'
import { Composer, type ComposerHandle } from '../components/chat/Composer'
import { Inspector, type InspectorTab, type SourcesSelection } from '../components/chat/Inspector'
import {
  AssistantMessage,
  MissingAnswer,
  UserMessage,
  type AnswerStatus,
  type ChatItem,
} from '../components/chat/Messages'
import { validatePdf } from '../lib/upload'
import { Button, IconButton } from '../components/ui/Button'

/** A question asked in this session, with its answer as it streams in. */
interface Turn {
  id: string
  question: string
  answer: string
  status: AnswerStatus
  createdAt: Date
  traceId?: string
  sources?: Passage[]
  error?: string
}

const SUGGESTIONS = [
  'Summarize this document in a few bullet points',
  'What are the key findings or conclusions?',
  'List any dates, deadlines, or amounts mentioned',
]

const WIDE_SCREEN = '(min-width: 1280px)'

export function ChatRoute() {
  const { conversationId = '' } = useParams()
  // Keyed so all local state resets when switching conversations
  return <ChatPage key={conversationId} conversationId={conversationId} />
}

function ChatPage({ conversationId }: { conversationId: string }) {
  const user = useStore(userStore)!
  const settings = useStore(settingsStore)
  const savedSources = useStore(sourcesStore)
  const conversationDocs = useStore(documentsStore)[conversationId]
  const queryClient = useQueryClient()
  const history = useMessages(conversationId)

  const [turns, setTurns] = useState<Turn[]>([])
  const controller = useRef<AbortController | null>(null)
  const composer = useRef<ComposerHandle>(null)
  const busy = turns.some((turn) => turn.status === 'retrieving' || turn.status === 'writing')

  const [inspectorOpen, setInspectorOpen] = useState(() => window.matchMedia(WIDE_SCREEN).matches)
  const [tab, setTab] = useState<InspectorTab>('sources')
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [focus, setFocus] = useState<SourcesSelection['focus']>()

  const scroller = useRef<HTMLDivElement>(null)
  const stickToBottom = useRef(true)
  const [showJump, setShowJump] = useState(false)

  const items = useMemo<ChatItem[]>(() => {
    const result: ChatItem[] = []
    const messages = (history.data ?? []).filter((m) => m.type === 'USER' || m.type === 'ASSISTANT')
    const answered = new Set(messages.filter((m) => m.type === 'ASSISTANT').map((m) => m.traceId))
    let question = ''
    messages.forEach((message, index) => {
      const date = parseServerDate(message.createdDate)
      const key = `${message.traceId}-${message.type}-${index}`
      if (message.type === 'USER') {
        if (index > 0 && messages[index - 1].type === 'USER') result.push({ kind: 'no-answer', key: `gap-${index}` })
        question = message.content
        result.push({ kind: 'user', key, text: message.content, date })
      } else {
        result.push({
          kind: 'assistant',
          key,
          text: message.content,
          date,
          question,
          status: 'done',
          sources: savedSources.byTrace[message.traceId],
        })
      }
    })
    if (messages.at(-1)?.type === 'USER') result.push({ kind: 'no-answer', key: 'gap-end' })

    for (const turn of turns) {
      if (turn.traceId && answered.has(turn.traceId)) continue
      result.push({ kind: 'user', key: `live-${turn.id}-q`, text: turn.question, date: turn.createdAt })
      result.push({
        kind: 'assistant',
        key: `live-${turn.id}-a`,
        text: turn.answer,
        date: turn.createdAt,
        question: turn.question,
        status: turn.status,
        sources: turn.sources,
        error: turn.error,
        retryId: turn.id,
      })
    }
    return result
  }, [history.data, savedSources, turns])

  const answers = items.filter((item): item is Extract<ChatItem, { kind: 'assistant' }> => item.kind === 'assistant')
  const selected = answers.find((item) => item.key === selectedKey) ?? answers.at(-1)
  const selection: SourcesSelection | null = selected
    ? { question: selected.question, sources: selected.sources, cited: citedNumbers(selected.text), focus }
    : null

  const firstQuestion = items.find((item) => item.kind === 'user')?.text
  const documents = conversationDocs?.documents ?? []
  const title = firstQuestion ? truncate(firstQuestion, 70) : (documents[0]?.name ?? 'New conversation')

  // ---- Asking questions -------------------------------------------------------------------------

  const markHistoryStale = useCallback(() => {
    // The server now has messages this page already shows; refetch on the next visit, not now.
    void queryClient.invalidateQueries({ queryKey: ['messages', conversationId], refetchType: 'none' })
    void queryClient.invalidateQueries({ queryKey: ['conversations'] })
  }, [conversationId, queryClient])

  const ask = useCallback(
    async (question: string) => {
      if (controller.current) return
      const id = crypto.randomUUID()
      setTurns((all) => [...all, { id, question, answer: '', status: 'retrieving', createdAt: new Date() }])
      setSelectedKey(`live-${id}-a`)
      setFocus(undefined)
      stickToBottom.current = true

      const update = (patch: Partial<Turn> | ((turn: Turn) => Partial<Turn>)) =>
        setTurns((all) =>
          all.map((turn) =>
            turn.id === id ? { ...turn, ...(typeof patch === 'function' ? patch(turn) : patch) } : turn,
          ),
        )

      const abort = new AbortController()
      controller.current = abort

      // Tokens arrive faster than the screen refreshes, so they are batched per animation frame
      let pendingText = ''
      let frame = 0
      const flush = () => {
        if (frame) cancelAnimationFrame(frame)
        frame = 0
        if (!pendingText) return
        const text = pendingText
        pendingText = ''
        update((turn) => ({ answer: turn.answer + text }))
      }

      try {
        if (settings.streaming) {
          let traceId: string | undefined
          await streamAnswer(
            conversationId,
            question,
            (event) => {
              switch (event.type) {
                case 'meta':
                  traceId = event.traceId
                  update({ traceId })
                  break
                case 'sources':
                  if (traceId) rememberSources(traceId, event.sources)
                  update({ sources: event.sources, status: 'writing' })
                  break
                case 'prompt':
                  promptStore.set(event.prompt)
                  break
                case 'token':
                  pendingText += event.text
                  if (!frame) frame = requestAnimationFrame(flush)
                  break
                case 'done':
                  flush()
                  update({ status: 'done' })
                  break
                case 'error':
                  flush()
                  update({ status: 'error', error: event.message })
                  break
              }
            },
            abort.signal,
          )
        } else {
          const result = await askOnce(conversationId, question, abort.signal)
          rememberSources(result.traceId, result.sources)
          if (result.prompt) promptStore.set(result.prompt)
          update({ traceId: result.traceId, sources: result.sources, answer: result.answer, status: 'done' })
        }
      } catch (error) {
        flush()
        if ((error as Error).name === 'AbortError') update({ status: 'stopped' })
        else update({ status: 'error', error: (error as Error).message })
      } finally {
        controller.current = null
        markHistoryStale()
      }
    },
    [conversationId, markHistoryStale, settings.streaming],
  )

  const retry = useCallback(
    (turnId: string) => {
      const turn = turns.find((t) => t.id === turnId)
      if (!turn || controller.current) return
      setTurns((all) => all.filter((t) => t.id !== turnId))
      void ask(turn.question)
    },
    [ask, turns],
  )

  // ---- Sources panel ----------------------------------------------------------------------------

  const openSources = useCallback((key: string, n?: number) => {
    setSelectedKey(key)
    setTab('sources')
    setInspectorOpen(true)
    setFocus(n ? { n, nonce: Date.now() } : undefined)
  }, [])
  const onCite = useCallback((key: string, n: number) => openSources(key, n), [openSources])
  const onShowSources = useCallback((key: string) => openSources(key), [openSources])

  // Close the overlay panel with Escape on smaller screens
  useEffect(() => {
    if (!inspectorOpen) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !window.matchMedia(WIDE_SCREEN).matches) setInspectorOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [inspectorOpen])

  // ---- Scrolling --------------------------------------------------------------------------------

  const onScroll = () => {
    const el = scroller.current
    if (!el) return
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80
    stickToBottom.current = nearBottom
    setShowJump(!nearBottom)
  }

  useLayoutEffect(() => {
    const el = scroller.current
    if (el && stickToBottom.current) el.scrollTop = el.scrollHeight
  }, [items])

  const jumpToBottom = () => {
    const el = scroller.current
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
  }

  // ---- Attaching another PDF --------------------------------------------------------------------

  const fileInput = useRef<HTMLInputElement>(null)
  const [attaching, setAttaching] = useState(false)

  async function attach(file: File) {
    const invalid = validatePdf(file)
    if (invalid) {
      toast.error(invalid)
      return
    }
    setAttaching(true)
    const toastId = toast.loading(`Uploading ${file.name}…`)
    try {
      await addDocument(conversationId, file, (progress) => {
        if (progress >= 1) toast.loading(`Indexing ${file.name}…`, { id: toastId })
      })
      rememberDocument(user.userId, conversationId, file)
      toast.success(`${file.name} added to this conversation`, { id: toastId })
    } catch (error) {
      toast.error((error as Error).message, { id: toastId })
    } finally {
      setAttaching(false)
    }
  }

  // ---- Render -----------------------------------------------------------------------------------

  const empty = history.isSuccess && items.length === 0

  return (
    <div className="flex min-h-0 flex-1">
      <div className="flex min-w-0 flex-1 flex-col">
        <PageHeader
          actions={
            <>
              <input
                ref={fileInput}
                type="file"
                aria-label="Add a PDF to this conversation"
                accept="application/pdf,.pdf"
                className="sr-only"
                tabIndex={-1}
                onChange={(event) => {
                  const file = event.target.files?.[0]
                  event.target.value = ''
                  if (file) void attach(file)
                }}
              />
              <IconButton
                label="Add a PDF to this conversation"
                onClick={() => fileInput.current?.click()}
                disabled={attaching}
              >
                <Paperclip className="size-[18px]" />
              </IconButton>
              <IconButton
                label={inspectorOpen ? 'Hide sources panel' : 'Show sources panel'}
                active={inspectorOpen}
                onClick={() => setInspectorOpen((open) => !open)}
              >
                <PanelRight className="size-[18px]" />
              </IconButton>
            </>
          }
        >
          <div className="min-w-0">
            <h1 className="truncate text-[15px] font-medium">{title}</h1>
            {firstQuestion && documents.length > 0 && (
              <p className="flex items-center gap-1 truncate text-xs text-subtle">
                <FileText className="size-3 shrink-0" />
                <span className="truncate">{documents.map((d) => d.name).join(', ')}</span>
              </p>
            )}
          </div>
        </PageHeader>

        <div className="relative min-h-0 flex-1">
          <div ref={scroller} onScroll={onScroll} className="h-full overflow-y-auto">
            <div className="mx-auto w-full max-w-3xl px-4 pt-6 pb-8 sm:px-6">
              {history.isPending ? (
                <ConversationSkeleton />
              ) : history.isError ? (
                <div className="mt-16 text-center">
                  <p className="text-sm text-muted">Couldn’t load this conversation.</p>
                  <Button className="mt-3" size="sm" onClick={() => void history.refetch()}>
                    <RotateCcw className="size-3.5" /> Try again
                  </Button>
                </div>
              ) : empty ? (
                <EmptyConversation documents={documents} onAsk={(q) => void ask(q)} />
              ) : (
                <div className="space-y-7">
                  {items.map((item) =>
                    item.kind === 'user' ? (
                      <UserMessage key={item.key} text={item.text} />
                    ) : item.kind === 'no-answer' ? (
                      <MissingAnswer key={item.key} />
                    ) : (
                      <AssistantMessage
                        key={item.key}
                        item={item}
                        selected={inspectorOpen && tab === 'sources' && selected?.key === item.key}
                        onCite={onCite}
                        onShowSources={onShowSources}
                        onRetry={retry}
                      />
                    ),
                  )}
                </div>
              )}
            </div>
          </div>

          {showJump && (
            <button
              type="button"
              onClick={jumpToBottom}
              aria-label="Scroll to latest"
              className="absolute bottom-3 left-1/2 flex size-9 -translate-x-1/2 animate-fade-in items-center justify-center rounded-full border border-line bg-surface text-muted shadow-pop hover:text-fg"
            >
              <ArrowDown className="size-4" />
            </button>
          )}
        </div>

        <div className="shrink-0 px-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:px-6">
          <div className="mx-auto w-full max-w-3xl">
            <Composer
              ref={composer}
              busy={busy}
              onSend={(text) => void ask(text)}
              onStop={() => controller.current?.abort()}
            />
            <p className="mt-2 text-center text-[11.5px] text-subtle">
              Answers come from your documents and can be wrong. Check the cited passages.
            </p>
          </div>
        </div>
      </div>

      {inspectorOpen && (
        <>
          <div
            className="fixed inset-0 z-40 animate-fade-in bg-black/30 xl:hidden"
            onClick={() => setInspectorOpen(false)}
          />
          <aside
            aria-label="Sources"
            className={clsx(
              'fixed inset-y-0 right-0 z-50 w-full max-w-[420px] animate-slide-in-right border-l border-line shadow-pop',
              'xl:static xl:z-auto xl:w-[400px] xl:max-w-none xl:shrink-0 xl:animate-none xl:shadow-none',
            )}
          >
            <Inspector
              tab={tab}
              onTabChange={setTab}
              onClose={() => setInspectorOpen(false)}
              selection={selection}
              conversationId={conversationId}
            />
          </aside>
        </>
      )}
    </div>
  )
}

function EmptyConversation({
  documents,
  onAsk,
}: {
  documents: { name: string; size: number }[]
  onAsk: (q: string) => void
}) {
  return (
    <div className="flex flex-col items-center pt-10 text-center sm:pt-16">
      <div className="flex flex-wrap justify-center gap-2">
        {(documents.length ? documents : [{ name: 'Your document', size: 0 }]).map((doc) => (
          <div
            key={doc.name}
            className="flex items-center gap-2.5 rounded-xl border border-line bg-surface py-2 pr-4 pl-2.5 text-left shadow-card"
          >
            <span className="flex size-8 items-center justify-center rounded-lg bg-mark-soft text-mark-fg">
              <FileText className="size-4" />
            </span>
            <span>
              <span className="block max-w-[240px] truncate text-sm font-medium">{doc.name}</span>
              <span className="block text-xs text-subtle">
                {doc.size ? `${formatBytes(doc.size)} · ready` : 'Indexed and ready'}
              </span>
            </span>
          </div>
        ))}
      </div>
      <h2 className="mt-8 font-serif text-[28px] leading-tight font-semibold tracking-tight">
        What would you like to know?
      </h2>
      <p className="mt-2 text-[15px] text-muted">Ask in your own words, or start with one of these.</p>
      <div className="mt-6 flex w-full max-w-md flex-col gap-2">
        {SUGGESTIONS.map((suggestion) => (
          <button
            key={suggestion}
            type="button"
            onClick={() => onAsk(suggestion)}
            className="rounded-xl border border-line bg-surface px-4 py-3 text-left text-sm shadow-card transition-colors hover:border-line-strong hover:bg-hover"
          >
            {suggestion}
          </button>
        ))}
      </div>
    </div>
  )
}

function ConversationSkeleton() {
  return (
    <div className="space-y-8" aria-label="Loading conversation">
      {[0, 1].map((i) => (
        <div key={i} className="space-y-4">
          <div className="ml-auto h-10 w-2/5 animate-pulse rounded-2xl bg-sunken" />
          <div className="flex gap-3">
            <div className="size-6 animate-pulse rounded-md bg-sunken" />
            <div className="flex-1 space-y-2">
              <div className="h-3.5 w-full animate-pulse rounded bg-sunken" />
              <div className="h-3.5 w-11/12 animate-pulse rounded bg-sunken" />
              <div className="h-3.5 w-3/5 animate-pulse rounded bg-sunken" />
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}
