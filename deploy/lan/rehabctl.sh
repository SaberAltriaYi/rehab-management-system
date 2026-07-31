#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ENV_FILE="$PROJECT_DIR/deploy/internal/.env"
COMPOSE_FILE="$PROJECT_DIR/deploy/internal/docker-compose.yml"
ACTION=${1:-status}

[ -f "$ENV_FILE" ] || {
  [ "$ACTION" = "install" ] && exec "$SCRIPT_DIR/install.sh" "${2:-}"
  echo "FAIL: 尚未安装，请先执行 ./install.sh" >&2
  exit 1
}

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

case "$ACTION" in
  install) exec "$SCRIPT_DIR/install.sh" "${2:-}" ;;
  start) compose up -d ;;
  stop) compose stop ;;
  restart) compose restart ;;
  status) compose ps ;;
  logs) compose logs --since=30m "${2:-server}" ;;
  check)
    "$PROJECT_DIR/deploy/internal/check-database.sh"
    "$PROJECT_DIR/deploy/internal/smoke-test.sh"
    ;;
  backup) exec "$PROJECT_DIR/deploy/internal/backup.sh" ;;
  restore)
    [ -n "${2:-}" ] || { echo "用法：./rehabctl.sh restore backups/rehab-时间" >&2; exit 1; }
    CONFIRM_RESTORE=RESTORE-REHAB-INTERNAL \
      exec "$PROJECT_DIR/deploy/internal/restore.sh" "$2"
    ;;
  update)
    "$PROJECT_DIR/deploy/internal/backup.sh"
    compose build --pull server admin
    compose up -d
    "$PROJECT_DIR/deploy/internal/check-database.sh"
    "$PROJECT_DIR/deploy/internal/smoke-test.sh"
    ;;
  *)
    echo "用法：./rehabctl.sh install|start|stop|restart|status|logs|check|backup|restore|update" >&2
    exit 1
    ;;
esac
