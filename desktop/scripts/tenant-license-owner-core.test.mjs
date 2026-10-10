import assert from 'node:assert/strict'
import { generateKeyPairSync, randomUUID, sign } from 'node:crypto'
import test from 'node:test'
import {
  OwnerLicenseCore, enrollmentMessage, renewalMessage
} from './tenant-license-owner-core.mjs'
import {
  MAX_OFFLINE_LEASE_MS, verifyTenantLicenseLease
} from './tenant-license-lease.mjs'

// All signing keys are ephemeral synthetic test-only memory; nothing is saved.
const ISSUER = generateKeyPairSync('ed25519')
const DEVICE = generateKeyPairSync('ed25519')
const ATTACKER = generateKeyPairSync('ed25519')
const OTHER_DEVICE = generateKeyPairSync('ed25519')
const NOW = Date.parse('2026-10-10T05:00:00.000Z')
const KID = 'synthetic-owner-v1'
const OWNER = Object.freeze({ issuedByTrustedMfaProvider: true })
const NONE = Object.freeze({ issuedByTrustedMfaProvider: false })

function fixture({ seatLimit = 17, expiresAtMs = NOW + 365 * 86_400_000,
  maxInstallations = 1 } = {}) {
  const tenantId = randomUUID()
  const installationId = randomUUID()
  const app = new OwnerLicenseCore({
    signingKey: ISSUER.privateKey, publicKey: ISSUER.publicKey,
    keyId: KID,
    // Stub only. Production needs independent MFA middleware: a request-body
    // boolean must NEVER be treated as a valid owner assertion.
    ownerPolicy: (session) => session === OWNER
  })
  const application = app.submitApplication({
    tenantId, installationId, installationPublicKey: DEVICE.publicKey,
    possessionSignature: sign(null, enrollmentMessage(tenantId, installationId), DEVICE.privateKey),
    nowMs: NOW
  })
  function approve(extra = {}) {
    return app.approveApplication({
      applicationId: application.applicationId, owner: OWNER, nowMs: NOW,
      expiresAtMs, seatLimit, maxInstallations,
      modules: ['report', 'motion', 'rehab'], ...extra
    })
  }
  function verifyLease(token, extra = {}) {
    return verifyTenantLicenseLease(token, {
      trustedKeys: new Map([[KID, ISSUER.publicKey]]),
      tenantId, installationId, nowMs: NOW,
      lastTrustedTimeMs: NOW, minimumRevocationEpoch: 0, ...extra
    })
  }
  function online(extra = {}) {
    const nonce = app.beginOnlineRenewal({ tenantId, installationId, nowMs: NOW })
    const possessionSignature = sign(
      null, renewalMessage(nonce, tenantId, installationId), DEVICE.privateKey
    )
    return app.issueOnlineLease({ tenantId, installationId, nonce, possessionSignature, nowMs: NOW, ...extra })
  }
  return { app, tenantId, installationId, application, approve, online, verifyLease }
}
function rejected(code, operation) {
  assert.throws(operation, (error) => error.code === code, `expected ${code}`)
}

