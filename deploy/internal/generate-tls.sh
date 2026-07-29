#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ENV_FILE=${ENV_FILE_OVERRIDE:-"$SCRIPT_DIR/.env"}
CERT_DIR="$SCRIPT_DIR/certs"
TLS_IP=${1:-}

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

[ -f "$ENV_FILE" ] || fail "缺少部署环境文件：$ENV_FILE"
if [ -z "$TLS_IP" ]; then
  TLS_IP=$(awk -F= '$1 == "BIND_ADDRESS" {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE")
fi
case "$TLS_IP" in
  ''|0.0.0.0|::*|*[!0-9.]*) fail "请传入明确的 IPv4 局域网地址或在 .env 设置 BIND_ADDRESS" ;;
esac

if [ -e "$CERT_DIR/ca.key" ] || [ -e "$CERT_DIR/server.key" ]; then
  [ "${FORCE_REGENERATE_TLS:-}" = "YES" ] \
    || fail "证书已存在；确需轮换时设置 FORCE_REGENERATE_TLS=YES"
fi

umask 077
mkdir -p "$CERT_DIR"
tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$CERT_DIR/ca.key"
openssl req -x509 -new -sha256 -days 3650 \
  -key "$CERT_DIR/ca.key" \
  -subj "/C=CN/O=Rehab Internal/CN=Rehab Internal Root CA" \
  -out "$CERT_DIR/ca.crt"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$CERT_DIR/server.key"
openssl req -new -sha256 \
  -key "$CERT_DIR/server.key" \
  -subj "/C=CN/O=Rehab Internal/CN=$TLS_IP" \
  -out "$tmp_dir/server.csr"

{
  echo "authorityKeyIdentifier=keyid,issuer"
  echo "basicConstraints=CA:FALSE"
  echo "keyUsage=digitalSignature,keyEncipherment"
  echo "extendedKeyUsage=serverAuth"
  echo "subjectAltName=@alt_names"
  echo "[alt_names]"
  echo "DNS.1=localhost"
  echo "DNS.2=rehab-internal.local"
  echo "IP.1=127.0.0.1"
  echo "IP.2=$TLS_IP"
} > "$tmp_dir/server.ext"

openssl x509 -req -sha256 -days 825 \
  -in "$tmp_dir/server.csr" \
  -CA "$CERT_DIR/ca.crt" \
  -CAkey "$CERT_DIR/ca.key" \
  -CAcreateserial \
  -extfile "$tmp_dir/server.ext" \
  -out "$CERT_DIR/server.crt"

chmod 600 "$CERT_DIR/ca.key" "$CERT_DIR/server.key"
chmod 644 "$CERT_DIR/ca.crt" "$CERT_DIR/server.crt"
openssl verify -CAfile "$CERT_DIR/ca.crt" "$CERT_DIR/server.crt"
openssl x509 -in "$CERT_DIR/server.crt" -noout -checkip "$TLS_IP"

echo "PASS: 已为 $TLS_IP 生成内部 HTTPS 证书"
echo "成员设备只需安装：$CERT_DIR/ca.crt"
