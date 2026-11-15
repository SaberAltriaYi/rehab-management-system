import assert from 'node:assert/strict'
import { test } from 'node:test'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { randomBytes } from 'node:crypto'
import { OwnerLicenseStore } from './store.mjs'
import { OwnerLicensingService } from './service.mjs'
import { SecretVault, totpAt } from './security.mjs'
import { createOwnerControlServer } from './http.mjs'

const OWNER_PASSWORD = 'Synthetic-Recovery-Owner-Password-123!'
const newChallenge = () => randomBytes(32).toString('base64url')

test('owner MFA challenge is single-use, bound to launcher, expires and is audited without secrets', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'rehab-admin-recovery-test-'))
  let store
  try {
    const file = join(dir, 'owner.sqlite')
    store = new OwnerLicenseStore(file)
    const owner = new OwnerLicensingService(store, new SecretVault(randomBytes(32)))
    const base = Date.parse('2026-11-12T04:00:00Z')
    const secret = (await owner.provisionOwner({ username: 'ownerqa', password: OWNER_PASSWORD, nowMs: base })).mfaSecret
    const session = (await owner.login({
      username: 'ownerqa', password: OWNER_PASSWORD,
      totp: totpAt(secret, Math.floor(base / 30000)), nowMs: base
    })).token
    const challenge = newChallenge()
    const request = { token: session, deviceChallenge: challenge,
      confirmation: 'RECOVER-CORE-ADMIN', nowMs: base }
    assert.throws(() => owner.authorizeCoreAdminRecovery({ ...request, token: 'fake' }),
      e => e.code === 'OWNER_UNAUTHORIZED')
    assert.throws(() => owner.authorizeCoreAdminRecovery({ ...request, confirmation: 'yes' }),
      e => e.code === 'INVALID_RECOVERY_CONFIRMATION')
    const issued = owner.authorizeCoreAdminRecovery(request)
    assert.match(issued.authorizationCode, /^[A-Za-z0-9_-]{43}$/)
    assert.equal(issued.targetUsername, 'admin')
    assert.equal(issued.expiresAt, new Date(base + 120_000).toISOString())

    assert.throws(() => owner.redeemCoreAdminRecovery({ authorizationCode: issued.authorizationCode,
      deviceChallenge: newChallenge(), nowMs: base + 1 }), e => e.code === 'INVALID_RECOVERY_AUTHORIZATION')
    const redeemed = owner.redeemCoreAdminRecovery({ authorizationCode: issued.authorizationCode,
      deviceChallenge: challenge, nowMs: base + 2 })
    assert.deepEqual(redeemed, { tenantId: 1, userId: 1, username: 'admin', scope: 'password-only' })
    assert.throws(() => owner.redeemCoreAdminRecovery({ authorizationCode: issued.authorizationCode,
      deviceChallenge: challenge, nowMs: base + 3 }), e => e.code === 'INVALID_RECOVERY_AUTHORIZATION')

    const expired = owner.authorizeCoreAdminRecovery({ ...request, nowMs: base + 3000 })
    assert.throws(() => owner.redeemCoreAdminRecovery({ authorizationCode: expired.authorizationCode,
      deviceChallenge: challenge, nowMs: base + 123_001 }), e => e.code === 'INVALID_RECOVERY_AUTHORIZATION')

    const database = readFileSync(file, 'utf8')
    assert.equal(database.includes(issued.authorizationCode), false)
    assert.equal(database.includes(challenge), false)
    assert.equal(store.verifyAuditChain(), true)
    assert.equal(owner.audit({ token: session, nowMs: base + 10000 }).some(x => x.action === 'CORE_ADMIN_RECOVERY_AUTHORIZED'), true)
    assert.equal(owner.audit({ token: session, nowMs: base + 10000 }).some(x => x.action === 'CORE_ADMIN_RECOVERY_REDEEMED'), true)
  } finally {
    store?.close()
    rmSync(dir, { recursive: true, force: true })
  }
})

test('recovery requires recent step-up, never resumes grant after logout or failed MFA', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'rehab-admin-stepup-test-'))
  let store
  try {
    store = new OwnerLicenseStore(join(dir, 'owner.sqlite'))
    const svc = new OwnerLicensingService(store, new SecretVault(randomBytes(32)))
    const base = Date.parse('2026-11-12T05:00:00Z')
    const seed = (await svc.provisionOwner({ username: 'ownerqa', password: OWNER_PASSWORD, nowMs: base })).mfaSecret
    const token = (await svc.login({ username: 'ownerqa', password: OWNER_PASSWORD,
      totp: totpAt(seed, Math.floor(base / 30000)), nowMs: base })).token
    assert.throws(() => svc.authorizeCoreAdminRecovery({ token,
      deviceChallenge: newChallenge(), confirmation: 'RECOVER-CORE-ADMIN', nowMs: base + 120_001 }),
    e => e.code === 'OWNER_STEP_UP_REQUIRED')
    svc.stepUp({ token, totp: totpAt(seed, Math.floor((base + 150000) / 30000)), nowMs: base + 150000 })
    const grant = svc.authorizeCoreAdminRecovery({ token, deviceChallenge: newChallenge(),
      confirmation: 'RECOVER-CORE-ADMIN', nowMs: base + 150001 })
    assert.match(grant.authorizationCode, /^[A-Za-z0-9_-]{43}$/)
    svc.logout(token, base + 150002)
    assert.throws(() => svc.authorizeCoreAdminRecovery({ token,
      deviceChallenge: newChallenge(), confirmation: 'RECOVER-CORE-ADMIN', nowMs: base + 150003 }),
    e => e.code === 'OWNER_UNAUTHORIZED')
  } finally {
    store?.close()
    rmSync(dir, { recursive: true, force: true })
  }
})

