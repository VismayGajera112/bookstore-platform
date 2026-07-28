#!/usr/bin/env bash
# Starts config-server first, then all domain/event services.
# Usage (from bookstore-platform):  source scripts/dev-env.sh && scripts/run-all.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p logs pids

# Load secrets/paths if the caller has not already.
# shellcheck disable=SC1091
source "$ROOT/scripts/dev-env.sh"

echo "Ensuring per-service PostgreSQL databases are up..."
docker compose up -d
for db in user-db book-db order-db payment-db notification-db analytics-db; do
  for _ in $(seq 1 30); do
    if docker compose exec -T "$db" pg_isready -U postgres >/dev/null 2>&1; then
      echo "$db is ready"
      break
    fi
    sleep 1
  done
done

start() {
  local name="$1"
  local jar
  jar="$(ls "$name"/target/"$name"-*.jar 2>/dev/null | grep -v original | head -n 1 || true)"
  if [[ -z "$jar" ]]; then
    echo "No jar for $name — run 'mvn install -DskipTests' first" >&2
    exit 1
  fi

  if [[ -f "pids/$name.pid" ]] && kill -0 "$(cat "pids/$name.pid")" 2>/dev/null; then
    echo "$name already running (pid $(cat "pids/$name.pid"))"
    return
  fi

  # Inherit CONFIG_REPO_PATH / JWT_SECRET / DB_PASSWORD from the environment.
  nohup java -jar "$jar" > "logs/$name.log" 2>&1 &
  echo $! > "pids/$name.pid"
  echo "started $name (pid $!), logging to logs/$name.log"
}

wait_healthy() {
  local name="$1" port="$2"
  for _ in $(seq 1 60); do
    if curl -fsS "http://localhost:$port/actuator/health" >/dev/null 2>&1; then
      echo "$name is up on $port"
      return 0
    fi
    sleep 1
  done
  echo "$name did not become healthy on $port; see logs/$name.log" >&2
  return 1
}

# Config Server must be up before any client boots — clients fail fast without it.
start config-server
wait_healthy config-server 8888

start user-service
start book-service
start order-service
start payment-service
start notification-service
start analytics-service

wait_healthy user-service 8081
wait_healthy book-service 8082
wait_healthy order-service 8083
wait_healthy payment-service 8084
wait_healthy notification-service 8085
wait_healthy analytics-service 8086

# Gateway starts last — it proxies to the services above.
start api-gateway
wait_healthy api-gateway 8080

echo
echo "API Gateway (front door): http://localhost:8080"
echo "Config Server serving:    curl -s $CONFIG_SERVER_URL/user-service/default | head"
echo "Demo property:            curl -s localhost:8080/api/config/demo -H 'Authorization: Bearer \$TOKEN'"
echo "Kafka UI:                 http://localhost:8090"
