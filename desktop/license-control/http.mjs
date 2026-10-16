// Minimal independent HTTPS owner-control API.
// Does NOT proxy any patient or clinic application data.
import http from 'node:http'
import https from 'node:https'
import { readFileSync } from 'node:fs'
import { LicensingServiceError } from './service.mjs'

const BODY_LIMIT = 16 * 1024
const limiter = new Map()
const PRIVATE_ENDPOINT = /^\/owner\/v1\//
const PUBLIC_PREFIX = /^(\/license\/v1|\/public\/v1)\//
// No authentication state, secrets or clinical records in these immutable
// browser assets. Owner data APIs continue to require MFA bearer on every call.
const CONSOLE_ASSETS = Object.freeze({
  '/owner/console': ['index.html', 'text/html; charset=utf-8'],
  '/owner/console/app.mjs': ['app.mjs', 'text/javascript; charset=utf-8'],
  '/owner/console/policy.mjs': ['policy.mjs', 'text/javascript; charset=utf-8'],
  '/owner/console/styles.css': ['styles.css', 'text/css; charset=utf-8'],
  '/owner/register': ['register.html', 'text/html; charset=utf-8'],
  '/owner/register.mjs': ['register.mjs', 'text/javascript; charset=utf-8']
})
function writeConsoleAsset(res, path) {
  const [file, type] = CONSOLE_ASSETS[path]
  const contents = readFileSync(new URL('./console/' + file, import.meta.url))
  res.writeHead(200, {
    'Content-Type': type,
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer',
    'X-Frame-Options': 'DENY',
    'Cross-Origin-Resource-Policy': 'same-origin',
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=()',
    'Content-Security-Policy': "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'; object-src 'none'; upgrade-insecure-requests"
  })
  res.end(contents)
}

