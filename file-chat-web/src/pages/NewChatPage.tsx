import { MessagesSquare, ScanText, Upload } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { createChat } from '../lib/api'
import { rememberDocument, userStore } from '../lib/storage'
import { useStore } from '../lib/store'
import { PageHeader } from '../components/PageHeader'
import { UploadDropzone } from '../components/UploadDropzone'
import { validatePdf, type UploadState } from '../lib/upload'

const STEPS = [
  { icon: Upload, title: 'Upload a PDF', body: 'Reports, papers, contracts, manuals.' },
  { icon: ScanText, title: 'FileChat indexes it', body: 'Every page is split into passages and embedded.' },
  { icon: MessagesSquare, title: 'Ask anything', body: 'Answers cite the passages they came from.' },
]

export function NewChatPage() {
  const user = useStore(userStore)!
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [state, setState] = useState<UploadState>({ phase: 'idle' })

  async function upload(file: File) {
    const invalid = validatePdf(file)
    if (invalid) {
      setState({ phase: 'error', file, message: invalid })
      return
    }
    setState({ phase: 'uploading', file, progress: 0 })
    try {
      const result = await createChat(user.userId, file, (progress) =>
        setState((s) => (s.phase === 'uploading' ? { ...s, progress } : s)),
      )
      rememberDocument(user.userId, result.conversationId, file)
      void queryClient.invalidateQueries({ queryKey: ['conversations'] })
      navigate(`/c/${result.conversationId}`)
    } catch (error) {
      setState({ phase: 'error', file, message: (error as Error).message })
    }
  }

  return (
    <>
      <PageHeader mobileOnly />
      <div className="flex-1 overflow-y-auto">
        <div className="mx-auto flex min-h-full max-w-2xl flex-col justify-center px-4 py-10 sm:px-6">
          <h1 className="text-center font-serif text-[32px] leading-tight font-semibold tracking-tight sm:text-[40px]">
            Ask your documents anything
          </h1>
          <p className="mx-auto mt-3 max-w-lg text-center text-[15px] text-muted sm:text-base">
            Upload a PDF to start a conversation. Every answer links back to the passages it was drawn from, so you can
            check it.
          </p>

          <div className="mt-8">
            <UploadDropzone state={state} onFile={upload} />
          </div>

          <ol className="mt-8 grid gap-4 sm:grid-cols-3">
            {STEPS.map((step, index) => (
              <li key={step.title} className="flex gap-3 sm:flex-col sm:gap-2">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-lg border border-line bg-surface text-muted">
                  <step.icon className="size-4" />
                </span>
                <div>
                  <p className="text-sm font-medium">
                    <span className="text-subtle tabular-nums">{index + 1}.</span> {step.title}
                  </p>
                  <p className="mt-0.5 text-[13px] text-muted">{step.body}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </div>
    </>
  )
}
