1: // Minimal independent HTTPS owner-control API.
2: // Does NOT proxy any patient or clinic application data.
3: import http from 'node:http'
4: import https from 'node:https'
5: import { readFileSync } from 'node:fs'
6: import { LicensingServiceError } from './service.mjs'
7: 
8: const BODY_LIMIT = 16 * 1024
9: const limiter = new Map()
10: const PRIVATE_ENDPOINT = /^\/owner\/v1\//
11: const PUBLIC_PREFIX = /^(\/license\/v1|\/public\/v1)\//
12: // No authentication state, secrets or clinical records in these immutable
13: // browser assets. Owner data APIs continue to require MFA bearer on every call.
14: const CONSOLE_ASSETS = Object.freeze({
15:   '/owner/console': ['index.html', 'text/html; charset=utf-8'],
16:   '/owner/console/app.mjs': ['app.mjs', 'text/javascript; charset=utf-8'],
17:   '/owner/console/policy.mjs': ['policy.mjs', 'text/javascript; charset=utf-8'],
18:   '/owner/console/styles.css': ['styles.css', 'text/css; charset=utf-8'],
19:   '/owner/register': ['register.html', 'text/html; charset=utf-8'],
20:   '/owner/register.mjs': ['register.mjs', 'text/javascript; charset=utf-8']
21: })
22: function writeConsoleAsset(res, path) {
23:   const [file, type] = CONSOLE_ASSETS[path]
24:   const contents = readFileSync(new URL('./console/' + file, import.meta.url))
25:   res.writeHead(200, {
26:     'Content-Type': type,
27:     'Cache-Control': 'no-store',
28:     'X-Content-Type-Options': 'nosniff',
29:     'Referrer-Policy': 'no-referrer',
30:     'X-Frame-Options': 'DENY',
31:     'Cross-Origin-Resource-Policy': 'same-origin',
32:     'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=()',
33:     'Content-Security-Policy': "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'; object-src 'none'; upgrade-insecure-requests"
34:   })
35:   res.end(contents)
36: }
37: 
38: function restricted(ip, path, nowMs) {
39:   const key = `${ip}:${path}`
40:   for (const [k, v] of limiter) if (v.until < nowMs) limiter.delete(k)
41:   if (limiter.size > 4096) return true
42:   const v = limiter.get(key) ?? { count: 0, until: nowMs + 60_000 }
43:   if (nowMs > v.until) { v.count = 0; v.until = nowMs + 60_000 }
44:   ++v.count
45:   limiter.set(key, v)
46:   return v.count > (path.includes('/login') ? 12 : 60)
47: }
48: function writeJson(res, status, value) {
49:   res.writeHead(status, {
50:     'Content-Type': 'application/json; charset=utf-8',
51:     'Cache-Control': 'no-store',
52:     'X-Content-Type-Options': 'nosniff',
53:     'Referrer-Policy': 'no-referrer',
54:     'X-Frame-Options': 'DENY'
55:   })
56:   res.end(JSON.stringify(value))
57: }
58: async function readJson(req) {
59:   if (req.headers['content-type']?.split(';')[0].trim() !== 'application/json') {
60:     throw new LicensingServiceError('JSON_CONTENT_TYPE_REQUIRED')
61:   }
62:   const parts = []
63:   let n = 0
64:   for await (const chunk of req) {
65:     n += chunk.length
66:     if (n > BODY_LIMIT) throw new LicensingServiceError('BODY_TOO_LARGE')
67:     parts.push(chunk)
68:   }
69:   let body
70:   try {
71:     body = JSON.parse(Buffer.concat(parts).toString('utf8'))
72:   } catch {
73:     throw new LicensingServiceError('INVALID_JSON')
74:   }
75:   if (!body || Array.isArray(body) || typeof body !== 'object') {
76:     throw new LicensingServiceError('INVALID_JSON')
77:   }
78:   return body
79: }
80: function fieldsOnly(body, fields) {
81:   if (Object.keys(body).some(name => !fields.includes(name))) {
82:     throw new LicensingServiceError('UNRECOGNIZED_INPUT_FIELD')
83:   }
84: }
85: function bearer(req) {
86:   const h = req.headers.authorization
87:   if (typeof h !== 'string' || !/^Bearer [A-Za-z0-9_-]{43}$/.test(h)) {
88:     throw new LicensingServiceError('OWNER_UNAUTHORIZED')
89:   }
90:   return h.slice(7)
91: }
92: function policy(body) {
93:   const p = body.policy
94:   if (!p || typeof p !== 'object' || Array.isArray(p)) {
95:     throw new LicensingServiceError('INVALID_POLICY')
96:   }
97:   fieldsOnly(p, ['expiresAtMs', 'seatLimit', 'maxInstallations', 'modules'])
98:   return p
99: }
100: const loopback = ip => ['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(ip)
101: 
102: export function createOwnerControlServer(service, {
103:   tlsOptions,
104:   allowInsecureLoopbackForTests = false,
105:   permittedBrowserOrigin = null
106: } = {}) {
107:   if (!tlsOptions && !allowInsecureLoopbackForTests) {
108:     throw new Error('Owner control API requires TLS by default')
109:   }
110:   if (permittedBrowserOrigin !== null &&
111:     (typeof permittedBrowserOrigin !== 'string' ||
112:     !permittedBrowserOrigin.startsWith('https://'))) {
113:     throw new Error('Browser origin must be an explicit HTTPS origin')
114:   }
115:   const handle = async (req, res) => {
116:     const nowMs = Date.now()
117:     const path = req.url
118:     try {
119:       if (typeof path !== 'string' || path.length > 256 ||
120:         path.includes('?') || path.includes('#') ||
121:         !(PRIVATE_ENDPOINT.test(path) || PUBLIC_PREFIX.test(path) ||
122:           path === '/health' || Object.hasOwn(CONSOLE_ASSETS, path))) {
123:         return writeJson(res, 404, { error: 'NOT_FOUND' })
124:       }
125:       if (!tlsOptions && !loopback(req.socket.remoteAddress)) {
126:         return writeJson(res, 403, { error: 'TLS_REQUIRED' })
127:       }
128:       if (req.headers.origin && req.headers.origin !== permittedBrowserOrigin) {
129:         return writeJson(res, 403, { error: 'CROSS_ORIGIN_FORBIDDEN' })
130:       }
131:       if (req.method === 'OPTIONS') return writeJson(res, 405, { error: 'NOT_ALLOWED' })
132:       if (restricted(req.socket.remoteAddress, path, nowMs)) {
133:         return writeJson(res, 429, { error: 'RATE_LIMITED' })
134:       }
135:       if (Object.hasOwn(CONSOLE_ASSETS, path)) {
136:         if (req.method !== 'GET') return writeJson(res, 405, { error: 'NOT_ALLOWED' })
137:         return writeConsoleAsset(res, path)
138:       }
139:       if (path === '/health' && req.method === 'GET') {
140:         return writeJson(res, 200, { status: 'ok' })
141:       }
142:       if (path === '/license/v1/issuers' && req.method === 'GET') {
143:         return writeJson(res, 200, { issuers: service.trustedIssuerKeys() })
144:       }
145:       if (path === '/public/v1/applications' && req.method === 'POST') {
146:         const b = await readJson(req)
147:         fieldsOnly(b, ['organization', 'contactName', 'contactPhone', 'contactEmail', 'region', 'note'])
148:         return writeJson(res, 201, service.submitPublicApplication({ ...b, nowMs }))
149:       }
150:       if (path === '/license/v1/applications' && req.method === 'POST') {
151:         const b = await readJson(req)
152:         fieldsOnly(b, ['tenantId', 'installationId', 'installationPublicKeyPem', 'possessionSignature', 'organization'])
153:         return writeJson(res, 201, service.submitApplication({ ...b, nowMs }))
154:       }
155:       if (path === '/license/v1/challenges' && req.method === 'POST') {
156:         const b = await readJson(req)
157:         fieldsOnly(b, ['tenantId', 'installationId'])
158:         return writeJson(res, 200, service.challenge({ ...b, nowMs }))
159:       }
160:       if (path === '/license/v1/status' && req.method === 'POST') {
161:         const b = await readJson(req)
162:         fieldsOnly(b, ['tenantId', 'installationId'])
163:         return writeJson(res, 200, { statusProof: service.issueStatusProof({ ...b, nowMs }) })
164:       }
165:       if (path === '/license/v1/leases/online' && req.method === 'POST') {
166:         const b = await readJson(req)
167:         fieldsOnly(b, ['tenantId', 'installationId', 'nonce', 'possessionSignature'])
168:         return writeJson(res, 200, service.issueOnline({ ...b, nowMs }))
169:       }
170:       if (path === '/owner/v1/login' && req.method === 'POST') {
171:         const b = await readJson(req)
172:         fieldsOnly(b, ['username', 'password', 'totp'])
173:         return writeJson(res, 200, await service.login({ ...b, nowMs }))
174:       }
175:       // Every owner operation must carry an MFA-authenticated bearer session.
176:       // Never accept owner flag, role or TOTP bypass from user-supplied JSON.
177:       if (!PRIVATE_ENDPOINT.test(path)) return writeJson(res, 404, { error: 'NOT_FOUND' })
178:       const token = bearer(req)
179:       service.requireOwner(token, nowMs)
180:       if (path === '/owner/v1/logout' && req.method === 'POST') {
181:         service.logout(token, nowMs)
182:         return writeJson(res, 200, { status: 'logged-out' })
183:       }
184:       if (path === '/owner/v1/step-up' && req.method === 'POST') {
185:         const b = await readJson(req)
186:         fieldsOnly(b, ['totp'])
187:         return writeJson(res, 200, service.stepUp({ token, totp: b.totp, nowMs }))
188:       }
189:       if (path === '/owner/v1/applications' && req.method === 'GET') {
190:         return writeJson(res, 200, { applications: service.applicationList({ token, nowMs }) })
191:       }
192:       if (path === '/owner/v1/signing-keys/rotate' && req.method === 'POST') {
193:         const b = await readJson(req)
194:         fieldsOnly(b, [])
195:         return writeJson(res, 200, service.rotateKey({ token, nowMs }))
196:       }
197:       const retire = /^\/owner\/v1\/signing-keys\/([a-zA-Z0-9._-]+)\/retire$/.exec(path)
198:       if (retire && req.method === 'POST') {
199:         const b = await readJson(req)
200:         fieldsOnly(b, [])
201:         service.retireVerificationKey({ token, kid: retire[1], nowMs })
202:         return writeJson(res, 200, { status: 'retired' })
203:       }
204:       const compromised = /^\/owner\/v1\/signing-keys\/([a-zA-Z0-9._-]+)\/emergency-revoke$/.exec(path)
205:       if (compromised && req.method === 'POST') {
206:         const b = await readJson(req)
207:         fieldsOnly(b, [])
208:         return writeJson(res, 200, service.emergencyRevokeSigner({
209:           token, kid: compromised[1], nowMs
210:         }))
211:       }
212:       const application = /^\/owner\/v1\/applications\/([0-9a-f-]{36})\/(approve|reject|request-info)$/.exec(path)
213:       if (application && req.method === 'POST') {
214:         const b = await readJson(req)
215:         fieldsOnly(b, application[2] === 'approve' ? ['reason', 'policy', 'credentials'] : ['reason'])
216:         return writeJson(res, 200, service.decideApplication({
217:           token, applicationId: application[1],
218:           decision: application[2] === 'approve' ? 'APPROVED' : application[2] === 'reject' ? 'REJECTED' : 'NEEDS_INFO',
219:           reason: b.reason, policy: application[2] === 'approve' ? policy(b) : null,
220:           credentials: application[2] === 'approve' ? b.credentials : null, nowMs
221:         }))
222:       }
223:       const retry = /^\/owner\/v1\/applications\/([0-9a-f-]{36})\/retry-provision$/.exec(path)
224:       if (retry && req.method === 'POST') {
225:         const b = await readJson(req)
226:         fieldsOnly(b, ['credentials'])
227:         return writeJson(res, 200, service.retryProvision({ token, applicationId: retry[1], credentials: b.credentials, nowMs }))
228:       }
229:       const status = /^\/owner\/v1\/tenants\/([0-9a-f-]{36})$/.exec(path)
230:       if (status && req.method === 'GET') {
231:         return writeJson(res, 200, { tenant: service.status({ token, tenantId: status[1], nowMs }) })
232:       }
233:       const change = /^\/owner\/v1\/tenants\/([0-9a-f-]{36})\/(policy|status)$/.exec(path)
234:       if (change && req.method === 'POST') {
235:         const b = await readJson(req)
236:         fieldsOnly(b, change[2] === 'policy' ? ['policy'] : ['status'])
237:         return writeJson(res, 200, change[2] === 'policy'
238:           ? service.updateEntitlement({ token, tenantId: change[1], policy: policy(b), nowMs })
239:           : service.setTenantStatus({ token, tenantId: change[1], status: b.status, nowMs }))
240:       }
241:       if (path === '/owner/v1/installations/replace' && req.method === 'POST') {
242:         const b = await readJson(req)
243:         fieldsOnly(b, ['tenantId', 'oldInstallationId', 'newInstallationId', 'newPublicKeyPem', 'proof'])
244:         return writeJson(res, 200, service.replaceInstallation({ token, ...b, nowMs }))
245:       }
246:       if (path === '/owner/v1/leases/offline' && req.method === 'POST') {
247:         const b = await readJson(req)
248:         fieldsOnly(b, ['tenantId', 'installationId'])
249:         return writeJson(res, 200, service.issueOffline({ token, ...b, nowMs }))
250:       }
251:       if (path === '/owner/v1/audit' && req.method === 'GET') {
252:         return writeJson(res, 200, { audit: service.audit({ token, nowMs }) })
253:       }
254:       if (path === '/owner/v1/audit/checkpoint' && req.method === 'POST') {
255:         const b = await readJson(req)
256:         fieldsOnly(b, [])
257:         return writeJson(res, 200, service.signedAuditCheckpoint({ token, nowMs }))
258:       }
259:       return writeJson(res, 404, { error: 'NOT_FOUND' })
260:     } catch (error) {
261:       const code = error instanceof LicensingServiceError ? error.code : 'INTERNAL_ERROR'
262:       const status = code === 'OWNER_UNAUTHORIZED' || code === 'INVALID_OWNER_CREDENTIALS' ||
263:         code === 'OWNER_STEP_UP_REQUIRED' ? 401
264:         : code === 'OWNER_ALREADY_PROVISIONED' || code === 'APPLICATION_ALREADY_EXISTS' ||
265:           code === 'APPLICATION_NOT_PENDING' || code === 'TENANT_NOT_APPROVED' ||
266:           code === 'CHALLENGE_CAPACITY_EXHAUSTED' || code === 'TENANT_NOT_ACTIVE' ? 409
267:           : code === 'INTERNAL_ERROR' ? 500 : 400
268:       // Never serialize exception details, bearer tokens or request bodies.
269:       return writeJson(res, status, { error: code })
270:     }
271:   }
272:   const server = tlsOptions ? https.createServer(tlsOptions, handle) : http.createServer(handle)
273:   // For tests, the only allowed cleartext listener is explicit loopback.
274:   if (!tlsOptions) {
275:     const originalListen = server.listen.bind(server)
276:     server.listen = (...args) => {
277:       const host = typeof args[1] === 'string' ? args[1] : null
278:       if (host !== '127.0.0.1' && host !== '::1') {
279:         throw new Error('Insecure owner API may only bind loopback in tests')
280:       }
281:       return originalListen(...args)
282:     }
283:   }
284:   return server
285: }