/**
 * Reconciles B/B/A/B/A decisions with technical and formal release gates.
 * Read-only, no API bearer use, no database writes, no sensitive output.
 * A decision record is never equivalent to an Owner MFA or clinical signoff.
 */
import { readFileSync, lstatSync } from 'node:fs'
import { spawnSync, execFileSync } from 'node:child_process'
import { resolve, dirname } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { homedir } from 'node:os'
import { createHash } from 'node:crypto'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const qa = resolve(homedir(), 'my-project/rehab/validation-evidence/qa-20261113-formal-owner-hep-identity')
const candidate = resolve(homedir(),
  'my-project/rehab/validation-evidence/qa-20261113-github-sync-qa-build/candidate-runtime/1.0.0')
const source = resolve(root, 'sql/mysql/rehab-hep-core-delivery-v1.sql')

// An App folder alone is not an activatable release. Check only bundled,
// public trust metadata; never load an issuer private key or query Owner.
export function inspectInstalledLauncherBundle(
  resources = '/Applications/康复管理系统内部版.app/Contents/Resources',
  infoPlist = '/Applications/康复管理系统内部版.app/Contents/Info.plist'
) {
  let installedLauncherBundlePresent = false
  let installedLauncherTrustConfigured = false
  try {
    installedLauncherBundlePresent = lstatSync(resources).isDirectory() &&
      lstatSync(infoPlist).isFile()
    if (!installedLauncherBundlePresent) {
      return { installedLauncherBundlePresent, installedLauncherTrustConfigured }
    }
    const path = resolve(resources, 'resources/license/trusted-issuers.json')
    const stat = lstatSync(path)
    if (!stat.isFile() || stat.isSymbolicLink() || stat.size > 8192) {
      return { installedLauncherBundlePresent, installedLauncherTrustConfigured }
    }
    const config = JSON.parse(readFileSync(path, 'utf8'))
    const endpoint = new URL(config.ownerBaseUrl)
    const host = endpoint.hostname.toLowerCase()
    const endpointValid = endpoint.protocol === 'https:' && !!host &&
      !endpoint.username && !endpoint.password && !endpoint.search &&
      !endpoint.hash && endpoint.pathname === '/' &&
      !/^(localhost|127\.|0\.0\.0\.0|\[::1\]|(?:.*\.)?(?:invalid|example|test|localhost))$/i.test(host)
    const keys = config.issuers
    const uniqueKids = new Set()
    const keysValid = Array.isArray(keys) && keys.length > 0 && keys.length <= 12 &&
      keys.every(item => {
        if (!item || typeof item.kid !== 'string' ||
            !/^[A-Za-z0-9._-]{1,48}$/.test(item.kid) || uniqueKids.has(item.kid) ||
            typeof item.publicKeyBase64url !== 'string' ||
            !/^[A-Za-z0-9_-]{43}$/.test(item.publicKeyBase64url)) return false
        uniqueKids.add(item.kid)
        const key = Buffer.from(item.publicKeyBase64url, 'base64url')
        return key.length === 32 && key.toString('base64url') === item.publicKeyBase64url
      })
    installedLauncherTrustConfigured = endpointValid && keysValid
  } catch { /* fail closed, do not expose bundle paths or key material */ }
  return { installedLauncherBundlePresent, installedLauncherTrustConfigured }
}

function runJson(file, args = [], permittedExitCodes = [0, 2]) {
  const result = spawnSync(process.execPath, [file, ...args], {
    cwd: root, encoding: 'utf8', timeout: 25000, maxBuffer: 128 * 1024
  })
  // These tools intentionally exit 2 on NO-GO; valid JSON is still evidence.
  if (!permittedExitCodes.includes(result.status)) throw Error('PREFLIGHT_TOOL_NOT_COMPLETE')
  return JSON.parse(result.stdout)
}