function restricted(ip, path, nowMs) {
  const key = `${ip}:${path}`
  for (const [k, v] of limiter) if (v.until < nowMs) limiter.delete(k)
  if (limiter.size > 4096) return true
  const v = limiter.get(key) ?? { count: 0, until: nowMs + 60_000 }
  if (nowMs > v.until) { v.count = 0; v.until = nowMs + 60_000 }
  ++v.count
  limiter.set(key, v)
  return v.count > (path.includes('/login') ? 12 : 60)
}
function writeJson(res, status, value) {
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer',
    'X-Frame-Options': 'DENY'
  })
  res.end(JSON.stringify(value))
}
async function readJson(req) {
  if (req.headers['content-type']?.split(';')[0].trim() !== 'application/json') {
    throw new LicensingServiceError('JSON_CONTENT_TYPE_REQUIRED')
  }
  const parts = []
  let n = 0
  for await (const chunk of req) {
    n += chunk.length
    if (n > BODY_LIMIT) throw new LicensingServiceError('BODY_TOO_LARGE')
    parts.push(chunk)
  }
  let body
  try {
    body = JSON.parse(Buffer.concat(parts).toString('utf8'))
  } catch {
    throw new LicensingServiceError('INVALID_JSON')
  }
  if (!body || Array.isArray(body) || typeof body !== 'object') {
    throw new LicensingServiceError('INVALID_JSON')
  }
  return body
}
function fieldsOnly(body, fields) {
  if (Object.keys(body).some(name => !fields.includes(name))) {
    throw new LicensingServiceError('UNRECOGNIZED_INPUT_FIELD')
  }
}
function bearer(req) {
  const h = req.headers.authorization
  if (typeof h !== 'string' || !/^Bearer [A-Za-z0-9_-]{43}$/.test(h)) {
    throw new LicensingServiceError('OWNER_UNAUTHORIZED')
  }
  return h.slice(7)
}
function policy(body) {
  const p = body.policy
  if (!p || typeof p !== 'object' || Array.isArray(p)) {
    throw new LicensingServiceError('INVALID_POLICY')
  }
  fieldsOnly(p, ['expiresAtMs', 'seatLimit', 'maxInstallations', 'modules'])
  return p
}
const loopback = ip => ['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(ip)

export function createOwnerControlServer(service, {
  tlsOptions,
  allowInsecureLoopbackForTests = false,
  permittedBrowserOrigin = null
} = {}) {
  if (!tlsOptions && !allowInsecureLoopbackForTests) {
    throw new Error('Owner control API requires TLS by default')
  }
  if (permittedBrowserOrigin !== null &&
    (typeof permittedBrowserOrigin !== 'string' ||
    !permittedBrowserOrigin.startsWith('https://'))) {
    throw new Error('Browser origin must be an explicit HTTPS origin')
  }
  const handle = async (req, res) => {
    const nowMs = Date.now()
    const path = req.url
    try {
      if (typeof path !== 'string' || path.length > 256 ||
        path.includes('?') || path.includes('#') ||
        !(PRIVATE_ENDPOINT.test(path) || PUBLIC_PREFIX.test(path) ||
          path === '/health' || Object.hasOwn(CONSOLE_ASSETS, path))) {
        return writeJson(res, 404, { error: 'NOT_FOUND' })
      }
      if (!tlsOptions && !loopback(req.socket.remoteAddress)) {
        return writeJson(res, 403, { error: 'TLS_REQUIRED' })
      }
      if (req.headers.origin && req.headers.origin !== permittedBrowserOrigin) {
        return writeJson(res, 403, { error: 'CROSS_ORIGIN_FORBIDDEN' })
      }
      if (req.method === 'OPTIONS') return writeJson(res, 405, { error: 'NOT_ALLOWED' })
      if (restricted(req.socket.remoteAddress, path, nowMs)) {
        return writeJson(res, 429, { error: 'RATE_LIMITED' })
      }
      if (Object.hasOwn(CONSOLE_ASSETS, path)) {
        if (req.method !== 'GET') return writeJson(res, 405, { error: 'NOT_ALLOWED' })
        return writeConsoleAsset(res, path)
      }
      if (path === '/health' && req.method === 'GET') {
        return writeJson(res, 200, { status: 'ok' })
      }
      if (path === '/license/v1/issuers' && req.method === 'GET') {
        return writeJson(res, 200, { issuers: service.trustedIssuerKeys() })
      }
      if (path === '/public/v1/applications' && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, ['organization', 'contactName', 'contactPhone', 'contactEmail', 'region', 'note'])
        return writeJson(res, 201, service.submitPublicApplication({ ...b, nowMs }))
      }
      if (path === '/license/v1/applications' && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, ['tenantId', 'installationId', 'installationPublicKeyPem', 'possessionSignature', 'organization'])
        return writeJson(res, 201, service.submitApplication({ ...b, nowMs }))
      }
      if (path === '/license/v1/challenges' && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, ['tenantId', 'installationId'])
        return writeJson(res, 200, service.challenge({ ...b, nowMs }))
      }
      if (path === '/license/v1/status' && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, ['tenantId', 'installationId'])
        return writeJson(res, 200, { statusProof: service.issueStatusProof({ ...b, nowMs }) })
      }
      if (path === '/license/v1/leases/online' && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, ['tenantId', 'installationId', 'nonce', 'possessionSignature'])
        return writeJson(res, 200, service.issueOnline({ ...b, nowMs }))
      }
      if (path === '/owner/v1/login' && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, ['username', 'password', 'totp'])
        return writeJson(res, 200, await service.login({ ...b, nowMs }))
      }
      // Every owner operation must carry an MFA-authenticated bearer session.
      // Never accept owner flag, role or TOTP bypass from user-supplied JSON.
      if (!PRIVATE_ENDPOINT.test(path)) return writeJson(res, 404, { error: 'NOT_FOUND' })
      const token = bearer(req)
      service.requireOwner(token, nowMs)
      if (path === '/owner/v1/logout' && req.method === 'POST') {
        service.logout(token, nowMs)
        return writeJson(res, 200, { status: 'logged-out' })
      }
      if (path === '/owner/v1/step-up' && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, ['totp'])
        return writeJson(res, 200, service.stepUp({ token, totp: b.totp, nowMs }))
      }
      if (path === '/owner/v1/applications' && req.method === 'GET') {
        return writeJson(res, 200, { applications: service.applicationList({ token, nowMs }) })
      }
      if (path === '/owner/v1/signing-keys/rotate' && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, [])
        return writeJson(res, 200, service.rotateKey({ token, nowMs }))
      }
      const retire = /^\/owner\/v1\/signing-keys\/([a-zA-Z0-9._-]+)\/retire$/.exec(path)
      if (retire && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, [])
        service.retireVerificationKey({ token, kid: retire[1], nowMs })
        return writeJson(res, 200, { status: 'retired' })
      }
      const compromised = /^\/owner\/v1\/signing-keys\/([a-zA-Z0-9._-]+)\/emergency-revoke$/.exec(path)
      if (compromised && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, [])
        return writeJson(res, 200, service.emergencyRevokeSigner({
          token, kid: compromised[1], nowMs
        }))
      }
      const application = /^\/owner\/v1\/applications\/([0-9a-f-]{36})\/(approve|reject|request-info)$/.exec(path)
      if (application && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, application[2] === 'approve' ? ['reason', 'policy'] : ['reason'])
        return writeJson(res, 200, service.decideApplication({
          token, applicationId: application[1],
          decision: application[2] === 'approve' ? 'APPROVED' : application[2] === 'reject' ? 'REJECTED' : 'NEEDS_INFO',
          reason: b.reason, policy: application[2] === 'approve' ? policy(b) : null, nowMs
        }))
      }
      const status = /^\/owner\/v1\/tenants\/([0-9a-f-]{36})$/.exec(path)
      if (status && req.method === 'GET') {
        return writeJson(res, 200, { tenant: service.status({ token, tenantId: status[1], nowMs }) })
      }
      const change = /^\/owner\/v1\/tenants\/([0-9a-f-]{36})\/(policy|status)$/.exec(path)
      if (change && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, change[2] === 'policy' ? ['policy'] : ['status'])
        return writeJson(res, 200, change[2] === 'policy'
          ? service.updateEntitlement({ token, tenantId: change[1], policy: policy(b), nowMs })
          : service.setTenantStatus({ token, tenantId: change[1], status: b.status, nowMs }))
      }
      if (path === '/owner/v1/installations/replace' && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, ['tenantId', 'oldInstallationId', 'newInstallationId', 'newPublicKeyPem', 'proof'])
        return writeJson(res, 200, service.replaceInstallation({ token, ...b, nowMs }))
      }
      if (path === '/owner/v1/leases/offline' && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, ['tenantId', 'installationId'])
        return writeJson(res, 200, service.issueOffline({ token, ...b, nowMs }))
      }
      if (path === '/owner/v1/audit' && req.method === 'GET') {
        return writeJson(res, 200, { audit: service.audit({ token, nowMs }) })
      }
      if (path === '/owner/v1/audit/checkpoint' && req.method === 'POST') {
        const b = await readJson(req)
        fieldsOnly(b, [])
        return writeJson(res, 200, service.signedAuditCheckpoint({ token, nowMs }))
      }
      return writeJson(res, 404, { error: 'NOT_FOUND' })
    } catch (error) {
      const code = error instanceof LicensingServiceError ? error.code : 'INTERNAL_ERROR'
      const status = code === 'OWNER_UNAUTHORIZED' || code === 'INVALID_OWNER_CREDENTIALS' ||
        code === 'OWNER_STEP_UP_REQUIRED' ? 401
        : code === 'OWNER_ALREADY_PROVISIONED' || code === 'APPLICATION_ALREADY_EXISTS' ||
          code === 'APPLICATION_NOT_PENDING' || code === 'TENANT_NOT_APPROVED' ||
          code === 'CHALLENGE_CAPACITY_EXHAUSTED' || code === 'TENANT_NOT_ACTIVE' ? 409
          : code === 'INTERNAL_ERROR' ? 500 : 400
      // Never serialize exception details, bearer tokens or request bodies.
      return writeJson(res, status, { error: code })
    }
  }
  const server = tlsOptions ? https.createServer(tlsOptions, handle) : http.createServer(handle)
  // For tests, the only allowed cleartext listener is explicit loopback.
  if (!tlsOptions) {
    const originalListen = server.listen.bind(server)
    server.listen = (...args) => {
      const host = typeof args[1] === 'string' ? args[1] : null
      if (host !== '127.0.0.1' && host !== '::1') {
        throw new Error('Insecure owner API may only bind loopback in tests')
      }
      return originalListen(...args)
    }
  }
  return server
}
