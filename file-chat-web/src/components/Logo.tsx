import { clsx } from 'clsx'

export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={clsx('size-8', className)} aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="var(--ink)" />
      <path d="M10 7.5h8.5L23 12v12.5a1 1 0 0 1-1 1H10a1 1 0 0 1-1-1v-16a1 1 0 0 1 1-1Z" fill="var(--surface)" />
      <path d="M18.5 7.5V12H23" fill="none" stroke="var(--ink)" strokeOpacity=".25" strokeWidth="1" />
      <rect x="11.5" y="15" width="9" height="3" rx="1" fill="#f6d35b" />
      <rect x="11.5" y="19.5" width="6" height="1.4" rx=".7" fill="var(--ink)" fillOpacity=".3" />
      <rect x="11.5" y="12" width="5" height="1.4" rx=".7" fill="var(--ink)" fillOpacity=".3" />
    </svg>
  )
}

export function Logo({ className }: { className?: string }) {
  return (
    <span className={clsx('inline-flex items-center gap-2.5', className)}>
      <LogoMark />
      <span className="font-serif text-[21px] font-semibold tracking-tight">FileChat</span>
    </span>
  )
}
