import { useEffect } from 'react'
import { settingsStore } from '../lib/storage'
import { useStore } from '../lib/store'

/** Applies the theme preference to <html data-theme>, following the OS when set to "system". */
export function useApplyTheme() {
  const { theme } = useStore(settingsStore)

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const apply = () => {
      const resolved = theme === 'system' ? (media.matches ? 'dark' : 'light') : theme
      document.documentElement.dataset.theme = resolved
    }
    apply()
    media.addEventListener('change', apply)
    return () => media.removeEventListener('change', apply)
  }, [theme])
}
