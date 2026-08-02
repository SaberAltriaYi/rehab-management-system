#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
INTERNAL_DIR="$PROJECT_DIR/deploy/internal"
ENV_FILE="$INTERNAL_DIR/.env"
COMPOSE_FILE="$INTERNAL_DIR/docker-compose.yml"
MARKER_FILE="$SCRIPT_DIR/.installed"
FIRST_LOGIN_FILE="$SCRIPT_DIR/FIRST_LOGIN.txt"
REQUESTED_IP=${1:-}
ADMIN_PASSWORD_MIN_LENGTH=12
ADMIN_PASSWORD_MAX_LENGTH=16

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

info() {
  echo "INFO: $1"
}

random_hex() {
  openssl rand -hex "$1"
}

detect_lan_ip() {
  if [ -n "$REQUESTED_IP" ]; then
    printf '%s\n' "$REQUESTED_IP"
    return
  fi
  if command -v ipconfig >/dev/null 2>&1; then
    default_interface=$(route get default 2>/dev/null | awk '/interface:/{print $2; exit}')
    [ -n "${default_interface:-}" ] && ipconfig getifaddr "$default_interface" 2>/dev/null && return
  fi
  if command -v ip >/dev/null 2>&1; then
    ip route get 1.1.1.1 2>/dev/null | awk '{
      for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit }
    }' && return
  fi
  hostname -I 2>/dev/null | awk '{print $1}'
}

validate_ipv4() {
  case "$1" in
    ''|0.0.0.0|::*|*[!0-9.]*) return 1 ;;
  esac
  old_ifs=$IFS
  IFS=.
  set -- $1
  IFS=$old_ifs
  [ "$#" -eq 4 ] || return 1
  for octet in "$@"; do
    [ "$octet" -ge 0 ] 2>/dev/null && [ "$octet" -le 255 ] || return 1
  done
}

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

wait_for_mysql() {
  attempt=0
  while [ "$attempt" -lt 90 ]; do
    if compose exec -T mysql sh -c \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin ping -h 127.0.0.1 -uroot --silent' \
      >/dev/null 2>&1; then
      return
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
  fail "MySQL 在 180 秒内未就绪"
}

wait_for_https() {
  attempt=0
  while [ "$attempt" -lt 60 ]; do
    if curl --fail --silent \
      --cacert "$INTERNAL_DIR/certs/ca.crt" \
      "https://$BIND_ADDRESS:8443/" >/dev/null 2>&1; then
      return
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
  fail "管理端在 120 秒内未就绪"
}

command -v docker >/dev/null 2>&1 || fail "请先安装 Docker Desktop 或 Docker Engine"
docker compose version >/dev/null 2>&1 || fail "Docker Compose 不可用"
command -v openssl >/dev/null 2>&1 || fail "缺少 openssl"
command -v curl >/dev/null 2>&1 || fail "缺少 curl"
[ -f "$PROJECT_DIR/yudao-server/target/yudao-server.jar" ] \
  || fail "发布包缺少 yudao-server.jar"
[ -f "$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3-app/dist-internal/index.html" ] \
  || fail "发布包缺少前端 dist-internal"

BIND_ADDRESS=$(detect_lan_ip)
validate_ipv4 "$BIND_ADDRESS" || fail "无法识别局域网 IPv4；请执行 ./install.sh 192.168.x.x"

if [ ! -f "$ENV_FILE" ]; then
  umask 077
  DB_PASSWORD=$(random_hex 24)
  MYSQL_ROOT_PASSWORD=$(random_hex 24)
  REDIS_PASSWORD=$(random_hex 24)
  {
    echo "COMPOSE_PROJECT_NAME=rehab-lan"
    echo "TZ=Asia/Shanghai"
    echo "BIND_ADDRESS=$BIND_ADDRESS"
    echo "LAN_HOSTNAME=rehab.local"
    echo "APP_PORT=8080"
    echo "TLS_PORT=8443"
    echo "DB_NAME=ruoyi-vue-pro"
    echo "DB_USERNAME=yudao"
    echo "DB_PASSWORD=$DB_PASSWORD"
    echo "MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD"
    echo "REDIS_PASSWORD=$REDIS_PASSWORD"
    echo "BACKUP_KEY_FILE=deploy/internal/secrets/backup.key"
    echo "JAVA_OPTS=-Xms256m -Xmx768m -Djava.security.egd=file:/dev/./urandom -Dsun.io.useCanonCaches=false"
  } > "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  info "已生成随机基础设施密钥"
