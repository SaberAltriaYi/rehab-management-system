#!/usr/bin/env bash
# Synthetic-only desktop E2E; no existing projects, persistent volumes or fixed ports.
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNTIME="${1:?Usage: test-runtime-e2e-ephemeral.sh /path/to/runtime/1.0.0 [--preflight-only|--upgrade-smoke|--upgrade-auth-smoke|--upgrade-browser-smoke|--report-browser-smoke|--launcher-fault-restore]}"
MODE="${2:-run}"
[[ "${MODE}" == 'run' || "${MODE}" == '--preflight-only' || "${MODE}" == '--upgrade-smoke' || "${MODE}" == '--upgrade-auth-smoke' || "${MODE}" == '--upgrade-browser-smoke' || "${MODE}" == '--report-browser-smoke' || "${MODE}" == '--launcher-fault-restore' ]] || exit 2
RUNTIME="$(cd "${RUNTIME}" && pwd)"
node "${SCRIPT_DIR}/check-runtime.mjs" "${RUNTIME}" >/dev/null
umask 077
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/rehab-ephemeral-e2e.XXXXXXXX")"
PROJECT="rehab-ephemeral-$(openssl rand -hex 6)"
OVERRIDE="${STAGE}/ephemeral.yml"
COMPOSE=(docker compose --project-name "${PROJECT}" --file "${RUNTIME}/docker-compose.yml" --file "${OVERRIDE}")
STARTED=0
cleanup() {
  if [[ "${STARTED}" == 1 ]]; then
    "${COMPOSE[@]}" down >/dev/null 2>&1 || true
  fi
  case "${STAGE}" in
    */rehab-ephemeral-e2e.*) rm -rf -- "${STAGE}" ;;
    *) echo 'FAIL: unsafe temporary cleanup path' >&2 ;;
  esac
}
trap cleanup EXIT

# Random host-local ports, never the currently occupied 18080 / 18443.
read -r APP_PORT TLS_PORT < <(python3 - <<'PY'
import socket
opened=[]
try:
    for _ in range(2):
        s=socket.socket()
        s.bind(('127.0.0.1',0))
        opened.append(s)
    print(*(s.getsockname()[1] for s in opened))
finally:
    for s in opened: s.close()
PY
)
export APP_PORT TLS_PORT BIND_ADDRESS=127.0.0.1
export DB_PASSWORD="$(openssl rand -hex 24)"
export MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
export REDIS_PASSWORD="$(openssl rand -hex 24)"
export TLS_CERT_PATH="${STAGE}/server.crt"
export TLS_KEY_PATH="${STAGE}/server.key"
export FIRST_START_SQL_PATH="${STAGE}/first.sql"
export MYSQL_CLIENT_CONFIG_PATH="${STAGE}/client.cnf"
export RUNTIME_BOOTSTRAP="${RUNTIME}/sql/desktop-bootstrap.sql"
export SERVER_IMAGE="rehab-ephemeral-server:${PROJECT}"
export ADMIN_IMAGE="rehab-ephemeral-admin:${PROJECT}"
export COMPOSE_PROJECT_NAME="${PROJECT}"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -subj /CN=127.0.0.1 -addext subjectAltName=IP:127.0.0.1 \
  -keyout "${TLS_KEY_PATH}" -out "${TLS_CERT_PATH}" >/dev/null 2>&1
printf '%s\n' 'SELECT 1;' > "${FIRST_START_SQL_PATH}"
printf '[client]\nuser=root\npassword=%s\n' "${MYSQL_ROOT_PASSWORD}" > "${MYSQL_CLIENT_CONFIG_PATH}"
chmod 600 "${TLS_KEY_PATH}" "${TLS_CERT_PATH}" "${MYSQL_CLIENT_CONFIG_PATH}"
chmod 644 "${FIRST_START_SQL_PATH}"

# Compose v5 !override clears the base template's four fixed named volumes.
cat > "${OVERRIDE}" <<'YAML'
services:
  mysql:
    restart: "no"
    volumes: !override
      - ${RUNTIME_BOOTSTRAP}:/docker-entrypoint-initdb.d/010-desktop-bootstrap.sql:ro
      - ${FIRST_START_SQL_PATH}:/docker-entrypoint-initdb.d/999-runtime-credentials.sql:ro
      - ${MYSQL_CLIENT_CONFIG_PATH}:/run/rehab-secrets/mysql-client.cnf:ro
    tmpfs:
      - /var/lib/mysql:size=512m
  redis:
    restart: "no"
    volumes: !override []
    tmpfs:
      - /data:size=128m
  server:
    restart: "no"
    image: ${SERVER_IMAGE}
    volumes: !override []
    tmpfs:
      - /app/data/rehab:size=128m,mode=1777
      - /app/logs:size=64m,mode=1777
  admin:
    restart: "no"
    image: ${ADMIN_IMAGE}
    volumes: !override
      - ${TLS_CERT_PATH}:/etc/nginx/tls/server.crt:ro
      - ${TLS_KEY_PATH}:/etc/nginx/tls/server.key:ro
