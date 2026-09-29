#!/usr/bin/env bash
# Start all five FileChat services (and, with --stub, the offline LLM stub) in the background.
# Logs go to ./logs/<service>.log.   Stop everything with:  demo/start-services.sh --stop
#
# Real models:  export API_KEY=<your DashScope key>   then run without --stub.
# Offline:      demo/start-services.sh --stub         (no API key needed, see demo/llm-stub)
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p logs

SERVICES=(file-chat-service/file-chat-service-auth file-chat-service/file-chat-service-upload
          file-chat-service/file-chat-service-search file-chat-gateway file-chat-gateway-openapi)

if [[ "${1:-}" == "--stop" ]]; then
  for f in logs/*.pid; do [[ -e "$f" ]] && kill "$(cat "$f")" 2>/dev/null; rm -f "$f"; done
  echo "stopped"; exit 0
fi

# Credentials matching deploy/docker-compose.yml (override as needed).
export MySQL_PASSWORD=${MySQL_PASSWORD:-filechat}
export NEO4J_PASSWORD=${NEO4J_PASSWORD:-filechat123}
export SPRING_ELASTICSEARCH_PASSWORD=${SPRING_ELASTICSEARCH_PASSWORD:-filechat}

if [[ "${1:-}" == "--stub" ]]; then
  export API_KEY=${API_KEY:-offline-stub}
  export SPRING_AI_OPENAI_BASE_URL=http://localhost:8090/compatible-mode
  export SPRING_AI_DASHSCOPE_BASE_URL=http://localhost:8090/
  python3 demo/llm-stub/llm_stub.py 8090 > logs/llm-stub.log 2>&1 &
  echo $! > logs/llm-stub.pid
fi
: "${API_KEY:?Set API_KEY to your DashScope key, or pass --stub}"

for module in "${SERVICES[@]}"; do
  name=$(basename "$module")
  java -jar "$module/target/$name-1.0.1-SNAPSHOT.jar" > "logs/$name.log" 2>&1 &
  echo $! > "logs/$name.pid"
done

echo "waiting for services..."
for module in "${SERVICES[@]}"; do
  name=$(basename "$module")
  until grep -qE 'Started [A-Za-z]+Application|APPLICATION FAILED' "logs/$name.log"; do sleep 1; done
  printf '  %-28s %s\n' "$name" "$(grep -oE 'Started [A-Za-z]+Application in [0-9.]+ seconds|APPLICATION FAILED TO START' "logs/$name.log")"
done
