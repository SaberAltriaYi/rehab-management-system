#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUTPUT_JAR="${PROJECT_ROOT}/yudao-server/target/yudao-server.jar"

command -v mvn >/dev/null 2>&1 || {
  echo "ERROR: mvn is required" >&2
  exit 1
}
command -v rsync >/dev/null 2>&1 || {
  echo "ERROR: rsync is required" >&2
  exit 1
}
command -v jar >/dev/null 2>&1 || {
  echo "ERROR: jar is required" >&2
  exit 1
}

# Mockito/Byte Buddy in the current test reactor does not support the host's JDK 23.
# Match the release workflow (JDK 17) instead of producing a misleading package.
MAVEN_JAVA_MAJOR="$(mvn -version 2>&1 | sed -nE 's/^Java version: ([0-9]+).*/\1/p' | head -n 1)"
if [[ "${MAVEN_JAVA_MAJOR}" != "17" ]]; then
  echo "ERROR: the isolated desktop backend build requires JDK 17; set JAVA_HOME to a JDK 17 installation (Maven is using ${MAVEN_JAVA_MAJOR:-unknown})." >&2
  exit 1
fi

# Never allow a failed isolated build to leave a previously produced JAR in
# the packaging input path. This path is generated output, not source data.
rm -f -- "${OUTPUT_JAR}"

BUILD_PARENT="${TMPDIR:-/tmp}"
BUILD_PARENT="${BUILD_PARENT%/}"
BUILD_ROOT="$(mktemp -d "${BUILD_PARENT}/rehab-server-build.XXXXXX")"
cleanup() {
  rm -rf -- "${BUILD_ROOT}"
}
trap cleanup EXIT

echo "Staging backend source in ${BUILD_ROOT}"
# An allowlist avoids copying unrelated desktop assets and cloud-backed media
# (which may stall reads or introduce private data into the staging tree).
# Exclude generated outputs before the module includes: rsync applies the
# first matching filter rule.
rsync -a \
  --exclude='.git/' \
  --exclude='.idea/' \
  --exclude='.env' \
  --exclude='.env.*' \
  --exclude='.flattened-pom*' \
  --exclude='target/' \
  --exclude='node_modules/' \
  --exclude='dist/' \
  --exclude='dist-internal/' \
  --exclude='* 2.*' \
  --include='/pom.xml' \
  --include='/lombok.config' \
  --include='/.mvn/***' \
  --include='/yudao-dependencies/***' \
  --include='/yudao-framework/***' \
  --include='/yudao-module-*/***' \
  --include='/yudao-server/***' \
  --exclude='*' \
  "${PROJECT_ROOT}/" "${BUILD_ROOT}/"

(
  cd "${BUILD_ROOT}"
  mvn -pl yudao-server -am -DskipTests package
)

BUILT_JAR="${BUILD_ROOT}/yudao-server/target/yudao-server.jar"
if [[ ! -s "${BUILT_JAR}" ]]; then
  echo "ERROR: backend JAR was not produced" >&2
  exit 1
fi
if jar tf "${BUILT_JAR}" | grep -Eq '(^|/)[^/]+ [0-9]+\.(class|xml|yml|yaml)$'; then
  echo "ERROR: backend JAR contains a synchronized conflict copy" >&2
  exit 1
fi

mkdir -p "$(dirname "${OUTPUT_JAR}")"
install -m 0644 "${BUILT_JAR}" "${OUTPUT_JAR}"
echo "Backend JAR ready: ${OUTPUT_JAR}"
shasum -a 256 "${OUTPUT_JAR}"
