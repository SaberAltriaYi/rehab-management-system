#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ENV_FILE=${ENV_FILE_OVERRIDE:-"$SCRIPT_DIR/.env"}
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
BACKUP_DIR=${1:-}

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

. "$SCRIPT_DIR/backup-crypto.sh"
resolve_backup_key

[ -n "$BACKUP_DIR" ] || fail "用法：restore.sh <rehab-backup-directory>"
[ -d "$BACKUP_DIR" ] || fail "备份目录不存在：$BACKUP_DIR"
[ -f "$ENV_FILE" ] || fail "缺少部署环境文件：$ENV_FILE"
[ -f "$BACKUP_KEY_PATH" ] || fail "缺少备份加密密钥：$BACKUP_KEY_PATH"
for file in database.sql.gz.enc attachments.tar.gz.enc SHA256SUMS SHA256SUMS.sig metadata.txt; do
  [ -f "$BACKUP_DIR/$file" ] || fail "备份缺少文件：$file"
done
grep -q '^format=rehab-encrypted-backup-v1$' "$BACKUP_DIR/metadata.txt" \
  || fail "不支持的备份格式"

tmp_dir=$(mktemp -d)
trap 'rm -rf -- "$tmp_dir"' EXIT HUP INT TERM

(cd "$BACKUP_DIR" && shasum -a 256 -c SHA256SUMS)
verify_backup_signature "$BACKUP_DIR" "$tmp_dir/ca-public.pem" \
  || fail "备份签名验证失败"
decrypt_backup_file "$BACKUP_DIR/database.sql.gz.enc" | gzip -t
decrypt_backup_file "$BACKUP_DIR/attachments.tar.gz.enc" | tar -tzf - >/dev/null
echo "PASS: 备份签名、加密文件和内容完整性校验通过"

if [ "${CONFIRM_RESTORE:-}" != "RESTORE-REHAB-INTERNAL" ]; then
  echo "未执行恢复。确认停机窗口后设置："
  echo "CONFIRM_RESTORE=RESTORE-REHAB-INTERNAL deploy/internal/restore.sh <rehab-backup-directory>"
  exit 2
fi

db_name=$(awk -F= '$1 == "DB_NAME" {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE")
db_name=${db_name:-ruoyi-vue-pro}
db_username=$(awk -F= '$1 == "DB_USERNAME" {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE")
db_username=${db_username:-yudao}
case "$db_name" in
  *[!A-Za-z0-9_-]*) fail "DB_NAME 只能包含字母、数字、下划线和连字符" ;;
esac
case "$db_username" in
  *[!A-Za-z0-9_-]*) fail "DB_USERNAME 只能包含字母、数字、下划线和连字符" ;;
esac

echo "先为当前状态创建加密安全备份..."
"$SCRIPT_DIR/backup.sh"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" stop admin server

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot' <<SQL
DROP DATABASE IF EXISTS \`$db_name\`;
CREATE DATABASE \`$db_name\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON \`$db_name\`.* TO '$db_username'@'%';
FLUSH PRIVILEGES;
SQL

decrypt_backup_file "$BACKUP_DIR/database.sql.gz.enc" \
  | gzip -dc \
  | docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
      sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE"'

decrypt_backup_file "$BACKUP_DIR/attachments.tar.gz.enc" \
  | docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" run --rm --no-deps -T \
      --entrypoint sh server -c \
      'find /app/data/rehab -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + && tar -C /app/data/rehab -xzf -'

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d
"$SCRIPT_DIR/smoke-test.sh"
echo "PASS: 恢复完成并通过冒烟测试"
