// Read-only consistency audit of historic Owner ACTIVE bindings vs current
// Core tenants. Never exposes UUIDs, organization names or patient records.
import { execFileSync } from 'node:child_process'
import { homedir } from 'node:os'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

export function reconcileOwnerCoreBindings(ownerIds, matchedCount) {
  if (!Array.isArray(ownerIds) || ownerIds.length > 1024 ||
      ownerIds.some(id => !Number.isSafeInteger(id) || id <= 0) ||
      new Set(ownerIds).size !== ownerIds.length ||
      !Number.isSafeInteger(matchedCount) || matchedCount < 0 || matchedCount > ownerIds.length) {
    throw Error('INVALID_OWNER_CORE_BINDINGS')
  }
  return {
    ready: ownerIds.length === matchedCount,
    ownerActiveBindings: ownerIds.length,
    currentCoreTenantMatches: matchedCount,
    unmatchedOwnerActive: ownerIds.length - matchedCount,
    // No actual identifier, organization name or medical record is returned.
    requiresHumanReconciliation: ownerIds.length !== matchedCount
  }
}

export function inspectOwnerCoreBindings() {
  const ownerDb = resolve(homedir(), 'my-project/rehab-runtime/owner-local/owner.sqlite')
  const raw = execFileSync('/usr/bin/sqlite3', [
    '-readonly', ownerDb,
    "SELECT main_tenant_id FROM applications WHERE status='APPROVED' AND provisioning_status='ACTIVE' ORDER BY id;"
  ], { encoding: 'utf8', timeout: 8000, maxBuffer: 128000 })
  const values = raw.trim() ? raw.trim().split(/\r?\n/).map(Number) : []
  // All SQL values come from checked integer columns, never raw strings.
  if (values.length > 1024 || values.some(id => !Number.isSafeInteger(id) || id < 1)) {
    throw Error('OWNER_ACTIVE_BINDING_INVALID')
  }
  const idList = values.join(',') || '0'
  const query = `SELECT COUNT(*) FROM system_tenant WHERE deleted=0 AND id IN (${idList});`
  const result = execFileSync('docker', [
    'exec', 'rehab-studio-internal-mysql-1', 'mysql',
    '--defaults-extra-file=/run/rehab-secrets/mysql-client.cnf',
    '--batch', '--skip-column-names', 'ruoyi-vue-pro', '--execute', query
  ], { encoding: 'utf8', timeout: 12000, maxBuffer: 3200 })
  return reconcileOwnerCoreBindings(values, Number(result.trim()))
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  try {
    if (process.argv[2] !== '--live') throw Error('READ_ONLY_FLAG_REQUIRED')
    const report = inspectOwnerCoreBindings()
    process.stdout.write(JSON.stringify(report, null, 2) + '\n')
    if (!report.ready) process.exitCode = 2
  } catch {
    // Do not dump raw queries, database error strings or tenant IDs.
    process.stderr.write('Owner/Core read-only link audit failed closed\n')
    process.exitCode = 2
  }
}
