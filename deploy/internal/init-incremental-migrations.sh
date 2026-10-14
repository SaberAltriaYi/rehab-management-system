#!/bin/bash
# 全新 MySQL 数据卷初始化（docker-entrypoint-initdb.d/111，位于 110-schema-history 之后）。
#
# 001-019 是不可重放的初始化基线，由 init-schema-history.sql 登记（baseline=1）。
# 020+ 是增量迁移：在此按 migrations.manifest 顺序逐个“实际执行 → 校验通过后登记”（baseline=0），
# 与 migrate.sh apply 的语义一致，因此新库执行 `migrate.sh status` 时不会出现已建表却显示 PENDING、
# 进而 apply 重复 CREATE TABLE 失败的问题。任何一步失败都会中止初始化（fail closed）。
#
# MySQL 官方入口对 *.sh 可能 source 也可能直接执行；主体放在子 shell 函数中，两种方式行为一致。

rehab_apply_incremental_migrations() (
  set -eu
  root="${REHAB_MIGRATION_ROOT:-/opt/rehab-migrations}"
  manifest="$root/migrations.manifest"
  mysql_bin="${REHAB_MYSQL_BIN:-mysql}"
  installed_by="${REHAB_MIGRATION_INSTALLED_BY:-docker-init}"
  [ -f "$manifest" ] || { echo "增量迁移清单不存在：$manifest" >&2; exit 1; }

  run_sql() {
    MYSQL_PWD="${MYSQL_ROOT_PASSWORD:-}" "$mysql_bin" --protocol=socket -uroot \
      --default-character-set=utf8mb4 --binary-mode "${MYSQL_DATABASE:?缺少 MYSQL_DATABASE}"
  }

  sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
      sha256sum "$1" | awk '{print $1}'
    else
      shasum -a 256 "$1" | awk '{print $1}'
    fi
  }

  # 第一遍：只校验（顺序、路径、校验和），全部通过后才执行任何 SQL
  previous=0
  pending=""
  while IFS='|' read -r version checksum relative_file description; do
    case "$version" in ''|'#'*) continue ;; esac
    case "$version" in *[!0-9]*) echo "非法迁移版本：$version" >&2; exit 1 ;; esac
    [ "${#version}" -eq 3 ] || { echo "迁移版本必须为三位数字：$version" >&2; exit 1; }
    [ "$version" -gt "$previous" ] || { echo "迁移版本重复或乱序：$version" >&2; exit 1; }
    previous=$version
    # 001-019 已由 init-schema-history.sql 作为基线登记，初始化 SQL 已由前序 initdb 脚本执行
    [ "$version" -gt 19 ] || continue
    case "$relative_file" in
      sql/mysql/*.sql) ;;
      *) echo "增量迁移只允许 sql/mysql/*.sql：$relative_file" >&2; exit 1 ;;
    esac
    case "$relative_file" in *..*|*\'*) echo "非法迁移路径：$relative_file" >&2; exit 1 ;; esac
    case "$description" in ''|*\'*|*\\*) echo "迁移说明为空或包含不支持字符：$version" >&2; exit 1 ;; esac
    case "$checksum" in ''|*[!0-9a-f]*) echo "非法校验和：$version" >&2; exit 1 ;; esac
    [ "${#checksum}" -eq 64 ] || { echo "非法校验和长度：$version" >&2; exit 1; }
    [ -f "$root/$relative_file" ] || { echo "增量迁移文件不存在：$relative_file" >&2; exit 1; }
    actual=$(sha256_of "$root/$relative_file") || { echo "无法计算校验和：$relative_file" >&2; exit 1; }
    [ "$actual" = "$checksum" ] || { echo "增量迁移校验和漂移：$relative_file" >&2; exit 1; }
    pending="$pending $version"
  done < "$manifest"

  # 第二遍：逐个执行，成功后立即登记
  applied=0
  while IFS='|' read -r version checksum relative_file description; do
    case " $pending " in *" $version "*) ;; *) continue ;; esac
    started=$(date +%s)
    run_sql < "$root/$relative_file" || { echo "增量迁移执行失败：${version}（未登记）" >&2; exit 1; }
    finished=$(date +%s)
    printf "INSERT INTO internal_schema_history(version, checksum, script_path, description, installed_by, baseline, execution_ms) VALUES ('%s','%s','%s','%s','%s',b'0',%s);\n" \
      "$version" "$checksum" "$relative_file" "$description" "$installed_by" "$(( (finished - started) * 1000 ))" | run_sql \
      || { echo "增量迁移登记失败：$version" >&2; exit 1; }
    echo "APPLIED: $version $description"
    applied=$((applied + 1))
  done < "$manifest"
  echo "PASS: 全新数据库已执行并登记 $applied 个增量迁移"
)

# 注意：不要写成 `f || ...`，否则子 shell 内的 set -e 会被 shell 忽略；每一步另有显式失败检查。
rehab_apply_incremental_migrations
rehab_incremental_rc=$?
if [ "$rehab_incremental_rc" -ne 0 ]; then
  echo "增量迁移初始化失败，已中止（失败版本未登记）" >&2
  exit 1
fi
unset rehab_incremental_rc
