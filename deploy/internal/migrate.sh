#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ENV_FILE=${ENV_FILE_OVERRIDE:-"$SCRIPT_DIR/.env"}
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
MANIFEST="$SCRIPT_DIR/migrations.manifest"
INIT_LEDGER="$SCRIPT_DIR/init-schema-history.sql"
MODE=${1:-status}
BASELINE_THROUGH=${2:-}

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

[ -f "$MANIFEST" ] || fail "缺少迁移清单 migrations.manifest"
[ -f "$INIT_LEDGER" ] || fail "缺少全新数据库迁移账本 init-schema-history.sql"

case "$MODE" in
  verify-files|status|apply) ;;
  baseline)
    [ -n "$BASELINE_THROUGH" ] || fail "baseline 必须指定最后一个已确认版本，例如：baseline 015"
    ;;
  *) fail "用法：$0 verify-files|status|apply|baseline <version>" ;;
esac

verify_manifest_files() {
  while IFS='|' read -r version expected_checksum relative_file description; do
    case "$version" in ''|'#'*) continue ;; esac
    migration_file="$PROJECT_DIR/$relative_file"
    [ -f "$migration_file" ] || fail "迁移文件不存在：$relative_file"
    actual_checksum=$(shasum -a 256 "$migration_file" | awk '{print $1}')
    [ "$actual_checksum" = "$expected_checksum" ] \
      || fail "迁移文件校验和漂移：$relative_file"
    [ -n "$description" ] || fail "迁移 $version 缺少说明"
    grep -Fq "'$version', '$expected_checksum', '$relative_file', '$description'" "$INIT_LEDGER" \
      || fail "全新数据库迁移账本缺少或不匹配版本：$version"
  done < "$MANIFEST"
}

verify_manifest_files

if [ "$MODE" = "verify-files" ]; then
  echo "PASS: 迁移文件与固定 SHA-256 清单一致"
  exit 0
fi

[ -f "$ENV_FILE" ] || fail "缺少部署环境文件：$ENV_FILE"

run_sql() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
    sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE" --batch --raw --skip-column-names'
}

run_sql <<'SQL'
CREATE TABLE IF NOT EXISTS internal_schema_history (
  version VARCHAR(32) NOT NULL COMMENT '迁移版本',
  checksum CHAR(64) NOT NULL COMMENT '脚本 SHA-256',
  script_path VARCHAR(255) NOT NULL COMMENT '项目内相对路径',
  description VARCHAR(255) NOT NULL COMMENT '迁移说明',
  installed_on DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间',
  installed_by VARCHAR(64) NOT NULL DEFAULT 'internal-migrate' COMMENT '执行主体',
  baseline BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否基线登记',
  execution_ms BIGINT NOT NULL DEFAULT 0 COMMENT '执行耗时毫秒',
  PRIMARY KEY (version),
  UNIQUE KEY uk_internal_schema_history_path (script_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内部版数据库迁移账本';
SQL

pending=0
while IFS='|' read -r version expected_checksum relative_file description; do
  case "$version" in ''|'#'*) continue ;; esac

  history_checksum=$(printf "SELECT checksum FROM internal_schema_history WHERE version='%s';\n" "$version" | run_sql)
  if [ -n "$history_checksum" ]; then
    [ "$history_checksum" = "$expected_checksum" ] \
      || fail "数据库中版本 $version 的校验和与发布清单不一致"
    echo "OK: $version $description"
    continue
  fi

  if [ "$MODE" = "baseline" ] && [ "$version" -le "$BASELINE_THROUGH" ]; then
    printf "INSERT INTO internal_schema_history(version, checksum, script_path, description, baseline) VALUES ('%s','%s','%s','%s',b'1');\n" \
      "$version" "$expected_checksum" "$relative_file" "$description" | run_sql
    echo "BASELINE: $version $description"
    continue
  fi

  if [ "$MODE" = "baseline" ]; then
    echo "PENDING: $version $description"
    continue
  fi

  if [ "$MODE" = "apply" ]; then
    started_at=$(date +%s)
    run_sql < "$PROJECT_DIR/$relative_file"
    finished_at=$(date +%s)
    execution_ms=$(( (finished_at - started_at) * 1000 ))
    printf "INSERT INTO internal_schema_history(version, checksum, script_path, description, baseline, execution_ms) VALUES ('%s','%s','%s','%s',b'0',%s);\n" \
      "$version" "$expected_checksum" "$relative_file" "$description" "$execution_ms" | run_sql
    echo "APPLIED: $version $description"
    continue
  fi

  echo "PENDING: $version $description"
  pending=$((pending + 1))
done < "$MANIFEST"

if [ "$pending" -ne 0 ]; then
  fail "存在 $pending 个未登记迁移"
fi

echo "PASS: 数据库迁移账本与发布清单一致"
