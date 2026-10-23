// L1 standalone owner licensing service: durable state and approval-controlled
// Ed25519 lease issuing. Not integrated with the existing clinic backend.
import {
  createHash, createPublicKey, createPrivateKey, generateKeyPairSync,
  randomBytes, randomUUID, sign, verify
} from 'node:crypto'
import {
  checkOwnerPassword, generateTotpSecret, hashOwnerPassword, totpAt, verifyTotp
} from './security.mjs'
import {
  enrollmentMessage, renewalMessage
} from '../scripts/tenant-license-owner-core.mjs'
import { MAX_OFFLINE_LEASE_MS, verifyTenantLicenseLease } from '../scripts/tenant-license-lease.mjs'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
const MOD = /^[a-z][a-z0-9_-]{0,31}$/
const SESSION_MS = 15 * 60_000
const STEP_UP_MS = 2 * 60_000
const CHALLENGE_MS = 5 * 60_000
const LOCK_MS = 15 * 60_000
const ATTEMPT_LIMIT = 5

export class LicensingServiceError extends Error {
  constructor(code) {
    super(code)
    this.name = 'LicensingServiceError'
    this.code = code
  }
}
function deny(code) { throw new LicensingServiceError(code) }
function validMs(value) {
  if (!Number.isSafeInteger(value) || value < 0) deny('INVALID_TIME')
}
function validUuid(value) {
  if (typeof value !== 'string' || !UUID.test(value)) deny('INVALID_UUID')
}
function validatePolicy({ seatLimit, maxInstallations, modules, expiresAtMs, nowMs }) {
  validMs(nowMs)
  validMs(expiresAtMs)
  if (expiresAtMs <= nowMs) deny('INVALID_EXPIRY')
  if (seatLimit !== null &&
    (!Number.isSafeInteger(seatLimit) || seatLimit < 1 || seatLimit > 2_147_483_647)) {
    deny('INVALID_SEATS')
  }
  if (!Number.isSafeInteger(maxInstallations) ||
    maxInstallations < 1 || maxInstallations > 100_000) deny('INVALID_INSTALLATIONS')
  if (!Array.isArray(modules) || modules.length < 1 || modules.length > 64 ||
    modules.some(m => typeof m !== 'string' || !MOD.test(m)) ||
    new Set(modules).size !== modules.length) deny('INVALID_MODULES')
}
function parseKey(pem) {
  if (typeof pem !== 'string' || pem.length > 1024) deny('INVALID_PUBLIC_KEY')
  try {
    const key = createPublicKey(pem)
    if (key.asymmetricKeyType !== 'ed25519') deny('INVALID_PUBLIC_KEY')
    return key
  } catch { deny('INVALID_PUBLIC_KEY') }
}
function fromUrl64(value) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{86}$/.test(value)) deny('INVALID_PROOF')
  const b = Buffer.from(value, 'base64url')
  if (b.length !== 64 || b.toString('base64url') !== value) deny('INVALID_PROOF')
  return b
}
function validateOrganization(name) {
  if (typeof name !== 'string' || name.trim().length < 2 ||
    name.length > 120 || /[\u0000-\u001f]/.test(name)) deny('INVALID_ORGANIZATION')
  return name.trim()
}
function validateContact(value, { required = false, max = 120 } = {}) {
  if (value === undefined || value === null || value === '') {
    if (required) deny('INVALID_CONTACT')
    return null
  }
  if (typeof value !== 'string' || value.trim().length < 2 || value.length > max || /[\u0000-\u001f]/.test(value)) {
    deny('INVALID_CONTACT')
  }
  return value.trim()
}
function validateEmail(value) {
  if (value === undefined || value === null || value === '') return null
  if (typeof value !== 'string' || value.length > 160 || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value)) deny('INVALID_EMAIL')
  return value.trim().toLowerCase()
}
const hashToken = value => createHash('sha256').update(value, 'utf8').digest('hex')
function ownerTotpUri(username, secret) {
  const issuer = 'Rehab Owner'
  return 'otpauth://totp/' + encodeURIComponent(issuer) + ':' + encodeURIComponent(username) +
    '?secret=' + secret + '&issuer=' + encodeURIComponent(issuer) +
    '&algorithm=SHA1&digits=6&period=30'
}

export class OwnerLicensingService {
  constructor(store, vault) {
    this.store = store
    this.vault = vault
    this.provisioning = {
      baseUrl: String(process.env.REHAB_MAIN_PROVISIONING_URL ?? '').replace(/\/$/, ''),
      token: process.env.REHAB_MAIN_PROVISIONING_TOKEN ?? '',
      planId: Number(process.env.REHAB_MAIN_PROVISIONING_PLAN_ID ?? 0),
      packageId: Number(process.env.REHAB_MAIN_PROVISIONING_PACKAGE_ID ?? 0),
      timeoutMs: Math.min(30_000, Math.max(1_000, Number(process.env.REHAB_MAIN_PROVISIONING_TIMEOUT_MS ?? 10_000)))
    }
  }
  get db() { return this.store.db }
  get hasOwner() { return Boolean(this.db.prepare('SELECT id FROM owner_identity WHERE id = 1').get()) }

