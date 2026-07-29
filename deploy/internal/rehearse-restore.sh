#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ENV_FILE=${ENV_FILE_OVERRIDE:-"$SCRIPT_DIR/.env"}
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
MIGRATION_MANIFEST="$SCRIPT_DIR/migrations.manifest"
BACKUP_DIR=${1:-}
REHEARSAL_PROJECT="rehab-restore-rehearsal-$(date +%Y%m%d%H%M%S)"
REHEARSAL_BACKUPS=$(mktemp -d)

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

[ -n "$BACKUP_DIR" ] || fail "用法：rehearse-restore.sh <rehab-backup-directory>"
[ -d "$BACKUP_DIR" ] || fail "备份目录不存在：$BACKUP_DIR"
[ -f "$MIGRATION_MANIFEST" ] || fail "缺少迁移清单：$MIGRATION_MANIFEST"
EXPECTED_MIGRATION_COUNT=$(awk -F '|' '$1 !~ /^($|#)/ {count++} END {print count + 0}' "$MIGRATION_MANIFEST")
[ "$EXPECTED_MIGRATION_COUNT" -gt 0 ] || fail "迁移清单为空"

case "$REHEARSAL_PROJECT" in
  rehab-restore-rehearsal-*) ;;
  *) fail "隔离项目名安全校验失败" ;;
esac

cleanup() {
  COMPOSE_PROJECT_NAME="$REHEARSAL_PROJECT" \
  BIND_ADDRESS=127.0.0.1 APP_PORT=8080 TLS_PORT=8443 \
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" \
      down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf -- "$REHEARSAL_BACKUPS"
}
trap cleanup EXIT HUP INT TERM

echo "启动隔离恢复环境：$REHEARSAL_PROJECT"
COMPOSE_PROJECT_NAME="$REHEARSAL_PROJECT" \
BIND_ADDRESS=127.0.0.1 APP_PORT=8080 TLS_PORT=8443 \
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --no-build

attempt=0
while [ "$attempt" -lt 60 ]; do
  all_healthy=true
  for service in mysql redis server admin; do
    container_id=$(COMPOSE_PROJECT_NAME="$REHEARSAL_PROJECT" \
      docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q "$service")
    health=$(docker inspect --format \
      '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id" 2>/dev/null || true)
    [ "$health" = "healthy" ] || all_healthy=false
  done
  [ "$all_healthy" = true ] && break
  attempt=$((attempt + 1))
  sleep 5
done
[ "$all_healthy" = true ] || fail "隔离环境未在 5 分钟内全部 healthy"

fresh_metrics=$(COMPOSE_PROJECT_NAME="$REHEARSAL_PROJECT" \
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
    sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE" --batch --raw --skip-column-names' <<'SQL'
SELECT CONCAT('fresh_migration_versions=', COUNT(*)) FROM internal_schema_history
UNION ALL
SELECT CONCAT('fresh_oauth_default_clients_enabled=', COUNT(*))
FROM system_oauth2_client
WHERE deleted = b'0' AND status = 0 AND client_id = 'default'
UNION ALL
SELECT CONCAT('fresh_oauth_nondefault_clients_enabled=', COUNT(*))
FROM system_oauth2_client
WHERE deleted = b'0' AND status = 0 AND client_id <> 'default'
UNION ALL
SELECT CONCAT('fresh_oauth_weak_secrets=', COUNT(*))
FROM system_oauth2_client
WHERE deleted = b'0'
  AND (LENGTH(secret) < 32 OR secret IN ('admin123', 'test', 'test2'));
SQL
)
printf '%s\n' "$fresh_metrics"
printf '%s\n' "$fresh_metrics" | grep -q "^fresh_migration_versions=${EXPECTED_MIGRATION_COUNT}$" \
  || fail "全新数据卷迁移版本数与清单不一致"
printf '%s\n' "$fresh_metrics" | grep -q '^fresh_oauth_default_clients_enabled=1$' \
  || fail "全新数据卷的内部登录客户端状态异常"
printf '%s\n' "$fresh_metrics" | grep -q '^fresh_oauth_nondefault_clients_enabled=0$' \
  || fail "全新数据卷仍有启用的非登录 OAuth2 演示客户端"
printf '%s\n' "$fresh_metrics" | grep -q '^fresh_oauth_weak_secrets=0$' \
  || fail "全新数据卷仍有弱 OAuth2 演示 secret"

COMPOSE_PROJECT_NAME="$REHEARSAL_PROJECT" \
BIND_ADDRESS=127.0.0.1 APP_PORT=8080 TLS_PORT=8443 \
BACKUP_ROOT="$REHEARSAL_BACKUPS" \
CONFIRM_RESTORE=RESTORE-REHAB-INTERNAL \
  "$SCRIPT_DIR/restore.sh" "$BACKUP_DIR"

metrics=$(COMPOSE_PROJECT_NAME="$REHEARSAL_PROJECT" \
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
    sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE" --batch --raw --skip-column-names' <<'SQL'
SELECT CONCAT('migration_versions=', COUNT(*)) FROM internal_schema_history
UNION ALL
SELECT CONCAT('rehab_foreign_keys=', COUNT(*))
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name LIKE 'rehab\_%'
  AND constraint_type = 'FOREIGN KEY';
SQL
)
printf '%s\n' "$metrics"
printf '%s\n' "$metrics" | grep -q "^migration_versions=${EXPECTED_MIGRATION_COUNT}$" \
  || fail "恢复后的迁移账本版本数异常"
printf '%s\n' "$metrics" | grep -q '^rehab_foreign_keys=41$' \
  || fail "恢复后的核心外键数量异常"

echo "PASS: 加密备份已在独立 Compose 项目完成破坏性恢复、结构核验和全套冒烟"