else
  BIND_ADDRESS=$(awk -F= '$1 == "BIND_ADDRESS" {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE")
  validate_ipv4 "$BIND_ADDRESS" || fail ".env 中 BIND_ADDRESS 无效"
fi

if [ ! -f "$INTERNAL_DIR/secrets/backup.key" ]; then
  "$INTERNAL_DIR/generate-backup-key.sh"
fi

if [ ! -f "$INTERNAL_DIR/certs/server.crt" ]; then
  TLS_HOSTNAME=rehab.local "$INTERNAL_DIR/generate-tls.sh" "$BIND_ADDRESS"
fi

project_name=$(awk -F= '$1 == "COMPOSE_PROJECT_NAME" {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE")
project_name=${project_name:-rehab-lan}
if [ ! -f "$MARKER_FILE" ] \
  && docker volume ls --format '{{.Name}}' | grep -Fxq "${project_name}_mysql-data"; then
  fail "发现已有数据库卷但缺少安装标记；为防止重置管理员密码，请先备份并按恢复流程接管"
fi

info "构建适配当前 CPU 架构的管理端和后端镜像"
compose build --pull server admin
compose up -d mysql redis
wait_for_mysql

if [ ! -f "$MARKER_FILE" ]; then
  # 新管理员密码限制为 4–16 字符；16 个十六进制字符提供 64 位临时随机熵。
  ADMIN_PASSWORD=$(random_hex 8)
  [ "${#ADMIN_PASSWORD}" -ge "$ADMIN_PASSWORD_MIN_LENGTH" ] \
    && [ "${#ADMIN_PASSWORD}" -le "$ADMIN_PASSWORD_MAX_LENGTH" ] \
    || fail "生成的管理员密码不符合登录接口长度限制"
  ADMIN_HASH=$(
    printf '%s\n' "$ADMIN_PASSWORD" \
      | docker run --rm -i --entrypoint java rehab-internal-server:latest \
        -Dloader.main=cn.iocoder.yudao.server.PasswordHashCli \
        -cp /app/app.jar org.springframework.boot.loader.PropertiesLauncher \
      | tail -n 1 | tr -d '\r\n'
  )
  [ -n "$ADMIN_HASH" ] || fail "生成管理员 BCrypt 密码失败"
  printf "UPDATE system_users SET password='%s', updater='lan-installer', update_time=NOW() WHERE tenant_id=1 AND username='admin' AND deleted=b'0';\n" \
    "$ADMIN_HASH" \
    | compose exec -T mysql sh -c \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE" --batch --raw'

  CA_SHA256=$(openssl dgst -sha256 "$INTERNAL_DIR/certs/ca.crt" | awk '{print $NF}')
  umask 077
  {
    echo "运动康复评估与业务管理系统 V1.0 首次登录信息"
    echo
    echo "访问地址：https://$BIND_ADDRESS:8443"
    echo "备用主机名：https://rehab.local:8443（需路由器 DNS 或设备 hosts 支持）"
    echo "CA 下载：http://$BIND_ADDRESS:8080/ca.crt"
    echo "CA 文件 SHA-256：$CA_SHA256"
    echo "租户：工作室内部"
    echo "用户名：admin"
    echo "临时随机密码：$ADMIN_PASSWORD"
    echo
    echo "首次登录后请立即修改密码，并删除本文件。"
  } > "$FIRST_LOGIN_FILE"
  chmod 600 "$FIRST_LOGIN_FILE"
  {
    echo "installed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "bind_address=$BIND_ADDRESS"
    echo "compose_project=$project_name"
  } > "$MARKER_FILE"
  chmod 600 "$MARKER_FILE"
fi

compose up -d server admin
wait_for_https
"$INTERNAL_DIR/check-database.sh"
"$INTERNAL_DIR/smoke-test.sh"

echo
echo "PASS: 运动康复评估与业务管理系统 V1.0 局域网部署完成"
echo "访问地址：https://$BIND_ADDRESS:8443"
echo "CA 下载：http://$BIND_ADDRESS:8080/ca.crt"
if [ -f "$FIRST_LOGIN_FILE" ]; then
  echo "首次登录信息：$FIRST_LOGIN_FILE"
fi
