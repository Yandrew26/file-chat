import { clsx } from 'clsx'
import { Menu } from 'lucide-react'
import type { ReactNode } from 'react'
import { useOutletContext } from 'react-router'
import type { LayoutContext } from './AppLayout'
import { IconButton } from './ui/Button'

/** Top bar for pages inside the app layout; shows the sidebar toggle on small screens. */
interface PageHeaderProps {
  children?: ReactNode
  actions?: ReactNode
  /** Only needed for the sidebar toggle, so hide it where the sidebar is always visible */
  mobileOnly?: boolean
}

export function PageHeader({ children, actions, mobileOnly }: PageHeaderProps) {
  const { openSidebar } = useOutletContext<LayoutContext>()
  return (
    <header
      className={clsx(
        'flex h-14 shrink-0 items-center gap-2 border-b border-line bg-paper/85 px-3 backdrop-blur sm:px-4',
        mobileOnly && 'lg:hidden',
      )}
    >
      <IconButton label="Open conversations" className="lg:hidden" onClick={openSidebar}>
        <Menu className="size-5" />
      </IconButton>
      <div className="flex min-w-0 flex-1 items-center gap-2">{children}</div>
      {actions && <div className="flex shrink-0 items-center gap-1">{actions}</div>}
    </header>
  )
}
