// RHL1 signed lease protocol — isolated Node contract/crypto prototype.
// NOT connected to the current Java login, Tauri launcher or live traffic.
// Production Ed25519 private keys MUST NEVER be generated/stored here.
import { verify as verifySignature } from 'node:crypto'

export const MAX_OFFLINE_LEASE_MS = 72 * 60 * 60 * 1000
const CLOCK_SKEW_MS = 5 * 60 * 1000
const MAX_TOKEN_BYTES = 8192
const MAX_PAYLOAD_BYTES = 4096
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
const KID = /^[a-zA-Z0-9._-]{1,48}$/
const MODULE = /^[a-z][a-z0-9_-]{0,31}$/
const TOKEN = /^RHL1\.([a-zA-Z0-9._-]{1,48})\.([a-zA-Z0-9_-]+)\.([a-zA-Z0-9_-]+)$/
const FIELDS = Object.freeze([
  'schemaVersion', 'mode', 'leaseId', 'tenantId', 'installationId',
  'issuedAt', 'notBefore', 'expiresAt', 'entitlementExpiresAt',
  'revocationEpoch', 'seatLimit', 'maxInstallations', 'modules'
])

export class LicenseVerificationError extends Error {
  constructor(code) {
    super(code)
    this.name = 'LicenseVerificationError'
    this.code = code
  }
}

function deny(code) {
  throw new LicenseVerificationError(code)
}

function parseUtc(value) {
  if (typeof value !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(value)) deny('INVALID_TIME')
  const ms = Date.parse(value)
  if (!Number.isFinite(ms) || new Date(ms).toISOString() !== value) deny('INVALID_TIME')
  return ms
}

function decodeCanonicalBase64url(value, limit) {
  if (!/^[A-Za-z0-9_-]+$/.test(value) || value.length > limit * 2) deny('MALFORMED_LICENSE')
  const data = Buffer.from(value, 'base64url')
  if (data.length > limit || data.toString('base64url') !== value) deny('MALFORMED_LICENSE')
  return data
}

function validateSignedClaims(value, serialized, context) {
  if (value === null || typeof value !== 'object' || Array.isArray(value) ||
    Object.keys(value).length !== FIELDS.length ||
    Object.keys(value).some((field, i) => field !== FIELDS[i]) ||
    JSON.stringify(value) !== serialized) deny('NON_CANONICAL_PAYLOAD')
  if (value.schemaVersion !== 1 || !['online', 'offline'].includes(value.mode)) deny('INVALID_CLAIMS')
  for (const id of ['leaseId', 'tenantId', 'installationId']) {
    if (typeof value[id] !== 'string' || !UUID.test(value[id])) deny('INVALID_CLAIMS')
  }
  if (value.tenantId !== context.tenantId || value.installationId !== context.installationId) {
    deny('LICENSE_BINDING_MISMATCH')
  }
  if (!Number.isSafeInteger(value.revocationEpoch) || value.revocationEpoch < 0 ||
    !Number.isSafeInteger(context.minimumRevocationEpoch) ||
    context.minimumRevocationEpoch < 0) deny('INVALID_CLAIMS')
  if (value.revocationEpoch < context.minimumRevocationEpoch) deny('REVOKED_LEASE')
  if (!(value.seatLimit === null ||
    (Number.isSafeInteger(value.seatLimit) && value.seatLimit > 0 &&
      value.seatLimit <= 2_147_483_647))) deny('INVALID_SEAT_LIMIT')
  if (!Number.isSafeInteger(value.maxInstallations) ||
    value.maxInstallations < 1 || value.maxInstallations > 100_000) deny('INVALID_CLAIMS')
  if (!Array.isArray(value.modules) || value.modules.length === 0 || value.modules.length > 64 ||
    value.modules.some((module) => typeof module !== 'string' || !MODULE.test(module)) ||
    value.modules.some((module, index) => index > 0 && value.modules[index - 1] >= module)) {
    deny('INVALID_MODULES')
  }
  const issuedAt = parseUtc(value.issuedAt)
  const notBefore = parseUtc(value.notBefore)
  const expiresAt = parseUtc(value.expiresAt)
  const entitlementExpiresAt = parseUtc(value.entitlementExpiresAt)
  if (issuedAt > notBefore || expiresAt <= notBefore || expiresAt <= issuedAt ||
    expiresAt - issuedAt > MAX_OFFLINE_LEASE_MS ||
    expiresAt > entitlementExpiresAt) deny('INVALID_LEASE_PERIOD')
  if (context.nowMs + CLOCK_SKEW_MS < issuedAt ||
    context.nowMs < notBefore) deny('LICENSE_NOT_YET_VALID')
  if (Number.isFinite(context.lastTrustedTimeMs) &&
    context.nowMs + CLOCK_SKEW_MS < context.lastTrustedTimeMs) deny('CLOCK_ROLLBACK')
  if (context.nowMs >= expiresAt) deny('LICENSE_EXPIRED')
  return Object.freeze({ ...value, modules: Object.freeze([...value.modules]) })
}