test('pending application is NOT licensed, even with valid installation proof', () => {
  const f = fixture()
  assert.equal(f.application.status, 'PENDING')
  rejected('TENANT_NOT_APPROVED', () => f.online())
  rejected('TENANT_NOT_APPROVED', () =>
    f.app.issueOfflineLease({ owner: OWNER, tenantId: f.tenantId,
      installationId: f.installationId, nowMs: NOW }))
})
test('owner must approve and the creator cannot impersonate MFA owner', () => {
  const f = fixture()
  rejected('OWNER_MFA_REQUIRED', () => f.approve({ owner: NONE }))
  rejected('OWNER_MFA_REQUIRED', () => f.approve({ owner: { issuedByTrustedMfaProvider: true } }))
  assert.equal(f.approve().status, 'ACTIVE')
  rejected('APPLICATION_NOT_PENDING', () => f.approve())
})
test('online lease requires signed device possession and verifies against trusted issuer', () => {
  const f = fixture()
  f.approve()
  const claims = f.verifyLease(f.online())
  assert.equal(claims.mode, 'online')
  assert.equal(claims.tenantId, f.tenantId)
  assert.equal(claims.installationId, f.installationId)
  assert.equal(claims.seatLimit, 17)
  assert.deepEqual(claims.modules, ['motion', 'rehab', 'report'])
  assert.equal(Date.parse(claims.expiresAt) - Date.parse(claims.issuedAt), MAX_OFFLINE_LEASE_MS)
})
test('offline license needs explicit owner approval and is <=72h, not a permanent bypass', () => {
  const f = fixture()
  f.approve()
  rejected('OWNER_MFA_REQUIRED', () => f.app.issueOfflineLease({
    owner: NONE, tenantId: f.tenantId, installationId: f.installationId, nowMs: NOW
  }))
  const token = f.app.issueOfflineLease({
    owner: OWNER, tenantId: f.tenantId, installationId: f.installationId, nowMs: NOW
  })
  assert.equal(f.verifyLease(token).mode, 'offline')
  rejected('LICENSE_EXPIRED', () =>
    f.verifyLease(token, { nowMs: NOW + MAX_OFFLINE_LEASE_MS }))
})
test('entitlement shorter than 72 hours limits BOTH kinds of lease', () => {
  const f = fixture({ expiresAtMs: NOW + 25_000 })
  f.approve()
  const token = f.online()
  assert.equal(f.verifyLease(token).expiresAt, new Date(NOW + 25_000).toISOString())
  const offline = f.app.issueOfflineLease({
    owner: OWNER, tenantId: f.tenantId, installationId: f.installationId, nowMs: NOW
  })
  assert.equal(f.verifyLease(offline).expiresAt, new Date(NOW + 25_000).toISOString())
})
test('license seats are defined by owner order; explicit null means unlimited', () => {
  const f = fixture({ seatLimit: null })
  f.approve()
  assert.equal(f.verifyLease(f.online()).seatLimit, null)
  const limited = fixture({ seatLimit: 3 })
  limited.approve()
  assert.equal(limited.verifyLease(limited.online()).seatLimit, 3)
})
test('zero or undefined seat quota and duplicate modules fail before approval', () => {
  const f = fixture()
  rejected('INVALID_SEAT_LIMIT', () => f.approve({ seatLimit: 0 }))
  rejected('INVALID_SEAT_LIMIT', () => f.approve({ seatLimit: undefined }))
  rejected('INVALID_MODULES', () => f.approve({ modules: ['rehab', 'rehab'] }))
  rejected('INVALID_INSTALLATION_LIMIT', () => f.approve({ maxInstallations: 0 }))
  assert.equal(f.approve().status, 'ACTIVE')
})
test('unsigned or attacker signed enrollment is not accepted', () => {
  const app = new OwnerLicenseCore({
    signingKey: ISSUER.privateKey, publicKey: ISSUER.publicKey, keyId: KID,
    ownerPolicy: (session) => session === OWNER
  })
  const tenantId = randomUUID()
  const installationId = randomUUID()
  rejected('INSTALLATION_PROOF_FAILED', () => app.submitApplication({
    tenantId, installationId, installationPublicKey: DEVICE.publicKey,
    possessionSignature: sign(null, enrollmentMessage(tenantId, installationId), ATTACKER.privateKey),
    nowMs: NOW
  }))
  rejected('INSTALLATION_PROOF_FAILED', () => app.submitApplication({
    tenantId, installationId, installationPublicKey: DEVICE.publicKey,
    possessionSignature: Buffer.alloc(64), nowMs: NOW
  }))
})
test('duplicate tenant application is refused even when different installation is offered', () => {
  const f = fixture()
  const otherId = randomUUID()
  rejected('TENANT_ALREADY_REGISTERED', () => f.app.submitApplication({
    tenantId: f.tenantId, installationId: otherId,
    installationPublicKey: OTHER_DEVICE.publicKey,
    possessionSignature: sign(null, enrollmentMessage(f.tenantId, otherId),
      OTHER_DEVICE.privateKey), nowMs: NOW
  }))
})
test('unrecognized installation ID does not receive a lease', () => {
  const f = fixture()
  f.approve()
  const fakeInstallation = randomUUID()
  rejected('INSTALLATION_NOT_APPROVED', () => f.app.beginOnlineRenewal({
    tenantId: f.tenantId, installationId: fakeInstallation, nowMs: NOW
  }))
  rejected('INSTALLATION_NOT_APPROVED', () => f.app.issueOfflineLease({
    owner: OWNER, tenantId: f.tenantId, installationId: fakeInstallation, nowMs: NOW
  }))
})
test('challenge cannot be signed by another device', () => {
  const f = fixture()
  f.approve()
  const nonce = f.app.beginOnlineRenewal({
    tenantId: f.tenantId, installationId: f.installationId, nowMs: NOW
  })
  rejected('INSTALLATION_PROOF_FAILED', () => f.app.issueOnlineLease({
    tenantId: f.tenantId, installationId: f.installationId, nowMs: NOW, nonce,
    possessionSignature: sign(null,
      renewalMessage(nonce, f.tenantId, f.installationId), ATTACKER.privateKey)
  }))
})
test('challenge is one-use and replay is rejected even after valid issue', () => {
  const f = fixture()
  f.approve()
  const nonce = f.app.beginOnlineRenewal({
    tenantId: f.tenantId, installationId: f.installationId, nowMs: NOW
  })
  const args = {
    tenantId: f.tenantId, installationId: f.installationId, nowMs: NOW, nonce,
    possessionSignature: sign(null,
      renewalMessage(nonce, f.tenantId, f.installationId), DEVICE.privateKey)
  }
  f.verifyLease(f.app.issueOnlineLease(args))
  rejected('INVALID_CHALLENGE', () => f.app.issueOnlineLease(args))
})
test('challenge expires after five minutes', () => {
  const f = fixture()
  f.approve()
  const nonce = f.app.beginOnlineRenewal({
    tenantId: f.tenantId, installationId: f.installationId, nowMs: NOW
  })
  rejected('INVALID_CHALLENGE', () => f.app.issueOnlineLease({
    tenantId: f.tenantId, installationId: f.installationId, nowMs: NOW + 300_001,
    nonce, possessionSignature: sign(null,
      renewalMessage(nonce, f.tenantId, f.installationId), DEVICE.privateKey)
  }))
})
test('revoking tenant denies future issue and previous lease fails with fresh revocation epoch', () => {
  const f = fixture()
  f.approve()
  const oldToken = f.online()
  rejected('OWNER_MFA_REQUIRED', () => f.app.revokeTenant({
    owner: NONE, tenantId: f.tenantId, nowMs: NOW + 1000
  }))
  const revoked = f.app.revokeTenant({
    owner: OWNER, tenantId: f.tenantId, nowMs: NOW + 1000
  })
  assert.equal(revoked.status, 'REVOKED')
  assert.equal(revoked.revocationEpoch, 1)
  rejected('REVOKED_LEASE', () => f.verifyLease(oldToken, {
    minimumRevocationEpoch: 1
  }))
  rejected('TENANT_NOT_APPROVED', () => f.online())
  rejected('TENANT_NOT_APPROVED', () => f.app.issueOfflineLease({
    owner: OWNER, tenantId: f.tenantId,
    installationId: f.installationId, nowMs: NOW + 1000
  }))
})
test('expired entitlement cannot issue online or offline tokens', () => {
  const f = fixture({ expiresAtMs: NOW + 1000 })
  f.approve()
  rejected('ENTITLEMENT_EXPIRED', () => f.app.beginOnlineRenewal({
    tenantId: f.tenantId, installationId: f.installationId, nowMs: NOW + 1000
  }))
  rejected('ENTITLEMENT_EXPIRED', () => f.app.issueOfflineLease({
    owner: OWNER, tenantId: f.tenantId,
    installationId: f.installationId, nowMs: NOW + 1000
  }))
})
test('public access to owner audit and status is rejected', () => {
  const f = fixture()
  f.approve()
  rejected('OWNER_MFA_REQUIRED', () => f.app.readAudit({ owner: NONE }))
  rejected('OWNER_MFA_REQUIRED', () => f.app.readStatus({ owner: NONE, tenantId: f.tenantId }))
  assert.equal(f.app.readStatus({ owner: OWNER, tenantId: f.tenantId }).seatLimit, 17)
  const audit = f.app.readAudit({ owner: OWNER })
  assert.deepEqual(audit.map((e) => e.action), ['APPLICATION_SUBMITTED', 'APPLICATION_APPROVED'])
  assert.ok(audit.every((event) => !('signingKey' in event) && !('possessionSignature' in event)))
})
test('mismatched owner signing key pair rejected during core creation', () => {
  rejected('KEY_PAIR_MISMATCH', () => new OwnerLicenseCore({
    signingKey: ATTACKER.privateKey, publicKey: ISSUER.publicKey,
    keyId: KID, ownerPolicy: (session) => session === OWNER
  }))
})
