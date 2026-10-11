#!/bin/bash
# Self-contained L2 schema/seat race verification. NEVER targets a live DB.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NAME="rehab-l2-license-synthetic-$(date +%s)-$$"
PASS="SyntheticOnlyL2-$(date +%s)-$$"
BEFORE="$(docker volume ls -q | wc -l | tr -d ' ')"
cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker image inspect mysql:8.4.10 >/dev/null 2>&1 || {
  echo "Missing local mysql:8.4.10 image; refusing to pull or use any other DB" >&2
  exit 2
}
docker run -d --rm --network none --name "$NAME" \
  --tmpfs /var/lib/mysql:rw,nosuid,nodev,size=700m \
  -e "MYSQL_ROOT_PASSWORD=$PASS" mysql:8.4.10 >/dev/null
READY=0
for i in $(seq 1 60); do
  if docker exec "$NAME" mysql -N -uroot "-p$PASS" -e 'SELECT 1' 2>/dev/null | grep -qx 1; then
    READY=1
    break
  fi
  sleep 1
done
test "$READY" = 1 || { echo "Isolated mysql not ready" >&2; exit 3; }
docker exec "$NAME" mysql -uroot "-p$PASS" -e "CREATE DATABASE rehab_l2_synthetic;" 2>/dev/null
docker exec -i "$NAME" mysql -uroot "-p$PASS" rehab_l2_synthetic < "$ROOT/sql/mysql/rehab-tenant-license-v1.sql" 2>/dev/null
docker exec "$NAME" mysql -uroot "-p$PASS" rehab_l2_synthetic -e "
CREATE TABLE system_users (
  id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL,
  status INT NOT NULL, deleted BIT(1) NOT NULL DEFAULT b'0'
) ENGINE=InnoDB;
INSERT INTO rehab_tenant_license_binding
  (tenant_id,tenant_uuid,installation_uuid,signed_lease,status_proof,revocation_epoch,trusted_time_ms)
VALUES
  (4711,'f12b2687-09a1-40b5-b47a-747d75ce5551',
   'd94123bf-2c94-4af4-9d5b-7ab8ce690509',
   'synthetic-unsigned-inaccessible','synthetic-status-inaccessible',0,1791600000000);
" 2>/dev/null
# The application verifies both cryptographic signatures BEFORE any access;
# this fixture tests only SQL constraints and in-transaction serial locking.
docker exec "$NAME" mysql -N -uroot "-p$PASS" rehab_l2_synthetic -e "
START TRANSACTION;
SELECT tenant_id FROM rehab_tenant_license_binding WHERE tenant_id=4711 FOR UPDATE;
INSERT INTO system_users (id,tenant_id,status) VALUES (1,4711,0);
DO SLEEP(3);
COMMIT;
" >/dev/null 2>&1 &
FIRST="$!"
sleep 1
START="$(date +%s)"
RESULT="$(docker exec "$NAME" mysql -N -uroot "-p$PASS" rehab_l2_synthetic -e "
START TRANSACTION;
SELECT tenant_id FROM rehab_tenant_license_binding WHERE tenant_id=4711 FOR UPDATE;
SELECT COUNT(*) FROM system_users WHERE tenant_id=4711 AND status=0 AND deleted=0;
ROLLBACK;
" 2>/dev/null)"
wait "$FIRST"
WAITED=$(( $(date +%s) - START ))
STAFF="$(printf '%s\n' "$RESULT" | tail -1)"
test "$STAFF" = 1 || { echo "Seat race: second writer did not observe first active seat" >&2; exit 4; }
test "$WAITED" -ge 1 || { echo "Row lock did not serialize synthetic sessions" >&2; exit 5; }
COUNT="$(docker exec "$NAME" mysql -N -uroot "-p$PASS" rehab_l2_synthetic -e 'SELECT COUNT(*) FROM system_users WHERE status=0 AND deleted=0' 2>/dev/null)"
test "$COUNT" = 1
docker rm -f "$NAME" >/dev/null
trap - EXIT
AFTER="$(docker volume ls -q | wc -l | tr -d ' ')"
test "$BEFORE" = "$AFTER" || { echo "Unexpected Docker volume change" >&2; exit 6; }
printf 'L2_MYSQL_SYNTHETIC_PASS=1\nROW_LOCK_WAIT_SECONDS=%s\nSECOND_SEAT_COUNT=%s\nVOL_COUNT_BEFORE=%s\nVOL_COUNT_AFTER=%s\n' "$WAITED" "$STAFF" "$BEFORE" "$AFTER"
