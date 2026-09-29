#!/usr/bin/env bash
# Guided walkthrough of FileChat through the public (authenticated) gateway.
# Prerequisites: infrastructure + all services running (see README "Run it locally").
set -euo pipefail
cd "$(dirname "$0")/.."

GW=${GW:-http://localhost:8100}
AUTH_ID=${AUTH_ID:-12345}
SECRET=${SECRET:-54321}
USER_ID=${USER_ID:-12345678}
PAUSE=${PAUSE:-1.2}

B="\033[1m"; Y="\033[33m"; M="\033[35m"; D="\033[2m"; R="\033[0m"
say()  { printf "\n${B}${Y}# %s${R}\n" "$*"; sleep "$PAUSE"; }
show() { printf "${M}\$${R} %s\n" "$*"; sleep 0.6; }

say "1. Every public request goes through the OpenAPI gateway (:8100), which asks the auth service to verify it"
show "curl -i \$GW/chat/history/pages?..."
curl -s -o /dev/null -w "HTTP %{http_code}  <- no credentials\n" "$GW/chat/history/pages?pageNum=1&pageSize=5&userId=$USER_ID"
show "curl -H 'authID: $AUTH_ID' -H 'authorization: not-a-real-signature' ..."
curl -s -o /dev/null -w "HTTP %{http_code}  <- bad signature\n" -H "authID: $AUTH_ID" -H "authorization: not-a-real-signature" \
  "$GW/chat/history/pages?pageNum=1&pageSize=5&userId=$USER_ID"

say "2. A valid signature is md5(\"authId=<id>&secretKey=<secret>\")"
show "SIG=\$(printf 'authId=$AUTH_ID&secretKey=$SECRET' | md5sum | cut -d' ' -f1)"
SIG=$(printf 'authId=%s&secretKey=%s' "$AUTH_ID" "$SECRET" | md5sum | cut -d' ' -f1)
echo "$SIG"
H=(-H "authID: $AUTH_ID" -H "authorization: $SIG")

say "3. Start a chat by uploading a PDF: it is split into chunks, embedded and indexed in Elasticsearch"
show "curl -X GET \"\$GW/chat/graph/create-chat?userId=$USER_ID\" --data-binary @demo/sample/rag-field-notes.pdf"
RESP=$(curl -s "${H[@]}" -X GET -H 'Content-Type: application/octet-stream' \
  --data-binary @demo/sample/rag-field-notes.pdf "$GW/chat/graph/create-chat?userId=$USER_ID")
echo "$RESP"
CID=${RESP##*: }

ask() {
  say "$1"
  show "curl -N \"\$GW/chat/graph/rag/stream?conversationId=\$CID&message=$2\" | python3 demo/sse_pretty.py"
  curl -sN "${H[@]}" --get --data-urlencode "message=$2" --data-urlencode "conversationId=$CID" \
    "$GW/chat/graph/rag/stream" | python3 demo/sse_pretty.py
}

ask "4. Ask a question - the graph runs vector search + author extraction -> Neo4j, then streams the answer" \
    "How large should chunks be?"
ask "5. Ask another - answers are grounded in the PDF, and Neo4j adds other books by the same authors" \
    "Why add a knowledge graph?"

say "6. Every turn is saved in MySQL: fetch the conversation history"
show "curl \"\$GW/chat/history/getByConversationId?conversationId=\$CID\""
curl -s "${H[@]}" "$GW/chat/history/getByConversationId?conversationId=$CID" | python3 -c '
import json, sys
for row in json.load(sys.stdin):
    when, kind, text = row["createdDate"], row["type"], " ".join(row["content"].split())
    print(when, " ", kind.ljust(9), text[:78] + ("..." if len(text) > 78 else ""))'

printf "\n${B}${Y}# Done - conversation ${CID}${R}\n"
