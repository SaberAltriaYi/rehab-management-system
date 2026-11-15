// Read-only, fail-closed check for the user-designated synthetic-only "test"
// institution. Never approves a license, alters production SQL or uses an
// Owner session / TOTP. Output contains only booleans and symbolic blockers.
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { inspectLocalProvisioning } from './owner-core-provisioning-preflight.mjs'

const OWNER_DB = resolve(homedir(), 'my-project/rehab-runtime/owner-local/owner.sqlite')
const OWNER_ENV = resolve(homedir(), 'my-project/rehab-runtime/owner-local/owner.env')
const PILOT_QUOTAS = Object.freeze({
  users: 20, patients: 400, assessments: 2000, storageBytes: 5368709120
})
// QA-only first-login scope: rehab navigation, motion page, and motion query.
// No patient write, assessment sign-off, staff management, HEP or other menus.
export const PILOT_READONLY_MENU_IDS = Object.freeze([9000, 9700, 9710])
// Exact QA candidate verified by synthetic HTTP E2E. Launcher Start can rebuild
// its older bundled image: compare the running immutable ID, never its tag.
export const PILOT_CORE_IMAGE_ID =
  'sha256:b0f26de5e12d6cddd8a5fd2a0b1e7e000af6aa6e6f207da05c5ab0b798fca660'

function positiveId(value) {
  return typeof value === 'string' && /^[1-9][0-9]{0,17}$/.test(value)
    ? value : null
}

