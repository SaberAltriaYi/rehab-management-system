#!/usr/bin/env bash
# L5 isolated Node owner -> Java production verifier interoperability.
# MUST NOT read/change patient DB, original Docker projects or volumes.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
umask 077
DIR="$(mktemp -d "${TMPDIR:-/tmp}/rehab-l5-cross-runtime.XXXXXXXX")"
cleanup() {
  case "$DIR" in
    */rehab-l5-cross-runtime.*) rm -rf -- "$DIR" ;;
    *) echo 'Unsafe L5 staging cleanup blocked' >&2; exit 1 ;;
  esac
}
trap cleanup EXIT
REHAB_L5_NODE_FIXTURE="$DIR/l5-node-fixture.json"
node desktop/scripts/l5-node-issuer-fixture.mjs "$REHAB_L5_NODE_FIXTURE"
test "$(stat -f '%Lp' "$REHAB_L5_NODE_FIXTURE")" = 600
export REHAB_L5_NODE_FIXTURE
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export PATH="$JAVA_HOME/bin:$PATH"
mvn -o -B -pl yudao-module-system -am \
  -Dtest=L5NodeIssuedLeaseInteropTest -Dsurefire.failIfNoSpecifiedTests=false test
export PATH="$HOME/.rustup/toolchains/stable-aarch64-apple-darwin/bin:$PATH"
CARGO_NET_OFFLINE=true cargo test --manifest-path \
  desktop/launcher/src-tauri/Cargo.toml --offline --features license-required \
  l5_node_owner_signed_tokens_interoperate_with_rust_real_issuer
echo 'L5_NODE_TO_RUST_E2E_PASS=1'
echo 'L5_NODE_TO_JAVA_E2E_PASS=1'
