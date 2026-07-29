#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ENV_FILE="$SCRIPT_DIR/.env"
SERVER_JAR="$PROJECT_DIR/yudao-server/target/yudao-server.jar"
ADMIN_DIST="$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3-app/dist-internal/index.html"
REHAB_TENANT_MIGRATION="$PROJECT_DIR/sql/mysql/rehab-step9-tenant-v1.sql"
REHAB_INTEGRITY_MIGRATION="$PROJECT_DIR/sql/mysql/rehab-step10-integrity-v1.sql"
AUTH_HARDENING_MIGRATION="$PROJECT_DIR/sql/mysql/rehab-step11-auth-hardening-v1.sql"
INTERNAL_LOGIN_MIGRATION="$PROJECT_DIR/sql/mysql/rehab-step12-internal-login-client-v1.sql"
CA_CERT="$SCRIPT_DIR/certs/ca.crt"
SERVER_CERT="$SCRIPT_DIR/certs/server.crt"
SERVER_KEY="$SCRIPT_DIR/certs/server.key"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

pass() {
  echo "PASS: $1"
}

command -v docker >/dev/null 2>&1 || fail "未安装 Docker"
docker compose version >/dev/null 2>&1 || fail "Docker Compose 不可用"
pass "Docker 与 Compose 可用"

available_kb=$(df -Pk "$PROJECT_DIR" | awk 'NR == 2 {print $4}')
minimum_kb=$((20 * 1024 * 1024))
[ "${available_kb:-0}" -ge "$minimum_kb" ] \
  || fail "部署磁盘可用空间不足 20 GiB"
pass "部署磁盘可用空间不少于 20 GiB"

[ -f "$ENV_FILE" ] || fail "缺少 deploy/internal/.env，请从 .env.example 复制后填写"
if grep -q "CHANGE_ME\\|^DB_PASSWORD=$\\|^MYSQL_ROOT_PASSWORD=$\\|^REDIS_PASSWORD=$" "$ENV_FILE"; then
  fail ".env 仍包含 CHANGE_ME 占位密码"
fi
env_permissions=$(ls -ld "$ENV_FILE" | cut -c5-10)
[ "$env_permissions" = "------" ] || fail ".env 对组或其他用户可读，请执行 chmod 600 deploy/internal/.env"

env_value() {
  awk -F= -v key="$1" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE"
}

db_password=$(env_value DB_PASSWORD)
mysql_root_password=$(env_value MYSQL_ROOT_PASSWORD)
redis_password=$(env_value REDIS_PASSWORD)
bind_address=$(env_value BIND_ADDRESS)
tls_port=$(env_value TLS_PORT)
backup_key_file=$(env_value BACKUP_KEY_FILE)
[ "${#db_password}" -ge 24 ] || fail "DB_PASSWORD 长度必须至少为 24"
[ "${#mysql_root_password}" -ge 24 ] || fail "MYSQL_ROOT_PASSWORD 长度必须至少为 24"
[ "${#redis_password}" -ge 24 ] || fail "REDIS_PASSWORD 长度必须至少为 24"
[ "$db_password" != "$mysql_root_password" ] || fail "DB_PASSWORD 与 MYSQL_ROOT_PASSWORD 不能相同"
[ "$db_password" != "$redis_password" ] || fail "DB_PASSWORD 与 REDIS_PASSWORD 不能相同"
[ "$mysql_root_password" != "$redis_password" ] || fail "MYSQL_ROOT_PASSWORD 与 REDIS_PASSWORD 不能相同"
[ -n "$bind_address" ] || fail "必须设置 BIND_ADDRESS"
case "$bind_address" in
  0.0.0.0|::|'[::]') fail "生产环境不得绑定所有网络接口，请使用明确局域网 IP 或 127.0.0.1" ;;
esac
[ "${tls_port:-8443}" = "8443" ] || fail "当前 Nginx HTTPS 跳转固定使用 TLS_PORT=8443"
pass "部署环境文件已配置"

[ -f "$CA_CERT" ] || fail "缺少内部 CA，请执行 deploy/internal/generate-tls.sh"
[ -f "$SERVER_CERT" ] || fail "缺少服务器证书，请执行 deploy/internal/generate-tls.sh"
[ -f "$SERVER_KEY" ] || fail "缺少服务器私钥，请执行 deploy/internal/generate-tls.sh"
[ "$(stat -f '%Lp' "$SERVER_KEY")" = "600" ] || fail "服务器私钥权限必须为 600"
openssl verify -CAfile "$CA_CERT" "$SERVER_CERT" >/dev/null || fail "服务器证书链校验失败"
openssl x509 -in "$SERVER_CERT" -noout -checkip "$bind_address" >/dev/null \
  || fail "服务器证书不包含 BIND_ADDRESS=$bind_address"
