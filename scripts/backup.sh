#!/bin/bash
set -euo pipefail
BACKUP_DIR="${1:-./backups}"
mkdir -p "$BACKUP_DIR"
STAMP=$(date +%Y%m%d_%H%M%S)

if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -q '^finflow-postgres$'; then
  docker exec finflow-postgres pg_dump -U postgres -d finance_db -Fc > "$BACKUP_DIR/finance_db_${STAMP}.dump"
else
  pg_dump -U postgres -d finance_db -Fc > "$BACKUP_DIR/finance_db_${STAMP}.dump"
fi

echo "Backup: $BACKUP_DIR/finance_db_${STAMP}.dump"
