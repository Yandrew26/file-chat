import { clsx } from 'clsx'
import { AlertCircle, FileText, FileUp } from 'lucide-react'
import { useRef, useState, type DragEvent } from 'react'
import { formatBytes } from '../lib/format'
import type { UploadState } from '../lib/upload'
import { Button } from './ui/Button'

interface UploadDropzoneProps {
  state: UploadState
  onFile: (file: File) => void
}

export function UploadDropzone({ state, onFile }: UploadDropzoneProps) {
  const input = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const busy = state.phase === 'uploading'

  const onDrop = (event: DragEvent) => {
    event.preventDefault()
    setDragging(false)
    const file = event.dataTransfer.files[0]
    if (file && !busy) onFile(file)
  }

  return (
    <div
      onDragOver={(event) => {
        event.preventDefault()
        if (!busy) setDragging(true)
      }}
      onDragLeave={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node)) setDragging(false)
      }}
      onDrop={onDrop}
      className={clsx(
        'relative rounded-2xl border-2 border-dashed bg-surface px-6 py-10 text-center transition-colors sm:py-12',
        dragging ? 'border-ink bg-ink-soft' : 'border-line-strong',
      )}
    >
      <input
        ref={input}
        type="file"
        aria-label="Upload a PDF"
        accept="application/pdf,.pdf"
        className="sr-only"
        tabIndex={-1}
        onChange={(event) => {
          const file = event.target.files?.[0]
          event.target.value = ''
          if (file) onFile(file)
        }}
      />

      {state.phase === 'uploading' ? (
        <UploadProgress file={state.file} progress={state.progress} />
      ) : (
        <>
          <div className="mx-auto flex size-12 items-center justify-center rounded-xl bg-mark-soft text-mark-fg">
            <FileUp className="size-6" />
          </div>
          <p className="mt-4 text-[15px] font-medium">{dragging ? 'Drop to upload' : 'Drag a PDF here'}</p>
          <p className="mt-1 text-sm text-muted">or</p>
          <Button variant="primary" className="mt-3" onClick={() => input.current?.click()}>
            Choose a file
          </Button>
          <p className="mt-4 text-xs text-subtle">PDF, up to 50 MB</p>
        </>
      )}

      {state.phase === 'error' && (
        <div
          role="alert"
          className="mx-auto mt-5 flex max-w-sm items-start gap-2 rounded-lg bg-danger-soft px-3 py-2.5 text-left text-[13px] text-danger"
        >
          <AlertCircle className="mt-px size-4 shrink-0" />
          <span>
            {state.file && <span className="font-medium">{state.file.name}: </span>}
            {state.message}
          </span>
        </div>
      )}
    </div>
  )
}

function UploadProgress({ file, progress }: { file: File; progress: number }) {
  const indexing = progress >= 1
  return (
    <div className="mx-auto max-w-sm" aria-live="polite">
      <div className="flex items-center gap-3 text-left">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-sunken text-muted">
          <FileText className="size-5" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{file.name}</p>
          <p className="text-xs text-muted">
            {indexing
              ? 'Reading and indexing pages…'
              : `Uploading · ${Math.round(progress * 100)}% of ${formatBytes(file.size)}`}
          </p>
        </div>
      </div>
      <div
        className="mt-4 h-1.5 overflow-hidden rounded-full bg-sunken"
        role="progressbar"
        aria-label={indexing ? 'Indexing document' : 'Uploading document'}
        aria-valuenow={indexing ? undefined : Math.round(progress * 100)}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        {indexing ? (
          <div className="h-full w-1/3 animate-[indeterminate_1.4s_ease-in-out_infinite] rounded-full bg-ink" />
        ) : (
          <div
            className="h-full rounded-full bg-ink transition-[width] duration-200"
            style={{ width: `${progress * 100}%` }}
          />
        )}
      </div>
      {indexing && (
        <p className="mt-3 text-xs text-subtle">Large documents can take a minute while each page is embedded.</p>
      )}
    </div>
  )
}
