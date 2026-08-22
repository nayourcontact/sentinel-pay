#!/usr/bin/env sh
set -eu
AGENT_URL="${AGENT_URL:-http://localhost:8081}"
AGENT_API_KEY="${AGENT_API_KEY:-dev-secret-change-me}"
for file in docs/runbooks/*.md; do
  echo "Indexing $file"
  curl -fsS -X POST "$AGENT_URL/api/v1/knowledge/files?classification=internal-runbook" \
    -H "X-API-Key: $AGENT_API_KEY" \
    -F "file=@$file" >/dev/null
  echo "indexed"
done
