#!/usr/bin/env sh
set -eu

TRIVY_BIN=${TRIVY_BIN:-trivy}

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

command -v "$TRIVY_BIN" >/dev/null 2>&1 || [ -x "$TRIVY_BIN" ] \
  || fail "未找到 Trivy；请设置 TRIVY_BIN=/可信路径/trivy"
command -v jq >/dev/null 2>&1 || fail "未找到 jq"

trivy_version=$("$TRIVY_BIN" --version | awk '/^Version:/ {print $2; exit}')
[ "$trivy_version" = "0.72.0" ] || fail "要求经过校验的 Trivy 0.72.0，当前为 ${trivy_version:-unknown}"

report_dir=$(mktemp -d "${TMPDIR:-/tmp}/rehab-security-scan.XXXXXX")
trap 'rm -rf "$report_dir"' EXIT HUP INT TERM

scan_os() {
  image_name=$1
  report_name=$(printf '%s' "$image_name" | tr ':/' '__')
  "$TRIVY_BIN" image --pkg-types os --scanners vuln --severity HIGH,CRITICAL \
    --format json --output "$report_dir/$report_name-os.json" "$image_name"
  finding_count=$(jq '[.Results[]?.Vulnerabilities[]?] | length' "$report_dir/$report_name-os.json")
  [ "$finding_count" -eq 0 ] || fail "$image_name 操作系统包存在 $finding_count 个 High/Critical"
  echo "PASS: $image_name 操作系统包 High/Critical = 0"
}

scan_os "rehab-internal-server:latest"
scan_os "rehab-internal-admin:latest"
scan_os "mysql:8.4.10"
scan_os "redis:7.4.10-alpine"

"$TRIVY_BIN" image --pkg-types library --scanners vuln --severity HIGH,CRITICAL \
  --format json --output "$report_dir/server-library.json" "rehab-internal-server:latest"

jq -r '.Results[]?.Vulnerabilities[]?.VulnerabilityID' "$report_dir/server-library.json" \
  | sort -u > "$report_dir/actual-library-cves.txt"

cat > "$report_dir/accepted-library-cves.txt" <<'EOF'
CVE-2016-1000027
CVE-2024-38816
CVE-2024-38819
CVE-2025-22228
CVE-2025-22235
CVE-2025-41249
CVE-2026-22732
CVE-2026-40973
EOF

unexpected=$(comm -13 "$report_dir/accepted-library-cves.txt" "$report_dir/actual-library-cves.txt")
[ -z "$unexpected" ] || {
  printf '%s\n' "$unexpected" >&2
  fail "发现未登记的 Java High/Critical 漏洞"
}

high_count=$(jq '[.Results[]?.Vulnerabilities[]? | select(.Severity == "HIGH")] | length' \
  "$report_dir/server-library.json")
critical_count=$(jq '[.Results[]?.Vulnerabilities[]? | select(.Severity == "CRITICAL")] | length' \
  "$report_dir/server-library.json")

echo "PASS: Java 库仅包含已登记条件型条目（High=${high_count}, Critical=${critical_count}）"
echo "PASS: 镜像安全扫描通过"
