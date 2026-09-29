import { useSyncExternalStore } from 'react'

/**
 * A tiny localStorage-backed store. Reads and writes are wrapped because storage can be
 * unavailable (private mode, blocked site data); the app keeps working in memory then.
 */
export interface Store<T> {
  get: () => T
  set: (next: T | ((previous: T) => T)) => void
  subscribe: (listener: () => void) => () => void
}

export function createStore<T>(key: string, initial: T): Store<T> {
  let value = initial
  try {
    const raw = localStorage.getItem(key)
    if (raw !== null) {
      const saved = JSON.parse(raw)
      // Merge saved objects over the defaults so newly added settings get a value
      const isPlainObject = typeof initial === 'object' && initial !== null && !Array.isArray(initial)
      value = (isPlainObject ? { ...initial, ...saved } : saved) as T
    }
  } catch {
    // ignore unreadable storage
  }
  const listeners = new Set<() => void>()

  return {
    get: () => value,
    set(next) {
      value = typeof next === 'function' ? (next as (previous: T) => T)(value) : next
      try {
        localStorage.setItem(key, JSON.stringify(value))
      } catch {
        // storage full or unavailable
      }
      listeners.forEach((listener) => listener())
    },
    subscribe(listener) {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
  }
}

export function useStore<T>(store: Store<T>): T {
  return useSyncExternalStore(store.subscribe, store.get, store.get)
}
