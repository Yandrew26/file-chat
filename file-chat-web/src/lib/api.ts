import { SseParser } from './sse'

const API_BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? '/system'
export const OPENAPI_BASE = (import.meta.env.VITE_OPENAPI_BASE as string | undefined) ?? '/openapi'

export interface User {
  userId: string
  userName: string
}

export interface ConversationSummary {
  conversationId: string
  userId: string
  userName: string
  /** The first question asked in the conversation */
  content: string
  createdDate: string
}

export interface Page<T> {
  data: T[]
  pageNo: number
  pageSize: number
  total: number
  pages: number
}

export interface HistoryMessage {
  id: number
  userId: string
  userName: string
  conversationId: string
  traceId: string
  content: string
  type: 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL'
  createdDate: string
}

export interface Passage {
  id: string
  text: string
  fileName: string | null
  pageNumber: number | null
  score: number | null
}

export interface UploadResult {
  conversationId: string
  fileName: string
}

export interface AnswerResult {
  traceId: string
  conversationId: string
  answer: string
  sources: Passage[]
  prompt: string
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  let message = ''
  try {
    const body = await response.json()
    message = typeof body?.message === 'string' ? body.message : ''
  } catch {
    // body was not JSON
  }
  return new ApiError(response.status, message || defaultMessage(response.status))
}

function defaultMessage(status: number): string {
  if (status === 404) return 'Not found.'
  if (status === 413) return 'That file is too large. The limit is 50 MB.'
  if (status === 415) return 'Only PDF files are supported.'
  if (status >= 500) return 'The server ran into a problem. Please try again.'
  return 'Something went wrong. Please try again.'
}

async function getJson<T>(
  path: string,
  params: Record<string, string | number | undefined> = {},
  signal?: AbortSignal,
) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') query.set(key, String(value))
  }
  const qs = query.toString()
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}${qs ? `?${qs}` : ''}`, { signal })
  } catch (error) {
    if ((error as Error).name === 'AbortError') throw error
    throw new ApiError(0, 'Could not reach the FileChat server. Is the gateway running?')
  }
  if (!response.ok) throw await toApiError(response)
  return (await response.json()) as T
}

export function getUser(userId: string) {
  return getJson<User>(`/user/${encodeURIComponent(userId)}`)
}

export function getConversations(params: {
  userId: string
  pageNum: number
  pageSize: number
  dateStart?: string
  dateEnd?: string
}) {
  return getJson<Page<ConversationSummary>>('/history/pages', params)
}

export function getMessages(conversationId: string, signal?: AbortSignal) {
  return getJson<HistoryMessage[]>('/history/getByConversationId', { conversationId }, signal)
}

export function searchDocument(conversationId: string, query: string, signal?: AbortSignal) {
  return getJson<Passage[]>('/graph/search', { conversationId, query }, signal)
}

export function askOnce(conversationId: string, message: string, signal?: AbortSignal) {
  return getJson<AnswerResult>('/graph/rag', { conversationId, message }, signal)
}

/** Uploads with XMLHttpRequest so upload progress can be reported. */
function uploadPdf(path: string, fields: Record<string, string>, file: File, onProgress: (fraction: number) => void) {
  return new Promise<UploadResult>((resolve, reject) => {
    const form = new FormData()
    for (const [key, value] of Object.entries(fields)) form.append(key, value)
    form.append('file', file)

    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${API_BASE}${path}`)
    xhr.responseType = 'json'
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(event.loaded / event.total)
    }
    xhr.upload.onload = () => onProgress(1)
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.response as UploadResult)
      } else {
        const message = typeof xhr.response?.message === 'string' && xhr.response.message
        reject(new ApiError(xhr.status, message || defaultMessage(xhr.status)))
      }
    }
    xhr.onerror = () => reject(new ApiError(0, 'Could not reach the FileChat server. Is the gateway running?'))
    xhr.send(form)
  })
}

export function createChat(userId: string, file: File, onProgress: (fraction: number) => void) {
  return uploadPdf('/graph/create-chat', { userId }, file, onProgress)
}

export function addDocument(conversationId: string, file: File, onProgress: (fraction: number) => void) {
  return uploadPdf('/graph/documents', { conversationId }, file, onProgress)
}

export type StreamEvent =
  | { type: 'meta'; traceId: string; conversationId: string }
  | { type: 'sources'; sources: Passage[] }
  | { type: 'prompt'; prompt: string }
  | { type: 'token'; text: string }
  | { type: 'done' }
  | { type: 'error'; message: string }

/** Streams an answer from the RAG graph, calling onEvent for each server-sent event. */
export async function streamAnswer(
  conversationId: string,
  message: string,
  onEvent: (event: StreamEvent) => void,
  signal: AbortSignal,
) {
  const query = new URLSearchParams({ conversationId, message })
  let response: Response
  try {
    response = await fetch(`${API_BASE}/graph/rag/stream?${query}`, {
      headers: { Accept: 'text/event-stream' },
      signal,
    })
  } catch (error) {
    if ((error as Error).name === 'AbortError') throw error
    throw new ApiError(0, 'Could not reach the FileChat server. Is the gateway running?')
  }
  if (!response.ok || !response.body) throw await toApiError(response)

  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader()
  const parser = new SseParser()
  let finished = false
  const handle = (name: string, data: string) => {
    const payload = JSON.parse(data)
    switch (name) {
      case 'meta':
        onEvent({ type: 'meta', traceId: payload.traceId, conversationId: payload.conversationId })
        break
      case 'sources':
        onEvent({ type: 'sources', sources: payload as Passage[] })
        break
      case 'prompt':
        onEvent({ type: 'prompt', prompt: String(payload) })
        break
      case 'token':
        onEvent({ type: 'token', text: String(payload.text ?? '') })
        break
      case 'done':
        finished = true
        onEvent({ type: 'done' })
        break
      case 'error':
        finished = true
        onEvent({ type: 'error', message: String(payload.message ?? 'The answer could not be generated.') })
        break
    }
  }

  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    for (const event of parser.push(value)) handle(event.event, event.data)
  }
  for (const event of parser.flush()) handle(event.event, event.data)
  if (!finished) onEvent({ type: 'error', message: 'The connection closed before the answer finished.' })
}
