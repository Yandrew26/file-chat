#!/usr/bin/env python3
"""Pretty-print the /graph/rag/stream Server-Sent Events: one tag per graph node, chat tokens inline."""
import json
import sys

DIM, CYAN, GREEN, RESET = "\033[2m", "\033[36m", "\033[32m", "\033[0m"
seen = set()

for line in sys.stdin:
    if not line.startswith("data:"):
        continue
    event = json.loads(line[5:])
    node, chunk = next(iter(event.items()))
    if node not in seen:
        seen.add(node)
        if node == "rag_chat_stream":
            print(f"\n{CYAN}[{node}]{RESET} ", flush=True)
        else:
            preview = " ".join(str(chunk).split())[:70]
            print(f"{CYAN}[{node}]{RESET} {DIM}{preview}...{RESET}", flush=True)
            continue
    if node == "rag_chat_stream":
        print(f"{GREEN}{chunk}{RESET}", end="", flush=True)
print()
