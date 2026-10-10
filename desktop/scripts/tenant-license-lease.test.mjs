import assert from 'node:assert/strict'
import { generateKeyPairSync, sign } from 'node:crypto'
import test from 'node:test'
import { MAX_OFFLINE_LEASE_MS, verifyTenantLicenseLease } from './tenant-license-lease.mjs'

// Synthetic ephemeral key pairs only — never write signing secrets to disk.
const { privateKey, publicKey } = generateKeyPairSync('ed25519')
const extraIssuer = generateKeyPairSync('ed25519')
const KID = 'synthetic-issuer-v1'
const TENANT = 'f12b2687-09a1-40b5-b47a-747d75ce5551'
const INSTALL = 'd94123bf-2c94-4af4-9d5b-7ab8ce690509'
const NOW = Date.parse('2026-10-10T04:00:00.000Z')
const stamp = (milliseconds) => new Date(milliseconds).toISOString()

function lease(overrides = {}) {
  // Insertion order is the RHL1 signed canonical JSON contract.
  return {
    schemaVersion: 1,
    mode: 'online',
    leaseId: 'c1dd250a-5f98-45f4-89ae-713f6a0f876e',
    tenantId: TENANT,
    installationId: INSTALL,
    issuedAt: stamp(NOW - 3_600_000),
    notBefore: stamp(NOW - 3_600_000),
    expiresAt: stamp(NOW + 3_600_000),
    entitlementExpiresAt: stamp(NOW + 365 * 86_400_000),
    revocationEpoch: 5,
    seatLimit: 27,
    maxInstallations: 1,
    modules: ['motion', 'rehab', 'report'],
    ...overrides
  }
}

function tokenFor(claims = lease(), { issuer = privateKey, kid = KID, serialized } = {}) {
  const bytes = Buffer.from(serialized ?? JSON.stringify(claims), 'utf8')
  const message = `RHL1.${kid}.${bytes.toString('base64url')}`
  return `${message}.${sign(null, Buffer.from(message, 'ascii'), issuer).toString('base64url')}`
}
function validOptions(extra = {}) {
  return {
    trustedKeys: new Map([[KID, publicKey]]),
    tenantId: TENANT,
    installationId: INSTALL,
    nowMs: NOW,
    minimumRevocationEpoch: 5,
    lastTrustedTimeMs: NOW,
    ...extra
  }
}
function rejects(code, token = tokenFor(), opts = validOptions()) {
  assert.throws(() => verifyTenantLicenseLease(token, opts), (error) => error.code === code)
}

