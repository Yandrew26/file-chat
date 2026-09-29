import { md5 } from 'js-md5'
import { Check, Copy, Eye, EyeOff, KeyRound, Play, Terminal } from 'lucide-react'
import { useMemo, useState, type ReactNode } from 'react'
import { OPENAPI_BASE } from '../lib/api'
import { userStore } from '../lib/storage'
import { useStore } from '../lib/store'
import { PageHeader } from '../components/PageHeader'
import { Button } from '../components/ui/Button'
import { Spinner } from '../components/ui/Spinner'

interface Endpoint {
  id: string
  label: string
  path: (userId: string) => string
}

const ENDPOINTS: Endpoint[] = [
  { id: 'user', label: 'Look up a user', path: (userId) => `/chat/user/${userId}` },
  {
    id: 'history',
    label: 'List conversations',
    path: (userId) => `/chat/history/pages?pageNum=1&pageSize=5&userId=${userId}`,
  },
]

const GATEWAY_ORIGIN = 'http://localhost:8100'

export function DevelopersPage() {
  const user = useStore(userStore)!
  const [authId, setAuthId] = useState('')
  const [secret, setSecret] = useState('')
  const [reveal, setReveal] = useState(false)
  const [endpointId, setEndpointId] = useState(ENDPOINTS[0].id)
  const [result, setResult] = useState<{ status: number; body: string } | { error: string } | null>(null)
  const [pending, setPending] = useState(false)

  const signature = useMemo(
    () => (authId && secret ? md5(`authId=${authId}&secretKey=${secret}`) : ''),
    [authId, secret],
  )
  const endpoint = ENDPOINTS.find((e) => e.id === endpointId)!
  const path = endpoint.path(user.userId)
  const curl = `curl '${GATEWAY_ORIGIN}${path}' \\\n  -H 'authID: ${authId || '<authId>'}' \\\n  -H 'authorization: ${signature || '<signature>'}'`

  async function sendTest() {
    setPending(true)
    setResult(null)
    try {
      const response = await fetch(`${OPENAPI_BASE}${path}`, { headers: { authID: authId, authorization: signature } })
      const text = await response.text()
      let body = text
      try {
        body = JSON.stringify(JSON.parse(text), null, 2)
      } catch {
        // not JSON
      }
      setResult({ status: response.status, body })
    } catch {
      setResult({ error: 'Could not reach the OpenAPI gateway. Is it running on port 8100?' })
    } finally {
      setPending(false)
    }
  }

  return (
    <>
      <PageHeader>
        <h1 className="text-[15px] font-medium">Developer API</h1>
      </PageHeader>
      <div className="flex-1 overflow-y-auto">
        <div className="mx-auto max-w-3xl space-y-8 px-4 py-8 sm:px-6 sm:py-10">
          <section>
            <h2 className="font-serif text-[28px] leading-tight font-semibold tracking-tight">
              Call FileChat from your own code
            </h2>
            <p className="mt-3 text-[15px] leading-relaxed text-muted">
              The OpenAPI gateway exposes the chat service under <Code>/chat/**</Code> and the upload service under{' '}
              <Code>/upload/**</Code>. Every request must carry two headers: your <Code>authID</Code> and an{' '}
              <Code>authorization</Code> signature, the MD5 hex digest of{' '}
              <Code>{'authId=<authId>&secretKey=<secret>'}</Code>. Credentials live in the <Code>rag_auth_base</Code>{' '}
              table.
            </p>
            <dl className="mt-5 grid gap-3 text-[13px] sm:grid-cols-3">
              <StatusHint code="401" text="A header is missing" />
              <StatusHint code="403" text="Unknown authID or wrong signature" />
              <StatusHint code="200" text="Verified and forwarded" />
            </dl>
          </section>

          <Card icon={<KeyRound className="size-4" />} title="Signature generator">
            <p className="text-[13px] text-muted">Computed in your browser. The secret is never sent or stored.</p>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <Field label="authID">
                <input
                  value={authId}
                  onChange={(event) => setAuthId(event.target.value.trim())}
                  spellCheck={false}
                  placeholder="12345"
                  className={inputClass}
                />
              </Field>
              <Field label="Secret key">
                <div className="relative">
                  <input
                    type={reveal ? 'text' : 'password'}
                    value={secret}
                    onChange={(event) => setSecret(event.target.value)}
                    spellCheck={false}
                    autoComplete="off"
                    placeholder="••••••"
                    className={`${inputClass} pr-10`}
                  />
                  <button
                    type="button"
                    aria-label={reveal ? 'Hide secret' : 'Show secret'}
                    onClick={() => setReveal((v) => !v)}
                    className="absolute top-1/2 right-2 -translate-y-1/2 rounded p-1 text-subtle hover:text-fg"
                  >
                    {reveal ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                  </button>
                </div>
              </Field>
            </div>
            <Field label="authorization" className="mt-4">
              <div className="flex items-center gap-2 rounded-lg border border-line bg-sunken px-3 py-2">
                <code className="min-w-0 flex-1 truncate font-mono text-[13px]">
                  {signature || <span className="text-subtle">Enter an authID and secret</span>}
                </code>
                {signature && <CopyButton value={signature} />}
              </div>
            </Field>
          </Card>

          <Card icon={<Terminal className="size-4" />} title="Try a request">
            <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Endpoint">
              {ENDPOINTS.map((e) => (
                <button
                  key={e.id}
                  type="button"
                  role="radio"
                  aria-checked={endpointId === e.id}
                  onClick={() => setEndpointId(e.id)}
                  className={
                    endpointId === e.id
                      ? 'rounded-full border border-ink bg-ink-soft px-3 py-1 text-[13px] font-medium text-ink'
                      : 'rounded-full border border-line px-3 py-1 text-[13px] text-muted hover:text-fg'
                  }
                >
                  {e.label}
                </button>
              ))}
            </div>
            <div className="relative mt-4">
              <pre
                tabIndex={0}
                aria-label="Example curl command"
                className="overflow-x-auto rounded-xl bg-[#1a1c22] p-4 font-mono text-[12.5px] leading-relaxed text-[#e6e6e3]"
              >
                {curl}
              </pre>
              <div className="absolute top-2 right-2">
                <CopyButton value={curl} dark />
              </div>
            </div>
            <div className="mt-4 flex items-center gap-3">
              <Button variant="primary" onClick={() => void sendTest()} disabled={pending}>
                {pending ? <Spinner /> : <Play className="size-3.5 fill-current" />}
                Send request
              </Button>
              {!signature && <span className="text-[13px] text-subtle">Without credentials you’ll see a 401.</span>}
            </div>
            {result && (
              <div className="mt-4" aria-live="polite">
                {'error' in result ? (
                  <p className="rounded-lg bg-danger-soft px-3 py-2 text-[13px] text-danger">{result.error}</p>
                ) : (
                  <>
                    <p className="text-[13px]">
                      <span
                        className={
                          result.status < 300
                            ? 'font-mono font-semibold text-success'
                            : 'font-mono font-semibold text-danger'
                        }
                      >
                        {result.status}
                      </span>{' '}
                      <span className="text-muted">{statusText(result.status)}</span>
                    </p>
                    {result.body && (
                      <pre
                        tabIndex={0}
                        aria-label="Response body"
                        className="mt-2 max-h-72 overflow-auto rounded-xl border border-line bg-sunken p-4 font-mono text-[12px] leading-relaxed"
                      >
                        {result.body}
                      </pre>
                    )}
                  </>
                )}
              </div>
            )}
          </Card>
        </div>
      </div>
    </>
  )
}

const inputClass =
  'h-10 w-full rounded-lg border border-line bg-surface px-3 font-mono text-[14px] placeholder:text-subtle focus:border-ink focus:outline-none'

function statusText(status: number) {
  if (status === 200) return 'Verified and forwarded'
  if (status === 401) return 'Missing authID or authorization header'
  if (status === 403) return 'The signature did not match'
  return 'Unexpected response'
}

function Code({ children }: { children: ReactNode }) {
  return <code className="rounded bg-sunken px-1 py-px font-mono text-[0.86em] text-fg">{children}</code>
}

function StatusHint({ code, text }: { code: string; text: string }) {
  return (
    <div className="rounded-lg border border-line bg-surface px-3 py-2.5">
      <dt className="font-mono font-semibold">{code}</dt>
      <dd className="text-muted">{text}</dd>
    </div>
  )
}

function Card({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <section className="rounded-2xl border border-line bg-surface p-5 shadow-card sm:p-6">
      <h3 className="flex items-center gap-2 text-[15px] font-semibold">
        <span className="flex size-7 items-center justify-center rounded-lg bg-sunken text-muted">{icon}</span>
        {title}
      </h3>
      <div className="mt-4">{children}</div>
    </section>
  )
}

function Field({ label, children, className }: { label: string; children: ReactNode; className?: string }) {
  return (
    <label className={`block ${className ?? ''}`}>
      <span className="font-mono text-xs font-medium text-muted">{label}</span>
      <div className="mt-1.5">{children}</div>
    </label>
  )
}

function CopyButton({ value, dark }: { value: string; dark?: boolean }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      aria-label={copied ? 'Copied' : 'Copy'}
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value)
          setCopied(true)
          setTimeout(() => setCopied(false), 1500)
        } catch {
          // clipboard unavailable
        }
      }}
      className={
        dark
          ? 'flex size-8 items-center justify-center rounded-md text-[#a3a3a0] hover:bg-white/10 hover:text-white'
          : 'flex size-7 shrink-0 items-center justify-center rounded-md text-muted hover:bg-hover hover:text-fg'
      }
    >
      {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
    </button>
  )
}
