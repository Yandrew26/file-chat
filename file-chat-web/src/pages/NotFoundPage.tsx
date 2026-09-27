import { Link } from 'react-router'
import { LogoMark } from '../components/Logo'

export function NotFoundPage() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center px-4 text-center">
      <LogoMark className="size-10" />
      <h1 className="mt-5 font-serif text-3xl font-semibold">Page not found</h1>
      <p className="mt-2 text-muted">That link doesn’t lead anywhere.</p>
      <Link to="/" className="mt-6 text-sm font-medium text-ink hover:underline">
        Back to FileChat
      </Link>
    </div>
  )
}
