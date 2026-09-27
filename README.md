# FileChat

Chat with your PDFs. FileChat splits each uploaded PDF into passages, embeds them into Elasticsearch, and answers
questions with Qwen (via DashScope), citing the passages each answer came from.

## Modules

| Module                                       | Port | Role                                                                      |
| -------------------------------------------- | ---- | ------------------------------------------------------------------------- |
| `file-chat-web`                              | 5173 | React web client                                                          |
| `file-chat-gateway`                          | 8080 | Gateway for the web client: `/system/**` → search service                 |
| `file-chat-gateway-openapi`                  | 8100 | Signed API for other programs: `/chat/**` → search, `/upload/**` → upload |
| `file-chat-service/file-chat-service-search` | 8081 | RAG chat graph (Spring AI Alibaba Graph), chat memory and history, users  |
| `file-chat-service/file-chat-service-upload` | 8082 | PDF parsing, DashScope embeddings, Elasticsearch vector store             |
| `file-chat-service/file-chat-service-auth`   | 8084 | Verifies OpenAPI signatures against `rag_auth_base`                       |

```
browser ──► web (Vite) ──► gateway :8080 ──► search :8081 ──► upload :8082 ──► Elasticsearch
                                                 │                  └────────► DashScope embeddings
                                                 ├──► DashScope chat (OpenAI-compatible)
                                                 └──► MySQL (users, history, memory)

other programs ──► openapi gateway :8100 ──► auth :8084 (verify) ──► search / upload
```

For each question the search service retrieves the closest passages from the conversation's documents, then runs a
graph that loads the prompt template and passages in parallel, streams the model's answer, and records it in the chat
history and chat memory (last 10 messages).

## Requirements

- Java 17+ and Maven 3.9+
- Node.js 20.19+
- MySQL 8
- Elasticsearch 8 with dense vector support
- A DashScope API key

## Running locally

1. Create the database and tables (this also seeds user `12345678` and API credentials `12345` / `54321`):

   ```sh
   mysql -uroot -p < sql/schema.sql
   ```

2. Configure your own credentials. Copy `.env.example` to `.env` in the repository root and fill in your DashScope
   API key and the MySQL and Elasticsearch passwords. `.env` is git-ignored; never commit it. Real environment
   variables work too and take precedence. The services refuse to start without `API_KEY`, so every deployment runs on
   its own key and its own bill.

   ```sh
   cp .env.example .env
   ```

3. Build and start the services from the repository root, so they find `.env` (each in its own terminal):

   ```sh
   mvn package -DskipTests
   java -jar file-chat-service/file-chat-service-upload/target/file-chat-service-upload-1.0.1-SNAPSHOT.jar
   java -jar file-chat-service/file-chat-service-search/target/file-chat-service-search-1.0.1-SNAPSHOT.jar
   java -jar file-chat-service/file-chat-service-auth/target/file-chat-service-auth-1.0.1-SNAPSHOT.jar
   java -jar file-chat-gateway/target/file-chat-gateway-1.0.1-SNAPSHOT.jar
   java -jar file-chat-gateway-openapi/target/file-chat-gateway-openapi-1.0.1-SNAPSHOT.jar
   ```

4. Start the web client and open http://localhost:5173:

   ```sh
   cd file-chat-web
   npm install
   npm run dev
   ```

## Cost controls

Questions, document searches and uploads call paid DashScope APIs, so the search service limits them. Defaults are
below; change them in `.env` (see `.env.example`).

| Limit                     | Default    | Variable                           |
| ------------------------- | ---------- | ---------------------------------- |
| Questions per client IP   | 10 / min   | `FILECHAT_QUESTIONS_PER_MINUTE`    |
| Searches per client IP    | 30 / min   | `FILECHAT_SEARCHES_PER_MINUTE`     |
| Uploads per client IP     | 10 / hour  | `FILECHAT_UPLOADS_PER_HOUR`        |
| Questions, all users      | 500 / day  | `FILECHAT_QUESTIONS_PER_DAY`       |
| Searches, all users       | 2000 / day | `FILECHAT_SEARCHES_PER_DAY`        |
| Uploads, all users        | 100 / day  | `FILECHAT_UPLOADS_PER_DAY`         |
| Tokens per answer         | 1500       | `FILECHAT_MAX_ANSWER_TOKENS`       |
| Passages embedded per PDF | 300        | `FILECHAT_MAX_PASSAGES_PER_UPLOAD` |

Over a limit, requests get `429` with a `Retry-After` header. Clients are identified by the address the gateway puts
in `X-Forwarded-For`, so expose only the gateways, never the services directly, and set `FILECHAT_TRUSTED_PROXIES`
if you add a reverse proxy in front of the gateway. Also set a spending limit in your DashScope console.

## Web client API

Paths are relative to the gateway (`http://localhost:8080/system`).

| Method | Path                           | Purpose                                                                                                                           |
| ------ | ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/user/{userId}`               | Sign-in lookup; 404 if the user does not exist                                                                                    |
| POST   | `/graph/create-chat`           | Multipart `userId`, `file` (PDF). Returns `{conversationId, fileName}`                                                            |
| POST   | `/graph/documents`             | Multipart `conversationId`, `file`. Adds a PDF to a conversation                                                                  |
| POST   | `/graph/rag/stream`            | JSON `{message, conversationId}`. Streams the answer as server-sent events (GET with query params also works for short questions) |
| POST   | `/graph/rag`                   | Same, as one JSON response: `{traceId, answer, sources, prompt}`                                                                  |
| GET    | `/graph/search`                | `query`, `conversationId`. Semantic search over the conversation's PDFs                                                           |
| GET    | `/history/pages`               | `userId`, `pageNum`, `pageSize`, optional `dateStart`/`dateEnd`                                                                   |
| GET    | `/history/getByConversationId` | All messages in a conversation                                                                                                    |

`/graph/rag/stream` emits these events, each with a JSON `data` payload:

| Event     | Data                                                            |
| --------- | --------------------------------------------------------------- |
| `meta`    | `{traceId, conversationId}`                                     |
| `sources` | Retrieved passages: `[{id, text, fileName, pageNumber, score}]` |
| `prompt`  | The system prompt template                                      |
| `token`   | `{text}`, one per streamed chunk of the answer                  |
| `done`    | `{traceId}`                                                     |
| `error`   | `{message}`                                                     |

Questions are limited to 4000 characters. Errors return `{message}`.

Passages are numbered in the prompt and the model cites them as `[1]`, `[2]`, in the same order as `sources`.

## OpenAPI gateway

Requests to port 8100 must send `authID` and `authorization` headers, where `authorization` is the MD5 hex digest of
`authId=<authId>&secretKey=<secretKey>`. The web client's **Developer API** page generates signatures and sends test
requests.