volumes: !override {}
YAML

if [[ "${MODE}" == '--launcher-fault-restore' ]]; then
  # A disposable private bind mount keeps attachments consistent between
  # `compose exec server` and `compose run --rm server` during explicit
  # restore. MySQL and Redis remain tmpfs-only; no existing named volume.
  export REHAB_LAUNCHER_ATTACHMENTS="${STAGE}/synthetic-attachments"
  mkdir -m 700 "${REHAB_LAUNCHER_ATTACHMENTS}"
  python3 - "${OVERRIDE}" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1])
source=p.read_text()
old='''    volumes: !override []
    tmpfs:
      - /app/data/rehab:size=128m,mode=1777
      - /app/logs:size=64m,mode=1777'''
new='''    volumes: !override
      - ${REHAB_LAUNCHER_ATTACHMENTS}:/app/data/rehab
    tmpfs:
      - /app/logs:size=64m,mode=1777'''
assert source.count(old)==1, 'unexpected synthetic override template'
p.write_text(source.replace(old,new))
PY
fi

if [[ "${MODE}" == '--upgrade-auth-smoke' || "${MODE}" == '--upgrade-browser-smoke' || "${MODE}" == '--report-browser-smoke' ]]; then
  # Disable captcha ONLY in this disposable synthetic container so the real
  # OAuth2 access-token path can be exercised without solving an image puzzle.
  cat > "${STAGE}/auth-captcha.yml" <<'YAML'
services:
  server:
    environment:
      YUDAO_CAPTCHA_ENABLE: "false"
YAML
  COMPOSE+=(--file "${STAGE}/auth-captcha.yml")
fi

if [[ "${MODE}" == '--report-browser-smoke' ]]; then
  # Frontend-only cross-worktree UI integration: keep the motion Runtime
  # backend intact, and mount a copy of the main-branch report frontend
  # into this ephemeral admin container. Never write to either source tree.
  REPORT_FRONTEND="${SCRIPT_DIR}/../../yudao-ui/yudao-ui-admin-vue3-app/dist-internal"
  [[ -f "${REPORT_FRONTEND}/index.html" ]] ||
    { echo 'FAIL: main report internal build is missing'; exit 1; }
  mkdir -m 755 "${STAGE}/report-web"
  cp -R "${REPORT_FRONTEND}/." "${STAGE}/report-web/"
  find "${STAGE}/report-web" -type d -exec chmod 755 {} +
  find "${STAGE}/report-web" -type f -exec chmod 644 {} +
  export REHAB_REPORT_WEB="${STAGE}/report-web"
  cat > "${STAGE}/report-web.yml" <<'YAML'
services:
  admin:
    volumes: !override
      - ${TLS_CERT_PATH}:/etc/nginx/tls/server.crt:ro
      - ${TLS_KEY_PATH}:/etc/nginx/tls/server.key:ro
      - ${REHAB_REPORT_WEB}:/usr/share/nginx/html:ro
YAML
  COMPOSE+=(--file "${STAGE}/report-web.yml")
fi

# Refuse anything other than the four expected services and synthetic mounts.
"${COMPOSE[@]}" config --format json | python3 -c '
import json,os,sys
from pathlib import Path
c=json.load(sys.stdin)
s=c["services"]
assert set(s)=={"mysql","redis","server","admin"}, "unexpected service"
assert not c.get("volumes"), "declared named volumes"
stage=Path(os.environ["TLS_CERT_PATH"]).parent.resolve()
runtime_sql=Path(os.environ["RUNTIME_BOOTSTRAP"]).resolve()
for name,service in s.items():
    assert service.get("restart") in (None,"no"), name
    for mount in service.get("volumes",[]):
        assert mount.get("type")=="bind", (name,mount)
        source=Path(mount["source"]).resolve()
        assert source==runtime_sql or source.is_relative_to(stage), (name,source)
    if name!="admin":
        assert not service.get("ports"), name
        assert service.get("tmpfs"), name
for p in s["admin"].get("ports",[]):
    assert p.get("host_ip")=="127.0.0.1", p
for name,mount in [("mysql","/var/lib/mysql"),("redis","/data"),
                   ("server","/app/logs")]:
    assert any(x.startswith(mount+":") for x in s[name]["tmpfs"]), name
