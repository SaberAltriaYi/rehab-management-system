import assert from 'node:assert/strict'
import { test } from 'node:test'
import { randomBytes, createPublicKey, verify } from 'node:crypto'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { OwnerLicenseStore } from './store.mjs'
import { OwnerLicensingService } from './service.mjs'
import { SecretVault, totpAt, verifyTotp, hashOwnerPassword, checkOwnerPassword } from './security.mjs'

const T = Date.parse('2026-10-10T05:00:00.000Z')
const PW = 'Only-For-Local-Test-Password-1234'
async function setup(run) {
  const dir = mkdtempSync(join(tmpdir(), 'rehab-auth-fixture-'))
  const store = new OwnerLicenseStore(join(dir, 'owner.sqlite'))
  const vault = new SecretVault(randomBytes(32))
  try {
    const svc = new OwnerLicensingService(store, vault)
    const { mfaSecret } = await svc.provisionOwner({ username: 'onlyowner', password: PW, nowMs: T })
    await run({ svc, store, vault, mfaSecret })
  } finally {
    store.close()
    rmSync(dir, { recursive: true, force: true })
  }
}
const rejects = (code, work) => assert.throws(work, e => e.code === code)
const rejectsAsync = async (code, work) =>
  assert.rejects(work, e => e.code === code)

