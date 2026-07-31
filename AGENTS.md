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
pnpm build:internal
```

Backend:

```bash
mvn -B -pl yudao-module-rehab -am test
mvn -B -pl yudao-framework/yudao-spring-boot-starter-web \
  -Dtest=ApiAccessLogInterceptorTest,GlobalExceptionHandlerTest test
deploy/internal/build-server-isolated.sh
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
node --test desktop/scripts/runtime-tools.test.mjs
desktop/scripts/test-runtime-e2e.sh # only on an isolated Docker host with no rehab-desktop volumes
```

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
6. Run desktop Rust and frontend tests without requiring Docker.
7. Confirm all Docker image versions are fixed and no `latest` base image is used.
8. Confirm normal stop/update paths never use `docker compose down -v`.
9. Scan the diff and runtime bundle for credentials, keys, patient data and build caches.
10. Build platform installers and publish SHA-256 files plus build metadata.

Do not weaken authentication, tenant isolation, upload restrictions, migration checks or TLS verification
to make desktop packaging pass.
