#!/usr/bin/env bash
# Exercise the actual installed Core saas_plans DDL with explicitly synthetic
# 20/400/2000 quota data. Never copy live plan rows, license or patient data.
# Never perform a transaction against the installed business MySQL.
set -Eeuo pipefail
[[ "${1:-}" == '--synthetic-only' && $# -eq 1 ]] || {
  echo 'Usage: internal-pilot-selected-plan-isolated.sh --synthetic-only' >&2
  exit 2
}
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
preview="$(node "$ROOT/desktop/scripts/internal-pilot-plan-preview.mjs" --selected-quota-draft)"
printf '%s' "$preview" | node -e '
  const fs=require("node:fs")
  const x=JSON.parse(fs.readFileSync(0,"utf8"))
  if(x.selectedQuota.maxUsers!==20 ||
     x.selectedQuota.maxPatients!==400 ||
     x.selectedQuota.motionQuota!==2000 ||
     x.selectedQuota.storageQuotaBytes!==null ||
     x.productionPlanCreationAllowed!==false ||
     x.provisionalStorageBytesForSyntheticOnly!==1073741824)process.exit(2)
'
free_kb="$(df -k "$ROOT" | awk 'NR==2{print $4}')"
[[ "$free_kb" -ge 8388608 ]] || {
  echo 'ABORT_LOW_DISK_DISPOSABLE_MYSQL' >&2; exit 3;
}
docker image inspect mysql:8.4.10 >/dev/null
NAME="rehab-pilot-quota-synthetic-$(openssl rand -hex 6)"
PASS="synthetic-$(openssl rand -hex 22)"
STARTED=0
cleanup() {
  if [[ "$STARTED" == 1 ]]; then
    docker stop "$NAME" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
docker run --rm -d --name "$NAME" --network none --tmpfs /var/lib/mysql:rw,size=768m \
  --pids-limit 256 -e "MYSQL_ROOT_PASSWORD=$PASS" mysql:8.4.10 >/dev/null
STARTED=1
mysql_test() {
  # Synthetic credential only. Keep it out of mysql's argv and test logs.
  docker exec -i -e "MYSQL_PWD=$PASS" "$NAME" mysql -uroot --batch --skip-column-names "$@"
}
ready=0
for _ in $(seq 1 50); do
  if mysql_test -e 'SELECT 1' >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
[[ "$ready" == 1 ]] || { echo 'DISPOSABLE_MYSQL_NOT_READY'; exit 3; }
mysql_test -e 'CREATE DATABASE synthetic_pilot CHARACTER SET utf8mb4'
# Transfer schema ONLY from the installed MySQL.
docker exec rehab-studio-internal-mysql-1 mysqldump \
  --defaults-extra-file=/run/rehab-secrets/mysql-client.cnf \
  --no-data --skip-comments --skip-triggers --set-gtid-purged=OFF \
  ruoyi-vue-pro saas_plans | mysql_test synthetic_pilot >/dev/null
mysql_test synthetic_pilot -e "
  INSERT INTO saas_plans(name,max_users,max_patients,motion_quota,storage_quota,status)
  VALUES('rehab-internal-pilot-20-400-2000',20,400,2000,1073741824,0);
" >/dev/null
first="$(mysql_test synthetic_pilot -e "
  SELECT CONCAT(max_users,'|',max_patients,'|',motion_quota,'|',storage_quota,'|',status)
  FROM saas_plans WHERE name='rehab-internal-pilot-20-400-2000';")"
[[ "$first" == '20|400|2000|1073741824|0' ]] || {
  echo 'INVALID_SYNTHETIC_PLAN_QUOTA'; exit 4;
}
[[ "$(mysql_test synthetic_pilot -e 'SELECT COUNT(*) FROM saas_plans WHERE status=1')" == 0 ]] || {
  echo 'UNAPPROVED_SYNTHETIC_PLAN_BECAME_ACTIVE';exit 4;
}
if mysql_test synthetic_pilot -e "
  INSERT INTO saas_plans(name,max_users,max_patients,motion_quota,storage_quota,status)
  VALUES('rehab-internal-pilot-20-400-2000',20,400,2000,1073741824,0);
" >/dev/null 2>&1; then
  echo 'DUPLICATE_PLAN_NOT_REJECTED'; exit 4;
fi
mysql_test synthetic_pilot -e "
  UPDATE saas_plans SET status=1 WHERE name='rehab-internal-pilot-20-400-2000';
" >/dev/null
[[ "$(mysql_test synthetic_pilot -e "SELECT COUNT(*) FROM saas_plans
 WHERE status=1 AND max_users=20 AND max_patients=400 AND motion_quota=2000")" == 1 ]] || {
  echo 'PLAN_NOT_ACTIVATABLE_IN_DISPOSABLE_DB'; exit 4;
}
mysql_test synthetic_pilot -e "
  UPDATE saas_plans SET status=0 WHERE name='rehab-internal-pilot-20-400-2000';
" >/dev/null
echo 'SYNTHETIC_USER_APPROVED_QUOTAS=20|400|2000'
echo 'SYNTHETIC_STORAGE_PROVISIONAL_BYTES=1073741824'
echo 'ACTUAL_SAAS_PLAN_TABLE_SCHEMA=PASS'
echo 'DUPLICATE_PLAN_NAME_REJECTED=PASS'
echo 'SYNTHETIC_ACTIVATION_THEN_DEACTIVATION=PASS'
echo 'REAL_CORE_PLAN_ROWS_UNTOUCHED=YES'
echo 'REAL_OWNER_MFA_UNTOUCHED=YES'
