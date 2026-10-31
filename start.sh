#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  echo "[FinFlow] Docker найден. Запуск PostgreSQL + backend + frontend..."
  docker compose up --build
  exit $?
fi

echo "[FinFlow] Docker недоступен. Запускаю backend локально через Maven wrapper."
cd "$ROOT/backend"
chmod +x mvnw
if [ ! -f "src/main/resources/application.yml" ]; then echo "application.yml не найден"; exit 1; fi
./mvnw clean spring-boot:run
