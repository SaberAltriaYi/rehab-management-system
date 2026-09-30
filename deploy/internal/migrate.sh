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
    case "$BASELINE_THROUGH" in
      ''|*[!0-9]*) fail "baseline 必须指定已人工核验的三位版本号" ;;
    esac
    [ "${#BASELINE_THROUGH}" -eq 3 ] || fail "baseline 版本必须为三位数字"
    [ "${CONFIRM_BASELINE:-}" = "BASELINE-REHAB-INTERNAL" ] \
      || fail "baseline 只登记历史，不验证业务 Schema；需人工核验和备份后显式设置 CONFIRM_BASELINE=BASELINE-REHAB-INTERNAL"
    [ "$BASELINE_THROUGH" -le 19 ] || fail "baseline 只允许历史初始化版本 001-019；新增迁移必须实际执行"
    ;;
  adopt)
    case "$BASELINE_THROUGH" in
      ''|*[!0-9]*) fail "adopt 必须指定一个三位增量迁移版本号" ;;
    esac
    [ "${#BASELINE_THROUGH}" -eq 3 ] || fail "adopt 版本必须为三位数字"
    [ "$BASELINE_THROUGH" -gt 19 ] || fail "adopt 只用于 020+ 增量迁移；001-019 请使用经人工核验的 baseline"
    [ "${CONFIRM_ADOPT:-}" = "ADOPT-REHAB-INITDB" ] \
      || fail "adopt 仅用于旧版 Compose 已在 initdb 直接建表但未登记的数据库；备份后显式设置 CONFIRM_ADOPT=ADOPT-REHAB-INITDB"
    ;;
  *) fail "用法：$0 verify-files|status|apply|baseline <version>|adopt <version>" ;;
esac

