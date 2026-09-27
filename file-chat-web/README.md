# FileChat web client

React 19 + TypeScript, built with Vite and styled with Tailwind CSS v4.

## Scripts

| Command          | What it does                                                           |
| ---------------- | ---------------------------------------------------------------------- |
| `npm run dev`    | Dev server on :5173, proxying `/system` and `/openapi` to the gateways |
| `npm run build`  | Typecheck and build to `dist/`                                         |
| `npm run lint`   | ESLint                                                                 |
| `npm test`       | Unit tests (Vitest)                                                    |
| `npm run format` | Prettier, including Tailwind class sorting                             |

## Configuration

See `.env.example`. In development the Vite proxy forwards API calls, so no CORS setup is needed. To serve the built
app from another origin, set `VITE_API_BASE` / `VITE_OPENAPI_BASE` to the gateway URLs at build time and allow that
origin on the gateway with `FILECHAT_ALLOWED_ORIGINS`.

## Features

- **Sign in** with a user ID from the `system_user` table.
- **Upload a PDF** to start a conversation, with upload progress and an indexing state. Attach more PDFs to an existing
  conversation from the paperclip button.
- **Streaming answers** with a stop button. Answers render as Markdown, and citations like `[2]` become chips that
  preview the passage on hover and open it in the sources panel.
- **Sources panel** with the passages retrieved for each answer (file, page, match score, cited or not); a
  **Search** tab for semantic search without asking the model; and a **Prompt** tab showing the system prompt.
- **History sidebar** grouped by date, with text filtering, date range filters and infinite scrolling.
- **Settings**: light/dark/system theme, and streaming on or off (off uses the single-response endpoint).
- **Developer API** page: signature generator and test requests for the OpenAPI gateway.

The server does not store file names per conversation or passages per answer, so the client remembers them in
`localStorage` (answers are keyed by trace ID). Conversations opened in another browser still work, without those
extras.

## Structure

```
src/
  lib/          API client, SSE parser, citation parsing, localStorage stores, formatting
  hooks/        React Query hooks and theme handling
  components/   App shell, sidebar, upload, and ui/ primitives
    chat/       Messages, Markdown, composer, sources panel
  pages/        Login, new chat, chat, developer API
```

Design tokens live at the top of `src/index.css`: components use semantic colors (`paper`, `surface`, `ink`, `mark`,
…) so light and dark themes stay in sync. The yellow `mark` color is reserved for citations and retrieved passages.
