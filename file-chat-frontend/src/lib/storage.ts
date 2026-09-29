import type { Passage, RelatedWork, User } from './api'
import { createStore } from './store'

export type ThemePreference = 'system' | 'light' | 'dark'

export interface Settings {
  theme: ThemePreference
  /** Stream answers token by token (graph/rag/stream) instead of waiting for the full answer (graph/rag) */
  streaming: boolean
}

export interface DocumentInfo {
  name: string
  size: number
  addedAt: string
}

export interface ConversationDocuments {
  userId: string
  createdAt: string
  documents: DocumentInfo[]
}

export const userStore = createStore<User | null>('filechat.user', null)

export const settingsStore = createStore<Settings>('filechat.settings', { theme: 'system', streaming: true })

/**
 * The backend does not keep a list of the files in a conversation, so the client remembers
 * what was uploaded from this browser. Keyed by conversationId.
 */
export const documentsStore = createStore<Record<string, ConversationDocuments>>('filechat.documents', {})

export function rememberDocument(userId: string, conversationId: string, file: File) {
  const now = new Date().toISOString()
  documentsStore.set((all) => {
    const existing = all[conversationId] ?? { userId, createdAt: now, documents: [] }
    return {
      ...all,
      [conversationId]: {
        ...existing,
        documents: [...existing.documents, { name: file.name, size: file.size, addedAt: now }],
      },
    }
  })
}

const MAX_SAVED_ANSWERS = 300

interface SavedSources {
  order: string[]
  byTrace: Record<string, Passage[]>
}

/**
 * Retrieved passages are not stored with the chat history, so they are cached per answer
 * (traceId) to keep citations working when a conversation is reopened.
 */
export const sourcesStore = createStore<SavedSources>('filechat.sources', { order: [], byTrace: {} })

export function rememberSources(traceId: string, sources: Passage[]) {
  sourcesStore.set((saved) => {
    const order = [...saved.order.filter((id) => id !== traceId), traceId]
    const byTrace = { ...saved.byTrace, [traceId]: sources }
    while (order.length > MAX_SAVED_ANSWERS) delete byTrace[order.shift()!]
    return { order, byTrace }
  })
}

/** Other works by the documents' authors (from the Neo4j catalog), cached per answer like the sources. */
export const relatedStore = createStore<{ order: string[]; byTrace: Record<string, RelatedWork[]> }>(
  'filechat.related',
  {
    order: [],
    byTrace: {},
  },
)

export function rememberRelated(traceId: string, related: RelatedWork[]) {
  relatedStore.set((saved) => {
    const order = [...saved.order.filter((id) => id !== traceId), traceId]
    const byTrace = { ...saved.byTrace, [traceId]: related }
    while (order.length > MAX_SAVED_ANSWERS) delete byTrace[order.shift()!]
    return { order, byTrace }
  })
}

/** The system prompt template is the same for every answer, so only the latest copy is kept. */
export const promptStore = createStore<string>('filechat.prompt', '')
