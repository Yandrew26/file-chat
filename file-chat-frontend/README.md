# File Chat UI

A small dependency-free web UI for the File Chat backend. It demonstrates every user-facing feature:

| UI | Backend |
| --- | --- |
| Upload a PDF (starts a conversation) | `POST /chat/graph/create-chat` → upload service → Elasticsearch |
| Streaming chat | `GET /chat/graph/rag/stream` (SSE) |
| **Pipeline** panel: author extraction, **Neo4j lookup**, vector search, prompt, answer | SSE events `author_extraction_node`, `neo4j_search_node`, `vector_search_stream`, `prompt_template_stream`, `rag_chat_stream` |
| "More by the same authors" card + knowledge-graph view | `neo4j_search_node` event (`"Author: X - Book: Y"` lines) |
| Conversation list / transcript | `GET /chat/history/pages`, `GET /chat/history/getByConversationId` |
| **Author explorer** | `GET /chat/test/books/byAuthor`, `GET /chat/test/getAuthor` |

## Run

Start the backend (MySQL, Elasticsearch, Neo4j, and the five Spring Boot services), then:

```bash
AUTH_ID=<auth_id from rag_auth_base> AUTH_SECRET=<its secret_key> node server.mjs
# http://localhost:5173
```

`server.mjs` serves `public/` and proxies `/api/*` to the auth gateway (`GATEWAY_URL`, default
`http://localhost:8100`), adding the `authID` / `authorization` headers
(`md5("authId=<id>&secretKey=<secret>")`). The secret stays on the server; the browser never sees it.

Node 18+ is enough; there is no build step and no npm dependencies.
