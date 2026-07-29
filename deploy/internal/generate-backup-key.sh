#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
KEY_FILE=${BACKUP_KEY_FILE:-"$SCRIPT_DIR/secrets/backup.key"}

if [ -e "$KEY_FILE" ]; then
  echo "FAIL: 备份密钥已存在，拒绝覆盖：$KEY_FILE" >&2
  exit 1
fi

umask 077
mkdir -p "$(dirname -- "$KEY_FILE")"
openssl rand -base64 -out "$KEY_FILE" 48
chmod 600 "$KEY_FILE"

echo "PASS: 已生成备份加密密钥：$KEY_FILE"
echo "请立即复制到另一份受控加密介质；密钥丢失后无法恢复备份。"
