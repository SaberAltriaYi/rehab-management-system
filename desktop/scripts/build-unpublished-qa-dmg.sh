#!/usr/bin/env bash
# Non-distribution QA packaging only. This NEVER installs, migrates,
# starts/stops business services, approves 182 cases or publishes a release.
set -Eeuo pipefail
[[ $# == 1 && "$1" == '--qa-only' ]] || { echo 'REQUIRE_QA_ONLY'; exit 2; }
ROOT="$HOME/my-project/rehab/rehab-management-system"
QA="$HOME/my-project/rehab/validation-evidence/qa-20261113-github-sync-qa-build"
CANDIDATE="$QA/candidate-runtime/1.0.0"
ASSETS="$ROOT/desktop/launcher/src-tauri/resources/runtime"
BACKUP="$QA/original-tauri-036-runtime-before-qa"
STAGED="$QA/staged-tauri-037-runtime-after-qa"
LOCK="$QA/qa-dmg-resource-lock"
TARGET="$ROOT/desktop/launcher/src-tauri/target/release/bundle/dmg/康复管理系统内部版_1.0.0_aarch64.dmg"
APP="$ROOT/desktop/launcher/src-tauri/target/release/bundle/macos/康复管理系统内部版.app"
OUTPUT="$QA/QA_ONLY_NOT_FOR_CLINICAL_INSTALL_HEP037_aarch64.dmg"
mkdir -p "$QA"
[[ ! -e "$BACKUP" && ! -e "$STAGED" && ! -e "$OUTPUT" ]] || exit 3
[[ -d "$ASSETS" && -s "$CANDIDATE/runtime-manifest.sha256" ]] || exit 3
[[ $(df -k "$ROOT" | awk 'NR==2{print $4}') -ge 8388608 ]] || {
  echo 'REFUSE_QA_LOW_DISK'; exit 3;
}
node "$ROOT/desktop/scripts/check-runtime.mjs" "$CANDIDATE" >/dev/null
node "$ROOT/desktop/scripts/check-runtime.mjs" "$ASSETS/1.0.0" >/dev/null
node - "$CANDIDATE/VERSION.json" "$ROOT" <<'JS'
const fs = require('node:fs')
const cp = require('node:child_process')
const candidate = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))
const root = process.argv[3]
const head = cp.execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim()
const dirty = cp.execFileSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' }).trim()
if (dirty || candidate.commitSha !== head) {
  process.stderr.write('QA_SOURCE_OR_RUNTIME_NOT_FROZEN\n')
  process.exit(3)
}
JS
[[ "$(tail -1 "$CANDIDATE/sql/migrations.manifest" | cut -d'|' -f1)" == 037 ]] || exit 3
[[ "$(tail -1 "$ASSETS/1.0.0/sql/migrations.manifest" | cut -d'|' -f1)" == 036 ]] || exit 3
mkdir "$LOCK"
CHANGED=0
restore() {
  code=$?
  trap - EXIT INT TERM
  set +e
  if [[ "$CHANGED" == 1 && -d "$BACKUP" ]]; then
    if [[ -d "$ASSETS" ]]; then
      mv "$ASSETS" "$STAGED" || { echo 'RESTORE_STAGE_FAILED' >&2; exit 95; }
    fi
    mv "$BACKUP" "$ASSETS" || { echo 'ORIGINAL_ASSET_RESTORE_FAILED' >&2; exit 95; }
    node "$ROOT/desktop/scripts/check-runtime.mjs" "$ASSETS/1.0.0" >/dev/null || exit 95
    [[ "$(tail -1 "$ASSETS/1.0.0/sql/migrations.manifest" | cut -d'|' -f1)" == 036 ]] || exit 95
    echo 'ORIGINAL_TAURI_RUNTIME_036_RESTORED=PASS'
  fi
  rmdir "$LOCK" >/dev/null 2>&1 || true
  exit "$code"
}
trap restore EXIT INT TERM
if [[ -f "$TARGET" ]]; then
  previous="$QA/old-target-dmg-before-qa.dmg"
  [[ ! -e "$previous" ]] || exit 3
  cp -p "$TARGET" "$previous"
fi
CHANGED=1
mv "$ASSETS" "$BACKUP"
mkdir "$ASSETS"
cp -pR "$CANDIDATE" "$ASSETS/1.0.0"
if [[ -f "$BACKUP/README.txt" ]]; then cp -p "$BACKUP/README.txt" "$ASSETS/README.txt"; fi
find "$ASSETS" -type d -exec chmod a+rx {} +
find "$ASSETS" -type f -exec chmod a+r {} +
node "$ROOT/desktop/scripts/check-runtime.mjs" "$ASSETS/1.0.0" >/dev/null
[[ "$(tail -1 "$ASSETS/1.0.0/sql/migrations.manifest" | cut -d'|' -f1)" == 037 ]] || exit 4
export PATH="$HOME/.rustup/toolchains/stable-aarch64-apple-darwin/bin:$PATH"
export CARGO_NET_OFFLINE=true
export APPLE_SIGNING_IDENTITY=-
umask 022
(
  cd "$ROOT/desktop/launcher"
  pnpm tauri build --bundles app,dmg --features studio-internal --config src-tauri/tauri.internal.conf.json
) > "$QA/qa-037-current-tauri-build.log" 2>&1
[[ -s "$TARGET" && -d "$APP" ]] || { echo 'QA_DMG_MISSING'; exit 4; }
codesign --verify --deep --strict "$APP" >/dev/null
hdiutil verify "$TARGET" > "$QA/qa-037-dmg-integrity.log" 2>&1
cp -p "$TARGET" "$OUTPUT"
shasum -a 256 "$OUTPUT" > "$QA/qa-037-dmg.sha256"
echo 'QA_ONLY_HEP037_DMG_BUILT_AND_VALIDATED=PASS'
echo "UNPUBLISHED_QA_INSTALLER=$OUTPUT"
echo 'FORMAL_RELEASE_APPROVED=false'
echo 'DO_NOT_INSTALL_IN_CLINICAL_PRODUCTION=true'
