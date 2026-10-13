#!/usr/bin/env bash
# Development-only isolated synthetic installation. Never reuse an existing directory.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
[[ "${1:-}" == --synthetic-only && -n "${2:-}" ]] || { echo 'Usage: bash deploy/saas/install-synthetic.sh --synthetic-only /absolute/new/directory'; exit 2; }
STATE="$2"
[[ "$STATE" == /* && ! -e "$STATE" && "$STATE" != *$'\n'* && "$STATE" != *' '* ]] || { echo 'Refusing existing, relative or whitespace-containing state path'; exit 2; }
# Keep generated credentials, certificates and volumes outside the source tree.
# This prevents an accidental install from polluting Git or being packaged.
case "$STATE/" in
  "$ROOT/"*) echo 'Refusing state path inside the source tree'; exit 2 ;;
esac
[[ ! -L "$(dirname "$STATE")" ]] || { echo 'Refusing state path below a symlinked parent'; exit 2; }
for cmd in docker openssl node python3 git; do command -v "$cmd" >/dev/null; done
[[ -s "$ROOT/yudao-server/target/yudao-server.jar" && -s "$ROOT/desktop/build/frontend-build-receipt.json" ]] || { echo 'Build isolated backend and current internal frontend first'; exit 1; }
[[ -z "$(git -C "$ROOT" status --porcelain)" ]] || { echo 'Commit reviewed source before creating an attributable installation'; exit 1; }
SHA="$(git -C "$ROOT" rev-parse HEAD)"
node -e 'const fs=require("fs"); const r=JSON.parse(fs.readFileSync(process.argv[1])); if(r.commitSha!==process.argv[2]) process.exit(1)' "$ROOT/desktop/build/frontend-build-receipt.json" "$SHA"
ENGINE_IMAGE="${REHAB_ENGINE_IMAGE:-}"
if [[ -n "$ENGINE_IMAGE" && ! "$ENGINE_IMAGE" =~ ^ghcr\.io/saberaltriayi/rehab-engine-dev@sha256:[a-f0-9]{64}$ ]]; then
  echo 'Engine requires the approved GHCR repository and immutable sha256 digest'; exit 2
fi
umask 077
mkdir -m 700 "$STATE"
PROJECT="rehab-saas-synthetic-$(openssl rand -hex 6)"
SERVER_IMAGE="rehab-saas-synthetic-server:${SHA:0:12}"
ADMIN_IMAGE="rehab-saas-synthetic-admin:${SHA:0:12}"
DB_PASSWORD="$(openssl rand -hex 24)"
MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
REDIS_PASSWORD="$(openssl rand -hex 24)"
OWNER_PASSWORD="$(openssl rand -base64 12 | tr '+/' '-_')"
read -r APP_PORT TLS_PORT < <(python3 - <<'PORTS'
import socket
sockets=[socket.socket(),socket.socket()]
try:
    for s in sockets:s.bind(('127.0.0.1',0))
    print(*(s.getsockname()[1] for s in sockets))
finally:
    for s in sockets:s.close()
PORTS
)
cat > "$STATE/.env" <<ENV
COMPOSE_PROJECT_NAME=$PROJECT
ROOT=$ROOT
SERVER_IMAGE=$SERVER_IMAGE
ADMIN_IMAGE=$ADMIN_IMAGE
DB_PASSWORD=$DB_PASSWORD
MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD
REDIS_PASSWORD=$REDIS_PASSWORD
APP_PORT=$APP_PORT
TLS_PORT=$TLS_PORT
ENV
openssl req -x509 -newkey rsa:2048 -nodes -days 7 -subj /CN=localhost \
  -addext subjectAltName=DNS:localhost,IP:127.0.0.1 \
  -keyout "$STATE/server.key" -out "$STATE/server.crt" >/dev/null 2>&1
node "$ROOT/desktop/scripts/build-sanitized-bootstrap.mjs" --output "$STATE/bootstrap.sql"
cat > "$STATE/compose.yml" <<'COMPOSE'
services:
  mysql:
    image: mysql:8.4.10@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6
    environment:
      MYSQL_DATABASE: ruoyi-vue-pro
      MYSQL_USER: yudao
      MYSQL_PASSWORD: ${DB_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      TZ: Asia/Shanghai
    command: ["--default-time-zone=+08:00"]
    volumes:
      - mysql-data:/var/lib/mysql
      - ./bootstrap.sql:/docker-entrypoint-initdb.d/010-bootstrap.sql:ro
      - ./owner.sql:/docker-entrypoint-initdb.d/999-owner.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "MYSQL_PWD=$$MYSQL_ROOT_PASSWORD mysql -uroot -e 'SELECT 1' >/dev/null"]
      interval: 5s
      retries: 50
  redis:
    image: redis:7.4.10-alpine
    environment:
      REDIS_PASSWORD: ${REDIS_PASSWORD}
    command: ["redis-server", "--requirepass", "${REDIS_PASSWORD}"]
    healthcheck:
      test: ["CMD-SHELL", "REDISCLI_AUTH=$$REDIS_PASSWORD redis-cli ping | grep PONG"]
      interval: 5s
      retries: 20
  server:
    image: ${SERVER_IMAGE}
    build:
      context: ${ROOT}
      dockerfile: deploy/saas/Dockerfile.server
    environment:
      TZ: Asia/Shanghai
      SPRING_PROFILES_ACTIVE: internal
      YUDAO_DEPLOYMENT_MODE: saas
      YUDAO_TENANT_ENABLE: "true"
      YUDAO_LICENSE_ENFORCE: "false"
      DB_HOST: mysql
      DB_PORT: 3306
      DB_NAME: ruoyi-vue-pro
      DB_USERNAME: yudao
      DB_PASSWORD: ${DB_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      REHAB_STORAGE_PATH: /app/data/rehab
      OPENAI_ENABLE_AI_ANALYSIS: "false"
      SERVER_SERVLET_SESSION_PERSISTENT: "false"
    volumes:
      - rehab-data:/app/data/rehab
      - server-logs:/app/logs
    depends_on:
      mysql: {condition: service_healthy}
      redis: {condition: service_healthy}
    healthcheck:
      test: ["CMD", "curl", "--fail", "--silent", "http://127.0.0.1:48080/actuator/health"]
      interval: 10s
      start_period: 60s
      retries: 30
  admin:
    image: ${ADMIN_IMAGE}
    build:
      context: ${ROOT}
      dockerfile: deploy/internal/Dockerfile.admin
    ports: ["127.0.0.1:${APP_PORT}:80", "127.0.0.1:${TLS_PORT}:443"]
    volumes:
      - ./server.crt:/etc/nginx/tls/ca.crt:ro
      - ./server.crt:/etc/nginx/tls/server.crt:ro
      - ./server.key:/etc/nginx/tls/server.key:ro
    depends_on:
      server: {condition: service_healthy}
    healthcheck:
      test: ["CMD", "curl", "--fail", "--silent", "--cacert", "/etc/nginx/tls/ca.crt", "https://localhost/"]
      interval: 10s
      retries: 15
volumes:
  mysql-data:
  rehab-data:
  server-logs:
COMPOSE
COMPOSE=(docker compose --env-file "$STATE/.env" --project-name "$PROJECT" -f "$STATE/compose.yml")
if [[ -n "$ENGINE_IMAGE" ]]; then
  MOTION_ENGINE_TOKEN="$(openssl rand -hex 32)"
  printf 'ENGINE_IMAGE=%s\nMOTION_ENGINE_TOKEN=%s\n' "$ENGINE_IMAGE" "$MOTION_ENGINE_TOKEN" >> "$STATE/.env"
  unset MOTION_ENGINE_TOKEN
  cat > "$STATE/engine.yml" <<'ENGINE'
services:
  engine:
    image: ${ENGINE_IMAGE}
    entrypoint: ["python", "-m", "rehab_biomechanics.motion.service"]
    command: ["--host", "0.0.0.0", "--port", "8790"]
    environment:
      MOTION_ENGINE_TOKEN: ${MOTION_ENGINE_TOKEN}
    read_only: true
    cap_drop: [ALL]
    security_opt: [no-new-privileges:true]
    tmpfs: ["/tmp:size=64m,mode=1777"]
    healthcheck:
      test: ["CMD", "python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8790/internal/v1/health',timeout=3).read()"]
      interval: 5s
      retries: 20
  server:
    environment:
      MOTION_ENGINE_URL: http://engine:8790
      MOTION_ENGINE_TOKEN: ${MOTION_ENGINE_TOKEN}
    depends_on:
      engine: {condition: service_healthy}
ENGINE
  COMPOSE+=(-f "$STATE/engine.yml")
fi
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" build server admin
OWNER_HASH="$(printf '%s\n' "$OWNER_PASSWORD" | docker run --rm -i --entrypoint java "$SERVER_IMAGE" -Dloader.main=cn.iocoder.yudao.server.PasswordHashCli -cp /app/app.jar org.springframework.boot.loader.PropertiesLauncher | tail -1 | tr -d '\r\n')"
[[ "$OWNER_HASH" =~ ^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$ ]] || { echo 'BCrypt generation failed; no database started'; exit 1; }
cat > "$STATE/owner.sql" <<SQL
-- Fresh sanitized database only. This file is never used for upgrades.
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE system_users SET password='$OWNER_HASH',nickname='合成平台管理员' WHERE tenant_id=1 AND username='admin' AND password='!desktop-runtime-sets-password!' AND deleted=0;
UPDATE system_tenant SET name='SaaS合成平台',registration_status='active',registration_source='synthetic-bootstrap' WHERE id=1;
INSERT INTO saas_plans(name,max_users,max_patients,motion_quota,storage_quota) VALUES('synthetic-owner',20,100,1000,1073741824);
INSERT INTO tenant_subscriptions(tenant_id,plan_id,status,start_time,expire_time) VALUES(1,LAST_INSERT_ID(),'active',CURRENT_TIMESTAMP,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 30 DAY));
INSERT INTO tenant_usage(tenant_id,user_count) SELECT 1,COUNT(*) FROM system_users WHERE tenant_id=1 AND deleted=0;
INSERT INTO platform_admins(user_id) SELECT id FROM system_users WHERE tenant_id=1 AND username='admin' AND deleted=0;
COMMIT;
SQL
chmod 644 "$STATE/bootstrap.sql" "$STATE/owner.sql" "$STATE/server.crt"
cat > "$STATE/FIRST_LOGIN.txt" <<LOGIN
SaaS V2 DEVELOPMENT — SYNTHETIC DATA ONLY — NOT PUBLIC BETA
URL: https://localhost:$TLS_PORT/saas/platform
Tenant: SaaS合成平台
Username: admin
Temporary password: $OWNER_PASSWORD
Local certificate: $STATE/server.crt
Change the temporary password after first login, then securely remove this file.
Do not upload this directory or credentials to source control or public hosting.
LOGIN
unset OWNER_PASSWORD OWNER_HASH
printf 'source_commit=%s\nproject=%s\nstatus=STARTING_NOT_VERIFIED\n' "$SHA" "$PROJECT" > "$STATE/installation.txt"
python3 - "$STATE" "$SHA" "$ENGINE_IMAGE" "$SERVER_IMAGE" "$ADMIN_IMAGE" <<'LOCK'
import hashlib,json,subprocess,sys
from pathlib import Path
state=Path(sys.argv[1])
images={name:json.loads(subprocess.check_output(['docker','image','inspect',name]))[0]['Id'] for name in sys.argv[4:]}
(state/'version-lock.json').write_text(json.dumps({'source_commit':sys.argv[2],'engine_image':sys.argv[3] or None,'images':images,'synthetic_only':True,'clinical_accepted':False},indent=2)+'\n')
LOCK
"${COMPOSE[@]}" up --detach --wait --wait-timeout 360
curl --fail --silent --show-error --cacert "$STATE/server.crt" "https://localhost:$TLS_PORT/" >/dev/null
printf 'status=LOCAL_HEALTHY_NOT_E2E_ACCEPTED\n' >> "$STATE/installation.txt"
echo "Local synthetic installation healthy. Credentials are stored privately at $STATE/FIRST_LOGIN.txt (not printed)."
echo "Owner console: https://localhost:$TLS_PORT/saas/platform"
echo 'No online deployment, patient workflow or signed installer is implied by this health check.'
