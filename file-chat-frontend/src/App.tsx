import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { Toaster } from 'sonner'
import { ApiError } from './lib/api'
import { settingsStore } from './lib/storage'
import { useStore } from './lib/store'
import { useApplyTheme } from './hooks/useTheme'
import { AppLayout } from './components/AppLayout'
import { LoginPage } from './pages/LoginPage'
import { NewChatPage } from './pages/NewChatPage'
import { NotFoundPage } from './pages/NotFoundPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Client errors will not succeed on retry
      retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
    },
  },
})

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <NewChatPage /> },
      // Loaded on demand: the chat page carries the markdown renderer
      { path: 'c/:conversationId', lazy: () => import('./pages/ChatPage').then((m) => ({ Component: m.ChatRoute })) },
      {
        path: 'developers',
        lazy: () => import('./pages/DevelopersPage').then((m) => ({ Component: m.DevelopersPage })),
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])

export function App() {
  useApplyTheme()
  const { theme } = useStore(settingsStore)
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Toaster
        position="bottom-right"
        theme={theme}
        toastOptions={{
          style: {
            background: 'var(--surface)',
            color: 'var(--text)',
            border: '1px solid var(--line)',
            fontFamily: 'var(--font-sans)',
          },
        }}
      />
    </QueryClientProvider>
  )
}
