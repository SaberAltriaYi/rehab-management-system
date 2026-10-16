1: // L1 standalone owner licensing service: durable state and approval-controlled
2: // Ed25519 lease issuing. Not integrated with the existing clinic backend.
3: import {
4:   createHash, createPublicKey, createPrivateKey, generateKeyPairSync,
5:   randomBytes, randomUUID, sign, verify
6: } from 'node:crypto'
7: import {
8:   checkOwnerPassword, generateTotpSecret, hashOwnerPassword, totpAt, verifyTotp
9: } from './security.mjs'
10: import {
11:   enrollmentMessage, renewalMessage
12: } from '../scripts/tenant-license-owner-core.mjs'
13: import { MAX_OFFLINE_LEASE_MS, verifyTenantLicenseLease } from '../scripts/tenant-license-lease.mjs'
14: 
15: const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
16: const MOD = /^[a-z][a-z0-9_-]{0,31}$/
17: const SESSION_MS = 15 * 60_000
18: const STEP_UP_MS = 2 * 60_000
19: const CHALLENGE_MS = 5 * 60_000
20: const LOCK_MS = 15 * 60_000
21: const ATTEMPT_LIMIT = 5
22: 
23: export class LicensingServiceError extends Error {
24:   constructor(code) {
25:     super(code)
26:     this.name = 'LicensingServiceError'
27:     this.code = code
28:   }
29: }
30: function deny(code) { throw new LicensingServiceError(code) }
31: function validMs(value) {
32:   if (!Number.isSafeInteger(value) || value < 0) deny('INVALID_TIME')
33: }
34: function validUuid(value) {
35:   if (typeof value !== 'string' || !UUID.test(value)) deny('INVALID_UUID')
36: }
37: function validatePolicy({ seatLimit, maxInstallations, modules, expiresAtMs, nowMs }) {
38:   validMs(nowMs)
39:   validMs(expiresAtMs)
40:   if (expiresAtMs <= nowMs) deny('INVALID_EXPIRY')
41:   if (seatLimit !== null &&
42:     (!Number.isSafeInteger(seatLimit) || seatLimit < 1 || seatLimit > 2_147_483_647)) {
43:     deny('INVALID_SEATS')
44:   }
45:   if (!Number.isSafeInteger(maxInstallations) ||
46:     maxInstallations < 1 || maxInstallations > 100_000) deny('INVALID_INSTALLATIONS')
47:   if (!Array.isArray(modules) || modules.length < 1 || modules.length > 64 ||
48:     modules.some(m => typeof m !== 'string' || !MOD.test(m)) ||
49:     new Set(modules).size !== modules.length) deny('INVALID_MODULES')
50: }
51: function parseKey(pem) {
52:   if (typeof pem !== 'string' || pem.length > 1024) deny('INVALID_PUBLIC_KEY')
53:   try {
54:     const key = createPublicKey(pem)
55:     if (key.asymmetricKeyType !== 'ed25519') deny('INVALID_PUBLIC_KEY')
56:     return key
57:   } catch { deny('INVALID_PUBLIC_KEY') }
58: }
59: function fromUrl64(value) {
60:   if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{86}$/.test(value)) deny('INVALID_PROOF')
61:   const b = Buffer.from(value, 'base64url')
62:   if (b.length !== 64 || b.toString('base64url') !== value) deny('INVALID_PROOF')
63:   return b
64: }
65: function validateOrganization(name) {
66:   if (typeof name !== 'string' || name.trim().length < 2 ||
67:     name.length > 120 || /[\u0000-\u001f]/.test(name)) deny('INVALID_ORGANIZATION')
68:   return name.trim()
69: }
70: function validateContact(value, { required = false, max = 120 } = {}) {
71:   if (value === undefined || value === null || value === '') {
72:     if (required) deny('INVALID_CONTACT')
73:     return null
74:   }
75:   if (typeof value !== 'string' || value.trim().length < 2 || value.length > max || /[\u0000-\u001f]/.test(value)) {
76:     deny('INVALID_CONTACT')
77:   }
78:   return value.trim()
79: }
80: function validateEmail(value) {
81:   if (value === undefined || value === null || value === '') return null
82:   if (typeof value !== 'string' || value.length > 160 || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value)) deny('INVALID_EMAIL')
83:   return value.trim().toLowerCase()
84: }
85: const hashToken = value => createHash('sha256').update(value, 'utf8').digest('hex')
86: 
87: export class OwnerLicensingService {
88:   constructor(store, vault) {
89:     this.store = store
90:     this.vault = vault
91:     this.provisioning = {
92:       baseUrl: String(process.env.REHAB_MAIN_PROVISIONING_URL ?? '').replace(/\/$/, ''),
93:       token: process.env.REHAB_MAIN_PROVISIONING_TOKEN ?? '',
94:       planId: Number(process.env.REHAB_MAIN_PROVISIONING_PLAN_ID ?? 0),
95:       packageId: Number(process.env.REHAB_MAIN_PROVISIONING_PACKAGE_ID ?? 0),
96:       timeoutMs: Math.min(30_000, Math.max(1_000, Number(process.env.REHAB_MAIN_PROVISIONING_TIMEOUT_MS ?? 10_000)))
97:     }
98:   }
99:   get db() { return this.store.db }
100:   get hasOwner() { return Boolean(this.db.prepare('SELECT id FROM owner_identity WHERE id = 1').get()) }
101: 
102:   // Only callable from local, interactive, private provisioning CLI, NEVER the HTTP API.
103:   async provisionOwner({ username, password, nowMs }) {
104:     validMs(nowMs)
105:     if (typeof username !== 'string' || !/^[a-z][a-z0-9._-]{3,39}$/.test(username)) deny('INVALID_OWNER_USERNAME')
106:     if (this.hasOwner) deny('OWNER_ALREADY_PROVISIONED')
107:     const passwordHash = await hashOwnerPassword(password)
108:     const secret = generateTotpSecret()
109:     this.store.transaction(() => {
110:       if (this.hasOwner) deny('OWNER_ALREADY_PROVISIONED')
111:       this.db.prepare(`INSERT INTO owner_identity
112:         (id,username,password_hash,mfa_encrypted) VALUES(1,?,?,?)`).run(username,
113:         passwordHash, this.vault.encrypt(secret, 'owner-mfa-v1'))
114:       this.store.audit({ actor: username, action: 'OWNER_PROVISIONED',
115:         subject: 'owner', nowMs })
116:     })
117:     // Shown once to the owner by the *interactive* provisioning workflow only.
118:     return Object.freeze({ username, mfaSecret: secret })
119:   }
120: 
121:   async login({ username, password, totp, nowMs }) {
122:     validMs(nowMs)
123:     const owner = this.db.prepare('SELECT * FROM owner_identity WHERE id = 1').get()
124:     if (!owner || username !== owner.username || owner.lock_until > nowMs) {
125:       deny('INVALID_OWNER_CREDENTIALS')
126:     }
127:     const passwordOk = await checkOwnerPassword(password, owner.password_hash)
128:     const seed = this.vault.decrypt(owner.mfa_encrypted, 'owner-mfa-v1')
129:     const mfaStep = passwordOk ? verifyTotp(seed, totp, nowMs, owner.last_mfa_step) : null
130:     const result = this.store.transaction(() => {
131:       const latest = this.db.prepare('SELECT * FROM owner_identity WHERE id=1').get()
132:       if (latest.lock_until > nowMs || latest.username !== username) deny('INVALID_OWNER_CREDENTIALS')
133:       if (!passwordOk || mfaStep === null || mfaStep <= latest.last_mfa_step) {
134:         const failures = latest.failure_count + 1
135:         this.db.prepare('UPDATE owner_identity SET failure_count=?,lock_until=? WHERE id=1')
136:           .run(failures, failures >= ATTEMPT_LIMIT ? nowMs + LOCK_MS : 0)
137:         this.store.audit({ actor: 'unknown', action: 'OWNER_LOGIN_REJECTED',
138:           subject: 'owner', details: { attempt: failures }, nowMs })
139:         return { denied: true }
140:       }
141:       this.db.prepare(`UPDATE owner_identity
142:         SET last_mfa_step=?,failure_count=0,lock_until=0 WHERE id=1`).run(mfaStep)
143:       const token = randomBytes(32).toString('base64url')
144:       this.db.prepare(`INSERT INTO owner_sessions(token_hash,issued_at,expires_at,step_up_until)
145:         VALUES(?,?,?,?)`).run(hashToken(token), nowMs, nowMs + SESSION_MS,
146:         nowMs + STEP_UP_MS)
147:       this.store.audit({ actor: owner.username, action: 'OWNER_LOGIN',
148:         subject: 'owner', nowMs })
149:       return { token, expiresAt: new Date(nowMs + SESSION_MS).toISOString() }
150:     })
151:     if (result.denied) deny('INVALID_OWNER_CREDENTIALS')
152:     return result
153:   }
154: 
155:   requireOwner(token, nowMs) {
156:     validMs(nowMs)
157:     if (typeof token !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(token)) deny('OWNER_UNAUTHORIZED')
158:     const session = this.db.prepare('SELECT expires_at FROM owner_sessions WHERE token_hash=?')
159:       .get(hashToken(token))
160:     if (!session || session.expires_at <= nowMs) deny('OWNER_UNAUTHORIZED')
161:     return this.db.prepare('SELECT username FROM owner_identity WHERE id=1').get().username
162:   }
163: 
164:   requireStrongOwner(token, nowMs) {
165:     const actor = this.requireOwner(token, nowMs)
166:     const session = this.db.prepare('SELECT step_up_until FROM owner_sessions WHERE token_hash=?')
167:       .get(hashToken(token))
168:     if (!session || session.step_up_until <= nowMs) deny('OWNER_STEP_UP_REQUIRED')
169:     return actor
170:   }
171: 
172:   stepUp({ token, totp, nowMs }) {
173:     const actor = this.requireOwner(token, nowMs)
174:     const owner = this.db.prepare('SELECT mfa_encrypted,last_mfa_step FROM owner_identity WHERE id=1').get()
175:     const seed = this.vault.decrypt(owner.mfa_encrypted, 'owner-mfa-v1')
176:     const newStep = verifyTotp(seed, totp, nowMs, owner.last_mfa_step)
177:     const outcome = this.store.transaction(() => {
178:       const latest = this.db.prepare('SELECT last_mfa_step FROM owner_identity WHERE id=1').get()
179:       if (newStep === null || newStep <= latest.last_mfa_step) {
180:         const current = this.db.prepare('SELECT mfa_failure_count FROM owner_sessions WHERE token_hash=?')
181:           .get(hashToken(token))
182:         if (!current) return { denied: true }
183:         const count = current.mfa_failure_count + 1
184:         if (count >= ATTEMPT_LIMIT) {
185:           this.db.prepare('DELETE FROM owner_sessions WHERE token_hash=?').run(hashToken(token))
186:         } else {
187:           this.db.prepare('UPDATE owner_sessions SET mfa_failure_count=? WHERE token_hash=?')
188:             .run(count, hashToken(token))
189:         }
190:         this.store.audit({ actor, action: 'OWNER_MFA_STEP_UP_REJECTED',
191:           subject: 'owner', details: { attempt: count }, nowMs })
192:         return { denied: true }
193:       }
194:       this.db.prepare('UPDATE owner_identity SET last_mfa_step=? WHERE id=1').run(newStep)
195:       this.db.prepare(`UPDATE owner_sessions
196:         SET step_up_until=MIN(expires_at,?),mfa_failure_count=0 WHERE token_hash=?`)
197:         .run(nowMs + STEP_UP_MS, hashToken(token))
198:       this.store.audit({ actor, action: 'OWNER_MFA_STEP_UP', subject: 'owner', nowMs })
199:       return { stepUpUntil: new Date(Math.min(nowMs + STEP_UP_MS,
200:         this.db.prepare('SELECT expires_at FROM owner_sessions WHERE token_hash=?')
201:           .get(hashToken(token)).expires_at)).toISOString() }
202:     })
203:     if (outcome.denied) deny('INVALID_OWNER_MFA')
204:     return outcome
205:   }
206: 
207:   logout(token, nowMs) {
208:     const actor = this.requireOwner(token, nowMs)
209:     this.store.transaction(() => {
210:       this.db.prepare('DELETE FROM owner_sessions WHERE token_hash=?').run(hashToken(token))
211:       this.store.audit({ actor, action: 'OWNER_LOGOUT', subject: 'owner', nowMs })
212:     })
213:   }
214: 
215:   rotateKey({ token, nowMs }) {
216:     const actor = this.requireStrongOwner(token, nowMs)
217:     const { privateKey, publicKey } = generateKeyPairSync('ed25519')
218:     const kid = 'owner-' + randomUUID()
219:     const publicPem = publicKey.export({ type: 'spki', format: 'pem' })
220:     const privatePem = privateKey.export({ type: 'pkcs8', format: 'pem' })
221:     this.store.transaction(() => {
222:       this.db.prepare("UPDATE signing_keys SET status='VERIFY_ONLY' WHERE status='ACTIVE'").run()
223:       this.db.prepare(`INSERT INTO signing_keys
224:         (kid,public_pem,private_encrypted,status,created_at) VALUES(?,?,?,'ACTIVE',?)`)
225:         .run(kid, publicPem, this.vault.encrypt(privatePem, 'issuer-key:' + kid), nowMs)
226:       this.store.audit({ actor, action: 'SIGNER_ROTATED', subject: kid, nowMs })
227:     })
228:     return { kid, publicKeyPem: publicPem }
229:   }
230: 
231:   retireVerificationKey({ token, kid, nowMs }) {
232:     const actor = this.requireStrongOwner(token, nowMs)
233:     this.store.transaction(() => {
234:       const key = this.db.prepare('SELECT status FROM signing_keys WHERE kid=?').get(kid)
235:       if (!key || key.status !== 'VERIFY_ONLY') deny('KEY_NOT_ELIGIBLE')
236:       // Even if no leases are currently held in this store, need a complete
237:       // validity window since rotation for offline-issued files.
238:       const newest = this.db.prepare(`SELECT MAX(expires_at) AS until_ms FROM license_issues WHERE kid=?`)
239:         .get(kid).until_ms ?? 0
240:       if (newest > nowMs) deny('KEY_STILL_IN_USE')
241:       this.db.prepare("UPDATE signing_keys SET status='RETIRED',private_encrypted='retired' WHERE kid=?").run(kid)
242:       this.store.audit({ actor, action: 'SIGNER_RETIRED', subject: kid, nowMs })
243:     })
244:   }
245: 
246:   emergencyRevokeSigner({ token, kid, nowMs }) {
247:     const actor = this.requireStrongOwner(token, nowMs)
248:     return this.store.transaction(() => {
249:       const key = this.db.prepare('SELECT status FROM signing_keys WHERE kid=?').get(kid)
250:       if (!key || key.status === 'RETIRED') deny('KEY_NOT_ELIGIBLE')
251:       // Emergency revocation intentionally fails closed for all new issuance
252:       // until an uncompromised replacement ACTIVE signer is provisioned.
253:       this.db.prepare("UPDATE signing_keys SET status='RETIRED',private_encrypted='retired' WHERE kid=?")
254:         .run(kid)
255:       this.store.audit({ actor, action: 'SIGNER_EMERGENCY_REVOKED',
256:         subject: kid, nowMs })
257:       return { kid, status: 'RETIRED', requiresFreshClientTrustUpdate: true }
258:     })
259:   }
260: 
261:   trustedIssuerKeys() {
262:     const rows = this.db.prepare(`SELECT kid,public_pem,status FROM signing_keys
263:       WHERE status IN ('ACTIVE','VERIFY_ONLY') ORDER BY created_at,kid`).all()
264:     return rows.map(row => Object.freeze({ kid: row.kid, publicKeyPem: row.public_pem }))
265:   }
266: 
267:   submitApplication({ tenantId, installationId, installationPublicKeyPem, possessionSignature,
268:     organization, nowMs }) {
269:     validUuid(tenantId)
270:     validUuid(installationId)
271:     validMs(nowMs)
272:     const name = validateOrganization(organization)
273:     const key = parseKey(installationPublicKeyPem)
274:     if (!verify(null, enrollmentMessage(tenantId, installationId),
275:       key, fromUrl64(possessionSignature))) deny('INSTALLATION_PROOF_FAILED')
276:     const applicationId = randomUUID()
277:     this.store.transaction(() => {
278:       if (this.db.prepare('SELECT id FROM applications WHERE tenant_uuid=? OR installation_uuid=?')
279:         .get(tenantId, installationId)) deny('APPLICATION_ALREADY_EXISTS')
280:       this.db.prepare(`INSERT INTO applications
281:         (id,tenant_uuid,installation_uuid,installation_public_pem,organization,status,submitted_at)
282:         VALUES(?,?,?,?,?,'PENDING',?)`).run(applicationId, tenantId, installationId,
283:         installationPublicKeyPem, name, nowMs)
284:       this.store.audit({ actor: 'installation', action: 'APPLICATION_SUBMITTED',
285:         subject: tenantId, details: { applicationId, installationId }, nowMs })
286:     })
287:     return { applicationId, status: 'PENDING' }
288:   }
289: 
290:   submitPublicApplication({ organization, contactName, contactPhone, contactEmail, region, note, nowMs }) {
291:     validMs(nowMs)
292:     const name = validateOrganization(organization)
293:     const contact = validateContact(contactName, { required: true, max: 80 })
294:     const phone = validateContact(contactPhone, { required: true, max: 40 })
295:     const email = validateEmail(contactEmail)
296:     const area = validateContact(region, { max: 80 })
297:     const memo = validateContact(note, { max: 500 })
298:     const tenantId = randomUUID()
299:     const installationId = randomUUID()
300:     const { publicKey } = generateKeyPairSync('ed25519')
301:     const publicPem = publicKey.export({ type: 'spki', format: 'pem' })
302:     const applicationId = randomUUID()
303:     this.store.transaction(() => {
304:       this.db.prepare(`INSERT INTO applications
305:         (id,tenant_uuid,installation_uuid,installation_public_pem,organization,status,submitted_at,
306:          source,contact_name,contact_phone,contact_email,region,note)
307:         VALUES(?,?,?,?,?,'PENDING',?,'public',?,?,?,?,?)`).run(applicationId, tenantId, installationId,
308:         publicPem, name, nowMs, contact, phone, email, area, memo)
309:       this.store.audit({ actor: 'public-registration', action: 'PUBLIC_APPLICATION_SUBMITTED',
310:         subject: tenantId, details: { applicationId, organization: name, region: area }, nowMs })
311:     })
312:     return { applicationId, tenantId, status: 'PENDING', message: '申请已提交，等待软件拥有者审核' }
313:   }
314: 
315:   applicationList({ token, nowMs }) {
316:     this.requireOwner(token, nowMs)
317:     return this.db.prepare(`SELECT id,tenant_uuid AS tenantId,installation_uuid AS installationId,
318:       organization,status,submitted_at AS submittedAt,decision_at AS decisionAt,
319:       decision_reason AS decisionReason,source,contact_name AS contactName,
320:       contact_phone AS contactPhone,contact_email AS contactEmail,region,note,
321:       provisioning_status AS provisioningStatus,provisioning_id AS provisioningId,
322:       provisioning_error AS provisioningError,main_tenant_id AS mainTenantId
323:       FROM applications ORDER BY submitted_at,id`).all()
324:   }
325: 
326:   decideApplication({ token, applicationId, decision, reason = '', policy, credentials = {}, nowMs }) {
327:     const actor = this.requireStrongOwner(token, nowMs)
328:     if (!['APPROVED', 'REJECTED', 'NEEDS_INFO'].includes(decision)) deny('INVALID_DECISION')
329:     if (typeof reason !== 'string' || reason.length > 240) deny('INVALID_REASON')
330:     if (decision === 'APPROVED') validatePolicy({ ...policy, nowMs })
331:     const app = this.store.transaction(() => {
332:       const app = this.db.prepare('SELECT * FROM applications WHERE id=?').get(applicationId)
333:       if (!app || !['PENDING', 'NEEDS_INFO'].includes(app.status)) deny('APPLICATION_NOT_PENDING')
334:       this.db.prepare(`UPDATE applications SET status=?,decision_at=?,decision_reason=? WHERE id=?`)
335:         .run(decision, nowMs, reason, applicationId)
336:       this.db.prepare(`UPDATE applications SET provisioning_status=? WHERE id=?`)
337:         .run(decision === 'APPROVED' ? (this.provisioningConfigured() ? 'PROVISIONING' : 'NOT_CONFIGURED') : 'NOT_CONFIGURED', applicationId)
338:       if (decision === 'APPROVED') {
339:         this.db.prepare(`INSERT INTO entitlements
340:           (tenant_uuid,expires_at,seat_limit,max_installations,modules_json,revocation_epoch,status,updated_at)
341:           VALUES(?,?,?,?,?,0,'ACTIVE',?)`).run(app.tenant_uuid, policy.expiresAtMs,
342:           policy.seatLimit, policy.maxInstallations,
343:           JSON.stringify([...policy.modules].sort()), nowMs)
344:         this.db.prepare(`INSERT INTO installations
345:           (installation_uuid,tenant_uuid,public_pem,status,added_at)
346:           VALUES(?,?,?,'ACTIVE',?)`).run(app.installation_uuid, app.tenant_uuid,
347:           app.installation_public_pem, nowMs)
348:       }
349:       this.store.audit({ actor, action: 'APPLICATION_' + decision,
350:         subject: app.tenant_uuid,
351:         details: {
352:           applicationId, reason,
353:           ...(decision === 'APPROVED' ? {
354:             expiresAtMs: policy.expiresAtMs, seatLimit: policy.seatLimit,
355:             maxInstallations: policy.maxInstallations, modules: [...policy.modules].sort()
356:           } : {})
357:         }, nowMs })
358:       return this.db.prepare('SELECT * FROM applications WHERE id=?').get(applicationId)
359:     })
360:     if (decision === 'APPROVED' && this.provisioningConfigured()) {
361:       void this.#provisionMain(app, policy, credentials, nowMs)
362:       return { applicationId, status: decision, provisioning: 'PROVISIONING' }
363:     }
364:     return { applicationId, status: decision, provisioning: app.provisioning_status }
365:   }
366: 
367:   provisioningConfigured() {
368:     return Boolean(this.provisioning.baseUrl && this.provisioning.token &&
369:       Number.isSafeInteger(this.provisioning.planId) && this.provisioning.planId > 0 &&
370:       Number.isSafeInteger(this.provisioning.packageId) && this.provisioning.packageId > 0)
371:   }
372: 
373:   #provisionMain(app, policy, credentials, nowMs) {
374:     const username = typeof credentials?.username === 'string' ? credentials.username.trim() : ''
375:     const password = typeof credentials?.password === 'string' ? credentials.password : ''
376:     if (!/^[a-zA-Z0-9]{4,30}$/.test(username) || password.length < 12 || password.length > 72) {
377:       const error = '初始管理员账号或密码不符合要求（密码仅转发，不写入控制面数据库）'
378:       this.db.prepare("UPDATE applications SET provisioning_status='FAILED',provisioning_error=? WHERE id=?").run(error, app.id)
379:       return Promise.resolve({ status: 'FAILED', error })
380:     }
381:     const controller = new AbortController()
382:     const timer = setTimeout(() => controller.abort(), this.provisioning.timeoutMs)
383:     const body = {
384:       idempotencyKey: app.id,
385:       tenantUuid: app.tenant_uuid,
386:       organizationName: app.organization,
387:       contactName: app.contact_name || '机构管理员',
388:       contactMobile: app.contact_phone,
389:       planId: this.provisioning.planId,
390:       packageId: this.provisioning.packageId,
391:       expiresAt: new Date(policy.expiresAtMs).toISOString().slice(0, 19),
392:       adminUsername: username,
393:       adminPassword: password
394:     }
395:     return fetch(`${this.provisioning.baseUrl}/system/saas/internal/provisioning`, {
396:       method: 'POST',
397:       headers: { 'content-type': 'application/json', 'x-rehab-provisioning-token': this.provisioning.token },
398:       body: JSON.stringify(body), signal: controller.signal
399:     }).then(async response => {
400:       const data = await response.json().catch(() => ({}))
401:       if (!response.ok || data?.data?.status === 'FAILED' || data?.status === 'FAILED') {
402:         throw new Error(data?.data?.failure_reason || data?.message || `主系统返回 HTTP ${response.status}`)
403:       }
404:       const result = data?.data ?? data
405:       this.db.prepare("UPDATE applications SET provisioning_status='ACTIVE',provisioning_id=?,provisioning_error=NULL,main_tenant_id=? WHERE id=?")
406:         .run(String(result.tenant_uuid || app.tenant_uuid), result.tenant_id ?? null, app.id)
407:       this.store.audit({ actor: this.db.prepare('SELECT username FROM owner_identity WHERE id=1').get().username,
408:         action: 'APPLICATION_PROVISIONED', subject: app.tenant_uuid,
409:         details: { applicationId: app.id, mainTenantId: result.tenant_id ?? null }, nowMs })
410:       return { status: 'ACTIVE', tenantId: result.tenant_id ?? null, tenantUuid: result.tenant_uuid ?? app.tenant_uuid }
411:     }).catch(error => {
412:       const message = error?.name === 'AbortError' ? '主系统开通请求超时' : String(error?.message || error).slice(0, 500)
413:       this.db.prepare("UPDATE applications SET provisioning_status='FAILED',provisioning_error=? WHERE id=?").run(message, app.id)
414:       return { status: 'FAILED', error: message }
415:     }).finally(() => clearTimeout(timer))
416:   }
417: 
418:   retryProvision({ token, applicationId, credentials = {}, nowMs }) {
419:     this.requireStrongOwner(token, nowMs)
420:     if (!this.provisioningConfigured()) deny('PROVISIONING_NOT_CONFIGURED')
421:     const app = this.db.prepare('SELECT * FROM applications WHERE id=?').get(applicationId)
422:     if (!app || app.status !== 'APPROVED') deny('APPLICATION_NOT_APPROVED')
423:     this.db.prepare("UPDATE applications SET provisioning_status='PROVISIONING',provisioning_error=NULL WHERE id=?").run(applicationId)
424:     const policy = this.db.prepare('SELECT expires_at FROM entitlements WHERE tenant_uuid=?').get(app.tenant_uuid)
425:     if (!policy) deny('ENTITLEMENT_NOT_FOUND')
426:     void this.#provisionMain(app, { expiresAtMs: policy.expires_at }, credentials, nowMs)
427:     return { applicationId, status: 'PROVISIONING' }
428:   }
429: 
430:   #active(tenantId, installationId, nowMs) {
431:     validUuid(tenantId)
432:     validUuid(installationId)
433:     validMs(nowMs)
434:     const entitlement = this.db.prepare('SELECT * FROM entitlements WHERE tenant_uuid=?').get(tenantId)
435:     if (!entitlement || entitlement.status !== 'ACTIVE') deny('TENANT_NOT_APPROVED')
436:     if (entitlement.expires_at <= nowMs) deny('ENTITLEMENT_EXPIRED')
437:     const installation = this.db.prepare(`SELECT public_pem FROM installations
438:       WHERE installation_uuid=? AND tenant_uuid=? AND status='ACTIVE'`)
439:       .get(installationId, tenantId)
440:     if (!installation) deny('INSTALLATION_NOT_APPROVED')
441:     return { entitlement, installation }
442:   }
443: 
444:   challenge({ tenantId, installationId, nowMs }) {
445:     this.#active(tenantId, installationId, nowMs)
446:     const nonce = randomBytes(32).toString('base64url')
447:     this.store.transaction(() => {
448:       this.db.prepare('DELETE FROM renewal_challenges WHERE expires_at <= ?').run(nowMs)
449:       const size = this.db.prepare('SELECT COUNT(*) AS n FROM renewal_challenges').get().n
450:       if (size >= 1024) deny('CHALLENGE_CAPACITY_EXHAUSTED')
451:       this.db.prepare(`INSERT INTO renewal_challenges
452:         (nonce_hash,tenant_uuid,installation_uuid,expires_at) VALUES(?,?,?,?)`)
453:         .run(hashToken(nonce), tenantId, installationId, nowMs + CHALLENGE_MS)
454:     })
455:     return { nonce, expiresAt: new Date(nowMs + CHALLENGE_MS).toISOString() }
456:   }
457: 
458:   issueOnline({ tenantId, installationId, nonce, possessionSignature, nowMs }) {
459:     const { entitlement, installation } = this.#active(tenantId, installationId, nowMs)
460:     if (typeof nonce !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(nonce)) deny('INVALID_CHALLENGE')
461:     // Consume once in a committed transaction; rejected proofs also burn nonce.
462:     const challenge = this.store.transaction(() => {
463:       const challenge = this.db.prepare('SELECT * FROM renewal_challenges WHERE nonce_hash=?')
464:         .get(hashToken(nonce))
465:       this.db.prepare('DELETE FROM renewal_challenges WHERE nonce_hash=?').run(hashToken(nonce))
466:       return challenge
467:     })
468:     if (!challenge || challenge.tenant_uuid !== tenantId ||
469:       challenge.installation_uuid !== installationId ||
470:       challenge.expires_at <= nowMs) deny('INVALID_CHALLENGE')
471:     if (!verify(null, renewalMessage(nonce, tenantId, installationId),
472:       parseKey(installation.public_pem), fromUrl64(possessionSignature))) {
473:       deny('INSTALLATION_PROOF_FAILED')
474:     }
475:     return this.store.transaction(() =>
476:       this.#issue({ entitlement, tenantId, installationId, nowMs, mode: 'online', actor: 'installation' }))
477:   }
478: 
479:   issueOffline({ token, tenantId, installationId, nowMs }) {
480:     const actor = this.requireStrongOwner(token, nowMs)
481:     const { entitlement } = this.#active(tenantId, installationId, nowMs)
482:     return this.store.transaction(() => {
483:       const issued = this.#issue({ entitlement, tenantId, installationId, nowMs, mode: 'offline', actor })
484:       return {
485:         ...issued,
486:         // Owner may save this JSON object as a .rehab-license file and deliver
487:         // it through an independently authenticated channel to the studio.
488:         licenseFile: {
489:           schemaVersion: 1, tenantId, installationId,
490:           token: issued.token, statusProof: issued.statusProof
491:         }
492:       }
493:     })
494:   }
495: 
496:   #issue({ entitlement, tenantId, installationId, nowMs, mode, actor }) {
497:     const key = this.db.prepare("SELECT * FROM signing_keys WHERE status='ACTIVE'").get()
498:     if (!key) deny('NO_ACTIVE_SIGNING_KEY')
499:     const leaseId = randomUUID()
500:     const expiresAt = Math.min(entitlement.expires_at, nowMs + MAX_OFFLINE_LEASE_MS)
501:     const claims = {
502:       schemaVersion: 1, mode, leaseId, tenantId, installationId,
503:       issuedAt: new Date(nowMs).toISOString(),
504:       notBefore: new Date(nowMs).toISOString(),
505:       expiresAt: new Date(expiresAt).toISOString(),
506:       entitlementExpiresAt: new Date(entitlement.expires_at).toISOString(),
507:       revocationEpoch: entitlement.revocation_epoch,
508:       seatLimit: entitlement.seat_limit,
509:       maxInstallations: entitlement.max_installations,
510:       modules: JSON.parse(entitlement.modules_json)
511:     }
512:     const payload = Buffer.from(JSON.stringify(claims)).toString('base64url')
513:     const signedBytes = Buffer.from(`RHL1.${key.kid}.${payload}`, 'ascii')
514:     const signer = createPrivateKey(this.vault.decrypt(key.private_encrypted, 'issuer-key:' + key.kid))
515:     const token = `${signedBytes.toString('ascii')}.${sign(null, signedBytes, signer).toString('base64url')}`
516:     verifyTenantLicenseLease(token, {
517:       trustedKeys: new Map([[key.kid, parseKey(key.public_pem)]]),
518:       tenantId, installationId, nowMs,
519:       minimumRevocationEpoch: entitlement.revocation_epoch, lastTrustedTimeMs: nowMs
520:     })
521:     this.db.prepare(`INSERT INTO license_issues
522:       (lease_uuid,tenant_uuid,installation_uuid,mode,kid,issued_at,expires_at)
523:       VALUES(?,?,?,?,?,?,?)`).run(leaseId, tenantId, installationId, mode,
524:       key.kid, nowMs, expiresAt)
525:     this.store.audit({ actor, action: 'LEASE_ISSUED', subject: tenantId,
526:       details: { leaseId, installationId, kid: key.kid, mode, expiresAt }, nowMs })
527:     return {
528:       token, expiresAt: claims.expiresAt, leaseId,
529:       statusProof: this.issueStatusProof({ tenantId, installationId, nowMs })
530:     }
531:   }
532: 
533:   // Publicly verifiable owner status, never a bearer credential. An offline
534:   // terminal can act on its last status proof for at most 72h.
535:   issueStatusProof({ tenantId, installationId, nowMs }) {
536:     validUuid(tenantId)
537:     validUuid(installationId)
538:     validMs(nowMs)
539:     const tenant = this.db.prepare('SELECT * FROM entitlements WHERE tenant_uuid=?')
540:       .get(tenantId)
541:     const device = this.db.prepare('SELECT status FROM installations WHERE tenant_uuid=? AND installation_uuid=?')
542:       .get(tenantId, installationId)
543:     if (!tenant || !device) deny('UNKNOWN_INSTALLATION')
544:     const key = this.db.prepare("SELECT * FROM signing_keys WHERE status='ACTIVE'").get()
545:     if (!key) deny('NO_ACTIVE_SIGNING_KEY')
546:     const status = device.status !== 'ACTIVE' ? 'REPLACED'
547:       : tenant.status !== 'ACTIVE' ? tenant.status
548:         : tenant.expires_at <= nowMs ? 'EXPIRED' : 'ACTIVE'
549:     const claims = {
550:       schemaVersion: 1, tenantId, installationId, status,
551:       revocationEpoch: tenant.revocation_epoch,
552:       observedAt: new Date(nowMs).toISOString(),
553:       expiresAt: new Date(nowMs + MAX_OFFLINE_LEASE_MS).toISOString()
554:     }
555:     const encoded = Buffer.from(JSON.stringify(claims)).toString('base64url')
556:     const prefix = `RHR1.${key.kid}.${encoded}`
557:     const signer = createPrivateKey(this.vault.decrypt(key.private_encrypted, 'issuer-key:' + key.kid))
558:     return `${prefix}.${sign(null, Buffer.from(prefix, 'ascii'), signer).toString('base64url')}`
559:   }
560: 
561:   updateEntitlement({ token, tenantId, policy, nowMs }) {
562:     const actor = this.requireStrongOwner(token, nowMs)
563:     validUuid(tenantId)
564:     validatePolicy({ ...policy, nowMs })
565:     return this.store.transaction(() => {
566:       const entitlement = this.db.prepare('SELECT * FROM entitlements WHERE tenant_uuid=?').get(tenantId)
567:       if (!entitlement || entitlement.status !== 'ACTIVE') deny('TENANT_NOT_APPROVED')
568:       const newEpoch = entitlement.revocation_epoch + 1
569:       this.db.prepare(`UPDATE entitlements SET expires_at=?,seat_limit=?,max_installations=?,
570:         modules_json=?,revocation_epoch=?,updated_at=? WHERE tenant_uuid=?`)
571:         .run(policy.expiresAtMs, policy.seatLimit, policy.maxInstallations,
572:           JSON.stringify([...policy.modules].sort()), newEpoch, nowMs, tenantId)
573:       this.db.prepare('DELETE FROM renewal_challenges WHERE tenant_uuid=?').run(tenantId)
574:       this.store.audit({ actor, action: 'ENTITLEMENT_UPDATED', subject: tenantId,
575:         details: { revocationEpoch: newEpoch, ...policy }, nowMs })
576:       return { tenantId, revocationEpoch: newEpoch }
577:     })
578:   }
579: 
580:   setTenantStatus({ token, tenantId, status, nowMs }) {
581:     const actor = this.requireStrongOwner(token, nowMs)
582:     validUuid(tenantId)
583:     if (!['ACTIVE', 'SUSPENDED', 'REVOKED'].includes(status)) deny('INVALID_STATUS')
584:     return this.store.transaction(() => {
585:       const existing = this.db.prepare('SELECT * FROM entitlements WHERE tenant_uuid=?').get(tenantId)
586:       if (!existing) deny('UNKNOWN_TENANT')
587:       if (existing.status === 'REVOKED' && status !== 'REVOKED') deny('REVOKED_IS_FINAL')
588:       if (existing.status === status) deny('NO_STATUS_CHANGE')
589:       const epoch = existing.revocation_epoch + 1
590:       this.db.prepare('UPDATE entitlements SET status=?,revocation_epoch=?,updated_at=? WHERE tenant_uuid=?')
591:         .run(status, epoch, nowMs, tenantId)
592:       this.db.prepare('DELETE FROM renewal_challenges WHERE tenant_uuid=?').run(tenantId)
593:       this.store.audit({ actor, action: 'TENANT_' + status, subject: tenantId,
594:         details: { previous: existing.status, epoch }, nowMs })
595:       return { tenantId, status, revocationEpoch: epoch }
596:     })
597:   }
598: 
599:   // No unapproved installation registration through this path: an owner has
600:   // explicitly verified the new public key proof. Replacement invalidates
601:   // outstanding leases and old device sessions once revocation snapshot is refreshed.
602:   replaceInstallation({ token, tenantId, oldInstallationId, newInstallationId,
603:     newPublicKeyPem, proof, nowMs }) {
604:     const actor = this.requireStrongOwner(token, nowMs)
605:     validUuid(tenantId)
606:     validUuid(oldInstallationId)
607:     validUuid(newInstallationId)
608:     const newKey = parseKey(newPublicKeyPem)
609:     if (!verify(null, enrollmentMessage(tenantId, newInstallationId),
610:       newKey, fromUrl64(proof))) deny('INSTALLATION_PROOF_FAILED')
611:     return this.store.transaction(() => {
612:       const old = this.#active(tenantId, oldInstallationId, nowMs)
613:       if (this.db.prepare('SELECT installation_uuid FROM installations WHERE installation_uuid=?')
614:         .get(newInstallationId)) deny('INSTALLATION_ALREADY_REGISTERED')
615:       this.db.prepare("UPDATE installations SET status='REPLACED' WHERE installation_uuid=?")
616:         .run(oldInstallationId)
617:       this.db.prepare(`INSERT INTO installations
618:         (installation_uuid,tenant_uuid,public_pem,status,added_at) VALUES(?,?,?,'ACTIVE',?)`)
619:         .run(newInstallationId, tenantId, newPublicKeyPem, nowMs)
620:       const epoch = old.entitlement.revocation_epoch + 1
621:       this.db.prepare('UPDATE entitlements SET revocation_epoch=?,updated_at=? WHERE tenant_uuid=?')
622:         .run(epoch, nowMs, tenantId)
623:       this.db.prepare('DELETE FROM renewal_challenges WHERE tenant_uuid=?').run(tenantId)
624:       this.store.audit({ actor, action: 'INSTALLATION_REPLACED', subject: tenantId,
625:         details: { oldInstallationId, newInstallationId, epoch }, nowMs })
626:       return { tenantId, revocationEpoch: epoch }
627:     })
628:   }
629: 
630:   status({ token, tenantId, nowMs }) {
631:     this.requireOwner(token, nowMs)
632:     validUuid(tenantId)
633:     const entitlement = this.db.prepare('SELECT * FROM entitlements WHERE tenant_uuid=?').get(tenantId)
634:     if (!entitlement) return null
635:     return {
636:       tenantId, status: entitlement.status,
637:       expiresAt: new Date(entitlement.expires_at).toISOString(),
638:       seatLimit: entitlement.seat_limit, maxInstallations: entitlement.max_installations,
639:       modules: JSON.parse(entitlement.modules_json),
640:       revocationEpoch: entitlement.revocation_epoch
641:     }
642:   }
643: 
644:   audit({ token, nowMs, limit = 100 }) {
645:     this.requireOwner(token, nowMs)
646:     if (!Number.isInteger(limit) || limit < 1 || limit > 500) deny('INVALID_LIMIT')
647:     return this.db.prepare(`SELECT event_uuid AS eventId,actor,action,subject,
648:       details_json AS details,occurred_at AS occurredAt,prev_hash AS prevHash,
649:       chain_hash AS chainHash FROM audit_events ORDER BY seq DESC LIMIT ?`)
650:       .all(limit).map(entry => ({ ...entry, details: JSON.parse(entry.details) }))
651:   }
652: 
653:   signedAuditCheckpoint({ token, nowMs }) {
654:     const actor = this.requireStrongOwner(token, nowMs)
655:     return this.store.transaction(() => {
656:       const signer = this.db.prepare("SELECT * FROM signing_keys WHERE status='ACTIVE'").get()
657:       if (!signer) deny('NO_ACTIVE_SIGNING_KEY')
658:       const row = this.db.prepare('SELECT seq,chain_hash FROM audit_events ORDER BY seq DESC LIMIT 1').get()
659:       const payload = {
660:         protocol: 'RHA1', sequence: row?.seq ?? 0,
661:         headHash: row?.chain_hash ?? '0'.repeat(64),
662:         witnessedAt: new Date(nowMs).toISOString()
663:       }
664:       const body = Buffer.from(JSON.stringify(payload)).toString('base64url')
665:       const signedBytes = Buffer.from(`RHA1.${signer.kid}.${body}`, 'ascii')
666:       const privateKey = createPrivateKey(this.vault.decrypt(signer.private_encrypted,
667:         'issuer-key:' + signer.kid))
668:       const checkpoint = `${signedBytes.toString('ascii')}.${sign(null,
669:         signedBytes, privateKey).toString('base64url')}`
670:       this.store.audit({ actor, action: 'AUDIT_CHECKPOINT_CREATED', subject: 'audit',
671:         details: { sequence: payload.sequence, headHash: payload.headHash }, nowMs })
672:       // Operator must anchor a copy in an independent WORM/log retention system.
673:       return { checkpoint, ...payload, kid: signer.kid }
674:     })
675:   }
676: }