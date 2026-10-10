// Isolated L1 owner-licensing domain prototype. No HTTP listener, database,
// owner login, real MFA, secure clock, key persistence or production signing.
// Must be embedded only behind an independently authenticated MFA owner API,
// a durable transactional store and an HSM/KMS signer before deployment.
// Do not import into the existing application login or launcher.
import {
  randomUUID, sign, verify, randomBytes
} from 'node:crypto'
import { MAX_OFFLINE_LEASE_MS, verifyTenantLicenseLease } from './tenant-license-lease.mjs'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
const KID = /^[a-zA-Z0-9_-]{1,48}$/
const MOD = /^[a-z][a-z0-9_-]{0,31}$/
const MAX_ONLINE_CHALLENGES = 1024
const CHALLENGE_LIFETIME_MS = 5 * 60_000

export class OwnerLicenseError extends Error {
  constructor(code) {
    super(code)
    this.code = code
    this.name = 'OwnerLicenseError'
  }
}

function fail(code) { throw new OwnerLicenseError(code) }

function checkTime(value) {
  if (!Number.isSafeInteger(value) || value < 0) fail('INVALID_TIME')
}

function checkUUID(value) {
  if (typeof value !== 'string' || !UUID.test(value)) fail('INVALID_UUID')
}

function checkPublicKey(key) {
  if (key?.type !== 'public' || key?.asymmetricKeyType !== 'ed25519') {
    fail('INVALID_INSTALLATION_KEY')
  }
}

function checkPolicy({ seatLimit, maxInstallations, modules }) {
  if (seatLimit !== null && (!Number.isSafeInteger(seatLimit) || seatLimit < 1 ||
      seatLimit > 2_147_483_647)) fail('INVALID_SEAT_LIMIT')
  if (!Number.isSafeInteger(maxInstallations) ||
      maxInstallations < 1 || maxInstallations > 100_000) fail('INVALID_INSTALLATION_LIMIT')
  if (!Array.isArray(modules) || modules.length < 1 || modules.length > 64 ||
      modules.some((m) => typeof m !== 'string' || !MOD.test(m)) ||
      new Set(modules).size !== modules.length) fail('INVALID_MODULES')
}

function publicProofMessage(tenantId, installationId) {
  return Buffer.from(`RHL1-ENROLL.${tenantId}.${installationId}`, 'ascii')
}

export function enrollmentMessage(tenantId, installationId) {
  checkUUID(tenantId)
  checkUUID(installationId)
  return publicProofMessage(tenantId, installationId)
}

export function renewalMessage(nonce, tenantId, installationId) {
  if (typeof nonce !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(nonce)) fail('INVALID_CHALLENGE')
  checkUUID(tenantId)
  checkUUID(installationId)
  return Buffer.from(`RHL1-RENEW.${nonce}.${tenantId}.${installationId}`, 'ascii')
}

export class OwnerLicenseCore {
  #applications = new Map()
  #tenants = new Map()
  #challenges = new Map()
  #audit = []
  #signingKey
  #publicKey
  #keyId
  #ownerPolicy

  constructor({ signingKey, publicKey, keyId, ownerPolicy }) {
    if (signingKey?.type !== 'private' || signingKey?.asymmetricKeyType !== 'ed25519' ||
        !KID.test(keyId ?? '') || typeof ownerPolicy !== 'function') fail('INVALID_OWNER_CONFIG')
    checkPublicKey(publicKey)
    const probe = randomBytes(32)
    if (!verify(null, probe, publicKey, sign(null, probe, signingKey))) fail('KEY_PAIR_MISMATCH')
    this.#signingKey = signingKey
    this.#publicKey = publicKey
    this.#keyId = keyId
    // ownerPolicy must verify an upstream, authenticated + MFA session.
    // Do NOT implement this with request-body booleans in a web adapter.
    this.#ownerPolicy = ownerPolicy
  }

  #requireOwner(owner) {
    if (this.#ownerPolicy(owner) !== true) fail('OWNER_MFA_REQUIRED')
  }

