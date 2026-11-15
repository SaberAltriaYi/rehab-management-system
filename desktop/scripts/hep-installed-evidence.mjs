// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
// Local-only, read-only observations. Sensitive configuration values are never returned.
import { timingSafeEqual } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'

const CORE = 'rehab-studio-internal-server-1'
const MYSQL = 'rehab-studio-internal-mysql-1'

function parseEnvText(text) {
  const result = {}
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim()
    if (!line || line.startsWith('#')) continue
    const matched = line.match(/^([A-Z][A-Z0-9_]*)=(.*)$/)
    if (matched) result[matched[1]] = matched[2].replace(/^(['"])(.*)\1$/, '$2')
  }
  return result
}

function sameSecret(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string' || a.length < 32 || b.length < 32) return false
  const x = Buffer.from(a), y = Buffer.from(b)
  return x.length === y.length && timingSafeEqual(x, y)
}

export function parseMigrationEvidence(stdout) {
  const lines = stdout.trim().split(/\r?\n/)
  const [count, version] = (lines[0] || '').split('\t')
  const tables = lines[1], menus = lines[2]
  if (!/^\d+$/.test(count) || !/^\d{3}$/.test(version || '') ||
      !/^\d+$/.test(tables || '') || !/^\d+$/.test(menus || '')) {
    throw new Error('MIGRATION_QUERY_FORMAT_INVALID')
  }
  return { count: Number(count), version, hepTables: Number(tables), hepMenus: Number(menus) }
}

export function observeInstalledHep({
  run = execFileSync, read = readFileSync, home = homedir(),
  hepEnvPath = join(home, 'my-project/rehab-runtime/hep-local/hep.env')
} = {}) {
  const installedPath = join(home, 'Library/Application Support/com.saberaltriayi.rehab.internal/runtime/1.0.0/VERSION.json')
  const installed = JSON.parse(read(installedPath, 'utf8'))
  const sql = 'SELECT COUNT(*),MAX(version) FROM internal_schema_history; ' +
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'rehab_hep_%'; " +
    'SELECT COUNT(*) FROM system_menu WHERE id BETWEEN 98900 AND 98909;'
  const database = parseMigrationEvidence(run('docker', [
    'exec', MYSQL, 'sh', '-c',
    `MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --raw --skip-column-names ruoyi-vue-pro -e "${sql}"`
  ], { encoding: 'utf8', timeout: 9000 }))
  const inspect = run('docker', ['inspect', '--format', '{{json .Config.Env}}', CORE],
    { encoding: 'utf8', timeout: 9000 })
  const coreConfig = Object.fromEntries(JSON.parse(inspect).map(item => {
    const offset = item.indexOf('=')
    return [item.slice(0, offset), item.slice(offset + 1)]
  }))
  const hepConfig = parseEnvText(read(hepEnvPath, 'utf8'))
  const keyReadable = run('docker', ['exec', CORE, 'sh', '-c',
    'if test -r /app/license/hep-core-jwt-private.pem; then printf yes; else printf no; fi'],
  { encoding: 'utf8', timeout: 9000 }).trim() === 'yes'

  const deployedLedger = database.count === Number(database.version)
    ? `001-${database.version}` : ''
  const expectedIssuer = coreConfig.REHAB_HEP_JWT_ISSUER
  const expectedJwks = 'https://127.0.0.1:18443/admin-api/rehab/hep-auth/jwks'
  return {
    observation: 'local-runtime-read-only',
    deployedLedger,
    runtimeLedger: installed.migrationLedger || '',
    bridgeEnabled: coreConfig.REHAB_HEP_BRIDGE_ENABLED === 'true',
    hepCoreUrl: hepConfig.HEP_CORE_BASE_URL || '',
    checks: {
      installedRuntimeMatchesDb: installed.migrationLedger === deployedLedger,
      hepSchemaReady: database.hepTables >= 9 && database.hepMenus >= 10,
      bridgeSecretMatched: sameSecret(coreConfig.REHAB_HEP_BRIDGE_TOKEN,
        hepConfig.HEP_CORE_INTEGRATION_TOKEN),
      jwtIssuerMatched: !!expectedIssuer && expectedIssuer === hepConfig.HEP_JWT_ISSUER,
      jwtJwksMatched: expectedJwks === hepConfig.HEP_JWT_JWKS_URI,
      privateKeyReadable: keyReadable
    },
    statistics: { migrationCount: database.count, lastMigration: database.version,
      hepTables: database.hepTables, hepMenus: database.hepMenus }
  }
}
