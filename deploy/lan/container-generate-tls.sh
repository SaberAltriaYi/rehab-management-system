#!/bin/sh
set -eu

CERT_DIR=/certs
TLS_IP=${TLS_IP:-}
TLS_HOSTNAME=${TLS_HOSTNAME:-rehab.local}

case "$TLS_IP" in
  ''|0.0.0.0|::*|*[!0-9.]*) echo "FAIL: TLS_IP 必须是明确的局域网 IPv4" >&2; exit 1 ;;
esac
case "$TLS_HOSTNAME" in
  ''|*[!A-Za-z0-9.-]*) echo "FAIL: TLS_HOSTNAME 格式无效" >&2; exit 1 ;;
esac

if [ -e "$CERT_DIR/ca.key" ] || [ -e "$CERT_DIR/server.key" ]; then
  [ "${FORCE_REGENERATE_TLS:-}" = "YES" ] || {
    echo "FAIL: 证书已存在，拒绝覆盖" >&2
    exit 1
  }
fi

umask 077
mkdir -p "$CERT_DIR"
tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$CERT_DIR/ca.key"
openssl req -x509 -new -sha256 -days 3650 \
  -key "$CERT_DIR/ca.key" \
  -subj "/C=CN/O=Rehab LAN/CN=Rehab LAN Root CA" \
  -out "$CERT_DIR/ca.crt"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$CERT_DIR/server.key"
openssl req -new -sha256 \
  -key "$CERT_DIR/server.key" \
  -subj "/C=CN/O=Rehab LAN/CN=$TLS_HOSTNAME" \
  -out "$tmp_dir/server.csr"

{
  echo "authorityKeyIdentifier=keyid,issuer"
  echo "basicConstraints=CA:FALSE"
  echo "keyUsage=digitalSignature,keyEncipherment"
  echo "extendedKeyUsage=serverAuth"
  echo "subjectAltName=@alt_names"
  echo "[alt_names]"
  echo "DNS.1=localhost"
  echo "DNS.2=rehab.local"
  echo "DNS.3=rehab-internal.local"
  echo "DNS.4=$TLS_HOSTNAME"
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
openssl x509 -in "$CERT_DIR/server.crt" -noout -checkhost "$TLS_HOSTNAME"
