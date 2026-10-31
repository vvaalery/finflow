#!/bin/bash
set -euo pipefail
BASE="${BASE_URL:-https://localhost:8443}"
CURL="curl -k -s -o /dev/null -w %{time_total}"
echo "Health: $($CURL "$BASE/actuator/health") sec"
echo "GraphQL companies: $($CURL -H 'Content-Type: application/json' -d '{"query":"{ companies { id name status } }"}' "$BASE/graphql") sec"
