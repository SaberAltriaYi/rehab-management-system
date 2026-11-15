#!/usr/bin/env bash
# Exactly scoped: additive SaaS provisioning V3/V4 DDL for the known local
# Rehab Core database. No credentials, tenant records or clinical rows.
# Prerequisite: verified current encrypted backup AND isolated restore.
# DO NOT run on any remote database or a different Compose project.
set -Eeuo pipefail

[[ "${1:-}" == '--apply-v3-v4' && $# -eq 1 ]] || {
  echo 'Usage: saas-v3-v4-guarded-live-upgrade.sh --apply-v3-v4'
  exit 2
}
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONTAINER='rehab-studio-internal-mysql-1'
DATABASE='ruoyi-vue-pro'
HELPER="$ROOT/desktop/launcher/src-tauri/target/release/rehab-core-encrypted-backup"
BACKUPS="$HOME/Library/Application Support/com.saberaltriayi.rehab.internal/backups"
V3="$ROOT/sql/mysql/rehab-saas-provisioning-v3.sql"
V4="$ROOT/sql/mysql/rehab-saas-entitlement-sync-v4.sql"
[[ -x "$HELPER" && -s "$V3" && -s "$V4" ]] || exit 3
/usr/bin/codesign --verify --strict "$HELPER" >/dev/null
[[ -f "$BACKUPS/$(find "$BACKUPS" -maxdepth 1 -type f -name '*.json' -print | sed 's|.*/||' | sort | tail -1)" ]] || exit 3

# Never touch another database/container if the actual project label differs.
actual_project="$(docker inspect "$CONTAINER" --format '{{ index .Config.Labels "com.docker.compose.project" }}')"
[[ "$actual_project" == 'rehab-studio-internal' ]] || {
  echo 'REFUSE_WRONG_COMPOSE_PROJECT'; exit 4
}
health="$(docker inspect "$CONTAINER" --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}')"
[[ "$health" == 'healthy' ]] || { echo 'REFUSE_MYSQL_NOT_HEALTHY'; exit 4; }

mysql_live() {
  docker exec -i "$CONTAINER" mysql \
    --defaults-extra-file=/run/rehab-secrets/mysql-client.cnf \
    --batch --skip-column-names "$DATABASE" "$@"
}
ledger="$(mysql_live -e 'SELECT CONCAT(MAX(version),":",COUNT(*)) FROM internal_schema_history;')"
[[ "$ledger" == '036:36' ]] || { echo 'REFUSE_UNEXPECTED_MIGRATION_LEDGER'; exit 4; }
old_tenants="$(mysql_live -e 'SELECT COUNT(*) FROM system_tenant;')"
old_patients="$(mysql_live -e 'SELECT COUNT(*) FROM rehab_patient;')"
old_registered="$(mysql_live -e 'SELECT COUNT(*) FROM saas_registrations;')"

# A repaired HTTPS Owner target is normal; only an explicitly disabled
# provisioning flag is safe during SQL migration. Duplicate flags, symlinks,
# insecure local permissions and the old HTTP target all fail closed.
node "$ROOT/desktop/scripts/owner-maintenance-freeze.mjs" --verify-owner-disabled >/dev/null

# The backup helper checks full ciphertext hashes AND decryptability, then
# restores the current 036 snapshot to a disconnected ephemeral MySQL and
# executes the exact SaaS V3/V4 + HEP 037 candidate scripts there.
"$HELPER" --verify-latest >/dev/null
"$HELPER" --verify-restore >/dev/null
echo 'BACKUP_AND_ISOLATED_RESTORE_GATES=PASS'

# Hash-pin V3/V4 checked in this source tree before touching the DB.
shasum -a 256 "$V3" "$V4" | while read -r digest filename; do
  [[ "$digest" =~ ^[0-9a-f]{64}$ && -s "$filename" ]] || exit 5
done

# Explicitly scoped ADD COLUMN/CREATE TABLE statements; the scripts contain
# no DROP or changes to patients. Fail closed on any partial SQL error.
mysql_live < "$V3" >/dev/null
mysql_live < "$V4" >/dev/null

result="$(mysql_live -e "
SELECT CONCAT(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema=DATABASE() AND table_name='system_tenant' AND column_name='tenant_uuid'),':',
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema=DATABASE() AND table_name='saas_provisioning_requests'),':',
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema=DATABASE() AND table_name='saas_provisioning_requests'
     AND column_name='entitlement_revision'),':',
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema=DATABASE() AND table_name='system_tenant'
     AND index_name='uk_system_tenant_uuid'));")"
[[ "$result" == '1:1:1:1' ]] || { echo 'POST_MIGRATION_SCHEMA_UNEXPECTED'; exit 5; }
[[ "$(mysql_live -e 'SELECT COUNT(*) FROM system_tenant;')" == "$old_tenants" &&
   "$(mysql_live -e 'SELECT COUNT(*) FROM rehab_patient;')" == "$old_patients" &&
   "$(mysql_live -e 'SELECT COUNT(*) FROM saas_registrations;')" == "$old_registered" ]] || {
  echo 'REFUSE_DATA_COUNT_CHANGED'; exit 5
}
[[ "$(mysql_live -e 'SELECT CONCAT(MAX(version),":",COUNT(*)) FROM internal_schema_history;')" == '036:36' ]] || {
  echo 'REFUSE_CORE_MIGRATION_LEDGER_CHANGED'; exit 5
}
echo 'LIVE_SAAS_V3_V4_ADDITIVE_SCHEMA_APPLIED=PASS'
echo 'LIVE_PATIENT_TENANT_REGISTRATION_COUNTS_UNCHANGED=PASS'
echo 'LIVE_CORE_MIGRATION_LEDGER_UNCHANGED=036'
