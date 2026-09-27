import { clsx } from 'clsx'
import { ArrowUp, Square } from 'lucide-react'
import { MAX_QUESTION_LENGTH } from '../../lib/api'
import { forwardRef, useImperativeHandle, useRef, useState, type KeyboardEvent } from 'react'

export interface ComposerHandle {
  focus: () => void
}

interface ComposerProps {
  onSend: (text: string) => void
  onStop: () => void
  busy: boolean
  placeholder?: string
}

export const Composer = forwardRef<ComposerHandle, ComposerProps>(function Composer(
  { onSend, onStop, busy, placeholder },
  ref,
) {
  const [text, setText] = useState('')
  const textarea = useRef<HTMLTextAreaElement>(null)
  const nearLimit = text.length > MAX_QUESTION_LENGTH * 0.8

  useImperativeHandle(ref, () => ({ focus: () => textarea.current?.focus() }))

  const resize = () => {
    const el = textarea.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 220)}px`
  }

  const submit = () => {
    const value = text.trim()
    if (!value || busy) return
    onSend(value)
    setText('')
    requestAnimationFrame(resize)
  }

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      submit()
    }
  }

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
      className="rounded-2xl border border-line bg-surface shadow-card transition-colors focus-within:border-line-strong"
    >
      <label htmlFor="composer" className="sr-only">
        Ask a question about your documents
      </label>
      <div className="flex items-end gap-2 p-2 pl-4">
        <textarea
          id="composer"
          ref={textarea}
          rows={1}
          value={text}
          onChange={(event) => {
            setText(event.target.value)
            resize()
          }}
          onKeyDown={onKeyDown}
          maxLength={MAX_QUESTION_LENGTH}
          aria-describedby={nearLimit ? 'composer-count' : undefined}
          placeholder={placeholder ?? 'Ask about your documents…'}
          className="max-h-[220px] min-h-[40px] flex-1 resize-none bg-transparent py-2 text-base leading-6 placeholder:text-subtle focus:outline-none sm:text-[15px]"
        />
        {busy ? (
          <button
            type="button"
            onClick={onStop}
            aria-label="Stop generating"
            title="Stop generating"
            className="flex size-9 shrink-0 items-center justify-center rounded-xl border border-line-strong bg-surface text-fg transition-colors hover:bg-hover"
          >
            <Square className="size-3.5 fill-current" />
          </button>
        ) : (
          <button
            type="submit"
            disabled={!text.trim()}
            aria-label="Send"
            title="Send (Enter)"
            className={clsx(
              'flex size-9 shrink-0 items-center justify-center rounded-xl transition-colors',
              text.trim() ? 'bg-ink text-ink-fg hover:bg-ink-hover' : 'bg-sunken text-subtle',
            )}
          >
            <ArrowUp className="size-4.5" strokeWidth={2.25} />
          </button>
        )}
      </div>
      {nearLimit && (
        <p
          id="composer-count"
          className="px-4 pb-2 text-right text-[11.5px] text-subtle tabular-nums"
          aria-live="polite"
        >
          {text.length} / {MAX_QUESTION_LENGTH}
        </p>
      )}
    </form>
  )
})