/**
 * Verify a pre-approved, Ed25519-signed RHL1 lease.
 *
 * trustedKeys: Map<kid, crypto.KeyObject> of trusted *public* keys pinned by
 * the distributor, never keys taken from the token or customer settings.
 * minimumRevocationEpoch/lastTrustedTimeMs must eventually come from an
 * authenticated monotonic source; they cannot protect against a local root
 * attacker who replaces the entire snapshot.
 *
 * This only checks a signed lease. It does NOT enforce per-request Java RBAC,
 * staff-seat transactions, owner's approval workflow or remote-device trust.
 */
export function verifyTenantLicenseLease(token, options) {
  if (typeof token !== 'string' || Buffer.byteLength(token) > MAX_TOKEN_BYTES) {
    deny('MALFORMED_LICENSE')
  }
  if (!options || !(options.trustedKeys instanceof Map) ||
    !Number.isSafeInteger(options.nowMs) ||
    !UUID.test(options.tenantId ?? '') || !UUID.test(options.installationId ?? '')) {
    deny('INVALID_VERIFICATION_CONTEXT')
  }
  // A missing revocation snapshot / clock watermark is never interpreted as
  // "fresh and active". First-time activation must supply an authenticated
  // baseline; production must persist and advance it monotonically.
  const { minimumRevocationEpoch, lastTrustedTimeMs } = options
  if (!Number.isSafeInteger(minimumRevocationEpoch) || minimumRevocationEpoch < 0 ||
    !Number.isSafeInteger(lastTrustedTimeMs) || lastTrustedTimeMs < 0) {
    deny('INVALID_VERIFICATION_CONTEXT')
  }
  const matched = TOKEN.exec(token)
  if (!matched) deny('MALFORMED_LICENSE')
  const [, kid, payload64, signature64] = matched
  if (!KID.test(kid)) deny('MALFORMED_LICENSE')
  const publicKey = options.trustedKeys.get(kid)
  if (!publicKey || publicKey.type !== 'public' || publicKey.asymmetricKeyType !== 'ed25519') {
    deny('UNTRUSTED_ISSUER_KEY')
  }
  const signature = decodeCanonicalBase64url(signature64, 64)
  if (signature.length !== 64) deny('INVALID_SIGNATURE')
  const payload = decodeCanonicalBase64url(payload64, MAX_PAYLOAD_BYTES)
  const signedBytes = Buffer.from(`RHL1.${kid}.${payload64}`, 'ascii')
  let signatureOk = false
  try {
    signatureOk = verifySignature(null, signedBytes, publicKey, signature)
  } catch {
    deny('INVALID_SIGNATURE')
  }
  if (!signatureOk) deny('INVALID_SIGNATURE')
  let serialized
  try {
    serialized = new TextDecoder('utf-8', { fatal: true }).decode(payload)
  } catch {
    deny('INVALID_PAYLOAD')
  }
  let claims
  try {
    claims = JSON.parse(serialized)
  } catch {
    deny('INVALID_PAYLOAD')
  }
  return validateSignedClaims(claims, serialized, {
    tenantId: options.tenantId,
    installationId: options.installationId,
    nowMs: options.nowMs,
    minimumRevocationEpoch,
    lastTrustedTimeMs
  })
}
