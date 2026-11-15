/**
 * Read-only HEP 037 rollout impact. It never grants existing users menus.
 * No patient data, UUIDs, role IDs, secrets or tenant names are output.
 */
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const source = resolve(dirname(fileURLToPath(import.meta.url)), '../../sql/mysql/rehab-hep-core-delivery-v1.sql')
const expectedSha256 = '2943301355afbeaa3573a0693b96229e74b6cb931e39df85ba39d60c9c13077d'

export function evaluate037PermissionImpact({
  ledger, hepTables, existingMenus, existingMenuGrants,
  parentMenuCount, superAdminRoles, therapistRoles, sourceVerified
}) {
  const checks = {
    sourceVerified: sourceVerified === true,
    baseline036: ledger === '036:36',
    noExistingHepTables: hepTables === 0,
    noMenuCollisions: existingMenus === 0 && existingMenuGrants === 0,
    correctParentMenu: parentMenuCount === 1,
    rolesExactlyScoped: superAdminRoles === 1 && therapistRoles === 1
  }
  const blockers = Object.entries(checks).filter(([, valid]) => !valid).map(([name]) => name)
  const adminGrants = 10 * Math.max(0, superAdminRoles || 0)
  const therapistGrants = 8 * Math.max(0, therapistRoles || 0)
  return {
    schemaPreconditionsVerified: blockers.length === 0,
    expectedNewAdminGrants: adminGrants,
    expectedNewTherapistGrants: therapistGrants,
    expectedNewTotalGrants: adminGrants + therapistGrants,
    expandsExistingRolePermissions: adminGrants + therapistGrants > 0,
    requiresRoleReviewBeforeLive037: adminGrants + therapistGrants > 0,
    checks, blockers
  }
}

function queryMetadata() {
  const sql = [
    'SELECT CONCAT(',
    "(SELECT CONCAT(MAX(version),':',COUNT(*)) FROM internal_schema_history),'|',",
    "(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND",
    "(table_name LIKE 'rehab_hep_%' OR table_name='rehab_patient_app_invitation')),'|',",
    "(SELECT COUNT(*) FROM system_menu WHERE id BETWEEN 98900 AND 98909),'|',",
    "(SELECT COUNT(*) FROM system_role_menu WHERE menu_id BETWEEN 98900 AND 98909),'|',",
    "(SELECT COUNT(*) FROM system_menu WHERE id=9000),'|',",
    "(SELECT COUNT(*) FROM system_role WHERE tenant_id=1 AND code='super_admin' AND deleted=b'0'),'|',",
    "(SELECT COUNT(*) FROM system_role WHERE tenant_id=1 AND code='rehab_therapist' AND deleted=b'0'));"
  ].join(' ')
  const raw = execFileSync('docker', [
    'exec', 'rehab-studio-internal-mysql-1', 'mysql',
    '--defaults-extra-file=/run/rehab-secrets/mysql-client.cnf',
    '--batch', '--skip-column-names', 'ruoyi-vue-pro', '--execute', sql
  ], { encoding: 'utf8', timeout: 12000, maxBuffer: 8192 }).trim()
  const values = raw.split('|')
  if (values.length !== 7 || values.slice(1).some(value => !/^(0|[1-9][0-9]*)$/.test(value))) {
    throw Error('CORE_SCHEMA_METADATA_UNAVAILABLE')
  }
  return {
    ledger: values[0], hepTables: Number(values[1]),
    existingMenus: Number(values[2]), existingMenuGrants: Number(values[3]),
    parentMenuCount: Number(values[4]), superAdminRoles: Number(values[5]),
    therapistRoles: Number(values[6])
  }
}

export function inspect037PermissionImpact() {
  const sourceVerified =
    createHash('sha256').update(readFileSync(source)).digest('hex') === expectedSha256
  return evaluate037PermissionImpact({ ...queryMetadata(), sourceVerified })
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    if (process.argv.length !== 3 || process.argv[2] !== '--live') throw Error('READ_ONLY_FLAG_REQUIRED')
    const report = inspect037PermissionImpact()
    process.stdout.write(JSON.stringify(report, null, 2) + '\n')
    if (!report.schemaPreconditionsVerified || report.requiresRoleReviewBeforeLive037) process.exitCode = 2
  } catch {
    process.stderr.write('037 permission preflight failed closed\n')
    process.exitCode = 2
  }
}
