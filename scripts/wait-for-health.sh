#!/usr/bin/env bash
# Waits until the backend reports healthy (used by make targets).
set -euo pipefail
API="${1:-http://localhost:8080}"
for _ in $(seq 1 90); do
  if curl -sf "${API}/actuator/health/readiness" | grep -q UP; then
    echo "Backend is up at ${API}"
    exit 0
  fi
  sleep 2
done
echo "Backend did not become healthy at ${API}" >&2
exit 1
