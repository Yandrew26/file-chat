import { createHash } from 'node:crypto'
import { defineConfig, loadEnv, type Plugin, type ProxyOptions } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

/**
 * Adds a Content-Security-Policy to the production build. Scripts are limited to our own files plus the inline
 * theme script (by hash), and images to same-origin, so injected markup cannot run code or load remote images.
 * Not applied in development, where Vite injects its own scripts.
 */
function contentSecurityPolicy(apiBase: string, openApiBase: string): Plugin {
  const origin = (base: string) => (/^https?:\/\//.test(base) ? new URL(base).origin : '')
  const connect = ["'self'", origin(apiBase), origin(openApiBase)].filter(Boolean).join(' ')
  return {
    name: 'filechat-csp',
    apply: 'build',
    transformIndexHtml(html) {
      const hashes = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(
        ([, code]) => `'sha256-${createHash('sha256').update(code).digest('base64')}'`,
      )
      const policy = [
        "default-src 'self'",
        `script-src 'self' ${hashes.join(' ')}`,
        "style-src 'self' 'unsafe-inline'",
        "img-src 'self' data:",
        "font-src 'self' data:",
        `connect-src ${connect}`,
        "object-src 'none'",
        "base-uri 'self'",
        "form-action 'self'",
      ].join('; ')
      return html.replace(
        '<meta charset="UTF-8" />',
        `<meta charset="UTF-8" />\n    <meta http-equiv="Content-Security-Policy" content="${policy}" />`,
      )
    },
  }
}

// In development the web client talks to the gateways through this proxy, so no CORS setup is needed.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const gateway = env.FILECHAT_GATEWAY_URL ?? 'http://localhost:8080'
  const openApiGateway = env.FILECHAT_OPENAPI_GATEWAY_URL ?? 'http://localhost:8100'

  // The browser sees these calls as same-origin, but it still sends an Origin header on POSTs. Forwarded as-is, the
  // gateway would treat it as a cross-origin request and reject any origin not in FILECHAT_ALLOWED_ORIGINS.
  const configure: ProxyOptions['configure'] = (server) =>
    server.on('proxyReq', (request) => request.removeHeader('origin'))
  const proxy = {
    '/system': { target: gateway, changeOrigin: true, configure },
    '/openapi': {
      target: openApiGateway,
      changeOrigin: true,
      configure,
      rewrite: (path: string) => path.replace(/^\/openapi/, ''),
    },
  }

  return {
    plugins: [
      react(),
      tailwindcss(),
      contentSecurityPolicy(env.VITE_API_BASE ?? '/system', env.VITE_OPENAPI_BASE ?? '/openapi'),
    ],
    server: { port: 5173, proxy },
    // `npm run preview` serves the production build (with its CSP) through the same proxy
    preview: { port: 4173, proxy },
    test: {
      environment: 'jsdom',
    },
  }
})