  // Only callable from local, interactive, private provisioning CLI, NEVER the HTTP API.
  async provisionOwner({ username, password, nowMs }) {
    validMs(nowMs)
    if (typeof username !== 'string' || !/^[A-Za-z][A-Za-z0-9._-]{3,39}$/.test(username)) deny('INVALID_OWNER_USERNAME')
    if (this.hasOwner) deny('OWNER_ALREADY_PROVISIONED')
    const passwordHash = await hashOwnerPassword(password)
    const secret = generateTotpSecret()
    this.store.transaction(() => {
      if (this.hasOwner) deny('OWNER_ALREADY_PROVISIONED')
      this.db.prepare(`INSERT INTO owner_identity
        (id,username,password_hash,mfa_encrypted) VALUES(1,?,?,?)`).run(username,
        passwordHash, this.vault.encrypt(secret, 'owner-mfa-v1'))
      this.store.audit({ actor: username, action: 'OWNER_PROVISIONED',
        subject: 'owner', nowMs })
    })
    // Shown once to the owner by the *interactive* provisioning workflow only.
    return Object.freeze({ username, mfaSecret: secret })
  }

  async login({ username, password, totp, nowMs }) {
    validMs(nowMs)
    const owner = this.db.prepare('SELECT * FROM owner_identity WHERE id = 1').get()
    if (!owner || username !== owner.username || owner.lock_until > nowMs) {
      deny('INVALID_OWNER_CREDENTIALS')
    }
    const passwordOk = await checkOwnerPassword(password, owner.password_hash)
    const seed = this.vault.decrypt(owner.mfa_encrypted, 'owner-mfa-v1')
    const mfaStep = passwordOk ? verifyTotp(seed, totp, nowMs, owner.last_mfa_step) : null
    const result = this.store.transaction(() => {
      const latest = this.db.prepare('SELECT * FROM owner_identity WHERE id=1').get()
      if (latest.lock_until > nowMs || latest.username !== username) deny('INVALID_OWNER_CREDENTIALS')
      if (!passwordOk || mfaStep === null || mfaStep <= latest.last_mfa_step) {
        const failures = latest.failure_count + 1
        this.db.prepare('UPDATE owner_identity SET failure_count=?,lock_until=? WHERE id=1')
          .run(failures, failures >= ATTEMPT_LIMIT ? nowMs + LOCK_MS : 0)
        this.store.audit({ actor: 'unknown', action: 'OWNER_LOGIN_REJECTED',
          subject: 'owner', details: { attempt: failures }, nowMs })
        return { denied: true }
      }
      this.db.prepare(`UPDATE owner_identity
        SET last_mfa_step=?,failure_count=0,lock_until=0 WHERE id=1`).run(mfaStep)
      const token = randomBytes(32).toString('base64url')
      this.db.prepare(`INSERT INTO owner_sessions(token_hash,issued_at,expires_at,step_up_until)
        VALUES(?,?,?,?)`).run(hashToken(token), nowMs, nowMs + SESSION_MS,
        nowMs + STEP_UP_MS)
      this.store.audit({ actor: owner.username, action: 'OWNER_LOGIN',
        subject: 'owner', nowMs })
      return { token, expiresAt: new Date(nowMs + SESSION_MS).toISOString() }
    })
    if (result.denied) deny('INVALID_OWNER_CREDENTIALS')
    return result
  }

  async recoverMfa({ username, password, confirmation, nowMs }) {
    validMs(nowMs)
    if (confirmation !== 'ROTATE-MFA') deny('INVALID_RECOVERY_CONFIRMATION')
    const owner = this.db.prepare('SELECT * FROM owner_identity WHERE id = 1').get()
    if (!owner || username !== owner.username || owner.lock_until > nowMs) {
      deny('INVALID_OWNER_CREDENTIALS')
    }
    const passwordOk = await checkOwnerPassword(password, owner.password_hash)
    if (!passwordOk) {
      this.store.transaction(() => {
        const latest = this.db.prepare('SELECT failure_count FROM owner_identity WHERE id=1').get()
        const failures = latest.failure_count + 1
        this.db.prepare('UPDATE owner_identity SET failure_count=?,lock_until=? WHERE id=1')
          .run(failures, failures >= ATTEMPT_LIMIT ? nowMs + LOCK_MS : 0)
        this.store.audit({ actor: 'unknown', action: 'OWNER_MFA_RECOVERY_REJECTED',
          subject: 'owner', details: { attempt: failures }, nowMs })
      })
      deny('INVALID_OWNER_CREDENTIALS')
    }
    const secret = generateTotpSecret()
    this.store.transaction(() => {
      this.db.prepare('UPDATE owner_identity SET mfa_encrypted=?,last_mfa_step=-1,failure_count=0,lock_until=0 WHERE id=1')
        .run(this.vault.encrypt(secret, 'owner-mfa-v1'))
      this.db.prepare('DELETE FROM owner_sessions').run()
      this.store.audit({ actor: owner.username, action: 'OWNER_MFA_RECOVERED',
        subject: 'owner', details: { sessionsRevoked: true }, nowMs })
    })
    return {
      username: owner.username,
      secret,
      otpauthUrl: ownerTotpUri(owner.username, secret),
      revealExpiresAt: new Date(nowMs + 60_000).toISOString()
    }
  }

