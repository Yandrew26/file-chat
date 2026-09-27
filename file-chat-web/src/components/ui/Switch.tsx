import { clsx } from 'clsx'

interface SwitchProps {
  checked: boolean
  onChange: (checked: boolean) => void
  id?: string
  'aria-describedby'?: string
}

export function Switch({ checked, onChange, ...rest }: SwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className={clsx(
        'relative inline-flex h-6 w-10 shrink-0 items-center rounded-full border transition-colors',
        checked ? 'border-ink bg-ink' : 'border-line-strong bg-sunken',
      )}
      {...rest}
    >
      <span
        className={clsx(
          'inline-block size-4.5 rounded-full bg-surface shadow-card transition-transform',
          checked ? 'translate-x-[18px]' : 'translate-x-[2px]',
        )}
      />
    </button>
  )
}
