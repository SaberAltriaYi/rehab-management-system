#!/usr/bin/env bash
# Disposable, fully network-disconnected MySQL 8.4 test.
# --live-schema reads ONLY the existing system_tenant DDL, never records.
# No production database/volume mounts, no business data or private keys.
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NAME="rehab-saas-schema-synthetic-20261112"
IMAGE="mysql:8.4.10"
PASS="synthetic-disposable-mysql-only"
MODE="${1:---synthetic-schema}"
if [[ "$MODE" != '--synthetic-schema' && "$MODE" != '--live-schema' ]]; then
  echo 'Usage: saas-v3-v4-isolated-rehearsal.sh [--synthetic-schema|--live-schema]'
  exit 2
fi

if docker container inspect "$NAME" >/dev/null 2>&1; then
  echo 'REFUSE: a container with the isolated test name already exists'
  exit 2
fi
cleanup() {
  docker stop "$NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker image inspect "$IMAGE" >/dev/null
docker run --rm -d --name "$NAME" --network none \
  --tmpfs /var/lib/mysql:rw,size=768m --pids-limit 256 \
  -e "MYSQL_ROOT_PASSWORD=$PASS" "$IMAGE" >/dev/null
ready=0
for _ in $(seq 1 45); do
  # mysqladmin ping may exit 0 on an AUTH DENIED response while the entrypoint
  # is still initializing accounts. Require an authenticated SQL query instead.
  if docker exec "$NAME" mysql -uroot "-p$PASS" --batch --skip-column-names \
      -e 'SELECT 1' >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
if [[ "$ready" != 1 ]]; then echo 'ISOLATED_MYSQL_NOT_READY'; exit 3; fi

mysql_exec() {
  docker exec -i "$NAME" mysql -uroot "-p$PASS" --batch --skip-column-names "$@"
}
mysql_exec -e 'CREATE DATABASE synthetic_saas CHARACTER SET utf8mb4'
if [[ "$MODE" == '--live-schema' ]]; then
  # This cross-container pipe sends schema-only SQL directly to a detached
  # disposable DB. It does not create an intermediate dump on the Mac,
  # mount the production volume or export any tenant/patient rows.
  docker exec rehab-studio-internal-mysql-1 mysqldump \
    --defaults-extra-file=/run/rehab-secrets/mysql-client.cnf \
    --no-data --skip-triggers --skip-comments --set-gtid-purged=OFF \
    ruoyi-vue-pro system_tenant | mysql_exec synthetic_saas >/dev/null
  echo 'LIVE_CORE_SYSTEM_TENANT_DDL_IMPORTED_WITHOUT_ROWS=YES'
else
  mysql_exec synthetic_saas -e 'CREATE TABLE system_tenant(id BIGINT PRIMARY KEY, name VARCHAR(120) NOT NULL) ENGINE=InnoDB'
fi

for pass in 1 2; do
  for name in rehab-saas-platform-v2 rehab-saas-onboarding-v2 rehab-saas-provisioning-v3 rehab-saas-entitlement-sync-v4; do
    mysql_exec synthetic_saas < "$ROOT/sql/mysql/$name.sql" >/dev/null
  done
  echo "ISOLATED_MIGRATION_PASS=$pass"
done

read -r uuid_column provisioning_table revision_column plan_count <<< "$(
  mysql_exec synthetic_saas --skip-column-names -e "
    SELECT
      (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
       AND table_name='system_tenant' AND column_name='tenant_uuid'),
      (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
       AND table_name='saas_provisioning_requests'),
      (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
       AND table_name='saas_provisioning_requests' AND column_name='entitlement_revision'),
      (SELECT COUNT(*) FROM saas_plans WHERE name='synthetic-beta' AND status=1)"
)"
[[ "$uuid_column" == 1 && "$provisioning_table" == 1 && "$revision_column" == 1 && "$plan_count" == 1 ]] ||
  { echo 'ISOLATED_SCHEMA_CONTRACT_FAILED'; exit 4; }
echo 'ISOLATED_SAAS_V2_V3_V4_SCHEMA=PASS'
echo "REHEARSAL_MODE=$MODE"
echo 'REAL_CORE_DATABASE_UNTOUCHED=YES'