export function evaluateRollout({ decisions, owner, hep, roles, links, mvp,
  packageGate, sourceDirty, migrationSha256, installedLauncherBundlePresent = false,
  installedLauncherTrustConfigured = false }) {
  if (decisions?.schema !== 'rehab-owner-core-hep-decisions/1') {
    throw Error('APPROVED_DECISION_RECORD_INVALID')
  }
  const confirmed = decisions.choices
  const rolePolicyApproved = confirmed.hep037Permissions.selected === 'B' &&
    confirmed.hep037Permissions.adminMenuCount === 10 &&
    confirmed.hep037Permissions.therapistMenuCount === 8 &&
    confirmed.hep037Permissions.migrationSha256 === migrationSha256 &&
    roles.expectedNewAdminGrants === 10 &&
    roles.expectedNewTherapistGrants === 8 &&
    roles.schemaPreconditionsVerified
  const historicalBindingsFrozen = confirmed.historicalOwnerCoreBindings.selected === 'A' &&
    confirmed.historicalOwnerCoreBindings.automaticRebindingPermitted === false &&
    links.unmatchedOwnerActive === 3 && links.currentCoreTenantMatches === 0
  const planPrepared = confirmed.internalPilotPlan.selected === 'B' &&
    confirmed.internalPilotPlan.planCreatedInLiveCore === false
  const checks = {
    userChoicesRecorded: ['B', 'B', 'A', 'B', 'A'].every((val, i) =>
      [confirmed.internalPilotPlan.selected, confirmed.hep037Permissions.selected,
        confirmed.historicalOwnerCoreBindings.selected, confirmed.deployment.selected,
        confirmed.clinicalReview.selected][i] === val),
    scoped037RolePolicyApproved: rolePolicyApproved,
    historicalUnmatchedBindingsFrozen: historicalBindingsFrozen,
    internalPilotPlanPreparedNotActive: planPrepared,
    ownerHttpsTargetHealthy: owner.checks.targetMatchesCurrentCore &&
      owner.checks.targetReachable,
    ownerMfaScopedProvisioningActivated: owner.ready,
    coreHepSignedIdentityLive: hep.ready,
    installedLauncherBundlePresent,
    installedLauncherTrustConfigured,
    sourceFrozen: !sourceDirty,
    mvp182FormallyAccepted: mvp.ready && mvp.pass === 182,
    formalInstallerGateReady: packageGate.ready,
    fiveClinicalReviewsActual: confirmed.clinicalReview.actualClinicalReviewPerformed === true
  }
  const prerequisites = [
    'ownerMfaScopedProvisioningActivated', 'coreHepSignedIdentityLive',
    'installedLauncherBundlePresent', 'installedLauncherTrustConfigured',
    'sourceFrozen', 'mvp182FormallyAccepted', 'formalInstallerGateReady',
    'fiveClinicalReviewsActual'
  ]
  return {
    schema: 'rehab-pilot-controlled-rollout-preflight/1',
    decisionSet: 'B/B/A/B/A',
    approvedPolicyReadyForIsolatedTesting: checks.userChoicesRecorded &&
      checks.scoped037RolePolicyApproved && checks.historicalUnmatchedBindingsFrozen &&
      checks.internalPilotPlanPreparedNotActive,
    liveProductionUpgradeAllowed: false,
    // A preflight is information, not an executor or authorization token.
    canScheduleFormalCutoverAfterAllGates:
      prerequisites.every(key => checks[key] === true),
    checks,
    remaining: Object.entries(checks).filter(([, okay]) => !okay)
      .map(([key]) => key),
    clinicalSignatureGrantedByThisTool: false,
    mutatesRuntime: false
  }
}

export function inspectCurrentRollout() {
  const decisions = JSON.parse(readFileSync(
    resolve(qa, 'CONFIRMED_B_B_A_B_A_DECISIONS_20261113.json'), 'utf8'))
  const owner = runJson(resolve(root, 'desktop/scripts/owner-core-provisioning-preflight.mjs'), ['--live'])
  const hep = runJson(resolve(root, 'desktop/scripts/hep-release-preflight.mjs'),
    ['--candidate', candidate, '--live'])
  const roles = runJson(resolve(root, 'desktop/scripts/hep-037-permission-diff.mjs'), ['--live'])
  const links = runJson(resolve(root, 'desktop/scripts/owner-core-link-audit.mjs'), ['--live'])
  const mvp = runJson(resolve(root, 'desktop/scripts/mvp306-release-gate.mjs'))
  const packageGate = runJson(resolve(root, 'desktop/scripts/prepackage-readiness.mjs'), [], [0, 1])
  const sourceDirty = Boolean(execFileSync('git', ['status', '--porcelain'], {
    cwd: root, encoding: 'utf8', timeout: 10000
  }).trim())
  const migrationSha256 = createHash('sha256').update(readFileSync(source)).digest('hex')
  const launcher = inspectInstalledLauncherBundle()
  return evaluateRollout({ decisions, owner, hep, roles, links, mvp,
    packageGate, sourceDirty, migrationSha256, ...launcher })
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  if (process.argv.length !== 3 || process.argv[2] !== '--live-readonly') {
    process.stderr.write('Usage: node approved-pilot-rollout-preflight.mjs --live-readonly\n')
    process.exitCode = 2
  } else {
    try {
      const result = inspectCurrentRollout()
      process.stdout.write(JSON.stringify(result, null, 2) + '\n')
      if (!result.canScheduleFormalCutoverAfterAllGates) process.exitCode = 2
    } catch {
      process.stderr.write('CONTROLLED_ROLLOUT_PREFLIGHT_FAILED_CLOSED\n')
      process.exitCode = 2
    }
  }
}
