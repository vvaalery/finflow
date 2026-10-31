#!/bin/bash
set -euo pipefail
BASE="${BASE_URL:-https://localhost:8443}"
if ! curl -kfsS "$BASE/actuator/health" >/tmp/finflow-health.json; then echo "Backend health check failed"; exit 1; fi
echo "Backend: OK"
if curl -kfsS -H 'Content-Type: application/json' -d '{"query":"{ companies { id name status } }"}' "$BASE/graphql" >/tmp/finflow-graphql.json; then
  echo "GraphQL: OK"
else
  echo "GraphQL request failed (authentication may be required)."
fi
echo "Verification finished."
