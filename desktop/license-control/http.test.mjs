import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { generateKeyPairSync, randomBytes, randomUUID, sign, verify, createPublicKey } from 'node:crypto'
import { mkdtempSync, rmSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { execFileSync } from 'node:child_process'
import https from 'node:https'
import { OwnerLicenseStore } from './store.mjs'
import { OwnerLicensingService } from './service.mjs'
import { SecretVault, totpAt } from './security.mjs'
import { createOwnerControlServer, canonicalOwnerPageRedirect } from './http.mjs'
import { enrollmentMessage, renewalMessage } from '../scripts/tenant-license-owner-core.mjs'
import { verifyTenantLicenseLease } from '../scripts/tenant-license-lease.mjs'

const DEVICE = generateKeyPairSync('ed25519')
const TENANT = randomUUID()
const INSTALL = randomUUID()
const ADMIN_PASSWORD = 'Synthetic-Only-Owner-Password-123!'
let temp, store, service, server, url, mfaSeed, sessionToken
const now = () => Date.now()
before(async () => {
  temp = mkdtempSync(join(tmpdir(), 'rehab-owner-http-test-'))
  store = new OwnerLicenseStore(join(temp, 'owner.sqlite'))
  service = new OwnerLicensingService(store, new SecretVault(randomBytes(32)))
  mfaSeed = (await service.provisionOwner({
    username: 'owneradmin', password: ADMIN_PASSWORD, nowMs: now()
  })).mfaSecret
  server = createOwnerControlServer(service, {
    allowInsecureLoopbackForTests: true,
    permittedBrowserOrigin: 'https://owner.example.invalid'
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  url = `http://127.0.0.1:${server.address().port}`
})
after(async () => {
  if (server?.listening) await new Promise(resolve => server.close(resolve))
  store?.close()
  if (temp) rmSync(temp, { recursive: true, force: true })
})
async function api(path, { method = 'GET', body, token, headers = {} } = {}) {
  const res = await fetch(url + path, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'content-type': 'application/json' }),
      ...(token ? { authorization: `Bearer ${token}` } : {}),
      ...headers
    },
    ...(body === undefined ? {} : {
      body: typeof body === 'string' ? body : JSON.stringify(body)
    })
  })
  return { status: res.status, body: await res.json(), headers: res.headers }
}
test('unexpected owner login host navigates to exact HTTPS allowlisted origin', () => {
  const origins = ['https://127.0.0.1:9444', 'https://localhost:9444']
  for (const path of ['/owner/console', '/owner/register', '/owner/recovery']) {
    assert.equal(canonicalOwnerPageRedirect(path, 'localhost:9444', origins, true), null)
    assert.equal(canonicalOwnerPageRedirect(path, '127.0.0.1:9444', origins, true), null)
    assert.equal(canonicalOwnerPageRedirect(path, 'example.test:9444', origins, true),
      'https://127.0.0.1:9444' + path)
    assert.equal(canonicalOwnerPageRedirect(path, '127.0.0.1:9445', origins, true),
      'https://127.0.0.1:9444' + path)
  }
  assert.equal(canonicalOwnerPageRedirect('/owner/v1/login', 'example.test:9444', origins, true), null)
  assert.equal(canonicalOwnerPageRedirect('/owner/console', 'example.test:9444', origins, false), null)
  assert.throws(() => createOwnerControlServer({}, {
    allowInsecureLoopbackForTests: true,
    permittedBrowserOrigin: 'https://owner.example.test/unexpected-path'
  }), /exact HTTPS origin/)
})