function privateEnv(contents) {
  const values = {}
  for (const line of contents.split(/\r?\n/)) {
    const match = /^\s*(?:export\s+)?(REHAB_MAIN_PROVISIONING_(?:PLAN|PACKAGE)_ID)=(.*)$/.exec(line)
    if (match) values[match[1]] = match[2].trim().replace(/^['"]|['"]$/g, '')
  }
  return values
}

export function evaluateInternalPilotReadiness({ applicants, planMatches, packageMatches,
  permissionMatches, coreImageVerified, ownerCore }) {
  const uniqueApplicant = Array.isArray(applicants) && applicants.length === 1 ? applicants[0] : null
  let modules = []
  try {
    modules = JSON.parse(uniqueApplicant?.modules_json)
  } catch { /* invalid license modules must fail closed */ }
  const checks = {
    exactlyOneDesignatedApplication: Boolean(uniqueApplicant),
    ownerApplicationApproved: uniqueApplicant?.status === 'APPROVED',
    ownerLicenseActive: uniqueApplicant?.license_status === 'ACTIVE',
    ownerLicenseIncludesRehab: Array.isArray(modules) && modules.includes('rehab'),
    ownerLicenseSeatLimit20: uniqueApplicant?.seat_limit === PILOT_QUOTAS.users,
    noPreviousCoreTenantBinding: uniqueApplicant?.main_tenant_id === null,
    ownerCoreProvisioningNotYetAttempted: uniqueApplicant?.provisioning_status === 'NOT_CONFIGURED',
    effectiveCorePilotQuotaVerified: planMatches === 1,
    effectiveCorePermissionPackageVerified: packageMatches === 1,
    // Require the exact three-menu QA scope; a broad pre-existing package is
    // not an implicit authorization to grant other system or clinical menus.
    corePermissionPackageHasExactPilotScope: permissionMatches === 1,
    coreRuntimeCandidateImageVerified: coreImageVerified === true,
    ownerCoreMachineChannelReady: ownerCore?.ready === true
  }
  return {
    schema: 'rehab-internal-pilot-live-readonly/2',
    permittedDataScope: 'SYNTHETIC_ONLY',
    actualPatientDataUseVerifiedByThisTool: false,
    expectedQuotas: PILOT_QUOTAS,
    technicalPrerequisitesReady: Object.values(checks).every(Boolean),
    productionWriteAuthorizedByThisTool: false,
    formalClinicalReleaseAuthorizedByThisTool: false,
    checks,
    blockers: Object.entries(checks).filter(([, ok]) => !ok).map(([key]) => key)
  }
}

export async function inspectInternalPilotLive() {
  const raw = execFileSync('/usr/bin/sqlite3', [
    '-readonly', '-json', OWNER_DB,
    `SELECT a.status,a.provisioning_status,a.main_tenant_id,
      e.status AS license_status,e.seat_limit,e.modules_json
     FROM applications a LEFT JOIN entitlements e ON e.tenant_uuid=a.tenant_uuid
     WHERE a.organization='test' AND a.submitted_at>=1794585600000
       AND a.submitted_at<1794672000000 LIMIT 2;`
  ], { encoding: 'utf8', timeout: 8000, maxBuffer: 8192 })
  const applicants = raw.trim() ? JSON.parse(raw) : []
  const owner = privateEnv(readFileSync(OWNER_ENV, 'utf8'))
  const plan = positiveId(owner.REHAB_MAIN_PROVISIONING_PLAN_ID)
  const pkg = positiveId(owner.REHAB_MAIN_PROVISIONING_PACKAGE_ID)
  let planMatches = 0
  let packageMatches = 0
  let permissionMatches = 0
  let coreImageVerified = false
  try {
    const activeImage = execFileSync('docker', [
      'inspect', 'rehab-studio-internal-server-1', '--format', '{{.Image}}'
    ], { encoding: 'utf8', timeout: 8000, maxBuffer: 512 }).trim()
    coreImageVerified = activeImage === PILOT_CORE_IMAGE_ID
  } catch { /* fail closed if a running candidate cannot be attested */ }
  if (plan && pkg) {
    const sql = `SELECT
      (SELECT COUNT(*) FROM saas_plans WHERE id=${plan} AND status=1
        AND max_users=20 AND max_patients=400 AND motion_quota=2000
        AND storage_quota=5368709120),
      (SELECT COUNT(*) FROM system_tenant_package WHERE id=${pkg}
        AND status=0 AND deleted=0),
      (SELECT COUNT(*) FROM system_tenant_package p WHERE p.id=${pkg}
        AND p.status=0 AND p.deleted=0
        AND JSON_VALID(p.menu_ids)=1 AND JSON_TYPE(p.menu_ids)='ARRAY'
        AND JSON_LENGTH(p.menu_ids)=${PILOT_READONLY_MENU_IDS.length}
        AND JSON_CONTAINS(p.menu_ids,'${JSON.stringify(PILOT_READONLY_MENU_IDS)}')
        AND (SELECT COUNT(*) FROM system_menu m WHERE m.status=0 AND m.deleted=0
          AND ((m.id=9000 AND m.type=1 AND m.parent_id=0)
            OR (m.id=9700 AND m.type=2 AND m.parent_id=9000
                AND m.permission='rehab:motion:query')
            OR (m.id=9710 AND m.type=3 AND m.parent_id=9700
                AND m.permission='rehab:motion:query')))=${PILOT_READONLY_MENU_IDS.length});`
    const output = execFileSync('docker', [
      'exec', 'rehab-studio-internal-mysql-1', 'mysql',
      '--defaults-extra-file=/run/rehab-secrets/mysql-client.cnf',
      '--batch', '--skip-column-names', 'ruoyi-vue-pro', '--execute', sql
    ], { encoding: 'utf8', timeout: 12000, maxBuffer: 128 })
    const counts = output.trim().split(/\s+/).map(Number)
    if (counts.length !== 3 || counts.some(n => !Number.isSafeInteger(n))) {
      throw Error('INVALID_CORE_PLAN_METADATA')
    }
    ;[planMatches, packageMatches, permissionMatches] = counts
  }
  return evaluateInternalPilotReadiness({
    applicants, planMatches, packageMatches, permissionMatches, coreImageVerified,
    ownerCore: await inspectLocalProvisioning()
  })
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  if (process.argv.length !== 3 || process.argv[2] !== '--live-readonly') {
    process.stderr.write('Usage: node owner-internal-pilot-readiness.mjs --live-readonly\n')
    process.exitCode = 2
  } else {
    try {
      const report = await inspectInternalPilotLive()
      process.stdout.write(JSON.stringify(report, null, 2) + '\n')
      if (!report.technicalPrerequisitesReady) process.exitCode = 2
    } catch {
      // Do not spill stdout, stderr or database errors containing IDs or keys.
      process.stderr.write('Internal pilot read-only preflight failed closed\n')
      process.exitCode = 2
    }
  }
}