pass "内部 HTTPS 证书有效并匹配绑定地址"

case "$backup_key_file" in
  /*) backup_key_path="$backup_key_file" ;;
  *) backup_key_path="$PROJECT_DIR/$backup_key_file" ;;
esac
[ -f "$backup_key_path" ] || fail "缺少备份加密密钥，请执行 deploy/internal/generate-backup-key.sh"
[ "$(stat -f '%Lp' "$backup_key_path")" = "600" ] || fail "备份加密密钥权限必须为 600"
[ "$(wc -c < "$backup_key_path" | tr -d ' ')" -ge 48 ] || fail "备份加密密钥强度不足"
pass "备份加密密钥已配置"

if command -v fdesetup >/dev/null 2>&1; then
  fdesetup status | grep -q 'FileVault is On' || fail "部署机必须开启 FileVault"
  pass "部署机 FileVault 已开启"
fi

[ -f "$SERVER_JAR" ] || fail "缺少后端产物，请先执行 Maven package"
[ -f "$ADMIN_DIST" ] || fail "缺少前端 dist-internal，请先执行 pnpm build:internal"
if rg -l -i '@form-create/(designer|element-ui|component-wangeditor)|wangeditor[ /-]?4\.7\.15' \
  "$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3-app/dist-internal" >/dev/null; then
  fail "内部前端产物仍包含已停用的高风险表单编辑依赖"
fi
pass "前后端构建产物存在"

[ -f "$REHAB_TENANT_MIGRATION" ] || fail "缺少康复业务表多租户迁移脚本"
[ -f "$REHAB_INTEGRITY_MIGRATION" ] || fail "缺少康复业务表关系完整性迁移脚本"
[ -f "$AUTH_HARDENING_MIGRATION" ] || fail "缺少内部认证安全迁移脚本"
[ -f "$INTERNAL_LOGIN_MIGRATION" ] || fail "缺少内部登录客户端迁移脚本"
grep -q "rehab-step9-tenant-v1.sql" "$SCRIPT_DIR/docker-compose.yml" \
  || fail "Compose 未挂载康复业务表多租户迁移脚本"
grep -q "rehab-step10-integrity-v1.sql" "$SCRIPT_DIR/docker-compose.yml" \
  || fail "Compose 未挂载康复业务表关系完整性迁移脚本"
grep -q "rehab-step11-auth-hardening-v1.sql" "$SCRIPT_DIR/docker-compose.yml" \
  || fail "Compose 未挂载内部认证安全迁移脚本"
grep -q "rehab-step12-internal-login-client-v1.sql" "$SCRIPT_DIR/docker-compose.yml" \
  || fail "Compose 未挂载内部登录客户端迁移脚本"
grep -q "init-schema-history.sql" "$SCRIPT_DIR/docker-compose.yml" \
  || fail "Compose 未挂载全新数据库迁移账本初始化脚本"
"$SCRIPT_DIR/migrate.sh" verify-files
pass "康复业务表迁移与固定校验和清单已就绪"

grep -q "OPENAI_ENABLE_AI_ANALYSIS: \"false\"" "$SCRIPT_DIR/docker-compose.yml" \
  || fail "Compose 未强制关闭 AI"
grep -q "VITE_REHAB_AI_ENABLED=false" \
  "$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3-app/.env.internal" \
  || fail "前端内部构建未关闭 AI"
pass "AI 开关保持关闭"

grep -q "console-enable: false" "$PROJECT_DIR/yudao-server/src/main/resources/application-internal.yaml" \
  || fail "内部环境未关闭控制台请求参数日志"
pass "敏感请求参数日志已关闭"

grep -q "image: mysql:8.4.10" "$SCRIPT_DIR/docker-compose.yml" \
  || fail "MySQL 镜像未锁定为已验收版本"
grep -q "image: redis:7.4.10-alpine" "$SCRIPT_DIR/docker-compose.yml" \
  || fail "Redis 镜像未锁定为已验收版本"
grep -q "FROM nginx:1.30.4-alpine" "$SCRIPT_DIR/Dockerfile.admin" \
  || fail "Nginx 镜像未锁定为已验收版本"
pass "MySQL、Redis 与 Nginx 镜像版本已锁定"

for script in check-database.sh smoke-test.sh backup.sh restore.sh migrate.sh \
  generate-tls.sh generate-backup-key.sh encrypt-legacy-backup.sh rehearse-restore.sh; do
  [ -x "$SCRIPT_DIR/$script" ] || fail "$script 不可执行"
done
pass "数据库检查、冒烟、备份与恢复脚本可执行"

docker compose --env-file "$ENV_FILE" -f "$SCRIPT_DIR/docker-compose.yml" config --quiet \
  || fail "Compose 配置校验失败"
pass "Compose 配置有效"

echo "部署前预检通过"