if os.getenv("REHAB_LAUNCHER_ATTACHMENTS"):
    assert not any(x.startswith("/app/data/rehab:") for x in s["server"]["tmpfs"])
    assert any(m["type"]=="bind" and m["target"]=="/app/data/rehab"
        and Path(m["source"]).resolve()==Path(os.environ["REHAB_LAUNCHER_ATTACHMENTS"]).resolve()
        for m in s["server"]["volumes"]), "launcher attachment bind must be synthetic"
else:
    assert any(x.startswith("/app/data/rehab:") for x in s["server"]["tmpfs"])
print("PASS: merged Compose is localhost-only, tmpfs-backed, no persistent volumes")
'
if [[ "${MODE}" == '--preflight-only' ]]; then
  echo 'PASS: configuration validated; no containers started'
  exit 0
fi

echo "Starting isolated synthetic project ${PROJECT} on 127.0.0.1 ports ${APP_PORT}/${TLS_PORT}"
"${COMPOSE[@]}" build server admin >/dev/null
STARTED=1
"${COMPOSE[@]}" up --detach --no-build >/dev/null
for service in mysql redis server admin; do
  container="${PROJECT}-${service}-1"
  [[ "$(docker inspect "${container}" --format '{{index .Config.Labels "com.docker.compose.project"}}')" == "${PROJECT}" ]] ||
    { echo 'FAIL: container belongs to unexpected project'; exit 1; }
  [[ -z "$(docker inspect "${container}" --format '{{range .Mounts}}{{if eq .Type "volume"}}BAD{{end}}{{end}}')" ]] ||
    { echo 'FAIL: persistent named volume detected'; exit 1; }
done

READY=0
for attempt in $(seq 1 120); do
  if "${COMPOSE[@]}" ps --format json | python3 -c '
import json,sys
raw=sys.stdin.read().strip()
rows=[json.loads(s) for s in raw.splitlines() if s.strip()]
assert {r["Service"] for r in rows}=={"mysql","redis","server","admin"}
assert all(r.get("Health")=="healthy" for r in rows)
' 2>/dev/null; then READY=1; break; fi
  sleep 3
done
[[ "${READY}" == 1 ]] || { echo 'FAIL: all four services did not become healthy'; exit 1; }
echo 'PASS: four isolated services healthy'

BASE="https://127.0.0.1:${TLS_PORT}"
# Every request must go directly to loopback. Never let an HTTPS_PROXY
# environment variable route synthetic health/API requests to an external proxy.
curl --noproxy '*' --fail --silent --show-error --cacert "${TLS_CERT_PATH}" "${BASE}/healthz" >/dev/null
echo 'PASS: direct-loopback TLS healthz'
curl --noproxy '*' --fail --silent --show-error --cacert "${TLS_CERT_PATH}" "${BASE}/" | grep -q '康复管理系统'
echo 'PASS: direct-loopback frontend index'
tenant="$(curl --noproxy '*' --fail --silent --show-error --cacert "${TLS_CERT_PATH}" --get \
  --data-urlencode 'name=工作室内部' "${BASE}/admin-api/system/tenant/get-id-by-name")"
printf '%s' "${tenant}" | jq -e '.code==0 and .data==1' >/dev/null
echo 'PASS: TLS, frontend, reverse proxy and tenant routing'

for action in login bind; do
  status="$(curl --noproxy '*' --silent --show-error --cacert "${TLS_CERT_PATH}" \
    --header 'Content-Type: application/json' --header 'tenant-id: 1' \
    --data '{"phone":"13800138000","bindCode":"SYNTHETIC_INVALID","patientNo":"SYNTHETIC_INVALID"}' \
    --output "${STAGE}/patient-auth.json" --write-out '%{http_code}' \
    "${BASE}/app-api/app-patient/auth/${action}")"
  [[ "${status}" == '403' ]] || { echo "FAIL: ${action} returned HTTP ${status}, expected 403"; exit 1; }
  jq -e '.code==403' "${STAGE}/patient-auth.json" >/dev/null
done
echo 'PASS: both patient authentication routes return explicit HTTP 403'

counts="$("${COMPOSE[@]}" exec -T mysql \
  mysql --defaults-extra-file=/run/rehab-secrets/mysql-client.cnf \
  -N -B ruoyi-vue-pro --execute \
  "SELECT CONCAT((SELECT COUNT(*) FROM rehab_patient),'|',(SELECT COUNT(*) FROM internal_schema_history));")"
[[ "${counts}" == '0|19' ]] || { echo "FAIL: unexpected patient|ledger: ${counts}"; exit 1; }
echo 'PASS: 0 patients and 19 baseline migration rows'

