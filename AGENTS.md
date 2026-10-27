# Repository Working Guide

## Project structure

- `yudao-server/`: Spring Boot executable application.
- `yudao-module-rehab/`: rehabilitation domain APIs, services and tests.
- `yudao-framework/`: shared web, security, data and test infrastructure.
- `yudao-ui/yudao-ui-admin-vue3-app/`: Vue 3 internal administration frontend.
- `deploy/internal/`: existing Docker Compose deployment, migrations, TLS, backup and smoke checks.
- `desktop/launcher/`: Tauri v2 desktop launcher.
- `desktop/scripts/`: reproducible runtime-resource build and integrity checks.
- `docs/`: desktop packaging, installation, security and release documentation.

## Required build and test commands

Frontend:

```bash
cd yudao-ui/yudao-ui-admin-vue3-app
corepack enable
pnpm install --frozen-lockfile
pnpm test:unit
pnpm build:internal
```

Backend:

Use JDK 17 for Maven; the current tests fail with Byte Buddy/Mockito on JDK 23.

```bash
mvn -B -pl yudao-module-rehab -am test
mvn -B -pl yudao-framework/yudao-spring-boot-starter-web \
  -am -Dtest=ApiAccessLogPrivacyTest,ApiAccessLogInterceptorTest,GlobalExceptionHandlerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
deploy/internal/build-server-isolated.sh
# CRM 候选验收包（正式内部版本仍默认关闭 CRM）
deploy/internal/build-server-isolated.sh --crm
```

Desktop:

```bash
cd desktop/launcher
pnpm install --frozen-lockfile
pnpm test
pnpm cargo:test
pnpm tauri build --bundles nsis   # Windows
pnpm tauri build --bundles dmg    # macOS
```

Runtime resources:

```bash
node desktop/scripts/build-sanitized-bootstrap.mjs
node desktop/scripts/build-runtime.mjs
node desktop/scripts/check-runtime.mjs desktop/runtime/1.0.0
node --test desktop/scripts/*.test.mjs
desktop/scripts/test-runtime-e2e.sh # controlled test Docker environment; uses unique test volumes/images, never business volumes
```

Synthetic patient provenance migration (local Docker only, random disposable
MySQL container, no ports or business volumes):

```bash
REHAB_TEST_MYSQL=1 python3 -B -m unittest discover -s deploy/internal \
  -p 'test_patient_data_origin_mysql.py' -v
REHAB_TEST_MYSQL=1 python3 -B -m unittest discover -s deploy/internal \
  -p 'test_report_version_mysql.py' -v
python3 -B -m unittest discover -s deploy/internal -p 'test_synthetic_mysql.py' -v
REHAB_TEST_MYSQL=1 python3 -B -m unittest discover -s deploy/internal \
  -p 'test_report_requests_mysql.py' -v
```

Migrations through 036 are required before deploying this candidate backend. Never grant 035's
`rehab:patient:data-governance` permission to existing clinic accounts automatically.

## S0 inventory

Python 3.11+, read-only; not a production release gate:

```bash
python3 -B script/rehab/s0_inventory.py
python3 -B script/rehab/s0_inventory.py --probe
python3 -B -m unittest discover -s script/rehab -p 'test_s0_inventory.py' -v
```

Keep runtime directories in place. S0 registry classifications are preservation/review decisions,
not authorization to delete, move, merge or deploy assets. Do not collect `.env`, databases,
patient media or private archive contents into snapshots. See `docs/s0/README.md`.

## Files that must never be committed

- Runtime `.env` files or generated settings containing passwords.
- Private keys, TLS server keys, CA keys, backup keys or signing certificates.
- Patient data, production logs, diagnostic archives containing business data or database dumps.
- Database backups, attachment backups or restore staging files.
- `node_modules`, Maven caches, Rust `target`, frontend `dist-internal`, desktop `dist`,
  generated runtime bundles and IDE metadata.
- First-login credentials, API tokens, OAuth secrets or notarization credentials.

## Release gates

Before a desktop release:

1. Run the backend rehabilitation and web-security tests.
2. Run the isolated backend build.
3. Build the frontend with `build:internal`.
4. Run frontend production dependency audit.
5. Build and check the minimal desktop runtime resources.
   The standard frontend build must write a matching `desktop/build/frontend-build-receipt.json`;
   never package an old `dist-internal` after a failed build.
6. Run desktop Rust and frontend tests without requiring Docker.
7. Confirm all Docker image versions are fixed and no `latest` base image is used.
8. Confirm normal stop/update paths never use `docker compose down -v`.
9. Scan the diff and runtime bundle for credentials, keys, patient data and build caches.
10. Build platform installers and publish SHA-256 files plus build metadata.

Do not weaken authentication, tenant isolation, upload restrictions, migration checks or TLS verification
to make desktop packaging pass.

## Local capacity safeguard

Before a new heavyweight local Maven/Vite/Rust/Docker build, check free disk.
Below 8 GiB, do not start another heavyweight local build; record that local
check as NOT_RUN and use the already-authorized CI without removing its tests.
Run heavyweight local builds serially to avoid excessive system swap. This is
an engineering minimum, not approval for a production backup or upgrade.
Never alter swap, reboot/stop business services, prune shared Docker assets or
delete backups to meet it. Only exact, verified, task-owned rebuildable caches
may be retired; preserve source, evidence and candidate artifacts.
