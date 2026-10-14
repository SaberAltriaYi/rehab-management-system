import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { generateKeyPairSync, randomBytes, randomUUID, sign, createPublicKey } from 'node:crypto'
import { mkdtempSync, readFileSync, rmSync, statSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { OwnerLicenseStore } from './store.mjs'
import { OwnerLicensingService } from './service.mjs'
import { SecretVault, totpAt } from './security.mjs'
import { createOwnerControlServer } from './http.mjs'
import { enrollmentMessage, renewalMessage } from '../scripts/tenant-license-owner-core.mjs'
import { MAX_OFFLINE_LEASE_MS, verifyTenantLicenseLease } from '../scripts/tenant-license-lease.mjs'

const NOW = Date.parse('2026-10-10T05:10:00.000Z')
const PASSWORD = 'Test-Only_Passphrase_12345!'
const DEVICE = generateKeyPairSync('ed25519')
const OTHER = generateKeyPairSync('ed25519')
const TENANT = randomUUID()
const INSTALL = randomUUID()
const policy = { expiresAtMs: NOW + 365 * 86_400_000,
  seatLimit: 13, maxInstallations: 1, modules: ['motion', 'rehab', 'report'] }
let temp, dbfile, vault, store, svc, secret, token

function denies(code, fn) { assert.throws(fn, e => e.code === code, `expected ${code}`) }
async function deniesAsync(code, fn) {
  await assert.rejects(fn, e => e.code === code, `expected ${code}`)
}
function studio() {
  return svc.submitApplication({
    tenantId: TENANT, installationId: INSTALL,
    installationPublicKeyPem: DEVICE.publicKey.export({ type: 'spki', format: 'pem' }),
    organization: '合成测试康复工作室',
    possessionSignature: sign(null, enrollmentMessage(TENANT, INSTALL),
      DEVICE.privateKey).toString('base64url'), nowMs: NOW
  })
}
function checkLease(lease, installationId = INSTALL, epoch = 0, timeMs = NOW) {
  return verifyTenantLicenseLease(lease.token, {
    trustedKeys: new Map(svc.trustedIssuerKeys().map(
      k => [k.kid, createPublicKey(k.publicKeyPem)])),
    tenantId: TENANT, installationId, nowMs: timeMs,
    minimumRevocationEpoch: epoch, lastTrustedTimeMs: NOW
  })
}
before(async () => {
  temp = mkdtempSync(join(tmpdir(), 'rehab-owner-test-'))
  dbfile = join(temp, 'control.sqlite')
  vault = new SecretVault(randomBytes(32))
  store = new OwnerLicenseStore(dbfile)
  svc = new OwnerLicensingService(store, vault)
  secret = (await svc.provisionOwner({
    username: 'licenseowner', password: PASSWORD, nowMs: NOW
  })).mfaSecret
  token = (await svc.login({
    username: 'licenseowner', password: PASSWORD,
    totp: totpAt(secret, Math.floor(NOW / 30_000)), nowMs: NOW
  })).token
})
after(() => {
  store?.close()
  if (temp) rmSync(temp, { recursive: true, force: true })
})

test('bootstrap is one-time, has encrypted seed, no plaintext password/private key', async () => {
  assert.equal(svc.hasOwner, true)
  await deniesAsync('OWNER_ALREADY_PROVISIONED', () => svc.provisionOwner({
    username: 'otherowner', password: PASSWORD, nowMs: NOW
  }))
  if (process.platform !== 'win32') assert.equal(statSync(dbfile).mode & 0o777, 0o600)
  const raw = readFileSync(dbfile, 'utf8')
  assert.equal(raw.includes(PASSWORD), false)
  assert.equal(raw.includes(secret), false)
})
test('login requires MFA and rejects replay; bearer expires', async () => {
  await deniesAsync('INVALID_OWNER_CREDENTIALS', () => svc.login({
    username: 'licenseowner', password: PASSWORD,
    totp: totpAt(secret, Math.floor(NOW / 30_000)), nowMs: NOW
  }))
  await deniesAsync('INVALID_OWNER_CREDENTIALS', () => svc.login({
    username: 'licenseowner', password: 'wrongpassword', totp: '000000', nowMs: NOW
  }))
  denies('OWNER_UNAUTHORIZED', () => svc.requireOwner('bad', NOW))
  assert.equal(svc.requireOwner(token, NOW), 'licenseowner')
  denies('OWNER_UNAUTHORIZED', () => svc.requireOwner(token, NOW + 900_000))
})
test('pending studio is unlicensed, approver must possess MFA bearer', () => {
  const application = studio()
  assert.equal(application.status, 'PENDING')
  denies('TENANT_NOT_APPROVED', () => svc.challenge({
    tenantId: TENANT, installationId: INSTALL, nowMs: NOW
  }))
  denies('OWNER_UNAUTHORIZED', () => svc.decideApplication({
    token: 'bad', applicationId: application.applicationId,
    decision: 'APPROVED', policy, nowMs: NOW
  }))
  assert.equal(svc.decideApplication({
    token, applicationId: application.applicationId,
    decision: 'APPROVED', policy, nowMs: NOW
  }).status, 'APPROVED')
  denies('APPLICATION_NOT_PENDING', () => svc.decideApplication({
    token, applicationId: application.applicationId,
    decision: 'APPROVED', policy, nowMs: NOW
  }))
  assert.equal(svc.status({ token, tenantId: TENANT, nowMs: NOW }).seatLimit, 13)
})
test('encrypted signing key rotates and does not appear in raw database', () => {
  const key = svc.rotateKey({ token, nowMs: NOW })
  assert.match(key.kid, /^owner-/)
  assert.equal(svc.trustedIssuerKeys().length, 1)
  const privateKeyMarker = ['BEGIN', 'PRIVATE KEY'].join(' ')
  assert.equal(readFileSync(dbfile, 'utf8').includes(privateKeyMarker), false)
  assert.equal(svc.db.prepare('SELECT private_encrypted FROM signing_keys')
    .get().private_encrypted.includes(privateKeyMarker), false)
})
test('renewal challenge is bound to instance, and invalid proof burns it', () => {
  const challenge = svc.challenge({ tenantId: TENANT, installationId: INSTALL, nowMs: NOW })
  denies('INSTALLATION_PROOF_FAILED', () => svc.issueOnline({
    tenantId: TENANT, installationId: INSTALL, nonce: challenge.nonce,
    possessionSignature: sign(null, renewalMessage(challenge.nonce, TENANT, INSTALL),
      OTHER.privateKey).toString('base64url'), nowMs: NOW
  }))
  denies('INVALID_CHALLENGE', () => svc.issueOnline({
    tenantId: TENANT, installationId: INSTALL, nonce: challenge.nonce,
    possessionSignature: sign(null, renewalMessage(challenge.nonce, TENANT, INSTALL),
      DEVICE.privateKey).toString('base64url'), nowMs: NOW
  }))
})
let initialLease
test('online and offline signed leases both enforce 72h and licensed seats', () => {
  const { nonce } = svc.challenge({ tenantId: TENANT, installationId: INSTALL, nowMs: NOW })
  initialLease = svc.issueOnline({
    tenantId: TENANT, installationId: INSTALL, nonce,
    possessionSignature: sign(null, renewalMessage(nonce, TENANT, INSTALL),
      DEVICE.privateKey).toString('base64url'), nowMs: NOW
  })
  assert.equal(checkLease(initialLease).seatLimit, 13)
  assert.equal(Date.parse(initialLease.expiresAt) - NOW, MAX_OFFLINE_LEASE_MS)
  denies('LICENSE_EXPIRED', () => checkLease(initialLease, INSTALL, 0,
    NOW + MAX_OFFLINE_LEASE_MS))
  denies('OWNER_UNAUTHORIZED', () => svc.issueOffline({
    token: 'bad', tenantId: TENANT, installationId: INSTALL, nowMs: NOW
  }))
  const offline = svc.issueOffline({ token, tenantId: TENANT, installationId: INSTALL, nowMs: NOW })
  assert.equal(checkLease(offline).mode, 'offline')
  assert.equal(Date.parse(offline.expiresAt) - NOW, MAX_OFFLINE_LEASE_MS)
  assert.deepEqual(Object.keys(offline.licenseFile),
    ['schemaVersion', 'tenantId', 'installationId', 'token', 'statusProof'])
  assert.equal(offline.licenseFile.schemaVersion, 1)
  assert.equal(offline.licenseFile.token, offline.token)
})
test('rotation retains old public key until all previously issued leases expire', async () => {
  const firstKid = svc.trustedIssuerKeys()[0].kid
  svc.rotateKey({ token, nowMs: NOW + 1000 })
  assert.equal(svc.trustedIssuerKeys().length, 2)
  assert.equal(checkLease(initialLease).mode, 'online')
  denies('KEY_STILL_IN_USE', () => svc.retireVerificationKey({
    token, kid: firstKid, nowMs: NOW + 1000
  }))
  const retireAt = NOW + MAX_OFFLINE_LEASE_MS + 1
  // Expired bearer tokens cannot authorize rotation, even for their owner.
  denies('OWNER_UNAUTHORIZED', () => svc.retireVerificationKey({
    token, kid: firstKid, nowMs: retireAt
  }))
  const freshToken = (await svc.login({
    username: 'licenseowner', password: PASSWORD,
    totp: totpAt(secret, Math.floor(retireAt / 30_000)), nowMs: retireAt
  })).token
  svc.retireVerificationKey({ token: freshToken, kid: firstKid,
    nowMs: retireAt })
  assert.equal(svc.trustedIssuerKeys().length, 1)
})
test('SQLite restart preserves owner sessions, approvals, TOTP watermark and signer', async () => {
  assert.equal(store.verifyAuditChain(), true)
  store.close()
  store = new OwnerLicenseStore(dbfile)
  svc = new OwnerLicensingService(store, vault)
  assert.equal(svc.status({ token, tenantId: TENANT, nowMs: NOW }).seatLimit, 13)
  assert.equal(svc.trustedIssuerKeys().length, 1)
  await deniesAsync('INVALID_OWNER_CREDENTIALS', () => svc.login({
    username: 'licenseowner', password: PASSWORD,
    totp: totpAt(secret, Math.floor(NOW / 30_000)), nowMs: NOW
  }))
})
test('entitlement amendment signed unlimited seats, revokes old cached epoch', () => {
  const prior = svc.issueOffline({ token, tenantId: TENANT, installationId: INSTALL, nowMs: NOW })
  const modified = svc.updateEntitlement({
    token, tenantId: TENANT, policy: { ...policy, seatLimit: null }, nowMs: NOW
  })
  denies('REVOKED_LEASE', () => checkLease(prior, INSTALL, modified.revocationEpoch))
  const newLease = svc.issueOffline({ token, tenantId: TENANT, installationId: INSTALL, nowMs: NOW })
  assert.equal(checkLease(newLease, INSTALL, modified.revocationEpoch).seatLimit, null)
})
test('device replacement needs owner and device proof, rejects old device', () => {
  const next = randomUUID()
  const pem = OTHER.publicKey.export({ type: 'spki', format: 'pem' })
  denies('INSTALLATION_PROOF_FAILED', () => svc.replaceInstallation({
    token, tenantId: TENANT, oldInstallationId: INSTALL, newInstallationId: next,
    newPublicKeyPem: pem,
    proof: sign(null, enrollmentMessage(TENANT, next),
      DEVICE.privateKey).toString('base64url'), nowMs: NOW
  }))
  const replaced = svc.replaceInstallation({
    token, tenantId: TENANT, oldInstallationId: INSTALL, newInstallationId: next,
    newPublicKeyPem: pem,
    proof: sign(null, enrollmentMessage(TENANT, next),
      OTHER.privateKey).toString('base64url'), nowMs: NOW
  })
  assert.ok(replaced.revocationEpoch >= 2)
  denies('INSTALLATION_NOT_APPROVED', () => svc.challenge({
    tenantId: TENANT, installationId: INSTALL, nowMs: NOW
  }))
  const { nonce } = svc.challenge({ tenantId: TENANT, installationId: next, nowMs: NOW })
  const issued = svc.issueOnline({
    tenantId: TENANT, installationId: next, nonce, nowMs: NOW,
    possessionSignature: sign(null, renewalMessage(nonce, TENANT, next),
      OTHER.privateKey).toString('base64url')
  })
  assert.equal(checkLease(issued, next, replaced.revocationEpoch).installationId, next)
})
test('suspension + revocation deny new issues and revocation is irreversible', () => {
  svc.setTenantStatus({ token, tenantId: TENANT, status: 'SUSPENDED', nowMs: NOW })
  denies('TENANT_NOT_APPROVED', () => svc.challenge({
    tenantId: TENANT, installationId: INSTALL, nowMs: NOW
  }))
  svc.setTenantStatus({ token, tenantId: TENANT, status: 'ACTIVE', nowMs: NOW })
  svc.setTenantStatus({ token, tenantId: TENANT, status: 'REVOKED', nowMs: NOW })
  denies('REVOKED_IS_FINAL', () => svc.setTenantStatus({
    token, tenantId: TENANT, status: 'ACTIVE', nowMs: NOW
  }))
  assert.equal(store.verifyAuditChain(), true)
  assert.ok(svc.audit({ token, nowMs: NOW }).every(a => !JSON.stringify(a).includes(PASSWORD)))
})
test('audit corruption is detected on next startup', () => {
  store.db.prepare("UPDATE audit_events SET action='FORGED' WHERE seq=1").run()
  assert.equal(store.verifyAuditChain(), false)
  store.close()
  assert.throws(() => new OwnerLicenseStore(dbfile), /audit chain integrity/)
  store = null
})
test('HTTP refuses production startup without TLS, test HTTP only permits loopback', () => {
  assert.throws(() => createOwnerControlServer(svc), /requires TLS/)
  const server = createOwnerControlServer(svc, { allowInsecureLoopbackForTests: true })
  assert.throws(() => server.listen(0, '0.0.0.0'), /loopback/)
  server.close()
})
