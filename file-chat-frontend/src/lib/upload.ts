import { formatBytes } from './format'

export const MAX_UPLOAD_BYTES = 50 * 1024 * 1024

export type UploadState =
  | { phase: 'idle' }
  | { phase: 'uploading'; file: File; progress: number }
  | { phase: 'error'; file?: File; message: string }

export function validatePdf(file: File): string | null {
  const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')
  if (!isPdf) return 'Only PDF files are supported.'
  if (file.size > MAX_UPLOAD_BYTES) return `That file is ${formatBytes(file.size)}. The limit is 50 MB.`
  if (file.size === 0) return 'That file is empty.'
  return null
}