  #record(action, tenantId, details, nowMs) {
    this.#audit.push(Object.freeze({
      eventId: randomUUID(), action, tenantId,
      occurredAt: new Date(nowMs).toISOString(),
      ...details
    }))
  }

  submitApplication({ tenantId, installationId, installationPublicKey, possessionSignature, nowMs }) {
    checkTime(nowMs)
    checkUUID(tenantId)
    checkUUID(installationId)
    checkPublicKey(installationPublicKey)
    if (!Buffer.isBuffer(possessionSignature) || possessionSignature.length !== 64 ||
        !verify(null, publicProofMessage(tenantId, installationId),
          installationPublicKey, possessionSignature)) fail('INSTALLATION_PROOF_FAILED')
    if (this.#tenants.has(tenantId) ||
        [...this.#applications.values()].some((app) => app.tenantId === tenantId)) {
      fail('TENANT_ALREADY_REGISTERED')
    }
    const applicationId = randomUUID()
    this.#applications.set(applicationId, {
      tenantId, installationId, installationPublicKey, status: 'PENDING'
    })
    this.#record('APPLICATION_SUBMITTED', tenantId, { applicationId }, nowMs)
    return Object.freeze({ applicationId, status: 'PENDING' })
  }

  approveApplication({ applicationId, owner, expiresAtMs, seatLimit, maxInstallations, modules, nowMs }) {
    this.#requireOwner(owner)
    checkTime(nowMs)
    checkTime(expiresAtMs)
    if (expiresAtMs <= nowMs) fail('ENTITLEMENT_EXPIRED')
    checkPolicy({ seatLimit, maxInstallations, modules })
    const application = this.#applications.get(applicationId)
    if (!application || application.status !== 'PENDING') fail('APPLICATION_NOT_PENDING')
    const tenant = {
      tenantId: application.tenantId,
      installations: new Map([[application.installationId, application.installationPublicKey]]),
      status: 'ACTIVE', revocationEpoch: 0,
      expiresAtMs, seatLimit, maxInstallations, modules: [...modules].sort()
    }
    this.#tenants.set(application.tenantId, tenant)
    application.status = 'APPROVED'
    this.#record('APPLICATION_APPROVED', tenant.tenantId,
      { applicationId, seatLimit, maxInstallations }, nowMs)
    return Object.freeze({ tenantId: tenant.tenantId, status: tenant.status })
  }

  revokeTenant({ owner, tenantId, nowMs }) {
    this.#requireOwner(owner)
    checkTime(nowMs)
    const tenant = this.#tenants.get(tenantId)
    if (!tenant) fail('UNKNOWN_TENANT')
    if (tenant.status !== 'ACTIVE') fail('TENANT_NOT_ACTIVE')
    tenant.status = 'REVOKED'
    tenant.revocationEpoch += 1
    for (const [nonce, challenge] of this.#challenges) {
      if (challenge.tenantId === tenantId) this.#challenges.delete(nonce)
    }
    this.#record('TENANT_REVOKED', tenantId,
      { revocationEpoch: tenant.revocationEpoch }, nowMs)
    return Object.freeze({ status: tenant.status, revocationEpoch: tenant.revocationEpoch })
  }

  #activeInstallation(tenantId, installationId, nowMs) {
    checkUUID(tenantId)
    checkUUID(installationId)
    checkTime(nowMs)
    const tenant = this.#tenants.get(tenantId)
    if (!tenant || tenant.status !== 'ACTIVE') fail('TENANT_NOT_APPROVED')
    if (!tenant.installations.has(installationId)) fail('INSTALLATION_NOT_APPROVED')
    if (tenant.expiresAtMs <= nowMs) fail('ENTITLEMENT_EXPIRED')
    return tenant
  }

  beginOnlineRenewal({ tenantId, installationId, nowMs }) {
    this.#activeInstallation(tenantId, installationId, nowMs)
    // In production use a shared, rate-limited, transactional nonce store.
    for (const [nonce, challenge] of this.#challenges) {
      if (challenge.expiresAtMs <= nowMs) this.#challenges.delete(nonce)
    }
    if (this.#challenges.size >= MAX_ONLINE_CHALLENGES) fail('CHALLENGE_CAPACITY_EXHAUSTED')
    const nonce = randomBytes(32).toString('base64url')
    this.#challenges.set(nonce, {
      tenantId, installationId, expiresAtMs: nowMs + CHALLENGE_LIFETIME_MS
    })
    return nonce
  }

  issueOnlineLease({ tenantId, installationId, nonce, possessionSignature, nowMs }) {
    const tenant = this.#activeInstallation(tenantId, installationId, nowMs)
    // Take nonce only once; a failed signature burns the challenge.
    const challenge = this.#challenges.get(nonce)
    this.#challenges.delete(nonce)
    if (!challenge || challenge.tenantId !== tenantId ||
        challenge.installationId !== installationId ||
        challenge.expiresAtMs <= nowMs) fail('INVALID_CHALLENGE')
    if (!Buffer.isBuffer(possessionSignature) || possessionSignature.length !== 64 ||
        !verify(null, renewalMessage(nonce, tenantId, installationId),
          tenant.installations.get(installationId), possessionSignature)) {
      fail('INSTALLATION_PROOF_FAILED')
    }
    return this.#issue(tenant, installationId, 'online', nowMs)
  }

  issueOfflineLease({ owner, tenantId, installationId, nowMs }) {
    this.#requireOwner(owner)
    const tenant = this.#activeInstallation(tenantId, installationId, nowMs)
    return this.#issue(tenant, installationId, 'offline', nowMs)
  }

  #issue(tenant, installationId, mode, nowMs) {
    const expiresAtMs = Math.min(nowMs + MAX_OFFLINE_LEASE_MS, tenant.expiresAtMs)
    const claims = {
      schemaVersion: 1,
      mode,
      leaseId: randomUUID(),
      tenantId: tenant.tenantId,
      installationId,
      issuedAt: new Date(nowMs).toISOString(),
      notBefore: new Date(nowMs).toISOString(),
      expiresAt: new Date(expiresAtMs).toISOString(),
      entitlementExpiresAt: new Date(tenant.expiresAtMs).toISOString(),
      revocationEpoch: tenant.revocationEpoch,
      seatLimit: tenant.seatLimit,
      maxInstallations: tenant.maxInstallations,
      modules: tenant.modules
    }
    const serialized = Buffer.from(JSON.stringify(claims), 'utf8').toString('base64url')
    const message = `RHL1.${this.#keyId}.${serialized}`
    const token = `${message}.${sign(null, Buffer.from(message, 'ascii'), this.#signingKey).toString('base64url')}`
    verifyTenantLicenseLease(token, {
      trustedKeys: new Map([[this.#keyId, this.#publicKey]]),
      tenantId: tenant.tenantId, installationId, nowMs,
      minimumRevocationEpoch: tenant.revocationEpoch,
      lastTrustedTimeMs: nowMs
    })
    this.#record('LEASE_ISSUED', tenant.tenantId,
      { leaseId: claims.leaseId, installationId, mode, expiresAt: claims.expiresAt }, nowMs)
    return token
  }

  readAudit({ owner }) {
    this.#requireOwner(owner)
    return this.#audit.map((entry) => Object.freeze({ ...entry }))
  }

  readStatus({ owner, tenantId }) {
    this.#requireOwner(owner)
    const tenant = this.#tenants.get(tenantId)
    if (!tenant) return null
    return Object.freeze({
      tenantId, status: tenant.status, revocationEpoch: tenant.revocationEpoch,
      seatLimit: tenant.seatLimit, maxInstallations: tenant.maxInstallations,
      installationCount: tenant.installations.size,
      expiresAt: new Date(tenant.expiresAtMs).toISOString()
    })
  }
}
