import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { evaluateRollout, inspectInstalledLauncherBundle } from './approved-pilot-rollout-preflight.mjs'

const sha = 'a'.repeat(64)
const decisions = {
  schema: 'rehab-owner-core-hep-decisions/1',
  choices: {
    internalPilotPlan: { selected: 'B', planCreatedInLiveCore: false },
    hep037Permissions: { selected: 'B', adminMenuCount: 10, therapistMenuCount: 8,
      migrationSha256: sha },
    historicalOwnerCoreBindings: { selected: 'A', automaticRebindingPermitted: false },
    deployment: { selected: 'B' },
    clinicalReview: { selected: 'A', actualClinicalReviewPerformed: false }
  }
}
const args = {
  decisions,
  owner: { ready: false, checks: { targetMatchesCurrentCore: true, targetReachable: true } },
  hep: { ready: false },
  roles: { expectedNewAdminGrants: 10, expectedNewTherapistGrants: 8,
    schemaPreconditionsVerified: true },
  links: { unmatchedOwnerActive: 3, currentCoreTenantMatches: 0 },
  mvp: { ready: false, pass: 0 }, packageGate: { ready: false },
  sourceDirty: true, migrationSha256: sha
}

test('explicit user scope can be prepared without pretending MFA/clinical release', () => {
  const result = evaluateRollout(args)
  assert.equal(result.approvedPolicyReadyForIsolatedTesting, true)
  assert.equal(result.liveProductionUpgradeAllowed, false)
  assert.equal(result.canScheduleFormalCutoverAfterAllGates, false)
  assert.equal(result.checks.scoped037RolePolicyApproved, true)
  assert.equal(result.checks.historicalUnmatchedBindingsFrozen, true)
  assert.ok(result.remaining.includes('ownerMfaScopedProvisioningActivated'))
  assert.ok(result.remaining.includes('fiveClinicalReviewsActual'))
  assert.ok(result.remaining.includes('installedLauncherBundlePresent'))
  assert.ok(result.remaining.includes('installedLauncherTrustConfigured'))
})

test('a modified 037 migration or historical tenant mismatch invalidates the granted scope', () => {
  const badMigration = evaluateRollout({ ...args, migrationSha256: 'b'.repeat(64) })
  assert.equal(badMigration.approvedPolicyReadyForIsolatedTesting, false)
  const badHistory = evaluateRollout({
    ...args, links: { unmatchedOwnerActive: 2, currentCoreTenantMatches: 1 }
  })
  assert.equal(badHistory.approvedPolicyReadyForIsolatedTesting, false)
})

test('preflight never treats all booleans as authorization to execute live upgrade', () => {
  const ready = evaluateRollout({
    ...args, sourceDirty: false, installedLauncherBundlePresent: true,
    installedLauncherTrustConfigured: true,
    owner: { ...args.owner, ready: true },
    hep: { ready: true }, mvp: { ready: true, pass: 182 },
    packageGate: { ready: true },
    decisions: { ...decisions, choices: {
      ...decisions.choices,
      clinicalReview: { ...decisions.choices.clinicalReview, actualClinicalReviewPerformed: true }
    }}
  })
  assert.equal(ready.canScheduleFormalCutoverAfterAllGates, true)
  assert.equal(ready.liveProductionUpgradeAllowed, false)
  assert.equal(ready.clinicalSignatureGrantedByThisTool, false)
})

test('an installed app without trusted publisher/Owner endpoint cannot pass cutover', () => {
  const result = evaluateRollout({
    ...args, sourceDirty: false, installedLauncherBundlePresent: true,
    owner: { ...args.owner, ready: true }, hep: { ready: true },
    mvp: { ready: true, pass: 182 }, packageGate: { ready: true },
    decisions: { ...decisions, choices: {
      ...decisions.choices,
      clinicalReview: { ...decisions.choices.clinicalReview, actualClinicalReviewPerformed: true }
    }}
  })
  assert.equal(result.checks.installedLauncherBundlePresent, true)
  assert.equal(result.checks.installedLauncherTrustConfigured, false)
  assert.equal(result.canScheduleFormalCutoverAfterAllGates, false)
  assert.ok(result.remaining.includes('installedLauncherTrustConfigured'))
})

test('read-only installed app trust check rejects empty and malformed issuer data', () => {
  const folder = mkdtempSync(join(tmpdir(), 'rehab-installed-launcher-trust-'))
  const resources = join(folder, 'Contents/Resources')
  const plist = join(folder, 'Contents/Info.plist')
  const trust = join(resources, 'resources/license/trusted-issuers.json')
  const key = Buffer.alloc(32, 7).toString('base64url')
  try {
    mkdirSync(dirname(trust), { recursive: true })
    writeFileSync(plist, 'synthetic fixture')
    const check = () => inspectInstalledLauncherBundle(resources, plist)
    assert.deepEqual(check(), {
      installedLauncherBundlePresent: true, installedLauncherTrustConfigured: false
    })
    writeFileSync(trust, JSON.stringify({ ownerBaseUrl: null, issuers: [] }))
    assert.equal(check().installedLauncherTrustConfigured, false)
    writeFileSync(trust, JSON.stringify({ ownerBaseUrl: 'http://owner.example/',
      issuers: [{ kid: 'owner-v1', publicKeyBase64url: key }] }))
    assert.equal(check().installedLauncherTrustConfigured, false)
    writeFileSync(trust, JSON.stringify({ ownerBaseUrl: 'https://licenses.publisher.test/',
      issuers: [{ kid: 'owner-v1', publicKeyBase64url: key }] }))
    assert.equal(check().installedLauncherTrustConfigured, false)
    writeFileSync(trust, JSON.stringify({ ownerBaseUrl: 'https://licenses.publisher.org/',
      issuers: [{ kid: 'owner-v1', publicKeyBase64url: 'not-a-key' }] }))
    assert.equal(check().installedLauncherTrustConfigured, false)
    writeFileSync(trust, JSON.stringify({ ownerBaseUrl: 'https://licenses.publisher.org/',
      issuers: [{ kid: 'owner-v1', publicKeyBase64url: key }] }))
    assert.equal(check().installedLauncherTrustConfigured, true)
  } finally {
    rmSync(folder, { recursive: true, force: true })
  }
})
