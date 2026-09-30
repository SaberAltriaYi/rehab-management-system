#!/usr/bin/env bash
# Copyright (c) 2026 杨玺龙
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${PROJECT_ROOT}/desktop/runtime/1.0.0/docker-compose.yml"
TEST_DIR="$(mktemp -d "${TMPDIR:-/tmp}/rehab-desktop-e2e.XXXXXX")"
PROJECT_NAME="rehab-desktop-e2e-$(openssl rand -hex 6)"
COMPOSE_OVERRIDE="${TEST_DIR}/compose.e2e.yml"
COMPOSE_ARGS=(--project-name "${PROJECT_NAME}" --file "${COMPOSE_FILE}" --file "${COMPOSE_OVERRIDE}")
COMPOSE_STARTED=0

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
  # Do not touch the fixed rehab-desktop-* production volumes, including on a
  # preflight failure. Only volumes created under this test's random project
  # may be removed, and only after compose up was actually attempted.
  if [[ "${COMPOSE_STARTED}" == 1 ]]; then
    docker compose "${COMPOSE_ARGS[@]}" down --remove-orphans --volumes >/dev/null 2>&1 || true
  fi
  unlink "${TLS_CERT_PATH}" 2>/dev/null || true
  unlink "${TLS_KEY_PATH}" 2>/dev/null || true
  unlink "${FIRST_START_SQL_PATH}" 2>/dev/null || true
  unlink "${MYSQL_CLIENT_CONFIG_PATH}" 2>/dev/null || true
  unlink "${TEST_DIR}/patient-auth.json" 2>/dev/null || true
  unlink "${COMPOSE_OVERRIDE}" 2>/dev/null || true
  rmdir "${TEST_DIR}" 2>/dev/null || true
}
trap cleanup EXIT

cat > "${COMPOSE_OVERRIDE}" <<EOF
services:
  server:
    image: rehab-desktop-server-e2e:${PROJECT_NAME}
  admin:
    image: rehab-desktop-admin-e2e:${PROJECT_NAME}
volumes:
  mysql-data:
    name: ${PROJECT_NAME}-mysql-data
  redis-data:
    name: ${PROJECT_NAME}-redis-data
  rehab-data:
    name: ${PROJECT_NAME}-rehab-data
  server-logs:
    name: ${PROJECT_NAME}-server-logs
EOF

for volume in mysql-data redis-data rehab-data server-logs; do
  if docker volume inspect "${PROJECT_NAME}-${volume}" >/dev/null 2>&1; then
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
# MySQL 官方入口切换为 mysql 用户后读取初始化 SQL；文件不含明文凭据。
chmod 644 "${FIRST_START_SQL_PATH}"

docker compose "${COMPOSE_ARGS[@]}" build server admin
COMPOSE_STARTED=1
docker compose "${COMPOSE_ARGS[@]}" up --detach --no-build

for attempt in $(seq 1 90); do
  states="$(docker compose "${COMPOSE_ARGS[@]}" \
    ps --format json 2>/dev/null || true)"
  healthy_count="$(
    (printf '%s' "${states}" | grep -o '"Health":"healthy"' || true) |
      wc -l |
      tr -d ' '
  )"
  if [[ "${healthy_count}" == "4" ]]; then
    break
  fi
  if [[ "${attempt}" == "90" ]]; then
    docker compose "${COMPOSE_ARGS[@]}" \
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
printf '%s' "${index_html}" | grep -q '康复管理系统'
tenant_response="$(
  curl --fail --silent --show-error \
    --cacert "${TLS_CERT_PATH}" \
    --get \
    --data-urlencode 'name=工作室内部' \
    "https://127.0.0.1:${TLS_PORT}/admin-api/system/tenant/get-id-by-name"
)"
printf '%s' "${tenant_response}" |
  jq -e '.code == 0 and .data == 1' >/dev/null

# The patient UI flag is insufficient: a fresh installer must reject both
# publicly reachable legacy authentication routes on the server itself. Send
# the synthetic tenant header so tenant validation does not mask the route's
# explicit HTTP 403 with an unrelated HTTP 200 / business-code 400 response.
for action in login bind; do
  auth_status="$(
    curl --silent --show-error \
      --cacert "${TLS_CERT_PATH}" \
      --header 'Content-Type: application/json' \
      --header 'tenant-id: 1' \
      --data '{"phone":"13800138000","bindCode":"INVALID_TEST_CODE","patientNo":"INVALID_TEST_CODE"}' \
      --output "${TEST_DIR}/patient-auth.json" \
      --write-out '%{http_code}' \
      "https://127.0.0.1:${TLS_PORT}/app-api/app-patient/auth/${action}"
  )"
  if [[ "${auth_status}" != 403 ]]; then
    echo "ERROR: patient auth ${action} returned HTTP ${auth_status}, expected 403" >&2
    # Only synthetic test input reaches this endpoint. Log the structured
    # error code/message (not data or tokens) to diagnose proxy/filter failures.
    jq -c '{code, msg}' "${TEST_DIR}/patient-auth.json" >&2 || true
    exit 1
  fi
  jq -e '.code == 403' "${TEST_DIR}/patient-auth.json" >/dev/null
done

counts="$(
  docker compose "${COMPOSE_ARGS[@]}" \
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