test('approved signed lease is bound to tenant and installation and exposes signed seat limit', () => {
  const result = verifyTenantLicenseLease(tokenFor(), validOptions())
  assert.equal(result.seatLimit, 27)
  assert.equal(result.maxInstallations, 1)
  assert.equal(result.tenantId, TENANT)
  assert.ok(Object.isFrozen(result) && Object.isFrozen(result.modules))
})
test('offline signed file shares the same 72-hour lease boundary', () => {
  const l = lease({
    mode: 'offline',
    issuedAt: stamp(NOW),
    notBefore: stamp(NOW),
    expiresAt: stamp(NOW + MAX_OFFLINE_LEASE_MS),
    seatLimit: null
  })
  assert.equal(verifyTenantLicenseLease(tokenFor(l), validOptions()).seatLimit, null)
  rejects('LICENSE_EXPIRED', tokenFor(l), validOptions({ nowMs: NOW + MAX_OFFLINE_LEASE_MS }))
  rejects('INVALID_LEASE_PERIOD', tokenFor({
    ...l, expiresAt: stamp(NOW + MAX_OFFLINE_LEASE_MS + 1)
  }))
})
test('tenant mismatch is refused', () => {
  rejects('LICENSE_BINDING_MISMATCH', tokenFor(), validOptions({
    tenantId: '07849ff8-25f9-40ed-a412-5ab9d4582052'
  }))
})
test('installation binding cannot be copied to another server', () => {
  rejects('LICENSE_BINDING_MISMATCH', tokenFor(), validOptions({
    installationId: '07849ff8-25f9-40ed-a412-5ab9d4582052'
  }))
})
test('tampered signed bytes fail verification', () => {
  const original = tokenFor()
  const chunks = original.split('.')
  chunks[2] = Buffer.from(JSON.stringify(lease({ seatLimit: null })), 'utf8').toString('base64url')
  rejects('INVALID_SIGNATURE', chunks.join('.'))
})
test('a different issuer signature cannot impersonate the pinned owner key', () => {
  rejects('INVALID_SIGNATURE', tokenFor(lease(), { issuer: extraIssuer.privateKey }))
})
test('attacker-selected kid cannot supply its own public key', () => {
  rejects('UNTRUSTED_ISSUER_KEY', tokenFor(lease(), { kid: 'untrusted-new-issuer' }))
})
test('pin rejects public keys with wrong cryptographic algorithm', () => {
  const { publicKey: wrongType } = generateKeyPairSync('rsa', { modulusLength: 2048 })
  rejects('UNTRUSTED_ISSUER_KEY', tokenFor(), validOptions({
    trustedKeys: new Map([[KID, wrongType]])
  }))
})
test('malformed tokens are never accepted', () => {
  rejects('MALFORMED_LICENSE', '')
  rejects('MALFORMED_LICENSE', 'RHL1.kid.data')
  rejects('MALFORMED_LICENSE', tokenFor() + '.unused')
  rejects('MALFORMED_LICENSE', 'x'.repeat(9000))
  rejects('MALFORMED_LICENSE', tokenFor().replace(/.$/, '='))
})
test('short signatures are rejected', () => {
  const parts = tokenFor().split('.')
  parts[3] = 'AA'
  rejects('INVALID_SIGNATURE', parts.join('.'))
})
test('signed payload rejects duplicate keys, unknown keys and altered field order', () => {
  const encoded = JSON.stringify(lease())
  rejects('NON_CANONICAL_PAYLOAD', tokenFor(lease(), {
    serialized: encoded.replace('"mode":"online"', '"mode":"online","mode":"offline"')
  }))
  rejects('NON_CANONICAL_PAYLOAD', tokenFor({ ...lease(), hidden: 'admin' }))
  const reordered = lease()
  delete reordered.schemaVersion
  reordered.schemaVersion = 1
  rejects('NON_CANONICAL_PAYLOAD', tokenFor(reordered))
})
test('signed claims reject unsorted, duplicate, and unknown modules', () => {
  rejects('INVALID_MODULES', tokenFor(lease({ modules: ['rehab', 'motion'] })))
  rejects('INVALID_MODULES', tokenFor(lease({ modules: ['rehab', 'rehab'] })))
  rejects('INVALID_MODULES', tokenFor(lease({ modules: ['unknown feature'] })))
  rejects('INVALID_MODULES', tokenFor(lease({ modules: [] })))
})
test('explicit unlimited seat quota allowed, missing/zero/negative quota refused', () => {
  assert.equal(verifyTenantLicenseLease(tokenFor(lease({ seatLimit: null })), validOptions()).seatLimit, null)
  rejects('INVALID_SEAT_LIMIT', tokenFor(lease({ seatLimit: 0 })))
  rejects('INVALID_SEAT_LIMIT', tokenFor(lease({ seatLimit: -1 })))
  rejects('NON_CANONICAL_PAYLOAD', tokenFor(lease({ seatLimit: undefined })))
})
test('invalid and zero installation quota refused', () => {
  rejects('INVALID_CLAIMS', tokenFor(lease({ maxInstallations: 0 })))
})
test('revoked epoch does not allow stale cached lease', () => {
  rejects('REVOKED_LEASE', tokenFor(), validOptions({ minimumRevocationEpoch: 6 }))
})
test('non-monotonic local clock fails closed when trusted watermark is later', () => {
  rejects('CLOCK_ROLLBACK', tokenFor(), validOptions({
    lastTrustedTimeMs: NOW + 6 * 60_000
  }))
})
test('not before and future issuance are refused', () => {
  rejects('LICENSE_NOT_YET_VALID', tokenFor(lease({
    issuedAt: stamp(NOW + 60_000),
    notBefore: stamp(NOW + 60_000)
  })))
  rejects('LICENSE_NOT_YET_VALID', tokenFor(lease({
    issuedAt: stamp(NOW + 600_000),
    notBefore: stamp(NOW + 600_000)
  })))
})
test('past expiry and invalid entitlement cutoff refused', () => {
  rejects('LICENSE_EXPIRED', tokenFor(lease({ expiresAt: stamp(NOW) })))
  rejects('INVALID_LEASE_PERIOD', tokenFor(lease({
    entitlementExpiresAt: stamp(NOW)
  })))
})
test('broken timestamp and inconsistent lease duration refused', () => {
  rejects('INVALID_TIME', tokenFor(lease({ expiresAt: '2026-13-10T04:00:00.000Z' })))
  rejects('INVALID_LEASE_PERIOD', tokenFor(lease({
    expiresAt: stamp(NOW - 3_600_000)
  })))
})
test('malformed verification context cannot disable revocation or time checks', () => {
  rejects('INVALID_VERIFICATION_CONTEXT', tokenFor(), validOptions({ minimumRevocationEpoch: -1 }))
  rejects('INVALID_VERIFICATION_CONTEXT', tokenFor(), validOptions({ minimumRevocationEpoch: undefined }))
  rejects('INVALID_VERIFICATION_CONTEXT', tokenFor(), validOptions({ nowMs: undefined }))
  rejects('INVALID_VERIFICATION_CONTEXT', tokenFor(), validOptions({ lastTrustedTimeMs: null }))
  rejects('INVALID_VERIFICATION_CONTEXT', tokenFor(), validOptions({ lastTrustedTimeMs: undefined }))
  rejects('INVALID_VERIFICATION_CONTEXT', tokenFor(), validOptions({ trustedKeys: {} }))
})
test('signed invalid UTF-8 payload has a typed failure, not an uncaught decoder error', () => {
  const payload = Buffer.from([0xc3, 0x28]).toString('base64url')
  const message = `RHL1.${KID}.${payload}`
  const token = `${message}.${sign(null, Buffer.from(message), privateKey).toString('base64url')}`
  rejects('INVALID_PAYLOAD', token)
})
