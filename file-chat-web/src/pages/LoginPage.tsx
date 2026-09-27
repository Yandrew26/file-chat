import { ArrowRight } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError, getUser } from '../lib/api'
import { userStore } from '../lib/storage'
import { useStore } from '../lib/store'
import { LogoMark } from '../components/Logo'
import { Button } from '../components/ui/Button'
import { Spinner } from '../components/ui/Spinner'

export function LoginPage() {
  const user = useStore(userStore)
  const navigate = useNavigate()
  const location = useLocation()
  const [userId, setUserId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const from = (location.state as { from?: string } | null)?.from ?? '/'
  if (user) return <Navigate to={from} replace />

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const id = userId.trim()
    if (!id) {
      setError('Enter your user ID.')
      return
    }
    setPending(true)
    setError(null)
    try {
      userStore.set(await getUser(id))
      navigate(from, { replace: true })
    } catch (e) {
      setError(
        e instanceof ApiError && e.status === 404
          ? 'We couldn’t find that user ID. Check it and try again.'
          : (e as Error).message,
      )
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center px-4 py-12">
      <div className="w-full max-w-[380px]">
        <div className="flex flex-col items-center text-center">
          <LogoMark className="size-11" />
          <h1 className="mt-5 font-serif text-[28px] leading-tight font-semibold tracking-tight">
            Sign in to FileChat
          </h1>
          <p className="mt-2 text-[15px] text-muted">
            Ask questions about your PDFs and get answers that cite the page.
          </p>
        </div>

        <form onSubmit={onSubmit} noValidate className="mt-8 rounded-2xl border border-line bg-surface p-6 shadow-card">
          <label htmlFor="user-id" className="text-sm font-medium">
            User ID
          </label>
          <input
            id="user-id"
            autoFocus
            autoComplete="username"
            spellCheck={false}
            value={userId}
            onChange={(event) => setUserId(event.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? 'user-id-error' : 'user-id-help'}
            placeholder="e.g. 12345678"
            className="mt-1.5 h-11 w-full rounded-lg border border-line bg-paper px-3 font-mono text-base placeholder:font-sans placeholder:text-subtle focus:border-ink focus:outline-none aria-invalid:border-danger sm:text-[15px]"
          />
          {error ? (
            <p id="user-id-error" role="alert" className="mt-2 text-[13px] text-danger">
              {error}
            </p>
          ) : (
            <p id="user-id-help" className="mt-2 text-[13px] text-subtle">
              Your ID from the <span className="font-mono text-[12px]">system_user</span> table.
            </p>
          )}
          <Button type="submit" variant="primary" size="lg" className="mt-5 w-full" disabled={pending}>
            {pending ? (
              <Spinner />
            ) : (
              <>
                Continue <ArrowRight className="size-4" />
              </>
            )}
          </Button>
        </form>
      </div>
    </div>
  )
}
