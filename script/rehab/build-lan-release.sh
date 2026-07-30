#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
VERSION=${1:-rehab-lan-dev}
OUTPUT_DIR=${OUTPUT_DIR:-"$PROJECT_DIR/deploy/lan/release-output"}
BUNDLE_NAME="rehab-management-${VERSION}"
STAGE_ROOT=$(mktemp -d)
STAGE_DIR="$STAGE_ROOT/$BUNDLE_NAME"

cleanup() {
  rm -rf "$STAGE_ROOT"
}
trap cleanup EXIT HUP INT TERM

sha256_file() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    sha256sum "$1" | awk '{print $1}'
  fi
}

copy_file() {
  source_file=$1
  destination_file="$STAGE_DIR/$source_file"
  mkdir -p "$(dirname -- "$destination_file")"
  cp "$PROJECT_DIR/$source_file" "$destination_file"
}

[ -f "$PROJECT_DIR/yudao-server/target/yudao-server.jar" ] || {
  echo "FAIL: 缺少后端 JAR" >&2
  exit 1
}
[ -f "$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3-app/dist-internal/index.html" ] || {
  echo "FAIL: 缺少前端 dist-internal" >&2
  exit 1
}

mkdir -p "$STAGE_DIR" "$OUTPUT_DIR"

for root_file in \
  .dockerignore DEPLOYMENT_CHECKLIST.md \
  install.sh install.ps1 rehabctl.sh rehabctl.ps1; do
  copy_file "$root_file"
done

mkdir -p "$STAGE_DIR/deploy/internal" "$STAGE_DIR/deploy/lan"
rsync -a \
  --exclude '.env' \
  --include 'certs/' \
  --include 'certs/README.md' \
  --exclude 'certs/*' \
  --include 'secrets/' \
  --include 'secrets/README.md' \
  --exclude 'secrets/*' \
  "$PROJECT_DIR/deploy/internal/" "$STAGE_DIR/deploy/internal/"
rsync -a \
  --exclude '.installed' \
  --exclude 'FIRST_LOGIN.txt' \
  --exclude 'release-output/' \
  "$PROJECT_DIR/deploy/lan/" "$STAGE_DIR/deploy/lan/"

mkdir -p "$STAGE_DIR/script/rehab"
rsync -a --exclude 'output/' "$PROJECT_DIR/script/rehab/" "$STAGE_DIR/script/rehab/"

copy_file "sql/mysql/ruoyi-vue-pro.sql"
copy_file "sql/mysql/quartz.sql"
awk -F'|' '!/^#/ && NF >= 4 {print $3}' "$PROJECT_DIR/deploy/internal/migrations.manifest" \
  | while IFS= read -r migration_file; do
      copy_file "$migration_file"
    done

copy_file "yudao-server/src/main/resources/application-internal.yaml"
mkdir -p "$STAGE_DIR/yudao-server/target"
cp "$PROJECT_DIR/yudao-server/target/yudao-server.jar" \
  "$STAGE_DIR/yudao-server/target/yudao-server.jar"

mkdir -p "$STAGE_DIR/yudao-ui/yudao-ui-admin-vue3-app"
cp "$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3-app/.env.internal" \
  "$STAGE_DIR/yudao-ui/yudao-ui-admin-vue3-app/.env.internal"
cp "$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3-app/package.json" \
  "$STAGE_DIR/yudao-ui/yudao-ui-admin-vue3-app/package.json"
cp "$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3-app/pnpm-lock.yaml" \
  "$STAGE_DIR/yudao-ui/yudao-ui-admin-vue3-app/pnpm-lock.yaml"
cp -R "$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3-app/dist-internal" \
  "$STAGE_DIR/yudao-ui/yudao-ui-admin-vue3-app/dist-internal"

backend_commit=$(git -C "$PROJECT_DIR" rev-parse HEAD)
frontend_commit=$(git -C "$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3-app" rev-parse HEAD 2>/dev/null || echo monorepo)
jar_sha=$(sha256_file "$PROJECT_DIR/yudao-server/target/yudao-server.jar")
{
  echo "# 康复管理系统局域网发布清单"
  echo
  echo "- 版本：\`$VERSION\`"
  echo "- 后端/部署提交：\`$backend_commit\`"
  echo "- 前端提交：\`$frontend_commit\`"
  echo "- JAR SHA-256：\`$jar_sha\`"
  echo "- 数据库迁移：001–019"
  echo "- AI：关闭"
  echo "- 默认业务数据：空"
  echo "- 管理员密码：安装时随机生成"
  echo
  echo "解压后在 macOS/Linux 执行 \`./install.sh\`，Windows 执行 \`.\\install.ps1\`。"
} > "$STAGE_DIR/RELEASE-MANIFEST.md"

(
  cd "$STAGE_DIR"
  find . -type f ! -name CHECKSUMS.sha256 -print | LC_ALL=C sort \
    | while IFS= read -r release_file; do
        if command -v shasum >/dev/null 2>&1; then
          shasum -a 256 "$release_file"
        else
          sha256sum "$release_file"
        fi
      done > CHECKSUMS.sha256
)

chmod +x \
  "$STAGE_DIR/install.sh" \
  "$STAGE_DIR/rehabctl.sh" \
  "$STAGE_DIR/deploy/lan/install.sh" \
  "$STAGE_DIR/deploy/lan/rehabctl.sh" \
  "$STAGE_DIR/deploy/lan/container-generate-tls.sh"

archive="$OUTPUT_DIR/$BUNDLE_NAME.tar.gz"
COPYFILE_DISABLE=1 tar -C "$STAGE_ROOT" -czf "$archive" "$BUNDLE_NAME"
archive_sha=$(sha256_file "$archive")
printf '%s  %s\n' "$archive_sha" "$(basename -- "$archive")" > "${archive}.sha256"

echo "PASS: 已生成局域网发布包"
echo "$archive"
