#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ENV_FILE=${ENV_FILE_OVERRIDE:-"$SCRIPT_DIR/.env"}
BACKUP_DIR=${1:-}
CA_KEY="$SCRIPT_DIR/certs/ca.key"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

. "$SCRIPT_DIR/backup-crypto.sh"
resolve_backup_key

[ -n "$BACKUP_DIR" ] || fail "用法：encrypt-legacy-backup.sh <legacy-backup-directory>"
[ -d "$BACKUP_DIR" ] || fail "备份目录不存在：$BACKUP_DIR"
[ -f "$BACKUP_KEY_PATH" ] || fail "缺少备份加密密钥"
[ -f "$CA_KEY" ] || fail "缺少签名密钥"
[ -f "$BACKUP_DIR/database.sql.gz" ] || fail "没有发现明文 database.sql.gz"
[ -f "$BACKUP_DIR/attachments.tar.gz" ] || fail "没有发现明文 attachments.tar.gz"

gzip -t "$BACKUP_DIR/database.sql.gz"
tar -tzf "$BACKUP_DIR/attachments.tar.gz" >/dev/null
encrypt_backup_file "$BACKUP_DIR/database.sql.gz" "$BACKUP_DIR/database.sql.gz.enc"
encrypt_backup_file "$BACKUP_DIR/attachments.tar.gz" "$BACKUP_DIR/attachments.tar.gz.enc"
decrypt_backup_file "$BACKUP_DIR/database.sql.gz.enc" | gzip -t
decrypt_backup_file "$BACKUP_DIR/attachments.tar.gz.enc" | tar -tzf - >/dev/null

{
  echo "format=rehab-encrypted-backup-v1"
  echo "created_at=legacy-conversion-$(date +%Y%m%d-%H%M%S)"
  echo "cipher=AES-256-CBC"
  echo "kdf=PBKDF2-SHA256"
  echo "iterations=600000"
} > "$BACKUP_DIR/metadata.txt"

(cd "$BACKUP_DIR" && shasum -a 256 \
  database.sql.gz.enc attachments.tar.gz.enc images.txt services.txt metadata.txt > SHA256SUMS)
openssl dgst -sha256 -sign "$CA_KEY" \
  -out "$BACKUP_DIR/SHA256SUMS.sig" "$BACKUP_DIR/SHA256SUMS"

tmp_public_key=$(mktemp)
trap 'rm -f -- "$tmp_public_key"' EXIT HUP INT TERM
verify_backup_signature "$BACKUP_DIR" "$tmp_public_key" || fail "转换后签名校验失败"
rm -f -- "$BACKUP_DIR/database.sql.gz" "$BACKUP_DIR/attachments.tar.gz"

echo "PASS: 旧备份已加密，原明文数据库与附件已删除"
