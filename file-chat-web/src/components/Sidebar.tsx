import { clsx } from 'clsx'
import {
  Calendar,
  Check,
  Code,
  FileText,
  LogOut,
  Monitor,
  Moon,
  Plus,
  RotateCcw,
  Search,
  Settings,
  Sun,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router'
import type { User } from '../lib/api'
import {
  dateGroup,
  endOfDay,
  formatTime,
  parseServerDate,
  startOfDay,
  toServerDate,
  truncate,
  type DateGroup,
} from '../lib/format'
import { documentsStore, settingsStore, userStore, type ThemePreference } from '../lib/storage'
import { useStore } from '../lib/store'
import { useConversations, type DateRange } from '../hooks/queries'
import { LogoMark } from './Logo'
import { Button, IconButton } from './ui/Button'
import { MenuItem, Popover } from './ui/Popover'
import { Spinner } from './ui/Spinner'

type RangePreset = 'all' | 'today' | 'week' | 'month' | 'custom'

const PRESET_LABELS: Record<RangePreset, string> = {
  all: 'All time',
  today: 'Today',
  week: 'Past 7 days',
  month: 'Past 30 days',
  custom: 'Custom range',
}

interface RangeState {
  preset: RangePreset
  from: string
  to: string
}

function toDateRange({ preset, from, to }: RangeState): DateRange {
  const now = new Date()
  const daysAgo = (days: number) => toServerDate(startOfDay(new Date(now.getTime() - days * 86_400_000)))
  switch (preset) {
    case 'today':
      return { dateStart: daysAgo(0) }
    case 'week':
      return { dateStart: daysAgo(6) }
    case 'month':
      return { dateStart: daysAgo(29) }
    case 'custom':
      return {
        dateStart: from ? toServerDate(startOfDay(new Date(`${from}T00:00`))) : undefined,
        dateEnd: to ? toServerDate(endOfDay(new Date(`${to}T00:00`))) : undefined,
      }
    default:
      return {}
  }
}

interface ListItem {
  conversationId: string
  title: string
  documentName?: string
  date: Date | null
  pending: boolean
}

interface SidebarProps {
  user: User
  onNavigate: () => void
  onOpenSettings: () => void
}

export function Sidebar({ user, onNavigate, onOpenSettings }: SidebarProps) {
  const navigate = useNavigate()
  const [filter, setFilter] = useState('')
  const [range, setRange] = useState<RangeState>({ preset: 'all', from: '', to: '' })
  const dateRange = useMemo(() => toDateRange(range), [range])
  const conversations = useConversations(user.userId, dateRange)
  const documents = useStore(documentsStore)

  const items = useMemo(() => {
    const pages = conversations.data?.pages ?? []
    const server = pages.flatMap((page) => page.data)
    const seen = new Set(server.map((c) => c.conversationId))
    const oldestLoaded = parseServerDate(server.at(-1)?.createdDate)
    const hasMore = conversations.hasNextPage

    const list: ListItem[] = server.map((c) => ({
      conversationId: c.conversationId,
      title: truncate(c.content, 80),
      documentName: documents[c.conversationId]?.documents[0]?.name,
      date: parseServerDate(c.createdDate),
      pending: false,
    }))

    // Conversations uploaded from this browser that have no questions yet are not in the server history.
    // A conversation newer than the oldest loaded row would already be in the loaded pages if it had questions.
    const start = parseServerDate(dateRange.dateStart)
    const end = parseServerDate(dateRange.dateEnd)
    for (const [conversationId, info] of Object.entries(documents)) {
      if (info.userId !== user.userId || seen.has(conversationId)) continue
      const created = new Date(info.createdAt)
      if ((start && created < start) || (end && created > end)) continue
      if (hasMore && oldestLoaded && created < oldestLoaded) continue
      list.push({
        conversationId,
        title: info.documents[0]?.name ?? 'New conversation',
        documentName: undefined,
        date: created,
        pending: true,
      })
    }

    const query = filter.trim().toLowerCase()
    return list
      .filter((item) => !query || `${item.title} ${item.documentName ?? ''}`.toLowerCase().includes(query))
      .sort((a, b) => (b.date?.getTime() ?? 0) - (a.date?.getTime() ?? 0))
  }, [conversations.data, conversations.hasNextPage, documents, filter, dateRange, user.userId])

  const groups = useMemo(() => {
    const result: { label: DateGroup; items: ListItem[] }[] = []
    for (const item of items) {
      const label = dateGroup(item.date)
      const group = result.at(-1)
      if (group?.label === label) group.items.push(item)
      else result.push({ label, items: [item] })
    }
    return result
  }, [items])

  // Load the next page when the end of the list scrolls into view
  const sentinel = useRef<HTMLDivElement>(null)
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = conversations
  useEffect(() => {
    const node = sentinel.current
    if (!node || !hasNextPage) return
    const observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting && !isFetchingNextPage) void fetchNextPage()
    })
    observer.observe(node)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  const filtered = range.preset !== 'all'

  return (
    <nav aria-label="Conversations" className="flex h-full w-full flex-col bg-surface">
      <div className="flex items-center justify-between px-4 pt-4 pb-3">
        <NavLink to="/" onClick={onNavigate} className="flex items-center gap-2.5 rounded-lg">
          <LogoMark className="size-7" />
          <span className="font-serif text-xl font-semibold tracking-tight">FileChat</span>
        </NavLink>
      </div>

      <div className="px-3">
        <Button
          variant="primary"
          className="w-full"
          onClick={() => {
            navigate('/')
            onNavigate()
          }}
        >
          <Plus className="size-4" />
          New chat
        </Button>
      </div>

      <div className="mt-4 flex items-center gap-1.5 px-3">
        <label className="relative flex-1">
          <span className="sr-only">Filter conversations</span>
          <Search className="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-subtle" />
          <input
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
            placeholder="Filter"
            className="h-8 w-full rounded-lg border border-line bg-paper pr-7 pl-8 text-[13px] placeholder:text-subtle focus:border-line-strong focus:outline-none"
          />
          {filter && (
            <button
              type="button"
              aria-label="Clear filter"
              onClick={() => setFilter('')}
              className="absolute top-1/2 right-1.5 -translate-y-1/2 rounded p-0.5 text-subtle hover:text-fg"
            >
              <X className="size-3.5" />
            </button>
          )}
        </label>
        <DateFilter range={range} onChange={setRange} />
      </div>

      {filtered && (
        <div className="mx-3 mt-2 flex items-center justify-between rounded-lg bg-ink-soft px-2.5 py-1.5 text-xs text-ink">
          <span className="font-medium">{rangeLabel(range)}</span>
          <button
            type="button"
            className="font-medium hover:underline"
            onClick={() => setRange({ preset: 'all', from: '', to: '' })}
          >
            Clear
          </button>
        </div>
      )}

      <div className="mt-3 min-h-0 flex-1 overflow-y-auto px-2 pb-3">
        {conversations.isPending ? (
          <ListSkeleton />
        ) : conversations.isError ? (
          <div className="mx-1 mt-2 rounded-lg border border-line bg-paper p-3 text-[13px]">
            <p className="text-muted">Couldn’t load your conversations.</p>
            <button
              type="button"
              onClick={() => void conversations.refetch()}
              className="mt-2 inline-flex items-center gap-1.5 font-medium text-ink hover:underline"
            >
              <RotateCcw className="size-3.5" /> Try again
            </button>
          </div>
        ) : items.length === 0 ? (
          <p className="px-3 py-6 text-center text-[13px] text-subtle">
            {filter || filtered ? 'No conversations match.' : 'No conversations yet. Upload a PDF to start one.'}
          </p>
        ) : (
          groups.map((group) => (
            <section key={group.label} className="mb-3">
              <h3 className="px-2.5 pt-2 pb-1 text-[11px] font-semibold tracking-wide text-subtle uppercase">
                {group.label}
              </h3>
              <ul>
                {group.items.map((item) => (
                  <li key={item.conversationId}>
                    <ConversationLink item={item} onNavigate={onNavigate} />
                  </li>
                ))}
              </ul>
            </section>
          ))
        )}
        <div ref={sentinel} />
        {isFetchingNextPage && (
          <div className="flex justify-center py-2 text-subtle">
            <Spinner />
          </div>
        )}
      </div>

      <UserMenu user={user} onOpenSettings={onOpenSettings} onNavigate={onNavigate} />
    </nav>
  )
}

