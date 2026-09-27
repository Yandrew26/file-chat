import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// In development the web client talks to the gateways through this proxy, so no CORS setup is needed.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const gateway = env.FILECHAT_GATEWAY_URL ?? 'http://localhost:8080'
  const openApiGateway = env.FILECHAT_OPENAPI_GATEWAY_URL ?? 'http://localhost:8100'

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      proxy: {
        '/system': { target: gateway, changeOrigin: true },
        '/openapi': {
          target: openApiGateway,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/openapi/, ''),
        },
      },
    },
    test: {
      environment: 'jsdom',
    },
  }
})