  requireOwner(token, nowMs) {
    validMs(nowMs)
    if (typeof token !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(token)) deny('OWNER_UNAUTHORIZED')
    const session = this.db.prepare('SELECT expires_at FROM owner_sessions WHERE token_hash=?')
      .get(hashToken(token))
    if (!session || session.expires_at <= nowMs) deny('OWNER_UNAUTHORIZED')
    return this.db.prepare('SELECT username FROM owner_identity WHERE id=1').get().username
  }

  requireStrongOwner(token, nowMs) {
    const actor = this.requireOwner(token, nowMs)
    const session = this.db.prepare('SELECT step_up_until FROM owner_sessions WHERE token_hash=?')
      .get(hashToken(token))
    if (!session || session.step_up_until <= nowMs) deny('OWNER_STEP_UP_REQUIRED')
    return actor
  }

  stepUp({ token, totp, nowMs }) {
    const actor = this.requireOwner(token, nowMs)
    const owner = this.db.prepare('SELECT mfa_encrypted,last_mfa_step FROM owner_identity WHERE id=1').get()
    const seed = this.vault.decrypt(owner.mfa_encrypted, 'owner-mfa-v1')
    const newStep = verifyTotp(seed, totp, nowMs, owner.last_mfa_step)
    const outcome = this.store.transaction(() => {
      const latest = this.db.prepare('SELECT last_mfa_step FROM owner_identity WHERE id=1').get()
      if (newStep === null || newStep <= latest.last_mfa_step) {
        const current = this.db.prepare('SELECT mfa_failure_count FROM owner_sessions WHERE token_hash=?')
          .get(hashToken(token))
        if (!current) return { denied: true }
        const count = current.mfa_failure_count + 1
        if (count >= ATTEMPT_LIMIT) {
          this.db.prepare('DELETE FROM owner_sessions WHERE token_hash=?').run(hashToken(token))
        } else {
          this.db.prepare('UPDATE owner_sessions SET mfa_failure_count=? WHERE token_hash=?')
            .run(count, hashToken(token))
        }
        this.store.audit({ actor, action: 'OWNER_MFA_STEP_UP_REJECTED',
          subject: 'owner', details: { attempt: count }, nowMs })
        return { denied: true }
      }
      this.db.prepare('UPDATE owner_identity SET last_mfa_step=? WHERE id=1').run(newStep)
      this.db.prepare(`UPDATE owner_sessions
        SET step_up_until=MIN(expires_at,?),mfa_failure_count=0 WHERE token_hash=?`)
        .run(nowMs + STEP_UP_MS, hashToken(token))
      this.store.audit({ actor, action: 'OWNER_MFA_STEP_UP', subject: 'owner', nowMs })
      return { stepUpUntil: new Date(Math.min(nowMs + STEP_UP_MS,
        this.db.prepare('SELECT expires_at FROM owner_sessions WHERE token_hash=?')
          .get(hashToken(token)).expires_at)).toISOString() }
    })
    if (outcome.denied) deny('INVALID_OWNER_MFA')
    return outcome
  }

  logout(token, nowMs) {
    const actor = this.requireOwner(token, nowMs)
    this.store.transaction(() => {
      this.db.prepare('DELETE FROM owner_sessions WHERE token_hash=?').run(hashToken(token))
      this.store.audit({ actor, action: 'OWNER_LOGOUT', subject: 'owner', nowMs })
    })
  }

  rotateKey({ token, nowMs }) {
    const actor = this.requireStrongOwner(token, nowMs)
    const { privateKey, publicKey } = generateKeyPairSync('ed25519')
    const kid = 'owner-' + randomUUID()
    const publicPem = publicKey.export({ type: 'spki', format: 'pem' })
    const privatePem = privateKey.export({ type: 'pkcs8', format: 'pem' })
    this.store.transaction(() => {
      this.db.prepare("UPDATE signing_keys SET status='VERIFY_ONLY' WHERE status='ACTIVE'").run()
      this.db.prepare(`INSERT INTO signing_keys
        (kid,public_pem,private_encrypted,status,created_at) VALUES(?,?,?,'ACTIVE',?)`)
        .run(kid, publicPem, this.vault.encrypt(privatePem, 'issuer-key:' + kid), nowMs)
      this.store.audit({ actor, action: 'SIGNER_ROTATED', subject: kid, nowMs })
    })
    return { kid, publicKeyPem: publicPem }
  }

  retireVerificationKey({ token, kid, nowMs }) {
    const actor = this.requireStrongOwner(token, nowMs)
    this.store.transaction(() => {
      const key = this.db.prepare('SELECT status FROM signing_keys WHERE kid=?').get(kid)
      if (!key || key.status !== 'VERIFY_ONLY') deny('KEY_NOT_ELIGIBLE')
      // Even if no leases are currently held in this store, need a complete
      // validity window since rotation for offline-issued files.
      const newest = this.db.prepare(`SELECT MAX(expires_at) AS until_ms FROM license_issues WHERE kid=?`)
        .get(kid).until_ms ?? 0
      if (newest > nowMs) deny('KEY_STILL_IN_USE')
      this.db.prepare("UPDATE signing_keys SET status='RETIRED',private_encrypted='retired' WHERE kid=?").run(kid)
      this.store.audit({ actor, action: 'SIGNER_RETIRED', subject: kid, nowMs })
    })
  }