if [[ "${MODE}" == '--launcher-fault-restore' ]]; then
  # Never run the ignored Rust test without preflight and the random project
  # already verified healthy and with zero named volumes.
  "${COMPOSE[@]}" exec -T mysql mysql \
    --defaults-extra-file=/run/rehab-secrets/mysql-client.cnf \
    ruoyi-vue-pro --execute \
    "CREATE TABLE synthetic_restore_probe (marker VARCHAR(64) NOT NULL); INSERT INTO synthetic_restore_probe(marker) VALUES ('SYNTHETIC_BEFORE_UPGRADE');"
  "${COMPOSE[@]}" exec -T server sh -c \
    'printf "SYNTHETIC_ATTACHMENT_BEFORE_UPGRADE\n" > /app/data/rehab/synthetic-attachment.txt'
  export REHAB_LAUNCHER_E2E_GUARD='SYNTHETIC_TMPFS_ONLY_V1'
  export REHAB_LAUNCHER_E2E_PROJECT="${PROJECT}"
  export REHAB_LAUNCHER_E2E_OVERRIDE="${OVERRIDE}"
  export REHAB_LAUNCHER_E2E_RUNTIME="${RUNTIME}"
  export PATH="${HOME}/.rustup/toolchains/stable-aarch64-apple-darwin/bin:${PATH}"
  WORKTREE="${SCRIPT_DIR}/../../../worktrees/motion-assessment/desktop/launcher/src-tauri"
  [[ -f "${WORKTREE}/src/service_live_synthetic.rs" ]] ||
    { echo 'FAIL: live synthetic Rust test source missing'; exit 1; }
  (
    cd "${WORKTREE}"
    cargo test --locked --offline partial_ddl_then_explicit_restore_on_real_synthetic_docker -- --ignored --nocapture
  )
  [[ "$("${COMPOSE[@]}" exec -T mysql mysql \
     --defaults-extra-file=/run/rehab-secrets/mysql-client.cnf \
     --batch --skip-column-names ruoyi-vue-pro --execute \
     'SELECT COUNT(*) FROM internal_schema_history;')" == 25 ]] ||
    { echo 'FAIL: successful launcher upgrade ledger is not 25'; exit 1; }
  [[ "$("${COMPOSE[@]}" exec -T server cat /app/data/rehab/synthetic-attachment.txt)" == 'SYNTHETIC_ATTACHMENT_BEFORE_UPGRADE' ]] ||
    { echo 'FAIL: restored synthetic attachment mismatch'; exit 1; }
  echo 'PASS: REAL launcher orchestration, partial-DDL failure, explicit DB/attachment recovery, then packaged 020-025 successful upgrade'
  exit 0
fi

