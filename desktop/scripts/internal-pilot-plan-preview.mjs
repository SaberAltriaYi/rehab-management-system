/**
 * B decision: non-mutating draft for the Owner→Core internal pilot plan.
 * The quota values are derived from the committed synthetic-beta fixture,
 * not inferred from a real customer's contract. This script never opens a DB.
 */
import { readFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const source = resolve(root, 'sql/mysql/rehab-saas-platform-v2.sql')

export function parseSyntheticPlanSeed(sql) {
  const pattern = /VALUES\s*\(\s*'synthetic-beta'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)/g
  const matches = [...sql.matchAll(pattern)]
  if (matches.length !== 1) throw Error('SYNTHETIC_PILOT_SEED_MISSING_OR_AMBIGUOUS')
  const values = matches[0].slice(1).map(Number)
  if (values.some(value => !Number.isSafeInteger(value) || value <= 0)) {
    throw Error('INVALID_SYNTHETIC_QUOTA')
  }
  const [maxUsers, maxPatients, motionQuota, storageQuotaBytes] = values
  return { maxUsers, maxPatients, motionQuota, storageQuotaBytes }
}

export function preparePilotDraft(seed, choices) {
  if (choices?.plan !== 'B' || choices?.rolePermissions !== 'B' ||
      choices?.historicalLinks !== 'A' || choices?.rollout !== 'B' ||
      choices?.clinicianSignoff !== 'A') {
    throw Error('USER_DECISION_SET_NOT_CONFIRMED')
  }
  if (!seed || Object.values(seed).length !== 4 ||
      Object.values(seed).some(v => !Number.isSafeInteger(v) || v <= 0)) {
    throw Error('INVALID_DRAFT_PLAN_QUOTAS')
  }
  return {
    schema: 'rehab-owner-internal-pilot-plan-draft/1',
    decisionSet: 'B/B/A/B/A',
    candidateName: 'rehab-internal-pilot-review',
    source: 'sql/mysql/rehab-saas-platform-v2.sql synthetic-beta seed',
    suggestedQuota: seed,
    quotaApprovedForRealTenant: false,
    status: 'DRAFT_NOT_CREATED',
    liveSaasPlanId: null,
    tenantPackageId: null,
    tenantId: null,
    expiresAt: null,
    existingHistoricalTenantBindingsFrozen: 3,
    roles: {
      scope: 'existing internal tenant 1 only',
      adminMenuGrants: 10,
      therapistMenuGrants: 8,
      otherTenantsMenuGrants: 0,
      clinicianAuthorizedToSignByThisDocument: false
    },
    gatedActions: [
      'Verify actual tenant package menu scope and the chosen quota limits',
      'Do not enable Core/Owner machine provisioning before live V3/V4 schema and token checks',
      'Reconcile legacy ACTIVE bindings separately; never recreate them automatically',
      'Use Owner MFA to approve one authorized pilot application',
      'No patient, clinician or license mutation until the maintenance-window gates pass'
    ],
    noSqlExecuted: true,
    formalReleaseApproved: false
  }
}

/** The user approved three metrics, but did not approve a storage quota. */
export function prepareSelectedPilotDraft(seed, choices, selected) {
  const base = preparePilotDraft(seed, choices)
  if (selected?.maxUsers !== 20 || selected?.maxPatients !== 400 ||
      selected?.motionQuota !== 2000 || selected?.storageQuotaBytes != null) {
    throw Error('CUSTOM_PILOT_DECISION_NOT_VERIFIED')
  }
  return {
    ...base,
    schema: 'rehab-owner-internal-pilot-custom-draft/1',
    candidateName: 'rehab-internal-pilot-20u-400p-2000motion',
    selectedQuota: {
      maxUsers: 20, maxPatients: 400, motionQuota: 2000,
      storageQuotaBytes: null
    },
    selectedQuotaApproved: {
      maxUsers: true, maxPatients: true, motionQuota: true,
      storageQuotaBytes: false
    },
    provisionalStorageBytesForSyntheticOnly: seed.storageQuotaBytes,
    proposedFullQuotaForSyntheticOnly: {
      maxUsers: 20, maxPatients: 400, motionQuota: 2000,
      storageQuotaBytes: seed.storageQuotaBytes
    },
    productionPlanCreationAllowed: false,
    storageRequiresOwnerDecision: true,
    formalSigning: 'QA_ONLY_NO_DEVELOPER_ID',
    trueOwnerMfaCompleted: false,
    source: 'User-selected 20/400/2000; storage provisional from synthetic-beta fixture'
  }
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  if (process.argv.length !== 3 ||
      !['--draft-only', '--selected-quota-draft'].includes(process.argv[2])) {
    process.stderr.write('Usage: node internal-pilot-plan-preview.mjs --draft-only|--selected-quota-draft\n')
    process.exitCode = 2
  } else {
    try {
      const values = parseSyntheticPlanSeed(readFileSync(source, 'utf8'))
      const choices = {
        plan: 'B', rolePermissions: 'B', historicalLinks: 'A',
        rollout: 'B', clinicianSignoff: 'A'
      }
      const draft = process.argv[2] === '--selected-quota-draft'
        ? prepareSelectedPilotDraft(values, choices, {
          maxUsers: 20, maxPatients: 400, motionQuota: 2000,
          storageQuotaBytes: null
        })
        : preparePilotDraft(values, choices)
      process.stdout.write(JSON.stringify(draft, null, 2) + '\n')
    } catch {
      process.stderr.write('INTERNAL_PILOT_PLAN_DRAFT_VALIDATION_FAILED\n')
      process.exitCode = 2
    }
  }
}