  emergencyRevokeSigner({ token, kid, nowMs }) {
    const actor = this.requireStrongOwner(token, nowMs)
    return this.store.transaction(() => {
      const key = this.db.prepare('SELECT status FROM signing_keys WHERE kid=?').get(kid)
      if (!key || key.status === 'RETIRED') deny('KEY_NOT_ELIGIBLE')
      // Emergency revocation intentionally fails closed for all new issuance
      // until an uncompromised replacement ACTIVE signer is provisioned.
      this.db.prepare("UPDATE signing_keys SET status='RETIRED',private_encrypted='retired' WHERE kid=?")
        .run(kid)
      this.store.audit({ actor, action: 'SIGNER_EMERGENCY_REVOKED',
        subject: kid, nowMs })
      return { kid, status: 'RETIRED', requiresFreshClientTrustUpdate: true }
    })
  }

  trustedIssuerKeys() {
    const rows = this.db.prepare(`SELECT kid,public_pem,status FROM signing_keys
      WHERE status IN ('ACTIVE','VERIFY_ONLY') ORDER BY created_at,kid`).all()
    return rows.map(row => Object.freeze({ kid: row.kid, publicKeyPem: row.public_pem }))
  }

  submitApplication({ tenantId, installationId, installationPublicKeyPem, possessionSignature,
    organization, nowMs }) {
    validUuid(tenantId)
    validUuid(installationId)
    validMs(nowMs)
    const name = validateOrganization(organization)
    const key = parseKey(installationPublicKeyPem)
    if (!verify(null, enrollmentMessage(tenantId, installationId),
      key, fromUrl64(possessionSignature))) deny('INSTALLATION_PROOF_FAILED')
    const applicationId = randomUUID()
    this.store.transaction(() => {
      if (this.db.prepare('SELECT id FROM applications WHERE tenant_uuid=? OR installation_uuid=?')
        .get(tenantId, installationId)) deny('APPLICATION_ALREADY_EXISTS')
      this.db.prepare(`INSERT INTO applications
        (id,tenant_uuid,installation_uuid,installation_public_pem,organization,status,submitted_at)
        VALUES(?,?,?,?,?,'PENDING',?)`).run(applicationId, tenantId, installationId,
        installationPublicKeyPem, name, nowMs)
      this.store.audit({ actor: 'installation', action: 'APPLICATION_SUBMITTED',
        subject: tenantId, details: { applicationId, installationId }, nowMs })
    })
    return { applicationId, status: 'PENDING' }
  }

  submitPublicApplication({ organization, contactName, contactPhone, contactEmail, region, note, nowMs }) {
    validMs(nowMs)
    const name = validateOrganization(organization)
    const contact = validateContact(contactName, { required: true, max: 80 })
    // Public intake is intentionally limited to three required fields.
    // Older callers may still supply a telephone number; it is optional.
    const phone = validateContact(contactPhone, { max: 40 })
    const email = validateEmail(contactEmail)
    const area = validateContact(region, { max: 80 })
    const memo = validateContact(note, { max: 500 })
    const tenantId = randomUUID()
    const installationId = randomUUID()
    const { publicKey } = generateKeyPairSync('ed25519')
    const publicPem = publicKey.export({ type: 'spki', format: 'pem' })
    const applicationId = randomUUID()
    this.store.transaction(() => {
      this.db.prepare(`INSERT INTO applications
        (id,tenant_uuid,installation_uuid,installation_public_pem,organization,status,submitted_at,
         source,contact_name,contact_phone,contact_email,region,note)
        VALUES(?,?,?,?,?,'PENDING',?,'public',?,?,?,?,?)`).run(applicationId, tenantId, installationId,
        publicPem, name, nowMs, contact, phone, email, area, memo)
      this.store.audit({ actor: 'public-registration', action: 'PUBLIC_APPLICATION_SUBMITTED',
        subject: tenantId, details: { applicationId, organization: name, region: area }, nowMs })
    })
    return { applicationId, tenantId, status: 'PENDING', message: '申请已提交，等待软件拥有者审核' }
  }

  applicationList({ token, nowMs }) {
    this.requireOwner(token, nowMs)
    return this.db.prepare(`SELECT id,tenant_uuid AS tenantId,installation_uuid AS installationId,
      organization,status,submitted_at AS submittedAt,decision_at AS decisionAt,
      decision_reason AS decisionReason,source,contact_name AS contactName,
      contact_phone AS contactPhone,contact_email AS contactEmail,region,note,
      provisioning_status AS provisioningStatus,provisioning_id AS provisioningId,
      provisioning_error AS provisioningError,main_tenant_id AS mainTenantId
      FROM applications ORDER BY submitted_at,id`).all()
  }

