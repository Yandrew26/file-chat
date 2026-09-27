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

2. Configure Elasticsearch in `file-chat-service/file-chat-service-upload/src/main/resources/application.yaml` and set:

   ```sh
   export MySQL_PASSWORD=...   # MySQL root password
   export API_KEY=...          # DashScope API key
   ```

3. Build and start the services (each in its own terminal):

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

## Web client API

Paths are relative to the gateway (`http://localhost:8080/system`).

| Method | Path                           | Purpose                                                                 |
| ------ | ------------------------------ | ----------------------------------------------------------------------- |
| GET    | `/user/{userId}`               | Sign-in lookup; 404 if the user does not exist                          |
| POST   | `/graph/create-chat`           | Multipart `userId`, `file` (PDF). Returns `{conversationId, fileName}`  |
| POST   | `/graph/documents`             | Multipart `conversationId`, `file`. Adds a PDF to a conversation        |
| GET    | `/graph/rag/stream`            | `message`, `conversationId`. Streams the answer as server-sent events   |
| GET    | `/graph/rag`                   | Same, as one JSON response: `{traceId, answer, sources, prompt}`        |
| GET    | `/graph/search`                | `query`, `conversationId`. Semantic search over the conversation's PDFs |
| GET    | `/history/pages`               | `userId`, `pageNum`, `pageSize`, optional `dateStart`/`dateEnd`         |
| GET    | `/history/getByConversationId` | All messages in a conversation                                          |

`/graph/rag/stream` emits these events, each with a JSON `data` payload:

| Event     | Data                                                            |
| --------- | --------------------------------------------------------------- |
| `meta`    | `{traceId, conversationId}`                                     |
| `sources` | Retrieved passages: `[{id, text, fileName, pageNumber, score}]` |
| `prompt`  | The system prompt template                                      |
| `token`   | `{text}`, one per streamed chunk of the answer                  |
| `done`    | `{traceId}`                                                     |
| `error`   | `{message}`                                                     |

Passages are numbered in the prompt and the model cites them as `[1]`, `[2]`, in the same order as `sources`.

## OpenAPI gateway

Requests to port 8100 must send `authID` and `authorization` headers, where `authorization` is the MD5 hex digest of
`authId=<authId>&secretKey=<secretKey>`. The web client's **Developer API** page generates signatures and sends test
requests.
