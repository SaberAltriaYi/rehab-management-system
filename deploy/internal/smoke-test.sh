#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ENV_FILE=${ENV_FILE_OVERRIDE:-"$SCRIPT_DIR/.env"}
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT HUP INT TERM

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

pass() {
  echo "PASS: $1"
}

[ -f "$ENV_FILE" ] || fail "缺少 deploy/internal/.env"

env_value() {
  awk -F= -v key="$1" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE"
}

app_port=${APP_PORT:-$(env_value APP_PORT)}
app_port=${app_port:-8080}
tls_port=${TLS_PORT:-$(env_value TLS_PORT)}
tls_port=${tls_port:-8443}
bind_address=${BIND_ADDRESS:-$(env_value BIND_ADDRESS)}
bind_address=${bind_address:-127.0.0.1}
base_url=${BASE_URL:-https://$bind_address:$tls_port}
http_url=${HTTP_URL:-http://$bind_address:$app_port}
ca_cert="$SCRIPT_DIR/certs/ca.crt"
[ -f "$ca_cert" ] || fail "缺少内部 HTTPS CA 证书"

secure_curl() {
  curl --cacert "$ca_cert" "$@"
}

attempt=0
all_healthy=false
while [ "$attempt" -lt 60 ]; do
  all_healthy=true
  for service in mysql redis server admin; do
    container_id=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q "$service")
    [ -n "$container_id" ] || {
      all_healthy=false
      continue
    }
    running=$(docker inspect --format '{{.State.Running}}' "$container_id")
    health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id")
    [ "$running" = "true" ] && [ "$health" = "healthy" ] || all_healthy=false
  done
  [ "$all_healthy" = true ] && break
  attempt=$((attempt + 1))
  sleep 5
done
[ "$all_healthy" = true ] || fail "Compose 服务未在 5 分钟内全部 healthy"
pass "四个 Compose 服务均为 healthy"

for service in mysql redis server; do
  container_id=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q "$service")
  published=$(docker inspect --format '{{range $port, $bindings := .NetworkSettings.Ports}}{{if $bindings}}{{$port}}{{end}}{{end}}' "$container_id")
  [ -z "$published" ] || fail "$service 意外发布了主机端口：$published"
done
pass "MySQL、Redis 与后端没有发布主机端口"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T server \
  curl --fail --silent http://127.0.0.1:48080/actuator/health \
  | grep -q '"status":"UP"' || fail "后端内部健康检查失败"
pass "后端内部健康检查通过"

secure_curl --fail --silent --show-error --dump-header "$TMP_DIR/headers" \
  --output "$TMP_DIR/index.html" "$base_url/"
grep -qi '^Strict-Transport-Security:' "$TMP_DIR/headers" \
  || fail "缺少 Strict-Transport-Security"
grep -qi '^X-Content-Type-Options: nosniff' "$TMP_DIR/headers" \
  || fail "缺少 X-Content-Type-Options"
grep -qi '^Content-Security-Policy:' "$TMP_DIR/headers" \
  || fail "缺少 Content-Security-Policy"
grep -qi '^Permissions-Policy:' "$TMP_DIR/headers" \
  || fail "缺少 Permissions-Policy"
if grep -Eqi '^Server: nginx/[0-9]' "$TMP_DIR/headers"; then
  fail "Nginx 响应泄露了精确版本"
fi
grep -q '<div id="app"' "$TMP_DIR/index.html" \
  || fail "管理后台入口内容异常"
pass "管理后台与安全响应头正常"

redirect_headers="$TMP_DIR/redirect-headers"
curl --silent --show-error --head "$http_url/" > "$redirect_headers"
grep -q '^HTTP/.* 308' "$redirect_headers" || fail "HTTP 入口未返回 308 HTTPS 重定向"
grep -qi "^Location: https://$bind_address:$tls_port/" "$redirect_headers" \
  || fail "HTTP 入口重定向目标不是内部 HTTPS"
pass "HTTP 仅用于跳转到 HTTPS"

actuator_code=$(secure_curl --silent --output "$TMP_DIR/actuator" --write-out '%{http_code}' \
  "$base_url/admin-api/actuator/health")
[ "$actuator_code" = "404" ] || fail "外部 actuator 应返回 404，实际 $actuator_code"
pass "外部 actuator 已封堵"

api_code=$(secure_curl --silent --output "$TMP_DIR/api" --write-out '%{http_code}' \
  "$base_url/admin-api/rehab/patient/page?pageNo=1&pageSize=1")
[ "$api_code" = "200" ] || fail "未登录 API HTTP 状态异常：$api_code"
grep -q '"code":401' "$TMP_DIR/api" || fail "未登录康复 API 未被认证拦截"
pass "康复 API 未登录访问被拦截"

"$SCRIPT_DIR/check-database.sh"

timings="$TMP_DIR/timings"
: > "$timings"
counter=0
while [ "$counter" -lt 5 ]; do
  secure_curl --silent --output /dev/null --write-out '%{time_total}\n' "$base_url/" >> "$timings"
  counter=$((counter + 1))
done
average=$(awk '{sum += $1} END {if (NR) printf "%.3f", sum / NR; else print "999"}' "$timings")
awk -v value="$average" 'BEGIN {exit !(value < 2.0)}' \
  || fail "首页本机平均响应 ${average}s，超过 2s 门槛"
pass "首页本机 5 次平均响应 ${average}s"

echo "内部部署冒烟测试通过"