  decideApplication({ token, applicationId, decision, reason = '', policy, credentials = {}, nowMs }) {
    const actor = this.requireStrongOwner(token, nowMs)
    if (!['APPROVED', 'REJECTED', 'NEEDS_INFO'].includes(decision)) deny('INVALID_DECISION')
    if (typeof reason !== 'string' || reason.length > 240) deny('INVALID_REASON')
    if (decision === 'APPROVED') validatePolicy({ ...policy, nowMs })
    const app = this.store.transaction(() => {
      const app = this.db.prepare('SELECT * FROM applications WHERE id=?').get(applicationId)
      if (!app || !['PENDING', 'NEEDS_INFO'].includes(app.status)) deny('APPLICATION_NOT_PENDING')
      this.db.prepare(`UPDATE applications SET status=?,decision_at=?,decision_reason=? WHERE id=?`)
        .run(decision, nowMs, reason, applicationId)
      this.db.prepare(`UPDATE applications SET provisioning_status=? WHERE id=?`)
        .run(decision === 'APPROVED' ? (this.provisioningConfigured() ? 'PROVISIONING' : 'NOT_CONFIGURED') : 'NOT_CONFIGURED', applicationId)
      if (decision === 'APPROVED') {
        this.db.prepare(`INSERT INTO entitlements
          (tenant_uuid,expires_at,seat_limit,max_installations,modules_json,revocation_epoch,status,updated_at)
          VALUES(?,?,?,?,?,0,'ACTIVE',?)`).run(app.tenant_uuid, policy.expiresAtMs,
          policy.seatLimit, policy.maxInstallations,
          JSON.stringify([...policy.modules].sort()), nowMs)
        this.db.prepare(`INSERT INTO installations
          (installation_uuid,tenant_uuid,public_pem,status,added_at)
          VALUES(?,?,?,'ACTIVE',?)`).run(app.installation_uuid, app.tenant_uuid,
          app.installation_public_pem, nowMs)
      }
      this.store.audit({ actor, action: 'APPLICATION_' + decision,
        subject: app.tenant_uuid,
        details: {
          applicationId, reason,
          ...(decision === 'APPROVED' ? {
            expiresAtMs: policy.expiresAtMs, seatLimit: policy.seatLimit,
            maxInstallations: policy.maxInstallations, modules: [...policy.modules].sort()
          } : {})
        }, nowMs })
      return this.db.prepare('SELECT * FROM applications WHERE id=?').get(applicationId)
    })
    if (decision === 'APPROVED' && this.provisioningConfigured()) {
      void this.#provisionMain(app, policy, credentials, nowMs)
      return { applicationId, status: decision, provisioning: 'PROVISIONING' }
    }
    return { applicationId, status: decision, provisioning: app.provisioning_status }
  }

  provisioningConfigured() {
    return Boolean(this.provisioning.baseUrl && this.provisioning.token &&
      Number.isSafeInteger(this.provisioning.planId) && this.provisioning.planId > 0 &&
      Number.isSafeInteger(this.provisioning.packageId) && this.provisioning.packageId > 0)
  }