test('password hash verifies with scrypt and does not contain password', async () => {
  const digest = await hashOwnerPassword(PW)
  assert.equal(digest.includes(PW), false)
  assert.equal(await checkOwnerPassword(PW, digest), true)
  assert.equal(await checkOwnerPassword(PW + 'x', digest), false)
  assert.equal(await checkOwnerPassword('', digest), false)
  await assert.rejects(hashOwnerPassword('short'), /16/)
})
test('vault AES-GCM ciphertext is authenticated and bound to AAD', () => {
  const key = randomBytes(32)
  const v = new SecretVault(key)
  const ciphertext = v.encrypt('synthetic-seed', 'owner-seed')
  assert.equal(v.decrypt(ciphertext, 'owner-seed'), 'synthetic-seed')
  assert.equal(ciphertext.includes('synthetic-seed'), false)
  assert.throws(() => v.decrypt(ciphertext, 'wrong-tenant'))
  assert.throws(() => new SecretVault(randomBytes(32)).decrypt(ciphertext, 'owner-seed'))
  const damaged = ciphertext.split('.')
  damaged[2] = randomBytes(18).toString('base64url')
  assert.throws(() => v.decrypt(damaged.join('.'), 'owner-seed'))
  assert.throws(() => new SecretVault(Buffer.alloc(16)), /32-byte/)
})
test('TOTP has one-time step, valid clock skew and detects bad digits', async () => {
  await setup(async ({ mfaSecret }) => {
    const step = Math.floor(T / 30_000)
    const code = totpAt(mfaSecret, step)
    assert.equal(verifyTotp(mfaSecret, code, T, -1), step)
    assert.equal(verifyTotp(mfaSecret, code, T, step), null)
    assert.equal(verifyTotp(mfaSecret, '1234567', T, -1), null)
    assert.equal(verifyTotp(mfaSecret, code, T + 30_000, -1), step)
    assert.equal(verifyTotp(mfaSecret, code, T + 60_000, -1), null)
  })
})
test('five invalid owner authentications lock login for 15 minutes', async () => {
  await setup(async ({ svc, mfaSecret }) => {
    for (let i = 0; i < 5; i++) {
      await rejectsAsync('INVALID_OWNER_CREDENTIALS', () => svc.login({
        username: 'onlyowner', password: 'bad', totp: '000000', nowMs: T
      }))
    }
    const correct = totpAt(mfaSecret, Math.floor(T / 30_000))
    await rejectsAsync('INVALID_OWNER_CREDENTIALS', () => svc.login({
      username: 'onlyowner', password: PW, totp: correct, nowMs: T + 1000
    }))
    const unlockAt = T + 15 * 60_000
    const fresh = await svc.login({
      username: 'onlyowner', password: PW,
      totp: totpAt(mfaSecret, Math.floor(unlockAt / 30_000)), nowMs: unlockAt
    })
    assert.match(fresh.token, /^[A-Za-z0-9_-]{43}$/)
  })
})
test('key rotation requires MFA step-up within 2 minutes, not only bearer', async () => {
  await setup(async ({ svc, mfaSecret }) => {
    const first = (await svc.login({
      username: 'onlyowner', password: PW,
      totp: totpAt(mfaSecret, Math.floor(T / 30_000)), nowMs: T
    })).token
    svc.rotateKey({ token: first, nowMs: T })
    rejects('OWNER_STEP_UP_REQUIRED', () => svc.rotateKey({
      token: first, nowMs: T + 2 * 60_000
    }))
    rejects('INVALID_OWNER_MFA', () => svc.stepUp({
      token: first, totp: totpAt(mfaSecret, Math.floor(T / 30_000)),
      nowMs: T + 2 * 60_000
    }))
    const freshCode = totpAt(mfaSecret, Math.floor((T + 2 * 60_000) / 30_000))
    assert.ok(svc.stepUp({ token: first, totp: freshCode,
      nowMs: T + 2 * 60_000 }).stepUpUntil)
    svc.rotateKey({ token: first, nowMs: T + 2 * 60_000 })
    rejects('INVALID_OWNER_MFA', () => svc.stepUp({
      token: first, totp: freshCode, nowMs: T + 2 * 60_000
    }))
    rejects('OWNER_STEP_UP_REQUIRED', () => svc.rotateKey({
      token: first, nowMs: T + 4 * 60_000
    }))
  })
})
test('signed audit checkpoint and emergency signer revocation are cryptographically verifiable', async () => {
  await setup(async ({ svc, store, mfaSecret }) => {
    const session = (await svc.login({
      username: 'onlyowner', password: PW,
      totp: totpAt(mfaSecret, Math.floor(T / 30_000)), nowMs: T
    })).token
    const signer = svc.rotateKey({ token: session, nowMs: T })
    const point = svc.signedAuditCheckpoint({ token: session, nowMs: T })
    const parts = point.checkpoint.split('.')
    assert.equal(parts.length, 4)
    assert.equal(parts[0], 'RHA1')
    assert.equal(parts[1], signer.kid)
    const message = Buffer.from(parts.slice(0, 3).join('.'), 'ascii')
    assert.equal(verify(null, message, createPublicKey(signer.publicKeyPem),
      Buffer.from(parts[3], 'base64url')), true)
    assert.equal(verify(null, Buffer.from(message.toString() + 'x'),
      createPublicKey(signer.publicKeyPem), Buffer.from(parts[3], 'base64url')), false)
    assert.equal(store.verifyAuditChain(), true)
    assert.equal(point.headHash.length, 64)
    const revoked = svc.emergencyRevokeSigner({ token: session, kid: signer.kid, nowMs: T })
    assert.equal(revoked.status, 'RETIRED')
    assert.equal(svc.trustedIssuerKeys().length, 0)
    rejects('NO_ACTIVE_SIGNING_KEY', () => svc.signedAuditCheckpoint({
      token: session, nowMs: T
    }))
    assert.equal(svc.db.prepare('SELECT private_encrypted FROM signing_keys WHERE kid=?')
      .get(signer.kid).private_encrypted, 'retired')
    svc.rotateKey({ token: session, nowMs: T })
    assert.equal(svc.trustedIssuerKeys().length, 1)
  })
})
test('five invalid MFA step-up codes revoke the current owner bearer session', async () => {
  await setup(async ({ svc, mfaSecret, store }) => {
    const session = (await svc.login({
      username: 'onlyowner', password: PW,
      totp: totpAt(mfaSecret, Math.floor(T / 30_000)), nowMs: T
    })).token
    for (let i = 0; i < 5; i++) {
      rejects('INVALID_OWNER_MFA', () => svc.stepUp({
        token: session, totp: 'not-valid', nowMs: T
      }))
    }
    rejects('OWNER_UNAUTHORIZED', () => svc.requireOwner(session, T))
    assert.equal(store.verifyAuditChain(), true)
  })
})
