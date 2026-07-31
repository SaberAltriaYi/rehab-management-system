#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ENV_FILE=${ENV_FILE_OVERRIDE:-"$SCRIPT_DIR/.env"}
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_ROOT=${BACKUP_ROOT:-"$PROJECT_DIR/backups"}
BACKUP_DIR="$BACKUP_ROOT/rehab-$TIMESTAMP"
CA_KEY="$SCRIPT_DIR/certs/ca.key"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

file_mode() {
  if stat -f '%Lp' "$1" >/dev/null 2>&1; then
    stat -f '%Lp' "$1"
  else
    stat -c '%a' "$1"
  fi
}

. "$SCRIPT_DIR/backup-crypto.sh"
resolve_backup_key

umask 077
[ -f "$ENV_FILE" ] || fail "缺少部署环境文件：$ENV_FILE"
[ -f "$BACKUP_KEY_PATH" ] || fail "缺少备份加密密钥：$BACKUP_KEY_PATH"
[ -f "$CA_KEY" ] || fail "缺少备份清单签名密钥：$CA_KEY"
[ "$(file_mode "$BACKUP_KEY_PATH")" = "600" ] || fail "备份加密密钥权限必须为 600"
mkdir -p "$BACKUP_ROOT"
staging_dir=$(mktemp -d "$BACKUP_ROOT/.rehab-backup.XXXXXX")
trap 'rm -rf -- "$staging_dir"' EXIT HUP INT TERM

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps --status running mysql \
  | grep -q mysql || fail "MySQL 容器未运行"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps --status running server \
  | grep -q server || fail "后端容器未运行"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot \
    --single-transaction --quick --routines --triggers --events \
    --set-gtid-purged=OFF "$MYSQL_DATABASE"' \
  | gzip -9 > "$staging_dir/database.sql.gz"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T server \
  tar -C /app/data/rehab -czf - . > "$staging_dir/attachments.tar.gz"

gzip -t "$staging_dir/database.sql.gz"
tar -tzf "$staging_dir/attachments.tar.gz" >/dev/null

encrypt_backup_file "$staging_dir/database.sql.gz" "$staging_dir/database.sql.gz.enc"
encrypt_backup_file "$staging_dir/attachments.tar.gz" "$staging_dir/attachments.tar.gz.enc"
rm -f -- "$staging_dir/database.sql.gz" "$staging_dir/attachments.tar.gz"

decrypt_backup_file "$staging_dir/database.sql.gz.enc" | gzip -t
decrypt_backup_file "$staging_dir/attachments.tar.gz.enc" | tar -tzf - >/dev/null

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" images > "$staging_dir/images.txt"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps > "$staging_dir/services.txt"
{
  echo "format=rehab-encrypted-backup-v1"
  echo "created_at=$TIMESTAMP"
  echo "cipher=AES-256-CBC"
  echo "kdf=PBKDF2-SHA256"
  echo "iterations=600000"
} > "$staging_dir/metadata.txt"

(
  cd "$staging_dir"
  shasum -a 256 database.sql.gz.enc attachments.tar.gz.enc images.txt services.txt metadata.txt \
    > SHA256SUMS
)
openssl dgst -sha256 -sign "$CA_KEY" \
  -out "$staging_dir/SHA256SUMS.sig" "$staging_dir/SHA256SUMS"

tmp_public_key="$staging_dir/.verify-public.pem"
verify_backup_signature "$staging_dir" "$tmp_public_key" || fail "备份清单签名验证失败"
rm -f -- "$tmp_public_key"

[ ! -e "$BACKUP_DIR" ] || fail "备份目录已存在：$BACKUP_DIR"
mv "$staging_dir" "$BACKUP_DIR"
trap - EXIT HUP INT TERM

echo "PASS: 加密备份已创建，并通过解密、内容校验和签名验证"
echo "$BACKUP_DIR"