  #provisionMain(app, policy, credentials, nowMs) {
    const username = typeof credentials?.username === 'string' ? credentials.username.trim() : ''
    const password = typeof credentials?.password === 'string' ? credentials.password : ''
    if (!/^[a-zA-Z0-9]{4,30}$/.test(username) || password.length < 12 || password.length > 16) {
      const error = '初始管理员账号或密码不符合要求（密码仅转发，不写入控制面数据库）'
      this.db.prepare("UPDATE applications SET provisioning_status='FAILED',provisioning_error=? WHERE id=?").run(error, app.id)
      return Promise.resolve({ status: 'FAILED', error })
    }
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), this.provisioning.timeoutMs)
    const body = {
      idempotencyKey: app.id,
      tenantUuid: app.tenant_uuid,
      organizationName: app.organization,
      contactName: app.contact_name || '机构管理员',
      contactMobile: app.contact_phone,
      planId: this.provisioning.planId,
      packageId: this.provisioning.packageId,
      // The main system uses its global epoch-millis LocalDateTime contract.
      expiresAt: policy.expiresAtMs,
      adminUsername: username,
      adminPassword: password
    }
    return fetch(`${this.provisioning.baseUrl}/admin-api/system/saas/internal/provisioning`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-rehab-provisioning-token': this.provisioning.token },
      body: JSON.stringify(body), signal: controller.signal
    }).then(async response => {
      const data = await response.json().catch(() => ({}))
      const result = data?.data
      if (!response.ok ||
        (typeof data?.code === 'number' && data.code !== 0) ||
        !result || result.status === 'FAILED') {
        throw new Error(result?.failure_reason || data?.msg || data?.message ||
          `主系统返回 HTTP ${response.status}`)
      }
      this.db.prepare("UPDATE applications SET provisioning_status='ACTIVE',provisioning_id=?,provisioning_error=NULL,main_tenant_id=? WHERE id=?")
        .run(String(result.tenant_uuid || app.tenant_uuid), result.tenant_id ?? null, app.id)
      this.store.audit({ actor: this.db.prepare('SELECT username FROM owner_identity WHERE id=1').get().username,
        action: 'APPLICATION_PROVISIONED', subject: app.tenant_uuid,
        details: { applicationId: app.id, mainTenantId: result.tenant_id ?? null }, nowMs })
      return { status: 'ACTIVE', tenantId: result.tenant_id ?? null, tenantUuid: result.tenant_uuid ?? app.tenant_uuid }
    }).catch(error => {
      const message = error?.name === 'AbortError' ? '主系统开通请求超时' : String(error?.message || error).slice(0, 500)
      this.db.prepare("UPDATE applications SET provisioning_status='FAILED',provisioning_error=? WHERE id=?").run(message, app.id)
      return { status: 'FAILED', error: message }
    }).finally(() => clearTimeout(timer))
  }

  retryProvision({ token, applicationId, credentials = {}, nowMs }) {
    this.requireStrongOwner(token, nowMs)
    if (!this.provisioningConfigured()) deny('PROVISIONING_NOT_CONFIGURED')
    const app = this.db.prepare('SELECT * FROM applications WHERE id=?').get(applicationId)
    if (!app || app.status !== 'APPROVED') deny('APPLICATION_NOT_APPROVED')
    this.db.prepare("UPDATE applications SET provisioning_status='PROVISIONING',provisioning_error=NULL WHERE id=?").run(applicationId)
    const policy = this.db.prepare('SELECT expires_at FROM entitlements WHERE tenant_uuid=?').get(app.tenant_uuid)
    if (!policy) deny('ENTITLEMENT_NOT_FOUND')
    void this.#provisionMain(app, { expiresAtMs: policy.expires_at }, credentials, nowMs)
    return { applicationId, status: 'PROVISIONING' }
  }

  #active(tenantId, installationId, nowMs) {
    validUuid(tenantId)
    validUuid(installationId)
    validMs(nowMs)
    const entitlement = this.db.prepare('SELECT * FROM entitlements WHERE tenant_uuid=?').get(tenantId)
    if (!entitlement || entitlement.status !== 'ACTIVE') deny('TENANT_NOT_APPROVED')
    if (entitlement.expires_at <= nowMs) deny('ENTITLEMENT_EXPIRED')
    const installation = this.db.prepare(`SELECT public_pem FROM installations
      WHERE installation_uuid=? AND tenant_uuid=? AND status='ACTIVE'`)
      .get(installationId, tenantId)
    if (!installation) deny('INSTALLATION_NOT_APPROVED')
    return { entitlement, installation }
  }

  challenge({ tenantId, installationId, nowMs }) {
    this.#active(tenantId, installationId, nowMs)
    const nonce = randomBytes(32).toString('base64url')
    this.store.transaction(() => {
      this.db.prepare('DELETE FROM renewal_challenges WHERE expires_at <= ?').run(nowMs)
      const size = this.db.prepare('SELECT COUNT(*) AS n FROM renewal_challenges').get().n
      if (size >= 1024) deny('CHALLENGE_CAPACITY_EXHAUSTED')
      this.db.prepare(`INSERT INTO renewal_challenges
        (nonce_hash,tenant_uuid,installation_uuid,expires_at) VALUES(?,?,?,?)`)
        .run(hashToken(nonce), tenantId, installationId, nowMs + CHALLENGE_MS)
    })
    return { nonce, expiresAt: new Date(nowMs + CHALLENGE_MS).toISOString() }
  }

  issueOnline({ tenantId, installationId, nonce, possessionSignature, nowMs }) {
    const { entitlement, installation } = this.#active(tenantId, installationId, nowMs)
    if (typeof nonce !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(nonce)) deny('INVALID_CHALLENGE')
    // Consume once in a committed transaction; rejected proofs also burn nonce.
    const challenge = this.store.transaction(() => {
      const challenge = this.db.prepare('SELECT * FROM renewal_challenges WHERE nonce_hash=?')
        .get(hashToken(nonce))
      this.db.prepare('DELETE FROM renewal_challenges WHERE nonce_hash=?').run(hashToken(nonce))
      return challenge
    })
    if (!challenge || challenge.tenant_uuid !== tenantId ||
      challenge.installation_uuid !== installationId ||
      challenge.expires_at <= nowMs) deny('INVALID_CHALLENGE')
    if (!verify(null, renewalMessage(nonce, tenantId, installationId),
      parseKey(installation.public_pem), fromUrl64(possessionSignature))) {
      deny('INSTALLATION_PROOF_FAILED')
    }
    return this.store.transaction(() =>
      this.#issue({ entitlement, tenantId, installationId, nowMs, mode: 'online', actor: 'installation' }))
  }

  issueOffline({ token, tenantId, installationId, nowMs }) {
    const actor = this.requireStrongOwner(token, nowMs)
    const { entitlement } = this.#active(tenantId, installationId, nowMs)
    return this.store.transaction(() => {
      const issued = this.#issue({ entitlement, tenantId, installationId, nowMs, mode: 'offline', actor })
      return {
        ...issued,
        // Owner may save this JSON object as a .rehab-license file and deliver
        // it through an independently authenticated channel to the studio.
        licenseFile: {
          schemaVersion: 1, tenantId, installationId,
          token: issued.token, statusProof: issued.statusProof
        }
      }
    })
  }

  #issue({ entitlement, tenantId, installationId, nowMs, mode, actor }) {
    const key = this.db.prepare("SELECT * FROM signing_keys WHERE status='ACTIVE'").get()
    if (!key) deny('NO_ACTIVE_SIGNING_KEY')
    const leaseId = randomUUID()
    const expiresAt = Math.min(entitlement.expires_at, nowMs + MAX_OFFLINE_LEASE_MS)
    const claims = {
      schemaVersion: 1, mode, leaseId, tenantId, installationId,
      issuedAt: new Date(nowMs).toISOString(),
      notBefore: new Date(nowMs).toISOString(),
      expiresAt: new Date(expiresAt).toISOString(),
      entitlementExpiresAt: new Date(entitlement.expires_at).toISOString(),
      revocationEpoch: entitlement.revocation_epoch,
      seatLimit: entitlement.seat_limit,
      maxInstallations: entitlement.max_installations,
      modules: JSON.parse(entitlement.modules_json)
    }
    const payload = Buffer.from(JSON.stringify(claims)).toString('base64url')
    const signedBytes = Buffer.from(`RHL1.${key.kid}.${payload}`, 'ascii')
    const signer = createPrivateKey(this.vault.decrypt(key.private_encrypted, 'issuer-key:' + key.kid))
    const token = `${signedBytes.toString('ascii')}.${sign(null, signedBytes, signer).toString('base64url')}`
    verifyTenantLicenseLease(token, {
      trustedKeys: new Map([[key.kid, parseKey(key.public_pem)]]),
      tenantId, installationId, nowMs,
      minimumRevocationEpoch: entitlement.revocation_epoch, lastTrustedTimeMs: nowMs
    })
    this.db.prepare(`INSERT INTO license_issues
      (lease_uuid,tenant_uuid,installation_uuid,mode,kid,issued_at,expires_at)
      VALUES(?,?,?,?,?,?,?)`).run(leaseId, tenantId, installationId, mode,
      key.kid, nowMs, expiresAt)
    this.store.audit({ actor, action: 'LEASE_ISSUED', subject: tenantId,
      details: { leaseId, installationId, kid: key.kid, mode, expiresAt }, nowMs })
    return {
      token, expiresAt: claims.expiresAt, leaseId,
      statusProof: this.issueStatusProof({ tenantId, installationId, nowMs })
    }
  }

  // Publicly verifiable owner status, never a bearer credential. An offline
  // terminal can act on its last status proof for at most 72h.
  issueStatusProof({ tenantId, installationId, nowMs }) {
    validUuid(tenantId)
    validUuid(installationId)
    validMs(nowMs)
    const tenant = this.db.prepare('SELECT * FROM entitlements WHERE tenant_uuid=?')
      .get(tenantId)
    const device = this.db.prepare('SELECT status FROM installations WHERE tenant_uuid=? AND installation_uuid=?')
      .get(tenantId, installationId)
    if (!tenant || !device) deny('UNKNOWN_INSTALLATION')
    const key = this.db.prepare("SELECT * FROM signing_keys WHERE status='ACTIVE'").get()
    if (!key) deny('NO_ACTIVE_SIGNING_KEY')
    const status = device.status !== 'ACTIVE' ? 'REPLACED'
      : tenant.status !== 'ACTIVE' ? tenant.status
        : tenant.expires_at <= nowMs ? 'EXPIRED' : 'ACTIVE'
    const claims = {
      schemaVersion: 1, tenantId, installationId, status,
      revocationEpoch: tenant.revocation_epoch,
      observedAt: new Date(nowMs).toISOString(),
      expiresAt: new Date(nowMs + MAX_OFFLINE_LEASE_MS).toISOString()
    }
    const encoded = Buffer.from(JSON.stringify(claims)).toString('base64url')
    const prefix = `RHR1.${key.kid}.${encoded}`
    const signer = createPrivateKey(this.vault.decrypt(key.private_encrypted, 'issuer-key:' + key.kid))
    return `${prefix}.${sign(null, Buffer.from(prefix, 'ascii'), signer).toString('base64url')}`
  }

  updateEntitlement({ token, tenantId, policy, reason = '', nowMs }) {
    const actor = this.requireStrongOwner(token, nowMs)
    validUuid(tenantId)
    validatePolicy({ ...policy, nowMs })
    if (typeof reason !== 'string' || reason.length > 240) deny('INVALID_REASON')
    const auditReason = reason.trim()
    return this.store.transaction(() => {
      const entitlement = this.db.prepare('SELECT * FROM entitlements WHERE tenant_uuid=?').get(tenantId)
      if (!entitlement || entitlement.status !== 'ACTIVE') deny('TENANT_NOT_APPROVED')
      const newEpoch = entitlement.revocation_epoch + 1
      this.db.prepare(`UPDATE entitlements SET expires_at=?,seat_limit=?,max_installations=?,
        modules_json=?,revocation_epoch=?,updated_at=? WHERE tenant_uuid=?`)
        .run(policy.expiresAtMs, policy.seatLimit, policy.maxInstallations,
          JSON.stringify([...policy.modules].sort()), newEpoch, nowMs, tenantId)
      this.db.prepare('DELETE FROM renewal_challenges WHERE tenant_uuid=?').run(tenantId)
      this.store.audit({ actor, action: 'ENTITLEMENT_UPDATED', subject: tenantId,
        details: { revocationEpoch: newEpoch, ...policy,
          ...(auditReason ? { reason: auditReason } : {}) }, nowMs })
      return { tenantId, revocationEpoch: newEpoch }
    })
  }

  setTenantStatus({ token, tenantId, status, nowMs }) {
    const actor = this.requireStrongOwner(token, nowMs)
    validUuid(tenantId)
    if (!['ACTIVE', 'SUSPENDED', 'REVOKED'].includes(status)) deny('INVALID_STATUS')
    return this.store.transaction(() => {
      const existing = this.db.prepare('SELECT * FROM entitlements WHERE tenant_uuid=?').get(tenantId)
      if (!existing) deny('UNKNOWN_TENANT')
      if (existing.status === 'REVOKED' && status !== 'REVOKED') deny('REVOKED_IS_FINAL')
      if (existing.status === status) deny('NO_STATUS_CHANGE')
      const epoch = existing.revocation_epoch + 1
      this.db.prepare('UPDATE entitlements SET status=?,revocation_epoch=?,updated_at=? WHERE tenant_uuid=?')
        .run(status, epoch, nowMs, tenantId)
      this.db.prepare('DELETE FROM renewal_challenges WHERE tenant_uuid=?').run(tenantId)
      this.store.audit({ actor, action: 'TENANT_' + status, subject: tenantId,
        details: { previous: existing.status, epoch }, nowMs })
      return { tenantId, status, revocationEpoch: epoch }
    })
  }

  // No unapproved installation registration through this path: an owner has
  // explicitly verified the new public key proof. Replacement invalidates
  // outstanding leases and old device sessions once revocation snapshot is refreshed.
  replaceInstallation({ token, tenantId, oldInstallationId, newInstallationId,
    newPublicKeyPem, proof, nowMs }) {
    const actor = this.requireStrongOwner(token, nowMs)
    validUuid(tenantId)
    validUuid(oldInstallationId)
    validUuid(newInstallationId)
    const newKey = parseKey(newPublicKeyPem)
    if (!verify(null, enrollmentMessage(tenantId, newInstallationId),
      newKey, fromUrl64(proof))) deny('INSTALLATION_PROOF_FAILED')
    return this.store.transaction(() => {
      const old = this.#active(tenantId, oldInstallationId, nowMs)
      if (this.db.prepare('SELECT installation_uuid FROM installations WHERE installation_uuid=?')
        .get(newInstallationId)) deny('INSTALLATION_ALREADY_REGISTERED')
      this.db.prepare("UPDATE installations SET status='REPLACED' WHERE installation_uuid=?")
        .run(oldInstallationId)
      this.db.prepare(`INSERT INTO installations
        (installation_uuid,tenant_uuid,public_pem,status,added_at) VALUES(?,?,?,'ACTIVE',?)`)
        .run(newInstallationId, tenantId, newPublicKeyPem, nowMs)
      const epoch = old.entitlement.revocation_epoch + 1
      this.db.prepare('UPDATE entitlements SET revocation_epoch=?,updated_at=? WHERE tenant_uuid=?')
        .run(epoch, nowMs, tenantId)
      this.db.prepare('DELETE FROM renewal_challenges WHERE tenant_uuid=?').run(tenantId)
      this.store.audit({ actor, action: 'INSTALLATION_REPLACED', subject: tenantId,
        details: { oldInstallationId, newInstallationId, epoch }, nowMs })
      return { tenantId, revocationEpoch: epoch }
    })
  }

  status({ token, tenantId, nowMs }) {
    this.requireOwner(token, nowMs)
    validUuid(tenantId)
    const entitlement = this.db.prepare('SELECT * FROM entitlements WHERE tenant_uuid=?').get(tenantId)
    if (!entitlement) return null
    return {
      tenantId, status: entitlement.status,
      expiresAt: new Date(entitlement.expires_at).toISOString(),
      seatLimit: entitlement.seat_limit, maxInstallations: entitlement.max_installations,
      modules: JSON.parse(entitlement.modules_json),
      revocationEpoch: entitlement.revocation_epoch
    }
  }

  audit({ token, nowMs, limit = 100 }) {
    this.requireOwner(token, nowMs)
    if (!Number.isInteger(limit) || limit < 1 || limit > 500) deny('INVALID_LIMIT')
    return this.db.prepare(`SELECT event_uuid AS eventId,actor,action,subject,
      details_json AS details,occurred_at AS occurredAt,prev_hash AS prevHash,
      chain_hash AS chainHash FROM audit_events ORDER BY seq DESC LIMIT ?`)
      .all(limit).map(entry => ({ ...entry, details: JSON.parse(entry.details) }))
  }

  signedAuditCheckpoint({ token, nowMs }) {
    const actor = this.requireStrongOwner(token, nowMs)
    return this.store.transaction(() => {
      const signer = this.db.prepare("SELECT * FROM signing_keys WHERE status='ACTIVE'").get()
      if (!signer) deny('NO_ACTIVE_SIGNING_KEY')
      const row = this.db.prepare('SELECT seq,chain_hash FROM audit_events ORDER BY seq DESC LIMIT 1').get()
      const payload = {
        protocol: 'RHA1', sequence: row?.seq ?? 0,
        headHash: row?.chain_hash ?? '0'.repeat(64),
        witnessedAt: new Date(nowMs).toISOString()
      }
      const body = Buffer.from(JSON.stringify(payload)).toString('base64url')
      const signedBytes = Buffer.from(`RHA1.${signer.kid}.${body}`, 'ascii')
      const privateKey = createPrivateKey(this.vault.decrypt(signer.private_encrypted,
        'issuer-key:' + signer.kid))
      const checkpoint = `${signedBytes.toString('ascii')}.${sign(null,
        signedBytes, privateKey).toString('base64url')}`
      this.store.audit({ actor, action: 'AUDIT_CHECKPOINT_CREATED', subject: 'audit',
        details: { sequence: payload.sequence, headHash: payload.headHash }, nowMs })
      // Operator must anchor a copy in an independent WORM/log retention system.
      return { checkpoint, ...payload, kid: signer.kid }
    })
  }
}