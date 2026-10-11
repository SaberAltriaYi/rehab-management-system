#!/bin/bash
# 只创建临时、无网络、无持久卷的合成数据库；不接受外部数据库地址。
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NAME="rehab-saas-synthetic-$(date +%s)-$$"
PASS="$(openssl rand -hex 24)"
cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker image inspect mysql:8.4.10 >/dev/null
docker run -d --rm --network none --name "$NAME" \
  --tmpfs /var/lib/mysql:rw,nosuid,nodev,size=700m \
  -e "MYSQL_ROOT_PASSWORD=$PASS" mysql:8.4.10 >/dev/null
sql() { docker exec -i -e "MYSQL_PWD=$PASS" "$NAME" mysql -N -uroot "$@"; }
READY=0
for i in $(seq 1 90); do
  if sql -e 'SELECT 1' >/dev/null 2>&1; then READY=1; break; fi
  sleep 1
done
test "$READY" = 1
sql -e 'CREATE DATABASE rehab_saas_synthetic;'
sql rehab_saas_synthetic <<'SQL'
CREATE TABLE system_tenant (id BIGINT PRIMARY KEY, name VARCHAR(64));
INSERT INTO system_tenant VALUES (910001, 'synthetic-tenant-a'), (910002, 'synthetic-tenant-b');
SQL
sql rehab_saas_synthetic < "$ROOT/sql/mysql/rehab-saas-platform-v2.sql"
sql rehab_saas_synthetic < "$ROOT/sql/mysql/rehab-saas-platform-v2.sql"
RESULT="$(sql rehab_saas_synthetic -e "SELECT COUNT(*) FROM system_tenant WHERE registration_status='active' AND registration_source='platform'; SELECT COUNT(*) FROM saas_plans WHERE name='synthetic-beta'; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='rehab_saas_synthetic' AND table_name IN ('platform_admins','saas_plans','tenant_subscriptions','tenant_usage');")"
test "$RESULT" = $'2\n1\n4'
echo 'PASS: MySQL 8.4 迁移首次执行、重复执行与原租户保留（仅合成数据）'
