#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
URL='http://127.0.0.1:17630/'
HEALTH='http://127.0.0.1:17630/health'
if /usr/bin/curl --silent --noproxy '*' --max-time 2 "$HEALTH" | grep -q '"mode": "synthetic-ui-only"'; then
  echo '模式 A 已运行，打开已有本机界面。'
  open "$URL"
  exit 0
fi
if /usr/bin/curl --silent --noproxy '*' --max-time 2 "$URL" >/dev/null; then
  echo '端口 17630 被非模式 A 服务占用；拒绝接管。'
  exit 2
fi
echo '正在启动独立模式 A（不连接正式服务）。'
/usr/bin/env python3 -B server.py --port 17630 &
server_pid=$!
cleanup() {
  kill "$server_pid" 2>/dev/null || true
  wait "$server_pid" 2>/dev/null || true
}
trap cleanup EXIT INT TERM
for i in $(seq 1 50); do
  if /usr/bin/curl --silent --noproxy '*' --max-time 2 "$HEALTH" | grep -q '"mode": "synthetic-ui-only"'; then
    open "$URL"
    wait "$server_pid"
    exit $?
  fi
  if ! kill -0 "$server_pid" 2>/dev/null; then
    echo '测试服务未能启动。'
    wait "$server_pid"
    exit 3
  fi
  sleep 0.1
done
echo '超时，测试服务未就绪。'
exit 4
