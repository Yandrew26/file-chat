import { useEffect, useState } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { userStore } from '../lib/storage'
import { useStore } from '../lib/store'
import { SettingsDialog } from './SettingsDialog'
import { Sidebar } from './Sidebar'

export interface LayoutContext {
  openSidebar: () => void
}

export function AppLayout() {
  const user = useStore(userStore)
  const location = useLocation()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)

  useEffect(() => {
    if (!sidebarOpen) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setSidebarOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [sidebarOpen])

  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />

  return (
    <div className="flex h-dvh overflow-hidden">
      {/* Desktop sidebar */}
      <aside className="hidden w-[276px] shrink-0 border-r border-line lg:block">
        <Sidebar user={user} onNavigate={() => {}} onOpenSettings={() => setSettingsOpen(true)} />
      </aside>

      {/* Mobile sidebar drawer */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 animate-fade-in bg-black/35" onClick={() => setSidebarOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-[min(300px,85vw)] animate-slide-in-left border-r border-line shadow-pop">
            <Sidebar
              user={user}
              onNavigate={() => setSidebarOpen(false)}
              onOpenSettings={() => {
                setSidebarOpen(false)
                setSettingsOpen(true)
              }}
            />
          </aside>
        </div>
      )}

      <main className="flex min-w-0 flex-1 flex-col">
        <Outlet context={{ openSidebar: () => setSidebarOpen(true) } satisfies LayoutContext} />
      </main>

      <SettingsDialog open={settingsOpen} onClose={() => setSettingsOpen(false)} user={user} />
    </div>
  )
}
