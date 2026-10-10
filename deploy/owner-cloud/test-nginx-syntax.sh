#!/usr/bin/env bash
# Offline synthetic Nginx syntax test only. Does NOT start a listener.
set -Eeuo pipefail
umask 077
command -v nginx >/dev/null || { echo 'NGINX_BINARY_REQUIRED' >&2; exit 1; }
command -v openssl >/dev/null || { echo 'OPENSSL_BINARY_REQUIRED' >&2; exit 1; }
ROOT="$(cd "$(dirname "$0")" && pwd)"
STAGE="$(mktemp -d /tmp/rehab-owner-nginx-syntax.XXXXXXXX)"
cleanup() {
  case "$STAGE" in
    /tmp/rehab-owner-nginx-syntax.*) rm -rf -- "$STAGE" ;;
    *) echo 'UNSAFE_STAGE_PATH' >&2; return 1 ;;
  esac
}
trap cleanup EXIT
mkdir -p "$STAGE/tls" "$STAGE/snippets"
for name in activation owner owner-admin-client upstream-ca owner-upstream; do
  openssl req -x509 -newkey ed25519 -nodes \
    -keyout "$STAGE/tls/$name.key" -out "$STAGE/tls/$name.crt" \
    -days 1 -subj "/CN=$name.internal" >/dev/null 2>&1
done
cp "$ROOT/rehab-owner-admin-acl.conf" "$STAGE/snippets/rehab-owner-admin-acl.conf"
python3 - "$STAGE" "$ROOT/nginx.conf.template" <<'PY'
from pathlib import Path
import sys
stage = Path(sys.argv[1])
src = Path(sys.argv[2]).read_text()
replacements = {
  '__ACTIVATION_FQDN__': 'activation.example.test',
  '__OWNER_FQDN__': 'owner.example.test',
  '/etc/nginx/snippets/rehab-owner-admin-acl.conf': str(stage / 'snippets/rehab-owner-admin-acl.conf'),
  '/etc/rehab-owner/tls/activation-fullchain.pem': str(stage / 'tls/activation.crt'),
  '/etc/rehab-owner/tls/activation-key.pem': str(stage / 'tls/activation.key'),
  '/etc/rehab-owner/tls/owner-fullchain.pem': str(stage / 'tls/owner.crt'),
  '/etc/rehab-owner/tls/owner-key.pem': str(stage / 'tls/owner.key'),
  '/etc/rehab-owner/tls/owner-admin-client-ca.pem': str(stage / 'tls/owner-admin-client.crt'),
  '/etc/rehab-owner/tls/upstream-ca.pem': str(stage / 'tls/upstream-ca.crt')
}
for key, val in replacements.items():
  assert key in src, f'missing required template placeholder: {key}'
  src = src.replace(key, val)
(stage / 'vhosts.conf').write_text(src)
(stage / 'nginx.conf').write_text(
  f'worker_processes 1;\npid {stage}/nginx.pid;\nevents {{worker_connections 16;}}\nhttp {{ include {stage}/vhosts.conf; }}\n')
PY
nginx -t -c "$STAGE/nginx.conf" -p "$STAGE"
echo 'OWNER_CLOUD_NGINX_SYNTHETIC_SYNTAX_PASS=1'
