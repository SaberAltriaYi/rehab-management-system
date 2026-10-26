#!/usr/bin/env bash
# Isolated 001-019 bootstrap -> 020+ migration -> synthetic backup/restore smoke.
# Never use any existing Docker Compose project, database, volume, or env file.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# An explicitly selected sibling worktree can be tested without changing either
# checkout or using any of its persistent Docker/SQL resources.
PROJECT_ROOT="${REHAB_MIGRATION_SOURCE_ROOT:-$(cd "${SCRIPT_DIR}/../.." && pwd)}"
BOOTSTRAP="${REHAB_SYNTHETIC_BOOTSTRAP:-}"
MANIFEST="${PROJECT_ROOT}/deploy/internal/migrations.manifest"
IMAGE='mysql:8.4.10@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6'

command -v docker >/dev/null
command -v openssl >/dev/null

umask 077
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/rehab-upgrade-synthetic.XXXXXXXX")"
PROJECT="rehab-synth-upgrade-$(openssl rand -hex 6)"
ROOT="${STAGE}/project"
mkdir -p "${ROOT}/deploy/internal"
COMPOSE="${ROOT}/deploy/internal/docker-compose.yml"
ENV_FILE="${ROOT}/deploy/internal/.env"
COMPOSE_ARGS=(--project-name "${PROJECT}" --env-file "${ENV_FILE}" --file "${COMPOSE}")
STARTED=0

cleanup() {
  if [[ "${STARTED}" == 1 ]]; then
    # Only this newly created random project; NEVER use --volumes.
    docker compose "${COMPOSE_ARGS[@]}" down >/dev/null 2>&1 || true
  fi
  case "${STAGE}" in
    */rehab-upgrade-synthetic.*) rm -rf -- "${STAGE}" ;;
    *) echo 'FAIL: unsafe staging cleanup path' >&2 ;;
  esac
}
trap cleanup EXIT

if [[ -z "${BOOTSTRAP}" ]]; then
  # The installer's default bootstrap already includes every migration. This
  # upgrade rehearsal needs its own 001-019 fixture, never the shipped dump.
  command -v node >/dev/null
  BOOTSTRAP="${STAGE}/baseline-only.sql"
  node "${PROJECT_ROOT}/desktop/scripts/build-sanitized-bootstrap.mjs" \
    --output "${BOOTSTRAP}" --baseline-only > "${STAGE}/baseline-build.log"
fi
[[ -s "${BOOTSTRAP}" ]] || { echo 'FAIL: missing sanitized baseline SQL' >&2; exit 1; }

cp "${PROJECT_ROOT}/deploy/internal/migrate.sh" "${MANIFEST}" \
  "${PROJECT_ROOT}/deploy/internal/init-schema-history.sql" "${ROOT}/deploy/internal/"
if [[ -f "${PROJECT_ROOT}/deploy/internal/adopt_schema_guard.py" ]]; then
  cp "${PROJECT_ROOT}/deploy/internal/adopt_schema_guard.py" "${ROOT}/deploy/internal/"