test('recovery HTTP refuses anonymous issuance and cross-origin redemption; ordinary paths remain protected', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'rehab-admin-recovery-http-'))
  let store, server
  try {
    store = new OwnerLicenseStore(join(dir, 'owner.sqlite'))
    const svc = new OwnerLicensingService(store, new SecretVault(randomBytes(32)))
    const base = Date.now()
    const seed = (await svc.provisionOwner({ username: 'ownerqa', password: OWNER_PASSWORD, nowMs: base })).mfaSecret
    server = createOwnerControlServer(svc, { allowInsecureLoopbackForTests: true,
      permittedBrowserOrigin: 'https://owner.example.invalid', adminRecoveryEnabled: true })
    await new Promise(done => server.listen(0, '127.0.0.1', done))
    const origin = `http://127.0.0.1:${server.address().port}`
    const send = async (path, body, headers = {}) => {
      const res = await fetch(origin + path, { method: 'POST',
        headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) })
      return { status: res.status, value: await res.json() }
    }
    assert.equal((await send('/owner/v1/core-admin-recovery/authorize', {
      deviceChallenge: newChallenge(), confirmation: 'RECOVER-CORE-ADMIN' })).status, 401)
    const login = await send('/owner/v1/login', { username: 'ownerqa', password: OWNER_PASSWORD,
      totp: totpAt(seed, Math.floor(base / 30000)) })
    assert.equal(login.status, 200)
    const token = login.value.token
    const challenge = newChallenge()
    const issued = await send('/owner/v1/core-admin-recovery/authorize', {
      deviceChallenge: challenge, confirmation: 'RECOVER-CORE-ADMIN' },
    { authorization: `Bearer ${token}`, origin: 'https://owner.example.invalid' })
    assert.equal(issued.status, 200)
    const code = issued.value.authorizationCode
    assert.equal((await send('/owner/v1/core-admin-recovery/redeem', { authorizationCode: code,
      deviceChallenge: challenge }, { origin: 'https://evil.example.invalid' })).status, 403)
    assert.equal((await send('/owner/v1/core-admin-recovery/redeem', { authorizationCode: code,
      deviceChallenge: challenge }, { origin: 'https://owner.example.invalid' })).status, 403)
    assert.equal((await send('/owner/v1/core-admin-recovery/redeem', { authorizationCode: code,
      deviceChallenge: challenge, admin: true })).status, 400)
    const redeemed = await send('/owner/v1/core-admin-recovery/redeem', { authorizationCode: code,
      deviceChallenge: challenge })
    assert.equal(redeemed.status, 200)
    assert.equal(redeemed.value.scope, 'password-only')
    assert.equal((await send('/owner/v1/core-admin-recovery/redeem', { authorizationCode: code,
      deviceChallenge: challenge })).status, 400)
    assert.equal((await send('/owner/v1/audit', {})).status, 401)
  } finally {
    if (server?.listening) await new Promise(done => server.close(done))
    store?.close()
    rmSync(dir, { recursive: true, force: true })
  }
})

test('recovery endpoints are absent unless Owner service explicitly opts in', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'rehab-admin-recovery-default-off-'))
  let store, server
  try {
    store = new OwnerLicenseStore(join(dir, 'owner.sqlite'))
    const svc = new OwnerLicensingService(store, new SecretVault(randomBytes(32)))
    server = createOwnerControlServer(svc, { allowInsecureLoopbackForTests: true,
      permittedBrowserOrigin: 'https://owner.example.invalid' })
    await new Promise(done => server.listen(0, '127.0.0.1', done))
    const origin = `http://127.0.0.1:${server.address().port}`
    for (const path of ['/owner/v1/core-admin-recovery/authorize',
      '/owner/v1/core-admin-recovery/redeem']) {
      const response = await fetch(origin + path, {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ deviceChallenge: newChallenge() })
      })
      assert.equal(response.status, 404, path)
      assert.deepEqual(await response.json(), { error: 'NOT_FOUND' })
    }
  } finally {
    if (server?.listening) await new Promise(done => server.close(done))
    store?.close()
    rmSync(dir, { recursive: true, force: true })
  }
})