verify_manifest_files() {
  previous_version=0
  while IFS='|' read -r version expected_checksum relative_file description; do
    case "$version" in ''|'#'*) continue ;; esac
    case "$version" in *[!0-9]*) fail "非法迁移版本：$version" ;; esac
    [ "${#version}" -eq 3 ] || fail "迁移版本必须为三位数字：$version"
    [ "$version" -gt "$previous_version" ] || fail "迁移版本重复或乱序：$version"
    previous_version=$version
    case "$expected_checksum" in ''|*[!0-9a-f]*) fail "非法校验和：$version" ;; esac
    [ "${#expected_checksum}" -eq 64 ] || fail "非法校验和长度：$version"
    case "$relative_file" in
      sql/mysql/*.sql|deploy/internal/*.sql) ;;
      *) fail "迁移文件不在允许目录：$relative_file" ;;
    esac
    case "$relative_file" in *..*|*\'*|*\|*) fail "非法迁移路径：$relative_file" ;; esac
    case "$description" in ''|*\'*|*\|*) fail "迁移说明为空或包含不支持字符：$version" ;; esac
    migration_file="$PROJECT_DIR/$relative_file"
    [ -f "$migration_file" ] || fail "迁移文件不存在：$relative_file"
    actual_checksum=$(shasum -a 256 "$migration_file" | awk '{print $1}')
    [ "$actual_checksum" = "$expected_checksum" ] \
      || fail "迁移文件校验和漂移：$relative_file"
    ledger_entry="'$version', '$expected_checksum', '$relative_file', '$description'"
    if [ "$version" -le 19 ]; then
      grep -Fq "$ledger_entry" "$INIT_LEDGER" \
        || fail "全新数据库迁移账本缺少或不匹配历史版本：$version"
    elif grep -Fq "$ledger_entry" "$INIT_LEDGER"; then
      fail "全新数据库迁移账本不可预登记增量迁移 ${version}；必须实际执行后再登记"
    fi
  done < "$MANIFEST"
  [ "$previous_version" -ge 19 ] || fail "迁移清单缺少已发布历史基线"
}

# These historical scripts contain bootstrap seeds, cleanup, or broad backfills.
# They are immutable and MUST NOT be replayed through an incremental upgrade.
is_bootstrap_only() {
  [ "$1" -le 19 ]
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

# All modes preflight read-only. Never create a ledger to guess an old database's
# baseline. Fresh installations still use init-schema-history.sql via bootstrap.
ledger_exists=$(printf '%s\n' "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'internal_schema_history';" | run_sql)
[ "$ledger_exists" = "1" ] \
  || fail "迁移账本缺失：已停止且未修改数据库。新库使用专用初始化流程；旧库先人工核验 Schema/备份，不允许自动建账或重放初始化 SQL。"

history_rows=$(printf '%s\n' 'SELECT version, checksum FROM internal_schema_history ORDER BY version;' | run_sql)
# Validate every recorded version before any write. A newer database must not be
# silently accepted by an older application release.
validate_history() {
  while read -r history_version history_checksum; do
    [ -n "$history_version" ] || continue
    case "$history_version" in *[!0-9]*) fail "数据库账本含非法版本" ;; esac
    [ "${#history_version}" -eq 3 ] || fail "数据库账本含非法版本长度"
    expected=$(awk -F'|' -v v="$history_version" '$1 == v { print $2 }' "$MANIFEST")
    [ -n "$expected" ] || fail "数据库包含本发布不认识的迁移：$history_version"
    [ "$history_checksum" = "$expected" ] || fail "数据库迁移校验和漂移：$history_version"
  done
}
printf '%s\n' "$history_rows" | validate_history

# adopt：只登记“纯 CREATE TABLE”增量迁移，且数据库中每张表的列名与顺序必须与脚本完全一致。
# 用于修复旧版 Compose 把 020-023 直接挂到 initdb 造成的“表已存在、账本未登记”缺口。
create_only_columns() {
  awk '
    function bad(msg) { print "BAD " NR ": " msg; failed = 1; exit }
    /^[[:space:]]*$/ || /^[[:space:]]*--/ { next }
    !inside && /^CREATE TABLE `[A-Za-z0-9_]+` \($/ {
      table = $3; gsub("`", "", table); cols = ""; inside = 1; next
    }
    !inside { bad("非 CREATE TABLE 语句") }
    inside && /^  `[A-Za-z0-9_]+`/ {
      col = $1; gsub("`", "", col); cols = (cols == "" ? col : cols "," col); next
    }
    inside && /^\)/ {
      if ($0 !~ /;[[:space:]]*$/) bad("CREATE TABLE 未以分号结束")
      print table " " cols; tables++; inside = 0; next
    }
    inside && /^  (PRIMARY KEY|UNIQUE KEY|KEY|INDEX|CONSTRAINT)/ { next }
    inside { bad("无法识别的表定义行") }
    END { if (!failed && (inside || tables == 0)) print "BAD 0: 未找到完整 CREATE TABLE" }
  ' "$1"
}

if [ "$MODE" = "adopt" ]; then
  adopt_row=$(awk -F'|' -v v="$BASELINE_THROUGH" '$1 == v { print }' "$MANIFEST")
  [ -n "$adopt_row" ] || fail "adopt 目标不在发布清单中"
  adopt_checksum=$(printf '%s\n' "$adopt_row" | awk -F'|' '{ print $2 }')
  adopt_file=$(printf '%s\n' "$adopt_row" | awk -F'|' '{ print $3 }')
  adopt_description=$(printf '%s\n' "$adopt_row" | awk -F'|' '{ print $4 }')
  already=$(printf '%s\n' "$history_rows" | awk -v v="$BASELINE_THROUGH" '$1 == v { print $1 }')
  [ -z "$already" ] || fail "迁移 $BASELINE_THROUGH 已登记，无需 adopt"
  while IFS='|' read -r version expected_checksum relative_file description; do
    case "$version" in ''|'#'*) continue ;; esac
    [ "$version" -lt "$BASELINE_THROUGH" ] || continue
    earlier=$(printf '%s\n' "$history_rows" | awk -v v="$version" '$1 == v { print $1 }')
    [ -n "$earlier" ] || fail "更早的迁移 $version 尚未登记；必须按顺序处理"
  done < "$MANIFEST"
  adopt_columns=$(create_only_columns "$PROJECT_DIR/$adopt_file")
  case "$adopt_columns" in *BAD*) fail "迁移 $BASELINE_THROUGH 不是纯建表脚本，禁止 adopt：$(printf '%s' "$adopt_columns" | grep BAD | head -1)" ;; esac
  printf '%s\n' "$adopt_columns" > "${TMPDIR:-/tmp}/rehab-adopt-$$"
  while read -r adopt_table expected_columns; do
    [ -n "$adopt_table" ] || continue
    actual_columns=$(printf "SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY ordinal_position SEPARATOR ','),'') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = '%s';\n" "$adopt_table" | run_sql)
    if [ "$actual_columns" != "$expected_columns" ]; then
      rm -f "${TMPDIR:-/tmp}/rehab-adopt-$$"
      fail "表 $adopt_table 不存在或列结构与迁移 $BASELINE_THROUGH 不一致：未登记。请人工核验后再决定 apply 或修复"
    fi
    echo "VERIFIED: $adopt_table"
  done < "${TMPDIR:-/tmp}/rehab-adopt-$$"
  rm -f "${TMPDIR:-/tmp}/rehab-adopt-$$"
  printf "INSERT INTO internal_schema_history(version, checksum, script_path, description, installed_by, baseline, execution_ms) VALUES ('%s','%s','%s','%s','adopt-verified',b'0',0);\n" \
    "$BASELINE_THROUGH" "$adopt_checksum" "$adopt_file" "$adopt_description" | run_sql
  echo "ADOPTED: $BASELINE_THROUGH $adopt_description"
  echo "PASS: 已按实际表结构登记 ${BASELINE_THROUGH}；请运行 status 确认剩余迁移"
  exit 0
fi

if [ "$MODE" = "baseline" ]; then
  baseline_known=$(awk -F'|' -v v="$BASELINE_THROUGH" '$1 == v { print $1 }' "$MANIFEST")
  [ -n "$baseline_known" ] || fail "baseline 目标不在发布清单中"
fi

# Build the complete pending list BEFORE executing anything. This guarantees
# that a missing 015 cannot be reached after partially applying earlier scripts.
pending=0
while IFS='|' read -r version expected_checksum relative_file description; do
  case "$version" in ''|'#'*) continue ;; esac
  history_checksum=$(printf '%s\n' "$history_rows" | awk -v v="$version" '$1 == v { print $2 }')
  if [ -n "$history_checksum" ]; then
    echo "OK: $version $description"
    continue
  fi
  pending=$((pending + 1))
  echo "PENDING: $version $description"
  if [ "$MODE" = "apply" ] && is_bootstrap_only "$version"; then
    fail "禁止升级重放初始化迁移 $version ($relative_file)：先核验历史基线；未执行任何迁移。"
  fi
done < "$MANIFEST"

if [ "$MODE" = "status" ]; then
  [ "$pending" -eq 0 ] || fail "存在 $pending 个未登记迁移（只读检查，未修改数据库）"
  echo "PASS: 数据库迁移账本与发布清单一致（只读）"
  exit 0
fi

# No historical file is changed by this guard. New incremental migrations must
# be reviewed separately, backed up and tested before they are added to manifest.
while IFS='|' read -r version expected_checksum relative_file description; do
  case "$version" in ''|'#'*) continue ;; esac
  history_checksum=$(printf '%s\n' "$history_rows" | awk -v v="$version" '$1 == v { print $2 }')
  [ -z "$history_checksum" ] || continue

  if [ "$MODE" = "baseline" ]; then
    [ "$version" -le "$BASELINE_THROUGH" ] || continue
    printf "INSERT INTO internal_schema_history(version, checksum, script_path, description, baseline) VALUES ('%s','%s','%s','%s',b'1');\n" \
      "$version" "$expected_checksum" "$relative_file" "$description" | run_sql
    echo "BASELINE: $version $description"
    continue
  fi

  is_bootstrap_only "$version" && fail "禁止执行初始化迁移：$version"
  started_at=$(date +%s)
  run_sql < "$PROJECT_DIR/$relative_file"
  finished_at=$(date +%s)
  execution_ms=$(( (finished_at - started_at) * 1000 ))
  printf "INSERT INTO internal_schema_history(version, checksum, script_path, description, baseline, execution_ms) VALUES ('%s','%s','%s','%s',b'0',%s);\n" \
    "$version" "$expected_checksum" "$relative_file" "$description" "$execution_ms" | run_sql
  echo "APPLIED: $version $description"
done < "$MANIFEST"

if [ "$MODE" = "baseline" ]; then
  echo "PASS: 已登记人工核验基线至 ${BASELINE_THROUGH}；请运行 status 确认剩余迁移"
else
  echo "PASS: 数据库迁移账本与发布清单一致"
fi
