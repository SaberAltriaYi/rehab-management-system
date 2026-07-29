#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ADMIN_DIR="$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3-app"

"$PROJECT_DIR/deploy/internal/preflight.sh"
"$PROJECT_DIR/deploy/internal/check-database.sh"
"$PROJECT_DIR/deploy/internal/smoke-test.sh"

(
  cd "$ADMIN_DIR"
  pnpm audit:prod
)

if rg -l -i '@form-create/(designer|element-ui|component-wangeditor)|wangeditor[ /-]?4\.7\.15' \
  "$ADMIN_DIR/dist-internal" >/dev/null; then
  echo "FAIL: 内部构建仍包含已停用的高风险表单编辑依赖" >&2
  exit 1
fi

echo "PASS: 康复内部版只读发布回归通过"