function rangeLabel(range: RangeState) {
  if (range.preset !== 'custom') return PRESET_LABELS[range.preset]
  const fmt = (value: string) =>
    new Date(`${value}T00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
  if (range.from && range.to) return `${fmt(range.from)} – ${fmt(range.to)}`
  if (range.from) return `Since ${fmt(range.from)}`
  if (range.to) return `Until ${fmt(range.to)}`
  return PRESET_LABELS.custom
}

function ConversationLink({ item, onNavigate }: { item: ListItem; onNavigate: () => void }) {
  return (
    <NavLink
      to={`/c/${item.conversationId}`}
      onClick={onNavigate}
      className={({ isActive }) =>
        clsx(
          'group relative flex flex-col gap-0.5 rounded-lg px-2.5 py-2 transition-colors',
          isActive ? 'bg-hover' : 'hover:bg-hover/60',
        )
      }
    >
      {({ isActive }) => (
        <>
          {isActive && <span className="absolute top-2 bottom-2 left-0 w-[3px] rounded-full bg-ink" />}
          <span className={clsx('truncate text-[13.5px] leading-snug', isActive ? 'font-medium text-fg' : 'text-fg')}>
            {item.title}
          </span>
          <span className="flex items-center gap-1.5 text-xs text-subtle">
            {item.pending ? (
              <span>No questions yet</span>
            ) : (
              item.documentName && (
                <span className="flex min-w-0 items-center gap-1">
                  <FileText className="size-3 shrink-0" />
                  <span className="truncate">{item.documentName}</span>
                </span>
              )
            )}
            <span className="ml-auto shrink-0 tabular-nums">{formatTime(item.date)}</span>
          </span>
        </>
      )}
    </NavLink>
  )
}

function DateFilter({ range, onChange }: { range: RangeState; onChange: (range: RangeState) => void }) {
  const [draft, setDraft] = useState({ from: range.from, to: range.to })
  return (
    <Popover
      align="end"
      className="w-64"
      trigger={({ toggle, ...aria }) => (
        <IconButton
          label="Filter by date"
          size="sm"
          active={range.preset !== 'all'}
          onClick={toggle}
          className="size-8"
          {...aria}
        >
          <Calendar className="size-4" />
        </IconButton>
      )}
    >
      {(close) => (
        <div>
          <p className="px-2.5 pt-1 pb-1.5 text-xs font-medium text-subtle">Started</p>
          {(['all', 'today', 'week', 'month'] as const).map((preset) => (
            <MenuItem
              key={preset}
              active={range.preset === preset}
              icon={range.preset === preset ? <Check /> : <span className="block size-4" />}
              onClick={() => {
                onChange({ preset, from: '', to: '' })
                close()
              }}
            >
              {PRESET_LABELS[preset]}
            </MenuItem>
          ))}
          <form
            className="mt-1 border-t border-line px-2.5 pt-2.5 pb-1"
            onSubmit={(event) => {
              event.preventDefault()
              onChange({ preset: 'custom', ...draft })
              close()
            }}
          >
            <p className="text-xs font-medium text-subtle">Custom range</p>
            <div className="mt-2 grid grid-cols-2 gap-2">
              <label className="text-xs text-muted">
                From
                <input
                  type="date"
                  value={draft.from}
                  max={draft.to || undefined}
                  onChange={(event) => setDraft((d) => ({ ...d, from: event.target.value }))}
                  className="mt-1 h-8 w-full rounded-md border border-line bg-paper px-1.5 text-xs text-fg"
                />
              </label>
              <label className="text-xs text-muted">
                To
                <input
                  type="date"
                  value={draft.to}
                  min={draft.from || undefined}
                  onChange={(event) => setDraft((d) => ({ ...d, to: event.target.value }))}
                  className="mt-1 h-8 w-full rounded-md border border-line bg-paper px-1.5 text-xs text-fg"
                />
              </label>
            </div>
            <Button type="submit" size="sm" className="mt-2.5 w-full" disabled={!draft.from && !draft.to}>
              Apply
            </Button>
          </form>
        </div>
      )}
    </Popover>
  )
}

const THEME_OPTIONS: { value: ThemePreference; label: string; icon: ReactNode }[] = [
  { value: 'system', label: 'System', icon: <Monitor /> },
  { value: 'light', label: 'Light', icon: <Sun /> },
  { value: 'dark', label: 'Dark', icon: <Moon /> },
]

function UserMenu({
  user,
  onOpenSettings,
  onNavigate,
}: {
  user: User
  onOpenSettings: () => void
  onNavigate: () => void
}) {
  const navigate = useNavigate()
  const settings = useStore(settingsStore)
  return (
    <div className="border-t border-line p-2">
      <Popover
        side="top"
        className="w-[calc(100%-0px)] min-w-60"
        trigger={({ toggle, ...aria }) => (
          <button
            type="button"
            onClick={toggle}
            {...aria}
            className="flex w-full items-center gap-2.5 rounded-lg px-2 py-1.5 text-left transition-colors hover:bg-hover"
          >
            <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-ink-soft font-serif text-[15px] font-semibold text-ink">
              {user.userName.slice(0, 1).toUpperCase()}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium">{user.userName}</span>
              <span className="block truncate font-mono text-[11px] text-subtle">{user.userId}</span>
            </span>
          </button>
        )}
      >
        {(close) => (
          <div>
            <div className="flex gap-1 p-1">
              {THEME_OPTIONS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  title={option.label}
                  aria-label={`${option.label} theme`}
                  aria-pressed={settings.theme === option.value}
                  onClick={() => settingsStore.set((s) => ({ ...s, theme: option.value }))}
                  className={clsx(
                    'flex flex-1 items-center justify-center gap-1.5 rounded-md py-1.5 text-xs transition-colors [&>svg]:size-3.5',
                    settings.theme === option.value ? 'bg-hover font-medium text-fg' : 'text-muted hover:text-fg',
                  )}
                >
                  {option.icon}
                  {option.label}
                </button>
              ))}
            </div>
            <div className="my-1 border-t border-line" />
            <MenuItem
              icon={<Settings />}
              onClick={() => {
                close()
                onOpenSettings()
              }}
            >
              Settings
            </MenuItem>
            <MenuItem
              icon={<Code />}
              onClick={() => {
                close()
                navigate('/developers')
                onNavigate()
              }}
            >
              Developer API
            </MenuItem>
            <div className="my-1 border-t border-line" />
            <MenuItem
              icon={<LogOut />}
              onClick={() => {
                userStore.set(null)
                navigate('/login')
              }}
            >
              Sign out
            </MenuItem>
          </div>
        )}
      </Popover>
    </div>
  )
}

function ListSkeleton() {
  return (
    <div className="space-y-1 px-1 pt-2" aria-hidden="true">
      {[70, 55, 80, 62, 48].map((width, i) => (
        <div key={i} className="rounded-lg px-2.5 py-2">
          <div className="h-3.5 animate-pulse rounded bg-sunken" style={{ width: `${width}%` }} />
          <div className="mt-1.5 h-2.5 w-1/3 animate-pulse rounded bg-sunken" />
        </div>
      ))}
    </div>
  )
}