test('owner console assets are same-origin, CSP-hardened and contain no secrets', async () => {
  const root = await fetch(url + '/', { redirect: 'manual' })
  assert.equal(root.status, 302)
  assert.equal(root.headers.get('location'), '/owner/console')
  for (const oldPath of ['/owner', '/owner/login']) {
    const oldLogin = await fetch(url + oldPath, { redirect: 'manual' })
    assert.equal(oldLogin.status, 302)
    assert.equal(oldLogin.headers.get('location'), '/owner/console')
    assert.equal(oldLogin.headers.get('cache-control'), 'no-store')
  }
  const html = await fetch(url + '/owner/console')
  assert.equal(html.status, 200)
  assert.match(html.headers.get('content-security-policy'), /default-src 'none'/)
  assert.match(html.headers.get('content-security-policy'), /connect-src 'self'/)
  assert.equal(html.headers.get('cache-control'), 'no-store')
  const body = await html.text()
  assert.match(body, /软件所有者授权管理台/)
  assert.match(body, /name="unlimited" type="checkbox" checked/,
    'Default license must explicitly sign unlimited staff seats')
  assert.match(body, /id="rotate"[^>]*disabled/, 'UI must not rotate an undistributed signer')
  assert.doesNotMatch(body, /<script(?![^>]*src=)/)
  for (const asset of ['/owner/console/app.mjs', '/owner/console/policy.mjs',
    '/owner/console/styles.css', '/owner/recovery', '/owner/recovery.mjs']) {
    const result = await fetch(url + asset)
    assert.equal(result.status, 200, asset)
    assert.equal(result.headers.get('x-content-type-options'), 'nosniff')
    if (asset.endsWith('/app.mjs')) {
      const browserScript = await result.text()
      assert.doesNotMatch(browserScript, /api\('\/owner\/v1\/signing-keys\/rotate'/)
      assert.doesNotMatch(browserScript, /\b(?:localStorage|sessionStorage)\s*[.\[]/)
    }
  }
  assert.equal((await api('/owner/console/hack.js')).status, 404)
  assert.equal((await api('/owner/console', { method: 'POST', body: {} })).status, 405)
  assert.equal((await api('/owner/console/app.mjs', {
    headers: { origin: 'https://evil.example.invalid' }
  })).status, 403)
  assert.equal((await api('/owner/v1/audit')).status, 401)
})
test('HTTP API is inaccessible as an owner without TOTP session', async () => {
  const denied = await api('/owner/v1/applications')
  assert.equal(denied.status, 401)
  assert.equal(denied.body.error, 'OWNER_UNAUTHORIZED')
  const login = await api('/owner/v1/login', {
    method: 'POST', body: {
      username: 'owneradmin', password: ADMIN_PASSWORD,
      totp: totpAt(mfaSeed, Math.floor(now() / 30_000))
    }
  })
  assert.equal(login.status, 200, JSON.stringify(login.body))
  sessionToken = login.body.token
  assert.match(sessionToken, /^[A-Za-z0-9_-]{43}$/)
  assert.equal(login.headers.get('cache-control'), 'no-store')
  assert.equal((await api('/owner/v1/applications', { token: sessionToken })).status, 200)
})
test('public institution registration creates a pending owner application without clinical data', async () => {
  const page = await fetch(url + '/owner/register')
  assert.equal(page.status, 200)
  const pageHtml = await page.text()
  assert.match(pageHtml, /机构申请/)
  assert.match(pageHtml, /接下来怎么做/)
  assert.match(pageHtml, /打开主系统登录页/)
  assert.match(pageHtml, /name="contactEmail" type="email" required/)
  assert.doesNotMatch(pageHtml, /name="contactPhone"/)
  assert.doesNotMatch(pageHtml, /name="region"/)
  assert.doesNotMatch(pageHtml, /name="note"/)
  const minimal = await api('/public/v1/applications', {
    method: 'POST', body: {
      organization: '三字段合成康复机构', contactName: '测试联系人',
      contactEmail: 'minimal@example.com'
    }
  })
  assert.equal(minimal.status, 201, JSON.stringify(minimal.body))
  assert.equal(minimal.body.status, 'PENDING')
  const submitted = await api('/public/v1/applications', {
    method: 'POST', body: {
      organization: '线上申请合成康复中心', contactName: '申请联系人',
      contactPhone: '13800000000', contactEmail: 'apply@example.com',
      region: '杭州', note: '需要主系统和动作评估模块'
    }
  })
  assert.equal(submitted.status, 201, JSON.stringify(submitted.body))
  assert.equal(submitted.body.status, 'PENDING')
  assert.match(submitted.body.tenantId, /^[0-9a-f-]{36}$/)
  const list = await api('/owner/v1/applications', { token: sessionToken })
  const application = list.body.applications.find(item => item.id === submitted.body.applicationId)
  assert.equal(application.source, 'public')
  assert.equal(application.contactName, '申请联系人')
  assert.equal(application.contactEmail, 'apply@example.com')
  const minimalApplication = list.body.applications.find(item => item.id === minimal.body.applicationId)
  assert.equal(minimalApplication.contactPhone, null)
  assert.equal(minimalApplication.status, 'PENDING')
  const requested = await api('/owner/v1/applications/' + submitted.body.applicationId + '/request-info', {
    method: 'POST', token: sessionToken, body: { reason: '请补充机构使用人数和所在地' }
  })
  assert.equal(requested.status, 200)
  assert.equal(requested.body.status, 'NEEDS_INFO')
})
test('CORS, unknown fields, payload length and alternate HTTP content types fail closed', async () => {
  assert.equal((await api('/public/v1/applications', {
    method: 'POST', body: {}, headers: { origin: 'http://127.0.0.1:5173' }
  })).status, 403)
  assert.equal((await api('/owner/v1/applications', {
    token: sessionToken, headers: { origin: 'https://evil.example.invalid' }
  })).status, 403)
  assert.equal((await api('/owner/v1/applications', {
    token: sessionToken, headers: { origin: 'https://owner.example.invalid' }
  })).status, 200)
  const unknown = await api('/owner/v1/signing-keys/rotate', {
    method: 'POST', token: sessionToken, body: { admin: true }
  })
  assert.equal(unknown.status, 400)
  assert.equal(unknown.body.error, 'UNRECOGNIZED_INPUT_FIELD')
  const invalidMedia = await api('/owner/v1/signing-keys/rotate', {
    method: 'POST', token: sessionToken,
    body: '{}', headers: { 'content-type': 'text/plain' }
  })
  assert.equal(invalidMedia.status, 400)
  const big = await api('/owner/v1/signing-keys/rotate', {
    method: 'POST', token: sessionToken, body: { x: 'x'.repeat(17_000) }
  })
  assert.equal(big.status, 400)
  assert.equal(big.body.error, 'BODY_TOO_LARGE')
})
test('owner controls cryptographic signer, pending studio cannot renew', async () => {
  const rotate = await api('/owner/v1/signing-keys/rotate', {
    method: 'POST', token: sessionToken, body: {}
  })
  assert.equal(rotate.status, 200)
  assert.match(rotate.body.kid, /^owner-/)
  const app = await api('/license/v1/applications', {
    method: 'POST', body: {
      tenantId: TENANT, installationId: INSTALL,
      installationPublicKeyPem: DEVICE.publicKey.export({ format: 'pem', type: 'spki' }),
      possessionSignature: sign(null, enrollmentMessage(TENANT, INSTALL),
        DEVICE.privateKey).toString('base64url'),
      organization: '合成网络测试工作室'
    }
  })
  assert.equal(app.status, 201, JSON.stringify(app.body))
  const unlicensed = await api('/license/v1/challenges', {
    method: 'POST', body: { tenantId: TENANT, installationId: INSTALL }
  })
  assert.equal(unlicensed.status, 409)
  const pending = await api('/owner/v1/applications', { token: sessionToken })
  assert.equal(pending.body.applications.find(item => item.tenantId === TENANT).status, 'PENDING')
  const unauthApproval = await api('/owner/v1/applications/' + app.body.applicationId + '/approve', {
    method: 'POST', body: {
      policy: { expiresAtMs: now() + 86_400_000, seatLimit: 6,
        maxInstallations: 1, modules: ['rehab', 'motion', 'report'] }
    }
  })
  assert.equal(unauthApproval.status, 401)
  const approved = await api('/owner/v1/applications/' + app.body.applicationId + '/approve', {
    method: 'POST', token: sessionToken, body: {
      reason: 'Synthetic approval',
      policy: { expiresAtMs: now() + 86_400_000, seatLimit: 6,
        maxInstallations: 1, modules: ['rehab', 'motion', 'report'] }
    }
  })
  assert.equal(approved.status, 200, JSON.stringify(approved.body))
})
test('real loopback HTTP sequence: owner approval -> challenge -> signed issue -> verification', async () => {
  const challenge = await api('/license/v1/challenges', {
    method: 'POST', body: { tenantId: TENANT, installationId: INSTALL }
  })
  assert.equal(challenge.status, 200)
  const online = await api('/license/v1/leases/online', {
    method: 'POST', body: {
      tenantId: TENANT, installationId: INSTALL, nonce: challenge.body.nonce,
      possessionSignature: sign(null,
        renewalMessage(challenge.body.nonce, TENANT, INSTALL),
        DEVICE.privateKey).toString('base64url')
    }
  })
  assert.equal(online.status, 200, JSON.stringify(online.body))
  const issuer = await api('/license/v1/issuers')
  assert.equal(issuer.status, 200)
  const signed = verifyTenantLicenseLease(online.body.token, {
    tenantId: TENANT, installationId: INSTALL, nowMs: now(),
    minimumRevocationEpoch: 0, lastTrustedTimeMs: now(),
    trustedKeys: new Map(issuer.body.issuers.map(x => [x.kid, createPublicKey(x.publicKeyPem)]))
  })
  assert.equal(signed.seatLimit, 6)
  assert.equal(signed.mode, 'online')
  const snapshot = online.body.statusProof.split('.')
  assert.equal(snapshot[0], 'RHR1')
  assert.equal(snapshot.length, 4)
  const ownerKey = issuer.body.issuers.find(x => x.kid === snapshot[1])
  assert.ok(ownerKey)
  assert.equal(verify(null, Buffer.from(snapshot.slice(0, 3).join('.')),
    createPublicKey(ownerKey.publicKeyPem),
    Buffer.from(snapshot[3], 'base64url')), true)
  assert.equal(JSON.parse(Buffer.from(snapshot[2], 'base64url').toString()).status, 'ACTIVE')
  const replay = await api('/license/v1/leases/online', {
    method: 'POST', body: {
      tenantId: TENANT, installationId: INSTALL, nonce: challenge.body.nonce,
      possessionSignature: sign(null,
        renewalMessage(challenge.body.nonce, TENANT, INSTALL),
        DEVICE.privateKey).toString('base64url')
    }
  })
  assert.equal(replay.status, 400)
  assert.equal(replay.body.error, 'INVALID_CHALLENGE')
})
test('owner can sign offline file, revoke studio and inspect private audit (not public)', async () => {
  const offline = await api('/owner/v1/leases/offline', {
    method: 'POST', token: sessionToken, body: { tenantId: TENANT, installationId: INSTALL }
  })
  assert.equal(offline.status, 200)
  const readBefore = await api('/owner/v1/tenants/' + TENANT, { token: sessionToken })
  assert.equal(readBefore.body.tenant.status, 'ACTIVE')
  const revoked = await api('/owner/v1/tenants/' + TENANT + '/status', {
    method: 'POST', token: sessionToken, body: { status: 'REVOKED' }
  })
  assert.equal(revoked.status, 200)
  const signedRevocation = await api('/license/v1/status', {
    method: 'POST', body: { tenantId: TENANT, installationId: INSTALL }
  })
  assert.equal(signedRevocation.status, 200)
  const signedParts = signedRevocation.body.statusProof.split('.')
  const signedState = JSON.parse(Buffer.from(signedParts[2], 'base64url').toString())
  assert.equal(signedState.status, 'REVOKED')
  assert.ok(signedState.revocationEpoch >= 1)
  const signer = service.trustedIssuerKeys().find(x => x.kid === signedParts[1])
  assert.equal(verify(null, Buffer.from(signedParts.slice(0, 3).join('.')),
    createPublicKey(signer.publicKeyPem),
    Buffer.from(signedParts[3], 'base64url')), true)
  const denied = await api('/license/v1/challenges', {
    method: 'POST', body: { tenantId: TENANT, installationId: INSTALL }
  })
  assert.equal(denied.status, 409)
  assert.equal((await api('/owner/v1/audit')).status, 401)
  const audit = await api('/owner/v1/audit', { token: sessionToken })
  assert.equal(audit.status, 200)
  assert.ok(audit.body.audit.some(x => x.action === 'TENANT_REVOKED'))
  assert.ok(store.verifyAuditChain())
})
test('checkpoint API is owner-only, emergency key revocation removes issuer immediately', async () => {
  assert.equal((await api('/owner/v1/audit/checkpoint', { method: 'POST', body: {} })).status, 401)
  const checkpoint = await api('/owner/v1/audit/checkpoint', {
    method: 'POST', token: sessionToken, body: {}
  })
  assert.equal(checkpoint.status, 200)
  assert.equal(checkpoint.body.checkpoint.split('.').length, 4)
  const publicKeys = await api('/license/v1/issuers')
  assert.equal(publicKeys.body.issuers.length, 1)
  const revokedKey = await api('/owner/v1/signing-keys/' + publicKeys.body.issuers[0].kid +
    '/emergency-revoke', {
    method: 'POST', token: sessionToken, body: {}
  })
  assert.equal(revokedKey.status, 200)
  assert.equal(revokedKey.body.status, 'RETIRED')
  assert.deepEqual((await api('/license/v1/issuers')).body.issuers, [])
  assert.equal(store.verifyAuditChain(), true)
})
test('owner logout invalidates bearer', async () => {
  const end = await api('/owner/v1/logout', { method: 'POST', token: sessionToken, body: {} })
  assert.equal(end.status, 200)
  assert.equal((await api('/owner/v1/audit', { token: sessionToken })).status, 401)
})
test('loopback MFA recovery challenge is one-time and revokes the old owner session', async () => {
  const challenge = await api('/owner/v1/mfa/recovery/challenge')
  assert.equal(challenge.status, 200)
  assert.match(challenge.body.challenge, /^[A-Za-z0-9_-]{43}$/)
  const recovered = await api('/owner/v1/mfa/recover', {
    method: 'POST',
    body: {
      challenge: challenge.body.challenge,
      username: 'owneradmin',
      password: ADMIN_PASSWORD,
      confirmation: 'ROTATE-MFA'
    }
  })
  assert.equal(recovered.status, 200, JSON.stringify(recovered.body))
  assert.match(recovered.body.secret, /^[A-Z2-7]{32}$/)
  assert.match(recovered.body.otpauthUrl, /^otpauth:\/\/totp\//)
  assert.equal((await api('/owner/v1/mfa/recover', {
    method: 'POST',
    body: {
      challenge: challenge.body.challenge,
      username: 'owneradmin',
      password: ADMIN_PASSWORD,
      confirmation: 'ROTATE-MFA'
    }
  })).status, 400)
  assert.equal((await api('/owner/v1/audit', { token: sessionToken })).status, 401)
})
test('actual TLS listener authenticates a pinned self-signed test certificate', async () => {
  const cert = join(temp, 'synthetic-server.crt')
  const key = join(temp, 'synthetic-server.key')
  execFileSync('openssl', [
    'req', '-x509', '-newkey', 'ed25519', '-nodes',
    '-keyout', key, '-out', cert, '-days', '1', '-subj', '/CN=localhost',
    '-addext', 'subjectAltName=IP:127.0.0.1,DNS:localhost'
  ], { stdio: 'ignore' })
  const secureServer = createOwnerControlServer(service, {
    tlsOptions: {
      cert: readFileSync(cert), key: readFileSync(key), minVersion: 'TLSv1.2'
    }
  })
  await new Promise(resolve => secureServer.listen(0, '127.0.0.1', resolve))
  try {
    const response = await new Promise((resolve, reject) => {
      https.get({
        hostname: '127.0.0.1', port: secureServer.address().port, path: '/health',
        ca: readFileSync(cert), rejectUnauthorized: true
      }, res => {
        let body = ''
        res.on('data', c => { body += c })
        res.on('end', () => resolve({ status: res.statusCode, json: JSON.parse(body) }))
      }).on('error', reject)
    })
    assert.equal(response.status, 200)
    assert.equal(response.json.status, 'ok')
  } finally {
    await new Promise(resolve => secureServer.close(resolve))
  }
})