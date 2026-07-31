#!/usr/bin/env bash
# Copyright (c) 2026 杨玺龙
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${PROJECT_ROOT}/desktop/runtime/1.0.0/docker-compose.yml"
TEST_DIR="$(mktemp -d "${TMPDIR:-/tmp}/rehab-desktop-e2e.XXXXXX")"
PROJECT_NAME="rehab-desktop-e2e"

export BIND_ADDRESS=127.0.0.1
export APP_PORT=18080
export TLS_PORT=18443
export DB_PASSWORD="$(openssl rand -hex 24)"
export MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
export REDIS_PASSWORD="$(openssl rand -hex 24)"
export TLS_CERT_PATH="${TEST_DIR}/server.crt"
export TLS_KEY_PATH="${TEST_DIR}/server.key"
export FIRST_START_SQL_PATH="${TEST_DIR}/first.sql"
export MYSQL_CLIENT_CONFIG_PATH="${TEST_DIR}/mysql-client.cnf"

cleanup() {
  docker compose --project-name "${PROJECT_NAME}" --file "${COMPOSE_FILE}" \
    down --remove-orphans >/dev/null 2>&1 || true
  for volume in \
    rehab-desktop-mysql-data \
    rehab-desktop-redis-data \
    rehab-desktop-rehab-data \
    rehab-desktop-server-logs; do
    docker volume rm "${volume}" >/dev/null 2>&1 || true
  done
  unlink "${TLS_CERT_PATH}" 2>/dev/null || true
  unlink "${TLS_KEY_PATH}" 2>/dev/null || true
  unlink "${FIRST_START_SQL_PATH}" 2>/dev/null || true
  unlink "${MYSQL_CLIENT_CONFIG_PATH}" 2>/dev/null || true
  rmdir "${TEST_DIR}" 2>/dev/null || true
}
trap cleanup EXIT

for volume in \
  rehab-desktop-mysql-data \
  rehab-desktop-redis-data \
  rehab-desktop-rehab-data \
  rehab-desktop-server-logs; do
  if docker volume inspect "${volume}" >/dev/null 2>&1; then
    echo "ERROR: refusing to run because volume already exists: ${volume}" >&2
    exit 1
  fi
done

openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -subj /CN=127.0.0.1 \
  -addext subjectAltName=IP:127.0.0.1 \
  -keyout "${TLS_KEY_PATH}" \
  -out "${TLS_CERT_PATH}" >/dev/null 2>&1
printf '%s\n' 'SELECT 1;' > "${FIRST_START_SQL_PATH}"
printf '%s\n' '[client]' 'user=root' "password=${MYSQL_ROOT_PASSWORD}" \
  > "${MYSQL_CLIENT_CONFIG_PATH}"
chmod 600 "${TEST_DIR}"/*

docker compose --project-name "${PROJECT_NAME}" --file "${COMPOSE_FILE}" \
  build server admin
docker compose --project-name "${PROJECT_NAME}" --file "${COMPOSE_FILE}" \
  up --detach --no-build

for attempt in $(seq 1 90); do
  states="$(docker compose --project-name "${PROJECT_NAME}" --file "${COMPOSE_FILE}" \
    ps --format json 2>/dev/null || true)"
  healthy_count="$(
    (printf '%s' "${states}" | rg -o '"Health":"healthy"' || true) |
      wc -l |
      tr -d ' '
  )"
  if [[ "${healthy_count}" == "4" ]]; then
    break
  fi
  if [[ "${attempt}" == "90" ]]; then
    docker compose --project-name "${PROJECT_NAME}" --file "${COMPOSE_FILE}" \
      logs --tail 120
    echo "ERROR: desktop runtime did not become healthy" >&2
    exit 1
  fi
  sleep 2
done

curl --fail --silent --show-error \
  --cacert "${TLS_CERT_PATH}" \
  "https://127.0.0.1:${TLS_PORT}/healthz" >/dev/null
index_html="$(
  curl --fail --silent --show-error \
    --cacert "${TLS_CERT_PATH}" \
    "https://127.0.0.1:${TLS_PORT}/"
)"
printf '%s' "${index_html}" | rg -q '康复管理系统'
tenant_response="$(
  curl --fail --silent --show-error \
    --cacert "${TLS_CERT_PATH}" \
    --get \
    --data-urlencode 'name=工作室内部' \
    "https://127.0.0.1:${TLS_PORT}/admin-api/system/tenant/get-id-by-name"
)"
printf '%s' "${tenant_response}" |
  jq -e '.code == 0 and .data == 1' >/dev/null

counts="$(
  docker compose --project-name "${PROJECT_NAME}" --file "${COMPOSE_FILE}" \
    exec --no-TTY mysql \
    mysql --defaults-extra-file=/run/rehab-secrets/mysql-client.cnf \
    --batch --skip-column-names ruoyi-vue-pro \
    --execute \
    "SELECT CONCAT((SELECT COUNT(*) FROM rehab_patient),'|',(SELECT COUNT(*) FROM internal_schema_history));"
)"
if [[ "${counts}" != "0|19" ]]; then
  echo "ERROR: expected patient|migration count 0|19, got ${counts}" >&2
  exit 1
fi

echo "Desktop runtime E2E passed: UI/proxy healthy, tenant=1, patient|migration=${counts}"
