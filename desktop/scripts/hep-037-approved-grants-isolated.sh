#!/usr/bin/env bash
# B decision: validate the existing 037 grant policy (admin 10, therapist 8)
# in disposable MySQL. No production role rows, patient data or persistent
# Docker volumes are copied or modified.
set -Eeuo pipefail
[[ "${1:-}" == '--synthetic-only' && $# -eq 1 ]] || {
  echo 'Usage: hep-037-approved-grants-isolated.sh --synthetic-only' >&2
  exit 2
}
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SOURCE="$ROOT/sql/mysql/rehab-hep-core-delivery-v1.sql"
EXPECTED='2943301355afbeaa3573a0693b96229e74b6cb931e39df85ba39d60c9c13077d'
[[ "$(shasum -a 256 "$SOURCE" | awk '{print $1}')" == "$EXPECTED" ]] || {
  echo 'REFUSE_CHANGED_HEP_037_MIGRATION' >&2; exit 2;
}
docker image inspect mysql:8.4.10 >/dev/null
NAME="rehab-hep-grants-synthetic-$(openssl rand -hex 6)"
PASS="synthetic-$(openssl rand -hex 22)"
STARTED=0
cleanup() {
  if [[ "$STARTED" == 1 ]]; then
    docker stop "$NAME" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
docker run --rm -d --name "$NAME" --network none \
  --tmpfs /var/lib/mysql:rw,size=768m --pids-limit 256 \
  -e "MYSQL_ROOT_PASSWORD=$PASS" mysql:8.4.10 >/dev/null
STARTED=1
mysql_test() {
  docker exec -i "$NAME" mysql -uroot "-p$PASS" \
    --batch --skip-column-names "$@"
}
ready=0
for _ in $(seq 1 50); do
  if mysql_test -e 'SELECT 1' >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
[[ "$ready" == 1 ]] || { echo 'DISPOSABLE_MYSQL_NOT_READY'; exit 3; }
mysql_test -e 'CREATE DATABASE synthetic_hep_037 CHARACTER SET utf8mb4'
# Schema only. This command cannot copy production roles, menu assignments,
# employees, patient records or credentials.
docker exec rehab-studio-internal-mysql-1 mysqldump \
  --defaults-extra-file=/run/rehab-secrets/mysql-client.cnf \
  --no-data --skip-comments --skip-triggers --set-gtid-purged=OFF \
  ruoyi-vue-pro system_role system_menu system_role_menu \
  | mysql_test synthetic_hep_037 >/dev/null
mysql_test synthetic_hep_037 -e "
  INSERT INTO system_menu (id,name,permission,type,sort,parent_id)
  VALUES (9000,'Synthetic rehab parent','',1,1,0);
  INSERT INTO system_role (id,name,code,sort,status,type,tenant_id)
  VALUES
    (1,'Synthetic admin','super_admin',1,0,1,1),
    (2,'Synthetic therapist','rehab_therapist',2,0,1,1),
    (3,'Other tenant admin','super_admin',1,0,1,2),
    (4,'Other tenant therapist','rehab_therapist',2,0,1,2);
" >/dev/null
mysql_test synthetic_hep_037 < "$SOURCE" >/dev/null
actual="$(mysql_test synthetic_hep_037 -e "
 SELECT CONCAT(
   (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND (table_name LIKE 'rehab_hep_%' OR table_name='rehab_patient_app_invitation')),'|',
   (SELECT COUNT(*) FROM system_menu WHERE id BETWEEN 98900 AND 98909),'|',
   (SELECT COUNT(*) FROM system_role_menu WHERE role_id=1 AND tenant_id=1),'|',
   (SELECT COUNT(*) FROM system_role_menu WHERE role_id=2 AND tenant_id=1),'|',
   (SELECT COUNT(*) FROM system_role_menu WHERE tenant_id=2),'|',
   (SELECT COUNT(*) FROM system_role_menu WHERE role_id=2 AND menu_id IN (98903,98909)),'|',
   (SELECT GROUP_CONCAT(menu_id ORDER BY menu_id) FROM system_role_menu WHERE role_id=1),'|',
   (SELECT GROUP_CONCAT(menu_id ORDER BY menu_id) FROM system_role_menu WHERE role_id=2),'|',
   (SELECT COUNT(*) FROM system_role_menu WHERE menu_id NOT BETWEEN 98900 AND 98909)
 );")"
expected='11|10|10|8|0|0|98900,98901,98902,98903,98904,98905,98906,98907,98908,98909|98900,98901,98902,98904,98905,98906,98907,98908|0'
[[ "$actual" == "$expected" ]] || {
  echo 'FAIL_037_GRANTS_NOT_EXACTLY_AUTHORIZED_SCOPE' >&2; exit 4;
}
echo 'SYNTHETIC_037_MENUS=10'
echo 'SYNTHETIC_037_NEW_HEP_TABLES=11'
echo 'SYNTHETIC_TENANT1_ADMIN_GRANTS=10'
echo 'SYNTHETIC_TENANT1_THERAPIST_GRANTS=8'
echo 'SYNTHETIC_TENANT2_GRANTS=0'
echo 'THERAPIST_REVIEW_ASSET_AND_INTEGRATION_ADMIN_GRANTS=0'
echo 'NO_ROLE_PERMISSIONS_OUTSIDE_HEP_RANGE=YES'
echo '037_MIGRATION_SHA256_PINNED=YES'
echo 'REAL_CORE_DATABASE_UNTOUCHED=YES'