if [[ "${MODE}" == '--upgrade-smoke' || "${MODE}" == '--upgrade-auth-smoke' || "${MODE}" == '--upgrade-browser-smoke' || "${MODE}" == '--report-browser-smoke' ]]; then
  # Exercise the *packaged* SQL bytes, not the repository SQL copies. This is
  # a SQL/runtime compatibility smoke, not a launcher/backup orchestration test.
  # All writes are strictly confined to the new tmpfs-only Compose database.
  sql() {
    "${COMPOSE[@]}" exec -T mysql \
      mysql --defaults-extra-file=/run/rehab-secrets/mysql-client.cnf \
      --batch --skip-column-names ruoyi-vue-pro --execute "$1" < /dev/null
  }
  # Synthesize a second tenant with an authorized therapist and an unauthorized
  # patient role before 024. The role-menu migration must honor tenant_id.
  sql "INSERT INTO system_role (id,name,code,sort,status,type,tenant_id) VALUES (990001,'SYNTHETIC_T2_THERAPIST','rehab_therapist',1,0,2,2),(990002,'SYNTHETIC_T2_PATIENT','rehab_patient',2,0,2,2);" >/dev/null
  manifest="${RUNTIME}/sql/migrations.manifest"
  [[ -s "${manifest}" ]] || { echo 'FAIL: packaged migration manifest missing'; exit 1; }
  applied=0
  while IFS='|' read -r version checksum source description; do
    [[ -z "${version}" || "${version}" == '#'* ]] && continue
    [[ "${version}" =~ ^[0-9]{3}$ ]] || { echo 'FAIL: unsafe migration version'; exit 1; }
    (( 10#${version} > 19 )) || continue
    [[ "${checksum}" =~ ^[a-f0-9]{64}$ ]] || { echo 'FAIL: unsafe migration checksum'; exit 1; }
    [[ "${source}" =~ ^sql/mysql/[A-Za-z0-9._-]+\.sql$ ]] &&
      [[ "${source}" != *'..'* ]] || { echo 'FAIL: unsafe migration source'; exit 1; }
    [[ -n "${description}" && "${description}" != *"'"* && "${description}" != *'|'* ]] ||
      { echo 'FAIL: unsafe migration description'; exit 1; }
    script="${RUNTIME}/sql/migrations/${version}.sql"
    [[ -s "${script}" ]] || { echo "FAIL: missing runtime migration ${version}"; exit 1; }
    actual="$(shasum -a 256 "${script}" | awk '{print $1}')"
    [[ "${actual}" == "${checksum}" ]] ||
      { echo "FAIL: packaged SQL checksum mismatch ${version}"; exit 1; }
    "${COMPOSE[@]}" exec -T mysql \
      mysql --defaults-extra-file=/run/rehab-secrets/mysql-client.cnf \
      --default-character-set=utf8mb4 --binary-mode ruoyi-vue-pro < "${script}" ||
      { echo "FAIL: packaged migration ${version} failed; no ledger registration"; exit 1; }
    sql "INSERT INTO internal_schema_history(version,checksum,script_path,description,installed_by,baseline,execution_ms) VALUES ('${version}','${checksum}','${source}','${description}','desktop-upgrade',b'0',0);" >/dev/null
    [[ "$(sql "SELECT checksum FROM internal_schema_history WHERE version='${version}';")" == "${checksum}" ]] ||
      { echo "FAIL: migration ledger checksum ${version}"; exit 1; }
    applied=$((applied + 1))
    echo "PASS: packaged migration ${version} applied with matching checksum"
  done < "${manifest}"
  [[ "${applied}" == 6 ]] || { echo "FAIL: expected 6 upgrades 020-025, got ${applied}"; exit 1; }

  upgraded="$(sql "SELECT CONCAT((SELECT COUNT(*) FROM rehab_patient),'|',(SELECT COUNT(*) FROM internal_schema_history),'|',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'rehab_motion_%'),'|',(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name LIKE 'rehab_motion_%' AND column_name IN ('creator','updater') AND is_nullable='YES'));")"
  [[ "${upgraded}" == '0|25|11|22' ]] ||
    { echo "FAIL: unexpected upgraded synthetic structure ${upgraded}"; exit 1; }
  [[ "$(sql "SELECT COUNT(*) FROM system_menu WHERE id IN (9700,9710,9711,9712,9713,9714,9715,9716,9717);")" == 9 ]] ||
    { echo 'FAIL: motion menus missing after packaged upgrade'; exit 1; }
  for role in super_admin rehab_therapist; do
    count="$(sql "SELECT COUNT(*) FROM system_role r JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0' WHERE r.code='${role}' AND r.tenant_id=1 AND rm.menu_id BETWEEN 9700 AND 9717;")"
    [[ "${count}" == 9 ]] || { echo "FAIL: ${role} motion grants=${count}, expected 9"; exit 1; }
  done
  [[ "$(sql "SELECT COUNT(*) FROM system_role_menu WHERE role_id=990001 AND tenant_id=2 AND menu_id BETWEEN 9700 AND 9717 AND deleted=b'0';")" == 9 ]] ||
    { echo 'FAIL: second tenant therapist did not receive 9 scoped motion grants'; exit 1; }
  [[ "$(sql "SELECT COUNT(*) FROM system_role_menu WHERE role_id=990001 AND tenant_id<>2 AND menu_id BETWEEN 9700 AND 9717;")" == 0 ]] ||
    { echo 'FAIL: second tenant role received cross-tenant motion grants'; exit 1; }
  [[ "$(sql "SELECT COUNT(*) FROM system_role_menu WHERE role_id=990002 AND menu_id BETWEEN 9700 AND 9717;")" == 0 ]] ||
    { echo 'FAIL: second tenant patient was granted motion menus'; exit 1; }
  [[ "$(sql "SELECT COUNT(*) FROM system_role r JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id WHERE r.code='rehab_patient' AND rm.menu_id BETWEEN 9700 AND 9717;")" == 0 ]] ||
    { echo 'FAIL: patient role granted therapist motion menus'; exit 1; }
  echo 'PASS: packaged 020-025, ledger=25, 11 tables, 22 audit columns, two tenant-scoped therapists, no patient grants'

  curl --noproxy '*' --fail --silent --show-error --cacert "${TLS_CERT_PATH}" "${BASE}/healthz" >/dev/null
  tenant="$(curl --noproxy '*' --fail --silent --show-error --cacert "${TLS_CERT_PATH}" --get \
    --data-urlencode 'name=工作室内部' "${BASE}/admin-api/system/tenant/get-id-by-name")"
  printf '%s' "${tenant}" | jq -e '.code==0 and .data==1' >/dev/null
  for action in login bind; do
    status="$(curl --noproxy '*' --silent --show-error --cacert "${TLS_CERT_PATH}" \
      --header 'Content-Type: application/json' --header 'tenant-id: 1' \
      --data '{"phone":"13800138000","bindCode":"SYNTHETIC_INVALID","patientNo":"SYNTHETIC_INVALID"}' \
      --output "${STAGE}/patient-auth-upgraded.json" --write-out '%{http_code}' \
      "${BASE}/app-api/app-patient/auth/${action}")"
    [[ "${status}" == '403' ]] || { echo "FAIL: upgraded patient ${action} returned ${status}"; exit 1; }
    jq -e '.code==403' "${STAGE}/patient-auth-upgraded.json" >/dev/null
  done
  echo 'PASS: after packaged upgrade HTTPS, tenant API and patient HTTP 403 stay enforced'
  # A newly deployed motion controller must not allow unauthenticated data reads
  # or signatures. Yudao's AuthenticationEntryPointImpl writes CommonResult
  # with business code 401 without setting HTTP status (may be HTTP 200).
  # Require the denial code AND empty data; 404 must never mask a missing route.
  for endpoint in 'page?pageNo=1&pageSize=10' 'get?id=1' 'sign'; do
    if [[ "${endpoint}" == sign ]]; then
      status="$(curl --noproxy '*' --silent --show-error --cacert "${TLS_CERT_PATH}" \
        --header 'Content-Type: application/json' --header 'tenant-id: 1' \
        --data '{"id":1}' --output "${STAGE}/motion-auth.json" \
        --write-out '%{http_code}' "${BASE}/admin-api/rehab/motion/${endpoint}")"
    else
      status="$(curl --noproxy '*' --silent --show-error --cacert "${TLS_CERT_PATH}" \
        --header 'tenant-id: 1' --output "${STAGE}/motion-auth.json" \
        --write-out '%{http_code}' "${BASE}/admin-api/rehab/motion/${endpoint}")"
    fi
    [[ "${status}" == 200 || "${status}" == 401 || "${status}" == 403 ]] ||
      { echo "FAIL: anonymous motion ${endpoint} returned HTTP ${status}"; exit 1; }
    jq -e '(.code==401 or .code==403) and (.data==null)' \
      "${STAGE}/motion-auth.json" >/dev/null ||
      { echo "FAIL: motion ${endpoint} did not return an authorization denial with null data"; exit 1; }
    echo "PASS: anonymous motion ${endpoint} denied with HTTP ${status} and no data"
  done
  echo 'PASS: anonymous motion page/get/sign denied on actual Java API'
  if [[ "${MODE}" == '--upgrade-auth-smoke' || "${MODE}" == '--upgrade-browser-smoke' || "${MODE}" == '--report-browser-smoke' ]]; then
    # One-time synthetic passwords/hashes/tokens are never echoed, committed or
    # sent outside this localhost-only Compose project.
    command -v htpasswd >/dev/null || { echo 'FAIL: htpasswd needed for isolated BCrypt credentials'; exit 1; }
    password="$(openssl rand -hex 12)"
    hash="$(printf '%s\n' "${password}" | htpasswd -niBC 10 synthetic | sed 's/^synthetic://')"
    [[ "${hash}" == '$2'* ]] || { echo 'FAIL: invalid BCrypt fixture hash'; exit 1; }
    sql "INSERT INTO system_tenant(id,name,contact_name,status,package_id,expire_time,account_count) VALUES (2,'SYNTHETIC_TENANT_2','SYNTHETIC',0,0,'2099-12-31 23:59:59',10);" >/dev/null
    sql "UPDATE system_users SET password='${hash}' WHERE id=1 AND tenant_id=1;" >/dev/null
    sql "INSERT INTO system_users(id,username,password,nickname,dept_id,status,tenant_id) VALUES (990010,'synthera1','${hash}','SYNTHETIC_T1',100,0,1),(990011,'synthera2','${hash}','SYNTHETIC_T2',100,0,2),(990012,'synpatient','${hash}','SYNTHETIC_NO_MOTION',100,0,2);" >/dev/null
    sql "INSERT INTO system_user_role(user_id,role_id,tenant_id) VALUES (990010,160,1),(990011,990001,2),(990012,990002,2);" >/dev/null
    # Three invented patient records: one assigned to each therapist and one
    # intentionally unassigned in tenant 1, all within this disposable tmpfs.
    sql "INSERT INTO rehab_patient(id,patient_no,name,current_therapist_user_id,tenant_id) VALUES (990101,'SYNTHETIC_T1_ALLOWED','SYNTHETIC_ALLOWED_1',990010,1),(990102,'SYNTHETIC_T1_HIDDEN','SYNTHETIC_HIDDEN_1',NULL,1),(990103,'SYNTHETIC_T2_ALLOWED','SYNTHETIC_ALLOWED_2',990011,2);" >/dev/null
    sql "INSERT INTO rehab_motion_assessment(id,patient_id,title,tenant_id) VALUES (990101,990101,'SYNTHETIC_MOTION_1',1),(990102,990102,'SYNTHETIC_MOTION_HIDDEN',1),(990103,990103,'SYNTHETIC_MOTION_2',2);" >/dev/null
    if [[ "${MODE}" == '--report-browser-smoke' ]]; then
      # Foreign-key-complete synthetic report lives ONLY in tmpfs MySQL.
      # No clinical report is approved, signed, or generated from patient data.
      sql "INSERT INTO rehab_episode(id,episode_no,patient_id,tenant_id) VALUES (990201,'SYNTHETIC_EPISODE',990101,1);" >/dev/null
      sql "INSERT INTO rehab_assessment_record(id,assessment_no,patient_id,episode_id,assessment_type,assessment_date,tenant_id) VALUES (990301,'SYNTHETIC_ASSESSMENT',990101,990201,'initial','2026-10-09',1);" >/dev/null
      sql "INSERT INTO rehab_report(id,report_no,patient_id,episode_id,assessment_id,report_type,report_status,generation_mode,html_snapshot_path,tenant_id) VALUES (990401,'SYNTHETIC_REPORT',990101,990201,990301,'comprehensive','draft','auto','/app/data/rehab/synthetic-report-preview.html',1);" >/dev/null
      # Deliberately untrusted harmless markup for the HTML sanitizer test.
      # The synthetic file is inside this disposable server tmpfs, never on host.
      printf '%s\n' '<h3>SYNTHETIC_PREVIEW_OK</h3><img src="x" onerror="window.__rehab_html_injected=1"><script>window.__rehab_html_injected=2</script><iframe src="about:blank"></iframe><a href="javascript:alert(1)">unsafe link</a>' |
        "${COMPOSE[@]}" exec -T server sh -c 'cat > /app/data/rehab/synthetic-report-preview.html'
      echo 'PASS: one synthetic unsigned report and HTML sanitizer fixture created in tmpfs'
    fi
    login() {
      local username="$1" tenant_id="$2" user_id="$3" token code
      jq -nc --arg username "${username}" --arg password "${password}" \
        '{username:$username,password:$password}' > "${STAGE}/synthetic-login.json"
      curl --noproxy '*' --silent --show-error --cacert "${TLS_CERT_PATH}" \
        --header 'Content-Type: application/json' --header "tenant-id: ${tenant_id}" \
        --data-binary "@${STAGE}/synthetic-login.json" \
        --output "${STAGE}/synthetic-auth.json" \
        "${BASE}/admin-api/system/auth/login"
      if ! jq -e --argjson uid "${user_id}" \
        '.code==0 and .data.userId==$uid and (.data.accessToken|type=="string" and length>10)' \
        "${STAGE}/synthetic-auth.json" >/dev/null; then
        code="$(jq -r '.code // "invalid"' "${STAGE}/synthetic-auth.json" 2>/dev/null)"
        echo "FAIL: synthetic OAuth2 login ${username} tenant=${tenant_id}, code=${code}" >&2
        return 1
      fi
      jq -r '.data.accessToken' "${STAGE}/synthetic-auth.json"
    }
    admin_token="$(login admin 1 1)" || exit 1
    t1_token="$(login synthera1 1 990010)" || exit 1
    t2_token="$(login synthera2 2 990011)" || exit 1
    patient_role_token="$(login synpatient 2 990012)" || exit 1
    echo 'PASS: real OAuth2 login: admin, tenant-1 therapist, tenant-2 therapist, restricted role'
    page() {
      local token="$1" tenant_id="$2"
      curl --noproxy '*' --silent --show-error --cacert "${TLS_CERT_PATH}" \
        --header "Authorization: Bearer ${token}" --header "tenant-id: ${tenant_id}" \
        --output "${STAGE}/synthetic-page.json" \
        "${BASE}/admin-api/rehab/motion/page?pageNo=1&pageSize=10"
    }
    page "${admin_token}" 1
    jq -e '.code==0 and .data.total==2 and ([.data.list[].id]|sort)==[990101,990102]' \
      "${STAGE}/synthetic-page.json" >/dev/null ||
      { echo 'FAIL: admin patient list mismatch or cross-tenant data'; exit 1; }
    page "${t1_token}" 1
    jq -e '.code==0 and .data.total==1 and [.data.list[].id]==[990101]' \
      "${STAGE}/synthetic-page.json" >/dev/null ||
      { echo 'FAIL: therapist-1 patient-level scope'; exit 1; }
    page "${t2_token}" 2
    jq -e '.code==0 and .data.total==1 and [.data.list[].id]==[990103]' \
      "${STAGE}/synthetic-page.json" >/dev/null ||
      { echo 'FAIL: therapist-2 tenant/patient-level scope'; exit 1; }
    page "${patient_role_token}" 2
    jq -e '.code!=0 and .data==null' "${STAGE}/synthetic-page.json" >/dev/null ||
      { echo 'FAIL: restricted role was allowed to enumerate patient assessments'; exit 1; }
    # A valid tenant-1 login must not be allowed to change tenant by header.
    page "${t1_token}" 2
    jq -e '.code!=0 and .data==null' "${STAGE}/synthetic-page.json" >/dev/null ||
      { echo 'FAIL: OAuth2 token tenant spoofing'; exit 1; }
    detail() {
      local token="$1" tenant_id="$2" assessment_id="$3"
      curl --noproxy '*' --silent --show-error --cacert "${TLS_CERT_PATH}" \
        --header "Authorization: Bearer ${token}" --header "tenant-id: ${tenant_id}" \
        --output "${STAGE}/synthetic-detail.json" \
        "${BASE}/admin-api/rehab/motion/get?id=${assessment_id}"
    }
    detail "${admin_token}" 1 990102
    jq -e '.code==0 and .data.id==990102' "${STAGE}/synthetic-detail.json" >/dev/null ||
      { echo 'FAIL: tenant-1 admin could not access tenant-1 unassigned patient'; exit 1; }
    for case in 't1 1 990102' 't1 1 990103' 't2 2 990101'; do
      read -r role tenant_id assessment_id <<< "${case}"
      if [[ "${role}" == t1 ]]; then token="${t1_token}"; else token="${t2_token}"; fi
      detail "${token}" "${tenant_id}" "${assessment_id}"
      jq -e '.code!=0 and .data==null' "${STAGE}/synthetic-detail.json" >/dev/null ||
        { echo "FAIL: ${role} exposed unauthorized assessment ${assessment_id}"; exit 1; }
    done
    echo 'PASS: OAuth2 real login enforces tenant isolation, therapist patient list/detail, and restricted role'
    if [[ "${MODE}" == '--report-browser-smoke' ]]; then
      curl --noproxy '*' --silent --show-error --cacert "${TLS_CERT_PATH}" \
        --header "Authorization: Bearer ${admin_token}" --header 'tenant-id: 1' \
        --output "${STAGE}/synthetic-report-page.json" \
        "${BASE}/admin-api/rehab/report/patient-page?pageNo=1&pageSize=10"
      jq -e '.code==0 and .data.total==1 and .data.list[0].patientId==990101 and .data.list[0].reportCount==1' \
        "${STAGE}/synthetic-report-page.json" >/dev/null ||
        { echo 'FAIL: synthetic report patient-page payload invalid'; exit 1; }
      curl --noproxy '*' --silent --show-error --cacert "${TLS_CERT_PATH}" \
        --header "Authorization: Bearer ${admin_token}" --header 'tenant-id: 1' \
        --output "${STAGE}/synthetic-report-preview.json" \
        "${BASE}/admin-api/rehab/report/preview?id=990401"
      jq -e '.code==0 and (.data.html|contains("SYNTHETIC_PREVIEW_OK"))' \
        "${STAGE}/synthetic-report-preview.json" >/dev/null ||
        { echo 'FAIL: synthetic report preview API'; exit 1; }
      echo 'PASS: report patient-page and unsafe synthetic preview supplied by real Java API'
    fi
    if [[ "${MODE}" == '--upgrade-browser-smoke' || "${MODE}" == '--report-browser-smoke' ]]; then
      : "${REHAB_BROWSER_EVIDENCE_DIR:?Set an empty private evidence directory for synthetic browser screenshots}"
      # Only localhost synthetic OAuth2 token goes to this short-lived Chrome
      # profile. Never print or persist browser credentials.
      REHAB_BROWSER_SCENARIO="${MODE}" \
        REHAB_BROWSER_BASE="${BASE}" REHAB_BROWSER_TOKEN="${admin_token}" \
        REHAB_BROWSER_EVIDENCE_DIR="${REHAB_BROWSER_EVIDENCE_DIR}" \
        python3 "${SCRIPT_DIR}/browser-visual-smoke.py"
    fi
    unset password hash admin_token t1_token t2_token patient_role_token
  fi
  echo 'NOTE: SQL/runtime smoke only; desktop launcher backup/restart/rollback is not exercised'
fi
echo 'PASS: isolated desktop Runtime E2E completed'
