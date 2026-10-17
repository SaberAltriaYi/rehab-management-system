1: import { parsePolicy, requireUuid, redactAuditItem } from './policy.mjs'
2: 
3: // Dedicated owner application: NO localStorage, sessionStorage, cookies or
4: // reuse of the clinic's Vue admin bearer. Every owner API call is server MFA.
5: let session = null
6: let selected = null
7: const $ = (id) => document.getElementById(id)
8: const formData = (id) => Object.fromEntries(new FormData($(id)))
9: function normalizeTotp(value) {
10:   return String(value ?? '').replace(/[^0-9]/g, '').slice(0, 6)
11: }
12: const node = (tag, value, className) => {
13:   const result = document.createElement(tag)
14:   if (value !== undefined) result.textContent = String(value)
15:   if (className) result.className = className
16:   return result
17: }
18: function feedback(message, isError = false) {
19:   const element = $('feedback')
20:   element.textContent = message
21:   element.classList.toggle('error', isError)
22: }
23: function displaySession() {
24:   $('auth-panel').hidden = Boolean(session)
25:   $('owner-content').hidden = !session
26:   $('connection').textContent = session ? '所有者 MFA 会话已验证' : '尚未登录'
27: }
28: async function api(path, { method = 'GET', body, auth = true } = {}) {
29:   if (auth && !session) throw Error('请先完成所有者双因素登录')
30:   if (!/^\/(owner|license)\/v1\/[a-zA-Z0-9/_-]+$/.test(path)) throw Error('非法的授权操作')
31:   const res = await fetch(path, {
32:     method, credentials: 'omit', mode: 'same-origin',
33:     cache: 'no-store', redirect: 'error',
34:     headers: {
35:       Accept: 'application/json',
36:       ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
37:       ...(auth ? { Authorization: 'Bearer ' + session.token } : {})
38:     },
39:     ...(body === undefined ? {} : { body: JSON.stringify(body) })
40:   })
41:   const result = await res.json()
42:   if (!res.ok) {
43:     if (result.error === 'OWNER_UNAUTHORIZED') {
44:       session = null
45:       displaySession()
46:     }
47:     const error = Error('授权操作拒绝：' + String(result.error || res.status))
48:     error.code = result.error
49:     throw error
50:   }
51:   return result
52: }
53: async function stepUp() {
54:   const totp = window.prompt('输入认证器新生成的六位 TOTP（不能与刚才登录用的验证码相同）：')
55:   if (totp === null) throw Error('已取消高风险操作')
56:   if (!/^\d{6}$/.test(totp)) throw Error('动态口令必须是六位数字')
57:   await api('/owner/v1/step-up', { method: 'POST', body: { totp } })
58:   feedback('所有者高风险操作的 MFA 再认证已通过')
59: }
60: async function strongOperation(operation, label) {
61:   if (!window.confirm('确认' + label + '？此操作会被审计，可能改变现有租户使用权限。')) return false
62:   try {
63:     await operation()
64:   } catch (error) {
65:     if (error.code !== 'OWNER_STEP_UP_REQUIRED') throw error
66:     await stepUp()
67:     await operation()
68:   }
69:   return true
70: }
71: function policyFromForm() {
72:   const field = $('policy-form')
73:   return parsePolicy({
74:     expiry: field.elements.namedItem('expiry').value,
75:     seats: field.elements.namedItem('seats').value,
76:     unlimited: field.elements.namedItem('unlimited').checked,
77:     installations: field.elements.namedItem('installations').value,
78:     modules: field.elements.namedItem('modules').value
79:   })
80: }
81: function credentialsFromForm() {
82:   const field = $('policy-form')
83:   return {
84:     username: String(field.elements.namedItem('adminUsername').value || '').trim(),
85:     password: String(field.elements.namedItem('adminPassword').value || '')
86:   }
87: }
88: const tenantId = () => requireUuid(String(formData('tenant-form').tenantId ?? '').trim())
89: const applicationStatusLabel = {
90:   PENDING: '待审核',
91:   NEEDS_INFO: '待补充',
92:   APPROVED: '已授权',
93:   REJECTED: '已驳回'
94: }
95: const provisioningStatusLabel = {
96:   NOT_CONFIGURED: '未接入主系统',
97:   PROVISIONING: '主系统开通中',
98:   ACTIVE: '主系统已开通',
99:   FAILED: '主系统开通失败'
100: }
101: function applyTenant(id) {
102:   $('tenant-form').elements.namedItem('tenantId').value = id
103:   $('offline-form').elements.namedItem('tenantId').value = id
104: }
105: async function refreshApplications() {
106:   const { applications } = await api('/owner/v1/applications')
107:   const body = $('applications')
108:   body.replaceChildren()
109:   $('application-count').textContent = applications.length + ' 条申请'
110:   selected = selected && applications.some(x => x.id === selected.id && ['PENDING', 'NEEDS_INFO'].includes(x.status))
111:     ? applications.find(x => x.id === selected.id) : null
112:   for (const app of applications) {
113:     const row = node('tr')
114:     if (selected?.id === app.id) row.className = 'selected'
115:     const name = node('td', app.organization)
116:     const ids = node('td')
117:     ids.append(node('small', app.tenantId), node('div', app.installationId))
118:     if (app.contactName || app.contactPhone || app.contactEmail) {
119:       ids.append(node('div', [app.contactName, app.contactPhone, app.contactEmail].filter(Boolean).join(' · '), 'muted'))
120:     }
121:     if (app.region) ids.append(node('div', app.region, 'muted'))
122:     const status = node('td', applicationStatusLabel[app.status] ?? app.status)
123:     if (app.provisioningStatus) status.append(node('div', provisioningStatusLabel[app.provisioningStatus] ?? app.provisioningStatus, 'muted'))
124:     if (app.provisioningError) status.append(node('small', app.provisioningError, 'muted'))
125:     const actions = node('td')
126:     if (['PENDING', 'NEEDS_INFO'].includes(app.status)) {
127:       const choose = node('button', '审核')
128:       choose.type = 'button'
129:       choose.addEventListener('click', () => {
130:         selected = app
131:         applyTenant(app.tenantId)
132:         $('offline-form').elements.namedItem('installationId').value = app.installationId
133:         $('approve').disabled = false
134:         $('reject').disabled = false
135:         for (const tr of body.children) tr.classList.remove('selected')
136:         row.classList.add('selected')
137:         feedback('已选中 ' + app.organization + '：请核实机构身份、部署实例和策略')
138:       })
139:       actions.append(choose)
140:       const requestInfo = node('button', '要求补充')
141:       requestInfo.type = 'button'
142:       requestInfo.className = 'outline'
143:       requestInfo.addEventListener('click', () => void handle(async () => {
144:         const reason = window.prompt('请填写需要机构补充的资料：')
145:         if (!reason || reason.trim().length < 5) throw Error('补充说明至少需要五个字符')
146:         await strongOperation(() => api('/owner/v1/applications/' + requireUuid(app.id) + '/request-info', {
147:           method: 'POST', body: { reason: reason.trim() }
148:         }), '要求机构补充资料')
149:         await refreshAll()
150:       }))
151:       actions.append(requestInfo)
152:     }
153:     if (app.status === 'APPROVED' && app.provisioningStatus === 'FAILED') {
154:       const retry = node('button', '重试开通')
155:       retry.type = 'button'
156:       retry.className = 'warning'
157:       retry.addEventListener('click', () => void handle(async () => {
158:         const credentials = credentialsFromForm()
159:         if (!credentials.username || !credentials.password) throw Error('重试前请重新填写机构管理员账号和临时密码')
160:         await strongOperation(() => api('/owner/v1/applications/' + requireUuid(app.id) + '/retry-provision', {
161:           method: 'POST', body: { credentials }
162:         }), '重试主系统租户开通')
163:         await refreshAll()
164:       }))
165:       actions.append(retry)
166:     }
167:     row.append(name, ids, status, actions)
168:     body.append(row)
169:   }
170:   $('approve').disabled = !selected
171:   $('reject').disabled = !selected
172: }
173: async function refreshAudit() {
174:   const { audit } = await api('/owner/v1/audit')
175:   const root = $('audit')
176:   root.replaceChildren()
177:   for (const event of audit) {
178:     const a = redactAuditItem(event)
179:     const item = node('div', undefined, 'event')
180:     item.append(node('strong', a.action), node('small', a.time + ' · ' + a.actor),
181:       node('div', '对象：' + a.subject), node('small', '审计哈希：' + a.chainHash))
182:     if (a.reason) item.append(node('div', '审核理由：' + a.reason))
183:     root.append(item)
184:   }
185: }
186: async function refreshAll() {
187:   await Promise.all([refreshApplications(), refreshAudit()])
188:   feedback('申请与独立授权审计已刷新')
189: }
190: async function handle(callback) {
191:   try { await callback() } catch (error) { feedback(error.message || '未知授权错误', true) }
192: }
193: function downloadLicense(signed, tenant, installation) {
194:   const safe = requireUuid(tenant) + '-' + requireUuid(installation)
195:   const contents = JSON.stringify(signed, null, 2)
196:   if (contents.length > 15000) throw Error('离线许可证文件超过限制')
197:   const objectUrl = URL.createObjectURL(new Blob([contents], { type: 'application/json' }))
198:   const link = document.createElement('a')
199:   link.download = safe + '.rehab-license'
200:   link.href = objectUrl
201:   link.click()
202:   setTimeout(() => URL.revokeObjectURL(objectUrl), 60000)
203: }
204: 
205: $('login-form').addEventListener('submit', event => {
206:   event.preventDefault()
207:   void handle(async () => {
208:     const data = formData('login-form')
209:     const totp = normalizeTotp(data.totp)
210:     if (totp.length !== 6) throw Error('TOTP 动态验证码必须是六位数字')
211:     const result = await api('/owner/v1/login', {
212:       method: 'POST', auth: false,
213:       body: { username: String(data.username), password: String(data.password), totp }
214:     })
215:     session = { token: result.token }
216:     $('login-form').reset()
217:     displaySession()
218:     await refreshAll()
219:   })
220: })
221: $('login-form').elements.namedItem('totp').addEventListener('input', event => {
222:   event.target.value = normalizeTotp(event.target.value)
223: })
224: $('logout').addEventListener('click', () => void handle(async () => {
225:   try { await api('/owner/v1/logout', { method: 'POST' }) }
226:   finally { session = null; selected = null; displaySession(); feedback('已注销此浏览器会话') }
227: }))
228: $('stepup').addEventListener('click', () => void handle(stepUp))
229: $('refresh').addEventListener('click', () => void handle(refreshAll))
230: $('approve').addEventListener('click', () => void handle(async () => {
231:   if (!selected) throw Error('请先选中待审核申请')
232:   const policy = policyFromForm()
233:   const reason = String(formData('policy-form').reason || '').trim()
234:   if (reason.length < 5) throw Error('人工批准必须填写至少五个字符的审核依据')
235:   const confirmed = await strongOperation(async () => {
236:     await api('/owner/v1/applications/' + requireUuid(selected.id) + '/approve', {
237:       method: 'POST', body: { reason, policy, credentials: credentialsFromForm() }
238:     })
239:   }, '批准工作室，并签署租户额度、实例和功能授权')
240:   if (!confirmed) return
241:   selected = null
242:   await refreshAll()
243: }))
244: $('reject').addEventListener('click', () => void handle(async () => {
245:   if (!selected) throw Error('请先选中待审核申请')
246:   const reason = String(formData('policy-form').reason || '').trim()
247:   if (reason.length < 5) throw Error('驳回需要填写清晰审核理由')
248:   const confirmed = await strongOperation(async () => {
249:     await api('/owner/v1/applications/' + requireUuid(selected.id) + '/reject', {
250:       method: 'POST', body: { reason }
251:     })
252:   }, '拒绝此工作室的申请')
253:   if (!confirmed) return
254:   selected = null
255:   await refreshAll()
256: }))
257: $('tenant-form').addEventListener('submit', event => {
258:   event.preventDefault()
259:   void handle(async () => {
260:     const id = tenantId()
261:     const result = await api('/owner/v1/tenants/' + id)
262:     $('tenant-status').textContent = result.tenant
263:       ? '状态：' + result.tenant.status + ' · 席位：' +
264:         (result.tenant.seatLimit === null ? '无限' : result.tenant.seatLimit) +
265:         ' · 部署实例：' + result.tenant.maxInstallations +
266:         ' · 撤销代际：' + result.tenant.revocationEpoch +
267:         ' · 合同截止：' + result.tenant.expiresAt
268:       : '无已批准许可证'
269:   })
270: })
271: $('update-policy').addEventListener('click', () => void handle(async () => {
272:   const id = tenantId()
273:   const policy = policyFromForm()
274:   await strongOperation(() => api('/owner/v1/tenants/' + id + '/policy', {
275:     method: 'POST', body: { policy }
276:   }), '更新此租户的合同、席位、实例和模块范围')
277:   await refreshAudit()
278: }))
279: for (const [id, status] of [['suspend', 'SUSPENDED'], ['resume', 'ACTIVE'], ['revoke', 'REVOKED']]) {
280:   $(id).addEventListener('click', () => void handle(async () => {
281:     const uuid = tenantId()
282:     if (status === 'REVOKED' && window.prompt(
283:       '撤销是不可逆操作。请输入租户 UUID 确认：') !== uuid) throw Error('撤销确认编号不匹配')
284:     await strongOperation(() => api('/owner/v1/tenants/' + uuid + '/status', {
285:       method: 'POST', body: { status }
286:     }), status === 'REVOKED' ? '不可逆撤销租户授权' : '更改租户状态为 ' + status)
287:     await refreshAudit()
288:   }))
289: }
290: $('offline-form').addEventListener('submit', event => {
291:   event.preventDefault()
292:   void handle(async () => {
293:     const data = formData('offline-form')
294:     const tenant = requireUuid(String(data.tenantId).trim())
295:     const installation = requireUuid(String(data.installationId).trim())
296:     await strongOperation(async () => {
297:       const issued = await api('/owner/v1/leases/offline', {
298:         method: 'POST', body: { tenantId: tenant, installationId: installation }
299:       })
300:       downloadLicense(issued.licenseFile, tenant, installation)
301:       feedback('所有者签署的短期离线许可证已生成；只能通过独立安全渠道交给该工作室')
302:     }, '为指定已批准设备签发 ≤72 小时离线许可证')
303:     await refreshAudit()
304:   })
305: })
306: $('replace-form').addEventListener('submit', event => {
307:   event.preventDefault()
308:   void handle(async () => {
309:     const text = String(formData('replace-form').request)
310:     if (text.length > 4096) throw Error('换机请求过大')
311:     const data = JSON.parse(text)
312:     const allowed = ['tenantId', 'oldInstallationId', 'newInstallationId', 'newPublicKeyPem', 'proof']
313:     if (Object.keys(data).length !== allowed.length || Object.keys(data).some(k => !allowed.includes(k))) {
314:       throw Error('换机请求字段无效')
315:     }
316:     for (const id of allowed.slice(0, 3)) requireUuid(data[id])
317:     await strongOperation(() => api('/owner/v1/installations/replace', { method: 'POST', body: data }),
318:       '将批准的新设备替换旧设备，并撤销旧租约代际')
319:     $('replace-form').reset()
320:     await refreshAudit()
321:   })
322: })
323: $('checkpoint').addEventListener('click', () => void handle(async () => {
324:   await strongOperation(async () => {
325:     const proof = await api('/owner/v1/audit/checkpoint', { method: 'POST', body: {} })
326:     await navigator.clipboard.writeText(JSON.stringify(proof))
327:     feedback('签名审计链头已复制；请存入独立 WORM 审计保留系统，不要只保留本浏览器副本')
328:   }, '生成所有者签名的审计链头')
329: }))
330: displaySession()