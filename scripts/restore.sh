#!/bin/bash
set -euo pipefail
DUMP_FILE="${1:?Укажите .dump файл}"
if docker ps --format '{{.Names}}' | grep -q '^finflow-postgres$'; then
  cat "$DUMP_FILE" | docker exec -i finflow-postgres pg_restore -U postgres -d finance_db --clean --if-exists
else
  pg_restore -U postgres -d finance_db --clean --if-exists "$DUMP_FILE"
fi
echo "Restore completed."
