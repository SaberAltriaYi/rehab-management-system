/**
 * L5 full owner-to-client protocol attack simulation.
 * Loopback HTTP is explicitly TEST ONLY. All state is a random private
 * temporary SQLite, no production owner, no clinical DB, no Docker resources.
 */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  createPublicKey, generateKeyPairSync, randomBytes, randomUUID, sign
} from 'node:crypto'
import { mkdtempSync, rmSync, copyFileSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { OwnerLicenseStore } from './store.mjs'
import { OwnerLicensingService } from './service.mjs'
import { SecretVault, totpAt } from './security.mjs'
import { createOwnerControlServer } from './http.mjs'
import { enrollmentMessage, renewalMessage } from '../scripts/tenant-license-owner-core.mjs'
import { MAX_OFFLINE_LEASE_MS, verifyTenantLicenseLease } from '../scripts/tenant-license-lease.mjs'

const ownerName = 'syntheticowner'
const password = 'Local-Synthetic-Owner-Safe-Password-123!'
const tenant = () => ({ tenantId: randomUUID(), installationId: randomUUID(),
  keys: generateKeyPairSync('ed25519') })
const possession = t => sign(null, enrollmentMessage(t.tenantId, t.installationId),
  t.keys.privateKey).toString('base64url')
const enrollment = t => ({
  tenantId: t.tenantId, installationId: t.installationId,
  organization: 'L5 合成工作室',
  installationPublicKeyPem: t.keys.publicKey.export({ type: 'spki', format: 'pem' }),
  possessionSignature: possession(t)
})
const httpCall = async (root, path, { method = 'GET', token, body, origin } = {}) => {
  const response = await fetch(root + path, {
    method, redirect: 'manual',
    headers: {
      ...(token ? { authorization: 'Bearer ' + token } : {}),
      ...(body === undefined ? {} : { 'content-type': 'application/json' }),
      ...(origin ? { origin } : {})
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  })
  return { code: response.status, value: await response.json() }
}
const successful = result => {
  assert.ok(result.code >= 200 && result.code < 300, JSON.stringify(result.value))
  return result.value
}
const rejected = (result, expectedCode) => {
  assert.ok(result.code >= 400, 'Expected an HTTP denial, got ' + result.code)
  if (expectedCode) assert.equal(result.value.error, expectedCode)
}
const no = (expected, operation) => assert.throws(operation, e =>
  e.code === expected, 'Expected ' + expected)

test('L5 end-to-end: isolated HTTPS-shape APIs, two tenants, two installations, attack and recovery',
  { timeout: 70_000 }, async t => {
    const dir = mkdtempSync(join(tmpdir(), 'rehab-l5-owner-e2e-'))
    const dbpath = join(dir, 'synthetic-owner.sqlite')
    const vault = new SecretVault(randomBytes(32))
    let store = new OwnerLicenseStore(dbpath)
    let service = new OwnerLicensingService(store, vault)
    let server = createOwnerControlServer(service, {
      allowInsecureLoopbackForTests: true,
      permittedBrowserOrigin: 'https://owner.synthetic.invalid'
    })
    let root = ''
    const A = tenant()
    const B = tenant()
    const unknown = tenant()
    let admin = ''
    let publicKeys, aLease, bLease, oldStatus
    const post = (path, body, token) => httpCall(root, path, { method: 'POST', body, token })
    const ownerPost = (path, body = {}) => post('/owner/v1/' + path, body, admin)
    const publicPost = (path, body) => post('/license/v1/' + path, body)
    const getOwner = path => httpCall(root, '/owner/v1/' + path, { token: admin })
    const trusted = () => new Map(publicKeys.map(k => [k.kid, createPublicKey(k.publicKeyPem)]))
    const verifyFor = (license, ctx, nowMs = Date.now(), epoch = 0, trustedTime = nowMs) =>
      verifyTenantLicenseLease(license.token, {
        trustedKeys: trusted(), tenantId: ctx.tenantId,
        installationId: ctx.installationId, nowMs,
        minimumRevocationEpoch: epoch, lastTrustedTimeMs: trustedTime
      })
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
    root = `http://127.0.0.1:${server.address().port}`
    try {
      await t.test('without owner MFA, all owner actions reject; third-party Origin blocks', async () => {
        rejected(await post('/owner/v1/applications', {}), 'OWNER_UNAUTHORIZED')
        assert.equal((await getOwner('audit')).code, 401)
        assert.equal((await httpCall(root, '/owner/console/app.mjs',
          { origin: 'https://attacker.synthetic.invalid' })).code, 403)
        const bogus = await publicPost('challenges', {
          tenantId: unknown.tenantId, installationId: unknown.installationId
        })
        rejected(bogus, 'TENANT_NOT_APPROVED')
      })
      await t.test('independent owner MFA opens a short session and creates signing key', async () => {
        const secret = (await service.provisionOwner({
          username: ownerName, password, nowMs: Date.now() })).mfaSecret
        rejected(await post('/owner/v1/login', {
          username: ownerName, password, totp: '000000'
        }), 'INVALID_OWNER_CREDENTIALS')
        admin = successful(await post('/owner/v1/login', {
          username: ownerName, password,
          totp: totpAt(secret, Math.floor(Date.now() / 30000))
        })).token
        assert.match(admin, /^[A-Za-z0-9_-]{43}$/)
        const issuer = successful(await ownerPost('signing-keys/rotate'))
        assert.ok(issuer.kid.startsWith('owner-'))
        publicKeys = successful(await httpCall(root, '/license/v1/issuers')).issuers
        assert.equal(publicKeys.length, 1)
        no('OWNER_UNAUTHORIZED', () => service.requireOwner(admin, Date.now() + 900_001))
      })
      await t.test('two separate studios must each be approved after signed enrollment', async () => {
        const applications = []
        for (const site of [A, B]) {
          const pending = successful(await publicPost('applications', enrollment(site)))
          assert.equal(pending.status, 'PENDING')
          applications.push(pending.applicationId)
          rejected(await publicPost('challenges', {
            tenantId: site.tenantId, installationId: site.installationId
          }), 'TENANT_NOT_APPROVED')
        }
        rejected(await post('/owner/v1/applications/' + applications[0] + '/approve', {
          policy: { expiresAtMs: Date.now() + 10000000,
            seatLimit: 2, maxInstallations: 1, modules: ['rehab'] }, reason: 'unauthorized'
        }), 'OWNER_UNAUTHORIZED')
        const expiry = Date.now() + 365 * 86400000
        const approve = (id, seats) => ownerPost('applications/' + id + '/approve', {
          reason: '合成工作室人工审核完成',
          policy: { expiresAtMs: expiry, seatLimit: seats,
            maxInstallations: 1, modules: ['rehab', 'report'] }
        })
        assert.equal(successful(await approve(applications[0], 2)).status, 'APPROVED')
        assert.equal(successful(await approve(applications[1], null)).status, 'APPROVED')
        assert.equal(successful(await getOwner('tenants/' + A.tenantId)).tenant.seatLimit, 2)
        assert.equal(successful(await getOwner('tenants/' + B.tenantId)).tenant.seatLimit, null)
      })
      await t.test('tenant A challenge replay is consumed on invalid proof; cross-tenant/device swap fails', async () => {
        const challenge = successful(await publicPost('challenges', {
          tenantId: A.tenantId, installationId: A.installationId
        }))
        const wrong = sign(null, renewalMessage(challenge.nonce, A.tenantId, A.installationId),
          B.keys.privateKey).toString('base64url')
        rejected(await publicPost('leases/online', {
          tenantId: A.tenantId, installationId: A.installationId,
          nonce: challenge.nonce, possessionSignature: wrong
        }), 'INSTALLATION_PROOF_FAILED')
        const good = sign(null, renewalMessage(challenge.nonce, A.tenantId, A.installationId),
          A.keys.privateKey).toString('base64url')
        rejected(await publicPost('leases/online', {
          tenantId: A.tenantId, installationId: A.installationId,
          nonce: challenge.nonce, possessionSignature: good
        }), 'INVALID_CHALLENGE')
        rejected(await publicPost('challenges', {
          tenantId: B.tenantId, installationId: A.installationId
        }), 'INSTALLATION_NOT_APPROVED')
      })
      await t.test('approved devices receive ≤72h canonical issuer-signed RHL1 + RHR1', async () => {
        for (const [site, name] of [[A, 'a'], [B, 'b']]) {
          const c = successful(await publicPost('challenges', {
            tenantId: site.tenantId, installationId: site.installationId
          }))
          const sig = sign(null, renewalMessage(c.nonce, site.tenantId, site.installationId),
            site.keys.privateKey).toString('base64url')
          const signedLease = successful(await publicPost('leases/online', {
            tenantId: site.tenantId, installationId: site.installationId,
            nonce: c.nonce, possessionSignature: sig
          }))
          assert.equal(signedLease.token.split('.')[0], 'RHL1')
          assert.equal(signedLease.statusProof.split('.')[0], 'RHR1')
          const claims = verifyFor(signedLease, site)
          assert.equal(claims.seatLimit, name === 'a' ? 2 : null)
          assert.equal(claims.modules.join(','), 'rehab,report')
          assert.ok(Date.parse(claims.expiresAt) - Date.parse(claims.issuedAt) <= MAX_OFFLINE_LEASE_MS)
          if (name === 'a') aLease = signedLease
          else bLease = signedLease
        }
      })
      await t.test('signed license cannot be cloned to another tenant or another device', () => {
        no('LICENSE_BINDING_MISMATCH', () => verifyFor(aLease, B))
        no('LICENSE_BINDING_MISMATCH', () => verifyFor(bLease, A))
        const fake = { ...A, installationId: B.installationId }
        no('LICENSE_BINDING_MISMATCH', () => verifyFor(aLease, fake))
        const components = aLease.token.split('.')
        components[2] = Buffer.from(JSON.stringify({ seatLimit: null })).toString('base64url')
        no('INVALID_SIGNATURE', () => verifyFor({ token: components.join('.') }, A))
        components[1] = 'untrusted-attacker'
        no('UNTRUSTED_ISSUER_KEY', () => verifyFor({ token: components.join('.') }, A))
      })
      await t.test('owner-only offline issue is device-bound and explicitly unlimited for B', async () => {
        rejected(await publicPost('leases/online', {
          tenantId: B.tenantId, installationId: B.installationId,
          nonce: 'invalid', possessionSignature: 'invalid'
        }), 'INVALID_CHALLENGE')
        const offline = successful(await ownerPost('leases/offline', {
          tenantId: B.tenantId, installationId: B.installationId
        }))
        assert.equal(offline.licenseFile.token, offline.token)
        assert.equal(offline.licenseFile.statusProof, offline.statusProof)
        assert.equal(verifyFor(offline, B).seatLimit, null)
        no('LICENSE_BINDING_MISMATCH', () => verifyFor(offline, A))
      })
      await t.test('time rollback, expiration and forged high-water epoch fail closed', () => {
        const issued = Date.parse(aLease.token && JSON.parse(
          Buffer.from(aLease.token.split('.')[2], 'base64url').toString()).issuedAt)
        no('CLOCK_ROLLBACK', () => verifyFor(aLease, A, issued,
          0, issued + 600_000))
        no('LICENSE_EXPIRED', () => verifyFor(aLease, A, issued + MAX_OFFLINE_LEASE_MS + 1,
          0, issued))
        no('REVOKED_LEASE', () => verifyFor(aLease, A, issued, 1, issued))
      })
      await t.test('tenant B suspension blocks B renewal but never A; owner revocation epoch advances', async () => {
        oldStatus = successful(await publicPost('status', {
          tenantId: B.tenantId, installationId: B.installationId
        })).statusProof
        const suspended = successful(await ownerPost('tenants/' + B.tenantId + '/status',
          { status: 'SUSPENDED' }))
        assert.equal(suspended.revocationEpoch, 1)
        const revokedState = successful(await publicPost('status', {
          tenantId: B.tenantId, installationId: B.installationId
        })).statusProof
        const status = JSON.parse(Buffer.from(revokedState.split('.')[2], 'base64url'))
        assert.equal(status.status, 'SUSPENDED')
        assert.equal(status.revocationEpoch, 1)
        rejected(await publicPost('challenges', {
          tenantId: B.tenantId, installationId: B.installationId
        }), 'TENANT_NOT_APPROVED')
        assert.equal((await publicPost('challenges', {
          tenantId: A.tenantId, installationId: A.installationId
        })).code, 200)
        no('REVOKED_LEASE', () => verifyFor(bLease, B, Date.now(), 1))
        assert.equal(oldStatus.split('.')[0], 'RHR1') // offline old status lags until sync
      })
      await t.test('resumed B cannot reuse a formerly issued lease or epoch', async () => {
        const restored = successful(await ownerPost('tenants/' + B.tenantId + '/status',
          { status: 'ACTIVE' }))
        assert.equal(restored.revocationEpoch, 2)
        no('REVOKED_LEASE', () => verifyFor(bLease, B, Date.now(), 2))
      })
      await t.test('owner-approved device replacement revokes old A install and requires new proof', async () => {
        const newer = tenant()
        newer.tenantId = A.tenantId
        rejected(await ownerPost('installations/replace', {
          tenantId: A.tenantId, oldInstallationId: A.installationId,
          newInstallationId: newer.installationId,
          newPublicKeyPem: newer.keys.publicKey.export({ type: 'spki', format: 'pem' }),
          proof: possession(B)
        }), 'INSTALLATION_PROOF_FAILED')
        const replaced = successful(await ownerPost('installations/replace', {
          tenantId: A.tenantId, oldInstallationId: A.installationId,
          newInstallationId: newer.installationId,
          newPublicKeyPem: newer.keys.publicKey.export({ type: 'spki', format: 'pem' }),
          proof: possession(newer)
        }))
        assert.equal(replaced.revocationEpoch, 1)
        rejected(await publicPost('challenges', {
          tenantId: A.tenantId, installationId: A.installationId
        }), 'INSTALLATION_NOT_APPROVED')
        no('REVOKED_LEASE', () => verifyFor(aLease, A, Date.now(), 1))
        const challenge = successful(await publicPost('challenges', {
          tenantId: newer.tenantId, installationId: newer.installationId
        }))
        const sig = sign(null, renewalMessage(challenge.nonce,
          newer.tenantId, newer.installationId), newer.keys.privateKey).toString('base64url')
        const newLease = successful(await publicPost('leases/online', {
          tenantId: newer.tenantId, installationId: newer.installationId,
          nonce: challenge.nonce, possessionSignature: sig
        }))
        assert.equal(verifyFor(newLease, newer).revocationEpoch, 1)
      })
      await t.test('irreversible revocation, logout, audit chain and crash/reopen persist', async () => {
        const revoked = successful(await ownerPost('tenants/' + B.tenantId + '/status',
          { status: 'REVOKED' }))
        assert.equal(revoked.revocationEpoch, 3)
        rejected(await ownerPost('tenants/' + B.tenantId + '/status',
          { status: 'ACTIVE' }), 'REVOKED_IS_FINAL')
        const proof = successful(await publicPost('status', {
          tenantId: B.tenantId, installationId: B.installationId
        })).statusProof
        assert.equal(JSON.parse(Buffer.from(proof.split('.')[2], 'base64url')).status, 'REVOKED')
        assert.equal(store.verifyAuditChain(), true)
        const events = successful(await getOwner('audit')).audit
        assert.ok(events.some(e => e.action === 'TENANT_REVOKED'))
        const checkpoint = successful(await ownerPost('audit/checkpoint'))
        assert.equal(checkpoint.checkpoint.split('.')[0], 'RHA1')
        assert.equal(checkpoint.headHash.length, 64)
        successful(await ownerPost('logout'))
        rejected(await getOwner('audit'), 'OWNER_UNAUTHORIZED')
        await new Promise(resolve => server.close(resolve))
        store.close()
        const saved = join(dir, 'synthetic-recovery.sqlite')
        copyFileSync(dbpath, saved)
        const reopened = new OwnerLicenseStore(saved)
        try {
          assert.equal(reopened.verifyAuditChain(), true)
          const restored = new OwnerLicensingService(reopened, vault)
          assert.equal(restored.hasOwner, true)
          assert.equal(restored.db.prepare('SELECT status FROM entitlements WHERE tenant_uuid=?')
            .get(B.tenantId).status, 'REVOKED')
          assert.equal(restored.db.prepare('SELECT COUNT(*) AS n FROM installations').get().n, 3)
          assert.equal(restored.db.prepare('SELECT COUNT(*) AS n FROM owner_sessions').get().n, 0)
        } finally { reopened.close() }
      })
    } finally {
      if (server?.listening) await new Promise(resolve => server.close(resolve))
      try { store?.close() } catch { /* already closed for simulated recovery */ }
      rmSync(dir, { recursive: true, force: true })
    }
  }
)
