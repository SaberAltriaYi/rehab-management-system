#!/usr/bin/env bash
# Run Core HEP 037 on a disposable, network-isolated MySQL with schema-only
# copies of existing Core role/menu tables. Never loads user/patient rows.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SOURCE="$ROOT/sql/mysql/rehab-hep-core-delivery-v1.sql"
EXPECTED='2943301355afbeaa3573a0693b96229e74b6cb931e39df85ba39d60c9c13077d'
NAME='rehab-hep-037-dryrun-p0-20261112'
PASS='synthetic-hep-037-ephemeral'

actual="$(shasum -a 256 "$SOURCE" | cut -d' ' -f1)"
[[ "$actual" == "$EXPECTED" ]] || { echo 'HEP_037_SHA256_MISMATCH'; exit 2; }
if docker container inspect "$NAME" >/dev/null 2>&1; then
  echo 'DISPOSABLE_NAME_ALREADY_EXISTS'
  exit 2
fi
cleanup() { docker stop "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker image inspect mysql:8.4.10 >/dev/null
docker run --rm -d --name "$NAME" --network none \
  --tmpfs /var/lib/mysql:rw,size=768m --pids-limit 256 \
  -e "MYSQL_ROOT_PASSWORD=$PASS" mysql:8.4.10 >/dev/null
ready=0
for _ in $(seq 1 50); do
  if docker exec "$NAME" mysql -uroot "-p$PASS" --batch --skip-column-names \
    -e 'SELECT 1' >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
[[ "$ready" == 1 ]] || { echo 'DISPOSABLE_MYSQL_NOT_READY'; exit 3; }
mysql_test() {
  docker exec -i "$NAME" mysql -uroot "-p$PASS" --batch --skip-column-names "$@"
}
mysql_test -e 'CREATE DATABASE hep_037_synthetic CHARACTER SET utf8mb4'

# Schema-only export of exactly these three metadata tables. The source
# command is read-only, never copies permissions/role assignments or PII.
docker exec rehab-studio-internal-mysql-1 mysqldump \
  --defaults-extra-file=/run/rehab-secrets/mysql-client.cnf \
  --no-data --skip-comments --skip-triggers --set-gtid-purged=OFF \
  ruoyi-vue-pro system_menu system_role system_role_menu \
  | mysql_test hep_037_synthetic >/dev/null
echo 'LIVE_CORE_ROLE_MENU_DDL_IMPORTED_WITHOUT_ROWS=YES'

mysql_test hep_037_synthetic < "$SOURCE" >/dev/null
read -r table_count menu_count role_grant_count <<< "$(
  mysql_test hep_037_synthetic -e "
    SELECT
      (SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema=DATABASE() AND
       (table_name LIKE 'rehab_hep_%' OR table_name='rehab_patient_app_invitation')),
      (SELECT COUNT(*) FROM system_menu WHERE id BETWEEN 98900 AND 98909),
      (SELECT COUNT(*) FROM system_role_menu WHERE menu_id BETWEEN 98900 AND 98909)"
)"
[[ "$table_count" == 11 && "$menu_count" == 10 && "$role_grant_count" == 0 ]] ||
  { echo 'HEP_037_SCHEMA_OR_PERMISSION_CONTRACT_FAILED'; exit 4; }
echo 'HEP_037_SCHEMA_MENU_COUNT=PASS'
echo 'HEP_037_ROLE_GRANTS_WITHOUT_ACTUAL_ROLES=0'
echo 'HEP_037_REAL_DATABASE_UNTOUCHED=YES'
