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

BUILD_PARENT="${TMPDIR:-/tmp}"
BUILD_PARENT="${BUILD_PARENT%/}"
BUILD_ROOT="$(mktemp -d "${BUILD_PARENT}/rehab-server-build.XXXXXX")"
cleanup() {
  rm -rf -- "${BUILD_ROOT}"
}
trap cleanup EXIT

echo "Staging backend source in ${BUILD_ROOT}"
rsync -a \
  --exclude='.git/' \
  --exclude='.idea/' \
  --exclude='.flattened-pom*' \
  --exclude='target/' \
  --exclude='node_modules/' \
  --exclude='dist/' \
  --exclude='dist-internal/' \
  --exclude='/yudao-ui/' \
  --exclude='/data/' \
  --exclude='/backups/' \
  --exclude='/deploy/internal/.env' \
  --exclude='/deploy/internal/certs/' \
  --exclude='/deploy/internal/secrets/' \
  --exclude='* 2.*' \
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
