import assert from 'node:assert/strict'
import { test } from 'node:test'
import { randomBytes } from 'node:crypto'
import { mkdtempSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { OwnerLicenseStore } from './store.mjs'
import { OwnerLicensingService, secureProvisioningOrigin, classifyCoreProvisioningFailure } from './service.mjs'

test('Core errors never persist raw SQL, initial passwords or private endpoints', () => {
  const raw = new Error("SQL VALUES('SyntheticPassword123','secret-token') host 127.0.0.1")
  const sanitized = classifyCoreProvisioningFailure(raw)
  assert.equal(sanitized, 'CORE_PROVISIONING_REQUIRES_REVIEW')
  assert.equal(sanitized.includes('SyntheticPassword123'), false)
  assert.equal(sanitized.includes('secret-token'), false)
  assert.equal(classifyCoreProvisioningFailure({name:'AbortError'}), 'CORE_PROVISIONING_TIMEOUT')
  assert.equal(classifyCoreProvisioningFailure({cause:{code:'UNABLE_TO_VERIFY_LEAF_SIGNATURE'}}),
    'CORE_PROVISIONING_TLS_NOT_TRUSTED')
  assert.equal(classifyCoreProvisioningFailure({cause:{code:'ECONNREFUSED'}}),
    'CORE_PROVISIONING_UNREACHABLE')
})

test('provisioning target rejects stale internal HTTP port and credentialed URLs', () => {
  assert.equal(secureProvisioningOrigin('https://127.0.0.1:18443'), true)
  assert.equal(secureProvisioningOrigin('https://core.example.invalid'), true)
  assert.equal(secureProvisioningOrigin('https://core.example.invalid/'), true)
  for (const invalid of [
    'http://127.0.0.1:48080', 'https://127.0.0.1:48080',
    'http://localhost:18443', 'https://user:pass@core.example.invalid',
    'https://core.example.invalid/admin-api', 'https://core.example.invalid?test=1',
    'https://core.example.invalid#fragment',
    'https://core.example.invalid.evil.invalid/path'
  ]) {
    assert.equal(secureProvisioningOrigin(invalid), false, invalid)
  }
})
import { SecretVault, totpAt } from './security.mjs'

test('main-system authorization gates provisioning and blocks unsynchronized revocation', async () => {
  const root = mkdtempSync(join(tmpdir(), 'owner-core-selector-test-'))
  const store = new OwnerLicenseStore(join(root, 'owner.sqlite'))
  const svc = new OwnerLicensingService(store, new SecretVault(randomBytes(32)))
  const originalFetch = globalThis.fetch
  try {
    const nowMs = Date.now()
    const secret = (await svc.provisionOwner({
      username: 'testowner', password: 'Synthetic-Owner-Password-Only-456!', nowMs
    })).mfaSecret
    const token = (await svc.login({
      username: 'testowner', password: 'Synthetic-Owner-Password-Only-456!',
      totp: totpAt(secret, Math.floor(nowMs / 30_000)), nowMs
    })).token
    const policy = { expiresAtMs: nowMs + 90 * 86400000,
      seatLimit: 6, maxInstallations: 1, modules: ['motion', 'report'] }
    const first = svc.submitPublicApplication({
      organization: 'Synthetic motion-only studio', contactName: 'Synthetic contact', nowMs
    })
    svc.provisioning = { enabled: true, baseUrl: 'https://core.example.invalid', token: 'synthetic-test-token-32characters',
      planId: 1, packageId: 1, timeoutMs: 1000 }
    assert.equal(svc.provisioningConfigured(), true)
    svc.provisioning.enabled = false
    assert.equal(svc.provisioningConfigured(), false,
      'Valid HTTPS and a configured token do not activate tenant provisioning')
    svc.provisioning.enabled = true
    assert.equal(svc.decideApplication({ token, applicationId: first.applicationId,
      decision: 'APPROVED', reason: 'Motion only', policy, nowMs }).provisioning, 'NOT_AUTHORIZED')
    assert.deepEqual(svc.status({ token, tenantId: first.tenantId, nowMs }).modules,
      ['motion', 'report'])
    assert.throws(() => svc.retryProvision({ token, applicationId: first.applicationId,
      credentials: {}, nowMs }), error => error.code === 'MAIN_SYSTEM_NOT_AUTHORIZED')
    svc.provisioning.baseUrl = ''
    const amended = svc.updateEntitlement({ token, tenantId: first.tenantId,
      policy: { ...policy, modules: ['motion', 'rehab', 'report'] }, reason: 'Enable Rehab', nowMs })
    assert.equal(amended.coreSyncStatus, 'NOT_REQUIRED')
    assert.equal(svc.applicationList({ token, nowMs })[0].provisioningStatus, 'NOT_CONFIGURED')
    // Simulate an already provisioned Core binding in the isolated test DB.
    store.db.prepare("UPDATE applications SET provisioning_status='ACTIVE',main_tenant_id=888 WHERE id=?")
      .run(first.applicationId)
    assert.throws(() => svc.updateEntitlement({ token, tenantId: first.tenantId,
      policy, reason: 'Revoke Rehab', nowMs }),
    error => error.code === 'MAIN_SYSTEM_REVOCATION_REQUIRES_SYNC')
    assert.equal(svc.status({ token, tenantId: first.tenantId, nowMs }).modules.includes('rehab'), true)
    const renewed = svc.updateEntitlement({ token, tenantId: first.tenantId,
      policy: { ...policy, expiresAtMs: policy.expiresAtMs + 86400000, seatLimit: 10,
        modules: ['motion', 'rehab', 'report'] }, reason: 'Extend seats', nowMs })
    assert.equal(renewed.coreSyncStatus, 'NOT_CONFIGURED',
      'Without Core sync configuration the platform must not imply seats or expiry were applied')
    svc.provisioning.baseUrl = 'https://core.example.invalid'
    let synchronizations = 0
    globalThis.fetch = async (url, options) => {
      synchronizations++
      assert.equal(url, 'https://core.example.invalid/admin-api/system/saas/internal/entitlements/sync')
      const body = JSON.parse(options.body)
      assert.equal(body.tenantUuid, first.tenantId)
      assert.equal(body.seatLimit, 15)
      assert.equal(body.expiresAtMs, policy.expiresAtMs + 2 * 86400000)
      assert.equal(body.revision, 3)
      return { ok: true, json: async () => ({ code: 0, data: { status: 'ACTIVE' } }) }
    }
    const synced = svc.updateEntitlement({ token, tenantId: first.tenantId,
      policy: { ...policy, modules: ['motion', 'rehab', 'report'], seatLimit: 15,
        expiresAtMs: policy.expiresAtMs + 2 * 86400000 }, reason: 'Sync renewal', nowMs })
    assert.equal(synced.coreSyncStatus, 'PENDING')
    for (let i = 0; i < 20 && svc.applicationList({ token, nowMs })[0].coreSyncStatus !== 'ACTIVE'; i++) {
      await new Promise(resolve => setTimeout(resolve, 10))
    }
    assert.equal(svc.applicationList({ token, nowMs })[0].coreSyncStatus, 'ACTIVE')
    assert.equal(synchronizations, 1)
  } finally {
    globalThis.fetch = originalFetch
    store.close()
    rmSync(root, { recursive: true, force: true })
  }
})
