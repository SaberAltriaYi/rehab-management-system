#!/usr/bin/env sh
# SPDX-FileCopyrightText: 2026 [软件著作权人名称]
# SPDX-License-Identifier: MIT
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
RESULT_FILE=$(mktemp)

cleanup() {
  rm -f "$RESULT_FILE"
}
trap cleanup EXIT HUP INT TERM

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

cd "$PROJECT_DIR"

git ls-files | while IFS= read -r tracked_file; do
  lower_file=$(printf '%s' "$tracked_file" | tr '[:upper:]' '[:lower:]')
  case "$lower_file" in
    */first_login.txt|first_login.txt|\
    *.pem|*.key|*.p12|*.pfx|*.jks|*.keystore|*.bak|*.backup|*.dump|\
    *.sql.gz|*.tar|*.tar.gz|*.tgz|*.zip)
      printf '%s\n' "$tracked_file"
      ;;
    backups/*|*/backups/*|output/playwright/*|*/release-output/*)
      printf '%s\n' "$tracked_file"
      ;;
  esac
done > "$RESULT_FILE"
[ ! -s "$RESULT_FILE" ] || {
  sed 's/^/  /' "$RESULT_FILE" >&2
  fail "Git 跟踪了私钥、备份、首次登录信息、测试输出或发布压缩包"
}

if git grep -n -I -E \
  'BEGIN ([A-Z ]+ )?PRIVATE KEY|gho_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9]{20,}|xox[baprs]-[0-9A-Za-z-]{10,}|MIIC[0-9A-Za-z+/=]{120,}' \
  -- . \
  ':(exclude)script/rehab/check-repository-sensitive-materials.sh' \
  ':(exclude)yudao-ui/yudao-ui-admin-vue3-app/package-lock.json' \
  ':(exclude)yudao-ui/yudao-ui-admin-vue3-app/pnpm-lock.yaml' > "$RESULT_FILE"; then
  sed 's/^/  /' "$RESULT_FILE" >&2
  fail "发现高置信度私钥或访问令牌"
fi

if git grep -n -I -E 'access(Key|Secret)' -- 'sql/*.sql' \
  | grep -v 'REDACTED' > "$RESULT_FILE"; then
  sed 's/^/  /' "$RESULT_FILE" >&2
  fail "数据库初始化脚本仍包含未脱敏的对象存储访问凭据"
fi

git ls-files 'yudao-ui/yudao-ui-admin-vue3-app/.env*' | while IFS= read -r env_file; do
  awk -F= '
    /^[[:space:]]*#/ { next }
    $1 ~ /VITE_APP_DEFAULT_LOGIN_(TENANT|USERNAME|PASSWORD)|VITE_APP_API_ENCRYPT_(REQUEST|RESPONSE)_KEY|VITE_BAIDU_MAP_KEY|VITE_APP_BAIDU_CODE/ {
      value = substr($0, index($0, "=") + 1)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      if (value != "" && value != "\047\047" && value != "\"\"") {
        print FILENAME ":" FNR ":" $0
      }
    }
  ' "$env_file"
done > "$RESULT_FILE"
[ ! -s "$RESULT_FILE" ] || {
  sed 's/^/  /' "$RESULT_FILE" >&2
  fail "前端受版本控制环境文件含默认登录信息、统计代码或密钥"
}

if git grep -n -I -E \
  'admin123|MYSQL_ROOT_PASSWORD[=:][[:space:]]*123456|MASTER_DATASOURCE_PASSWORD[^[:space:]]*123456|SLAVE_DATASOURCE_PASSWORD[^[:space:]]*123456|fadc1bd5db1a1d6f581df60a1807f8ab' \
  -- 'deploy/internal/.env.example' 'deploy/internal/docker-compose.yml' \
  'deploy/lan/install.sh' 'deploy/lan/install.ps1' \
  'script/docker/docker.env' 'script/docker/docker-compose.yml' \
  'yudao-ui/yudao-ui-admin-vue3-app/.env*' \
  'yudao-server/src/main/resources/**' > "$RESULT_FILE"; then
  sed 's/^/  /' "$RESULT_FILE" >&2
  fail "交付配置仍包含弱演示密码"
fi

git ls-files | grep -E '(^|/)(backups?|release-output|playwright-report|test-results)(/|$)' \
  > "$RESULT_FILE" || true
[ ! -s "$RESULT_FILE" ] || {
  sed 's/^/  /' "$RESULT_FILE" >&2
  fail "Git 跟踪了运行期数据或测试报告目录"
}

grep -q '演示数据（患者' sql/mysql/rehab-step2-v1.sql \
  || fail "历史迁移中的合成患者样本未明确标记为演示数据"
[ -f deploy/internal/clean-demo-rehab-data.sql ] \
  || fail "缺少全新部署演示康复数据清理脚本"

echo "PASS: 当前 Git 跟踪内容未发现真实凭据、私钥、备份、首次登录文件或未标记患者样本"
