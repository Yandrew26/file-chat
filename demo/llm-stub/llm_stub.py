#!/usr/bin/env python3
"""
Offline stand-in for the DashScope / Qwen endpoints used by FileChat.

It lets the whole pipeline (upload -> embed -> Elasticsearch kNN -> author
extraction -> Neo4j -> graph -> SSE) run end to end without an API key.
It is NOT a language model:

  * Embeddings  - deterministic hashed bag-of-words vectors (1536 dims), so
                  chunks that share words with the question really do rank
                  higher in Elasticsearch.
  * Chat        - extractive: picks the sentences from the retrieved chunks
                  that overlap most with the question, cites them, and appends
                  the Neo4j "same author" recommendations. Author extraction
                  is a regex over "by ..." / "Authors: ..." lines.

Endpoints (both services only need their base-url pointed here):
  POST /compatible-mode/v1/chat/completions                  (OpenAI format, stream + non-stream)
  POST /api/v1/services/embeddings/text-embedding/text-embedding  (DashScope format)

Usage:  python3 llm_stub.py [port]   (default 8090)
"""
import hashlib
import json
import math
import re
import sys
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DIMS = 1536
STREAM_DELAY_SECONDS = 0.035
STOPWORDS = set("""
a an and are as at be by for from has have how i in is it its of on or that the this to was were what
when where which who why will with you your do does did can could should would about into than then
there these those they them their our we us me my not no yes if so but also just only very more most
""".split())
NAME = r"[A-Z][a-z]+(?:\s+[A-Z]\.)?\s+[A-Z][a-z]+"


def tokens(text):
    return [t for t in re.findall(r"[a-z0-9]+", text.lower()) if t not in STOPWORDS and len(t) > 1]


def embed(text):
    vec = [0.0] * DIMS
    for tok in tokens(text):
        h = int(hashlib.md5(tok.encode()).hexdigest(), 16)
        vec[h % DIMS] += 1.0 if (h >> 12) & 1 else -1.0
    vec[0] += 0.01  # never a zero vector (cosine similarity needs a norm)
    norm = math.sqrt(sum(v * v for v in vec))
    return [v / norm for v in vec]


def section(system_text, header):
    """Return the text under a '**HEADER ...**' block of the prompt template."""
    m = re.search(r"\*\*" + re.escape(header) + r"[^*]*\*\*:?\s*(.*?)(?=\n\*\*[A-Z0-9 ]+|\Z)", system_text, re.S)
    return m.group(1).strip() if m else ""


def split_sentences(text):
    text = re.sub(r"\s+", " ", text.strip("[] \n"))
    return [s.strip(" ,[]") for s in re.split(r"(?<=[.!?])\s+", text) if len(s.strip()) > 25]


def answer_question(question, system_text):
    retrieved = section(system_text, "VECTOR SEARCH RESULTS")
    neo4j = section(system_text, "NEO4J SEARCH RESULTS")
    q = set(tokens(question))

    scored = []
    for i, sentence in enumerate(split_sentences(retrieved)):
        overlap = len(q & set(tokens(sentence)))
        if overlap and not sentence.endswith("?"):  # headings and questions are not answers
            scored.append((overlap, -i, sentence))
    scored.sort(reverse=True)
    cutoff = scored[0][0] * 0.6 if scored else 0
    best = sorted([s for s in scored[:3] if s[0] >= cutoff], key=lambda s: -s[1])

    if not best:
        body = ("Based on the available documents, I cannot find specific information about that. "
                "Try asking about something covered in the uploaded file.")
    else:
        body = "Here is what the documents say:\n\n" + "\n".join(
            f"- {s[2]} [Doc: uploaded file]" for s in best)

    recs = re.findall(r"Author: (.+?) - Book: (.+?)\s*(?=\n|$)", neo4j)
    if recs:
        body += "\n\nYou might also like other works by the same authors:\n" + "\n".join(
            f"- *{title.strip()}* by {author.strip()}" for author, title in recs)
    return body


def extract_authors(prompt):
    prompt = re.sub(r"\s+", " ", prompt)  # PDF text keeps layout spacing ("Ada   Park")
    names = []
    for group in re.findall(r"(?:\bby|Authors?:)\s+(" + NAME + r"(?:,?\s+(?:and\s+)?" + NAME + r")*)", prompt):
        names += re.findall(NAME, group)
    seen = []
    for n in names:
        if n not in seen:
            seen.append(n)
    return ", ".join(seen)


def complete(messages):
    system = "\n".join(m.get("content") or "" for m in messages if m.get("role") == "system")
    users = [m.get("content") or "" for m in messages if m.get("role") == "user"]
    last_user = users[-1] if users else ""
    if last_user.startswith("Based on the following documents, extract all author names"):
        return extract_authors(last_user)
    return answer_question(last_user, system)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("[llm-stub] " + (fmt % args) + "\n")

    def _json(self, obj, status=200):
        body = json.dumps(obj).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _body(self):
        if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
            data = b""
            while True:
                size = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                if size == 0:
                    self.rfile.readline()
                    return data
                data += self.rfile.read(size)
                self.rfile.readline()
        return self.rfile.read(int(self.headers.get("Content-Length", 0)))

    def do_POST(self):
        payload = json.loads(self._body() or b"{}")
        if "embedding" in self.path:
            texts = payload.get("input", {}).get("texts", [])
            self._json({
                "request_id": str(uuid.uuid4()),
                "output": {"embeddings": [{"text_index": i, "embedding": embed(t)} for i, t in enumerate(texts)]},
                "usage": {"total_tokens": sum(len(tokens(t)) for t in texts)},
            })
        elif self.path.endswith("/chat/completions"):
            self._chat(payload)
        else:
            self._json({"error": f"unknown path {self.path}"}, 404)

    def _chat(self, payload):
        text = complete(payload.get("messages", []))
        cid, model, created = "chatcmpl-" + uuid.uuid4().hex[:12], payload.get("model", "qwen-max"), int(time.time())
        usage = {"prompt_tokens": 0, "completion_tokens": len(text.split()), "total_tokens": len(text.split())}
        if not payload.get("stream"):
            self._json({"id": cid, "object": "chat.completion", "created": created, "model": model,
                        "choices": [{"index": 0, "finish_reason": "stop",
                                     "message": {"role": "assistant", "content": text}}],
                        "usage": usage})
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()

        def send(delta, finish=None):
            chunk = {"id": cid, "object": "chat.completion.chunk", "created": created, "model": model,
                     "choices": [{"index": 0, "delta": delta, "finish_reason": finish}]}
            self.wfile.write(f"data: {json.dumps(chunk)}\n\n".encode())
            self.wfile.flush()

        send({"role": "assistant", "content": ""})
        for piece in re.findall(r"\S+\s*", text):
            send({"content": piece})
            time.sleep(STREAM_DELAY_SECONDS)
        send({}, "stop")
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()
        self.close_connection = True


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8090
    print(f"[llm-stub] listening on http://localhost:{port}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
