import { clsx } from 'clsx'
import { toast } from 'sonner'
import type { User } from '../lib/api'
import { documentsStore, promptStore, settingsStore, sourcesStore, type ThemePreference } from '../lib/storage'
import { useStore } from '../lib/store'
import { Button } from './ui/Button'
import { Dialog } from './ui/Dialog'
import { Switch } from './ui/Switch'

const THEMES: { value: ThemePreference; label: string }[] = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

export function SettingsDialog({ open, onClose, user }: { open: boolean; onClose: () => void; user: User }) {
  const settings = useStore(settingsStore)

  return (
    <Dialog open={open} onClose={onClose} title="Settings">
      <div className="space-y-6">
        <section>
          <h3 className="text-sm font-medium">Appearance</h3>
          <div role="radiogroup" aria-label="Theme" className="mt-2 grid grid-cols-3 gap-1 rounded-lg bg-sunken p-1">
            {THEMES.map((theme) => (
              <button
                key={theme.value}
                type="button"
                role="radio"
                aria-checked={settings.theme === theme.value}
                onClick={() => settingsStore.set((s) => ({ ...s, theme: theme.value }))}
                className={clsx(
                  'rounded-md py-1.5 text-sm transition-colors',
                  settings.theme === theme.value
                    ? 'bg-surface font-medium text-fg shadow-card'
                    : 'text-muted hover:text-fg',
                )}
              >
                {theme.label}
              </button>
            ))}
          </div>
        </section>

        <section className="flex items-start justify-between gap-6">
          <div>
            <label htmlFor="streaming" className="text-sm font-medium">
              Stream answers
            </label>
            <p id="streaming-help" className="mt-0.5 text-[13px] text-muted">
              Show answers word by word as they’re written. Turn off to wait for the complete answer instead.
            </p>
          </div>
          <Switch
            id="streaming"
            aria-describedby="streaming-help"
            checked={settings.streaming}
            onChange={(streaming) => settingsStore.set((s) => ({ ...s, streaming }))}
          />
        </section>

        <section>
          <h3 className="text-sm font-medium">Account</h3>
          <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-[13px]">
            <dt className="text-muted">Name</dt>
            <dd>{user.userName}</dd>
            <dt className="text-muted">User ID</dt>
            <dd className="font-mono">{user.userId}</dd>
          </dl>
        </section>

        <section className="flex items-start justify-between gap-6 border-t border-line pt-5">
          <div>
            <h3 className="text-sm font-medium">Local data</h3>
            <p className="mt-0.5 text-[13px] text-muted">
              File names and retrieved passages are remembered in this browser. Your chat history on the server is not
              affected.
            </p>
          </div>
          <Button
            size="sm"
            variant="danger"
            onClick={() => {
              documentsStore.set({})
              sourcesStore.set({ order: [], byTrace: {} })
              promptStore.set('')
              toast.success('Local data cleared')
            }}
          >
            Clear
          </Button>
        </section>
      </div>
    </Dialog>
  )
}