fi
while IFS='|' read -r version checksum relative_file description; do
  case "${version}" in ''|'#'*) continue ;; esac
  case "${relative_file}" in
    sql/mysql/*.sql|deploy/internal/*.sql) ;;
    *) echo 'FAIL: migration file outside allowlist' >&2; exit 1 ;;
  esac
  [[ "${relative_file}" != *'..'* ]] || exit 1
  mkdir -p "${ROOT}/$(dirname "${relative_file}")"
  cp "${PROJECT_ROOT}/${relative_file}" "${ROOT}/${relative_file}"
done < "${MANIFEST}"

printf 'MYSQL_ROOT_PASSWORD=%s\nMYSQL_DATABASE=ruoyi-vue-pro\n' \
  "$(openssl rand -hex 32)" > "${ENV_FILE}"
chmod 600 "${ENV_FILE}"
cat > "${COMPOSE}" <<'YAML'
services:
  mysql:
    image: mysql:8.4.10@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6
    network_mode: none
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
    tmpfs:
      - /var/lib/mysql:size=512m
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
YAML

# Compose must have exactly one mysql service, no ports or Docker volumes.
docker compose "${COMPOSE_ARGS[@]}" config --format json |
  python3 -c 'import json,sys; c=json.load(sys.stdin); s=c["services"]; assert list(s)==["mysql"]; m=s["mysql"]; assert m.get("network_mode")=="none"; assert not m.get("ports") and not m.get("volumes") and not c.get("volumes"); assert any("/var/lib/mysql" in x and "size=512m" in x for x in m.get("tmpfs",[])); print("PASS: isolated Compose with tmpfs, no network/port/volume")'

export COMPOSE_PROJECT_NAME="${PROJECT}"
MIGRATOR="${ROOT}/deploy/internal/migrate.sh"
ENV_FILE_OVERRIDE="${ENV_FILE}" sh "${MIGRATOR}" verify-files
STARTED=1
docker compose "${COMPOSE_ARGS[@]}" up --detach --no-build mysql >/dev/null
CONTAINER="${PROJECT}-mysql-1"
[[ "$(docker inspect "${CONTAINER}" --format '{{index .Config.Labels "com.docker.compose.project"}}')" == "${PROJECT}" ]] ||
  { echo 'FAIL: mismatched container identity'; exit 1; }
[[ "$(docker inspect "${CONTAINER}" --format '{{.HostConfig.NetworkMode}}')" == 'none' ]] ||
  { echo 'FAIL: container has networking'; exit 1; }
case "$(docker inspect "${CONTAINER}" --format '{{index .HostConfig.Tmpfs "/var/lib/mysql"}}')" in
  *size=512m*) ;;
  *) echo 'FAIL: MySQL data storage is not tmpfs'; exit 1 ;;
esac

SQL_READY=0
for i in $(seq 1 90); do
  # TCP deliberately excludes the temporary MySQL initialization server.
  if docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=tcp -h127.0.0.1 -uroot -N -e "SELECT 1"' \
    2>/dev/null | grep -qx 1; then
    SQL_READY=1
    break
  fi
  sleep 1
done
[[ "${SQL_READY}" == 1 ]] || { echo 'FAIL: stable MySQL unavailable'; exit 1; }

sql() {
  docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE" -N -B --execute "$1"' \
    sql-verify "$1"
}
counts() {
  sql "SELECT CONCAT((SELECT COUNT(*) FROM rehab_patient),'|',(SELECT COUNT(*) FROM internal_schema_history));"
}

docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE" --binary-mode' < "${BOOTSTRAP}"
[[ "$(counts)" == '0|19' ]] || { echo 'FAIL: expected empty patient registry and 001-019 ledger'; exit 1; }
echo 'PASS: sanitized bootstrap restored (patients=0, ledger=19)'

if ENV_FILE_OVERRIDE="${ENV_FILE}" sh "${MIGRATOR}" status > "${STAGE}/pending.log" 2>&1; then
  echo 'FAIL: initial migration status should report pending versions'
  exit 1
fi
last_version="$(awk -F'|' '$1 ~ /^[0-9][0-9][0-9]$/ { last=$1 } END { print last }' "${MANIFEST}")"
[[ "${last_version}" =~ ^[0-9]{3}$ ]] && (( 10#${last_version} >= 23 )) ||
  { echo "FAIL: unsupported test manifest range ${last_version}"; exit 1; }
last_count=$((10#${last_version}))
incrementals="$(awk -F'|' '$1 ~ /^[0-9][0-9][0-9]$/ && ($1+0)>19 {print $1}' "${MANIFEST}")"
for version in ${incrementals}; do
  grep -q "^PENDING: ${version} " "${STAGE}/pending.log" ||
    { echo "FAIL: migration ${version} not pending"; exit 1; }
done
[[ "$(grep -c '^PENDING:' "${STAGE}/pending.log")" == "$((last_count - 19))" ]] ||
  { echo 'FAIL: unexpected pending migrations'; exit 1; }
echo "PASS: 020-${last_version} pending after baseline"

if (( last_count >= 25 )); then
  # Do not modify immutable migration SQL: stage a pre-024 manifest only for
  # this isolated disposable test fixture, then restore the original bytes.
  cp "${ROOT}/deploy/internal/migrations.manifest" "${STAGE}/original.manifest"
  awk -F'|' '$1 ~ /^[0-9][0-9][0-9]$/ && ($1+0)>=24 {next} {print}' \
    "${STAGE}/original.manifest" > "${ROOT}/deploy/internal/migrations.manifest"
  ENV_FILE_OVERRIDE="${ENV_FILE}" sh "${MIGRATOR}" apply > "${STAGE}/earlier.log" 2>&1 ||
    { tail -20 "${STAGE}/earlier.log"; exit 1; }
  [[ "$(counts)" == '0|23' ]] || { echo 'FAIL: pre-024 ledger mismatch'; exit 1; }
  cp "${STAGE}/original.manifest" "${ROOT}/deploy/internal/migrations.manifest"
  ENV_FILE_OVERRIDE="${ENV_FILE}" sh "${MIGRATOR}" verify-files

  # Deliberately create a conflicting synthetic table to assert that an
  # interrupted 024 does not falsely write history; no patient data exists.
  sql 'CREATE TABLE rehab_motion_assessment (id BIGINT PRIMARY KEY);'
  if ENV_FILE_OVERRIDE="${ENV_FILE}" sh "${MIGRATOR}" apply > "${STAGE}/expected-failure.log" 2>&1; then
    echo 'FAIL: 024 accepted a conflicting table'; exit 1
  fi
  [[ "$(counts)" == '0|23' ]] || { echo 'FAIL: conflicting 024 wrote migration ledger'; exit 1; }
  [[ "$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='rehab_motion_task';")" == 0 ]] ||
    { echo 'FAIL: failed 024 created later motion tables'; exit 1; }
  echo 'PASS: pre-existing table collision blocks 024, ledger remains at 23'
  sql 'DROP TABLE rehab_motion_assessment;' # Disposable collision created above only.
  ENV_FILE_OVERRIDE="${ENV_FILE}" sh "${MIGRATOR}" apply > "${STAGE}/later.log" 2>&1 ||
    { tail -22 "${STAGE}/later.log"; exit 1; }
  cat "${STAGE}/earlier.log" "${STAGE}/later.log" > "${STAGE}/applied.log"
  # Check the immutable 024 set, not a prefix whose cardinality grows when
  # later migrations add the independent workspace inbox (032).
  MOTION_024_TABLES="'rehab_motion_assessment','rehab_motion_task','rehab_motion_file','rehab_motion_trial','rehab_motion_metric','rehab_motion_rule_result','rehab_motion_score','rehab_motion_manual_edit','rehab_motion_ai_draft','rehab_motion_report','rehab_motion_protocol_version'"
  [[ "$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN (${MOTION_024_TABLES});")" == 11 ]] ||
    { echo 'FAIL: 024 did not create 11 tenant motion tables'; exit 1; }
  [[ "$(sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN (${MOTION_024_TABLES}) AND column_name IN ('creator','updater') AND is_nullable='YES';")" == 22 ]] ||
    { echo 'FAIL: 025 audit columns were not made nullable'; exit 1; }
  if (( last_count >= 32 )); then
    [[ "$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='rehab_motion_workspace_inbox';")" == 1 ]] ||
      { echo 'FAIL: 032 workspace inbox missing'; exit 1; }
  fi
  [[ "$(sql "SELECT COUNT(*) FROM system_menu WHERE id IN (9700,9710,9711,9712,9713,9714,9715,9716,9717);")" == 9 ]] ||
    { echo 'FAIL: 024 menu entries missing'; exit 1; }
  echo 'PASS: 024-025 created 11 tables, relaxed 22 audit fields, created 9 menus'
  if (( last_count >= 26 )); then
    [[ "$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='rehab_tenant_license_binding';")" == 1 ]] ||
      { echo 'FAIL: 026 signature-bound tenant license table missing'; exit 1; }
    [[ "$(sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rehab_tenant_license_binding' AND column_name IN ('tenant_uuid','installation_uuid','signed_lease','status_proof','revocation_epoch','trusted_time_ms');")" == 6 ]] ||
      { echo 'FAIL: 026 critical license columns missing'; exit 1; }
    sql "INSERT INTO rehab_tenant_license_binding(tenant_id,tenant_uuid,installation_uuid,signed_lease,status_proof,revocation_epoch,trusted_time_ms) VALUES(991701,'00000000-0000-0000-0000-000000099171','00000000-0000-0000-0000-000000099172','synthetic-RHL1','synthetic-RHR1',0,0);"
    [[ "$(sql "SELECT COUNT(*) FROM rehab_tenant_license_binding WHERE tenant_id=991701 AND revocation_epoch=0;")" == 1 ]] ||
      { echo 'FAIL: 026 synthetic signed-license binding insert/read mismatch'; exit 1; }
    if sql "INSERT INTO rehab_tenant_license_binding(tenant_id,tenant_uuid,installation_uuid,signed_lease,status_proof,revocation_epoch,trusted_time_ms) VALUES(991702,'00000000-0000-0000-0000-000000099171','00000000-0000-0000-0000-000000099173','synthetic-reused-id','synthetic-proof',0,0);" >/dev/null 2>&1; then
      echo 'FAIL: 026 allowed duplicate tenant UUID'; exit 1
    fi
    echo 'PASS: 026 license table, critical columns, synthetic binding and tenant UUID uniqueness'
  fi
else
  ENV_FILE_OVERRIDE="${ENV_FILE}" sh "${MIGRATOR}" apply > "${STAGE}/applied.log" 2>&1 ||
    { tail -20 "${STAGE}/applied.log"; exit 1; }
fi
for version in ${incrementals}; do
  grep -q "^APPLIED: ${version} " "${STAGE}/applied.log" ||
    { echo "FAIL: migration ${version} not applied"; exit 1; }
done
[[ "$(counts)" == "0|${last_count}" ]] || { echo 'FAIL: migration ledger/patient count mismatch'; exit 1; }
ENV_FILE_OVERRIDE="${ENV_FILE}" sh "${MIGRATOR}" status > "${STAGE}/status-final.log"
ENV_FILE_OVERRIDE="${ENV_FILE}" sh "${MIGRATOR}" apply > "${STAGE}/apply-repeat.log"
! grep -q '^APPLIED:' "${STAGE}/apply-repeat.log" ||
  { echo 'FAIL: migration re-executed on idempotent apply'; exit 1; }
[[ "$(counts)" == "0|${last_count}" ]] || { echo 'FAIL: repeat apply changed ledger'; exit 1; }
echo "PASS: actual migrate.sh applied 020-${last_version}; ${last_count} checksummed ledger rows; idempotent"

if (( last_count >= 31 )); then
  [[ "$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('crm_department_workspace','crm_department_case','crm_department_case_note','crm_department_transfer','crm_customer_pool_event');")" == 5 ]] ||
    { echo 'FAIL: 031 CRM department tables missing'; exit 1; }
  sql "INSERT INTO crm_customer (id,name,tenant_id) VALUES (9917010001,'synthetic-department-case',991701);"
  sql "INSERT INTO crm_department_case (id,tenant_id,customer_id,dept_id,claimed_by) VALUES (9917010001,991701,9917010001,101,1);"
  if sql "INSERT INTO crm_department_case (tenant_id,customer_id,dept_id,claimed_by) VALUES (991701,9917010001,102,2);" >/dev/null 2>&1; then
    echo 'FAIL: 031 allowed simultaneous ownership by two departments'; exit 1
  fi
  sql "INSERT INTO crm_department_transfer (id,tenant_id,case_id,from_dept_id,to_dept_id,requested_by,status) VALUES (9917010001,991701,9917010001,101,102,1,'PENDING');"
  if sql "INSERT INTO crm_department_transfer (tenant_id,case_id,from_dept_id,to_dept_id,requested_by,status) VALUES (991701,9917010001,101,103,1,'PENDING');" >/dev/null 2>&1; then
    echo 'FAIL: 031 allowed duplicate pending transfer'; exit 1
  fi
  sql "UPDATE crm_department_transfer SET status='REJECTED' WHERE id=9917010001;"
  sql "INSERT INTO crm_department_transfer (tenant_id,case_id,from_dept_id,to_dept_id,requested_by,status) VALUES (991701,9917010001,101,103,1,'PENDING');"
  sql "INSERT INTO crm_customer_pool_event (tenant_id,customer_id,owner_user_id,event_type) VALUES (991701,9917010001,1,'PUT');"
  [[ "$(sql "SELECT COUNT(*) FROM crm_customer_pool_event WHERE tenant_id=991701 AND event_type='PUT';")" == 1 ]] ||
    { echo 'FAIL: 031 real pool event missing'; exit 1; }
  echo 'PASS: 031 unique department ownership, one pending transfer, real pool event'
fi

# Synthetic-only backup/restore into a second database in the same tmpfs instance.
docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --no-tablespaces --single-transaction --column-statistics=0 --set-gtid-purged=OFF "$MYSQL_DATABASE"' \
  > "${STAGE}/synthetic-backup.sql"
[[ -s "${STAGE}/synthetic-backup.sql" ]] || { echo 'FAIL: empty synthetic backup'; exit 1; }
RESTORE_DB="rehab_synthetic_restored_$(openssl rand -hex 5)"
docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --execute "$1"' \
  restore-verify "CREATE DATABASE \`${RESTORE_DB}\` CHARACTER SET utf8mb4;"
docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$1" --binary-mode' \
  restore-verify "${RESTORE_DB}" < "${STAGE}/synthetic-backup.sql"
RESTORED="$(docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$1" -N -B --execute "$2"' \
  restore-verify "${RESTORE_DB}" \
  "SELECT CONCAT((SELECT COUNT(*) FROM rehab_patient),'|',(SELECT COUNT(*) FROM internal_schema_history));")"
[[ "${RESTORED}" == "0|${last_count}" ]] || { echo 'FAIL: synthetic backup/restore count mismatch'; exit 1; }
if (( last_count >= 26 )); then
  LICENSE_RESTORED="$(docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c     'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$1" -N -B --execute "$2"'     restore-verify "${RESTORE_DB}"     "SELECT COUNT(*) FROM rehab_tenant_license_binding WHERE tenant_id=991701 AND revocation_epoch=0;")"
  [[ "${LICENSE_RESTORED}" == 1 ]] || { echo 'FAIL: license binding not recovered after synthetic restore'; exit 1; }
  echo 'PASS: synthetic license binding survives backup/restore'
fi
echo "PASS: synthetic post-migration backup/restore (patients=0, ledger=${last_count})"
echo 'PASS: full isolated migration and backup/restore suite complete'
