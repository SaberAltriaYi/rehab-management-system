1: import { parsePolicy, requireUuid, redactAuditItem } from './policy.mjs'
2: 
3: // Dedicated owner application: NO localStorage, sessionStorage, cookies or
4: // reuse of the clinic's Vue admin bearer. Every owner API call is server MFA.
5: let session = null
6: let selected = null
7: const $ = (id) => document.getElementById(id)
8: const formData = (id) => Object.fromEntries(new FormData($(id)))
9: const node = (tag, value, className) => {
10:   const result = document.createElement(tag)
11:   if (value !== undefined) result.textContent = String(value)
12:   if (className) result.className = className
13:   return result
14: }
15: function feedback(message, isError = false) {
16:   const element = $('feedback')
17:   element.textContent = message
18:   element.classList.toggle('error', isError)
19: }
20: function displaySession() {
21:   $('auth-panel').hidden = Boolean(session)
22:   $('owner-content').hidden = !session
23:   $('connection').textContent = session ? '所有者 MFA 会话已验证' : '尚未登录'
24: }
25: async function api(path, { method = 'GET', body, auth = true } = {}) {
26:   if (auth && !session) throw Error('请先完成所有者双因素登录')
27:   if (!/^\/(owner|license)\/v1\/[a-zA-Z0-9/_-]+$/.test(path)) throw Error('非法的授权操作')
28:   const res = await fetch(path, {
29:     method, credentials: 'omit', mode: 'same-origin',
30:     cache: 'no-store', redirect: 'error',
31:     headers: {
32:       Accept: 'application/json',
33:       ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
34:       ...(auth ? { Authorization: 'Bearer ' + session.token } : {})
35:     },
36:     ...(body === undefined ? {} : { body: JSON.stringify(body) })
37:   })
38:   const result = await res.json()
39:   if (!res.ok) {
40:     if (result.error === 'OWNER_UNAUTHORIZED') {
41:       session = null
42:       displaySession()
43:     }
44:     const error = Error('授权操作拒绝：' + String(result.error || res.status))
45:     error.code = result.error
46:     throw error
47:   }
48:   return result
49: }
50: async function stepUp() {
51:   const totp = window.prompt('输入认证器新生成的六位 TOTP（不能与刚才登录用的验证码相同）：')
52:   if (totp === null) throw Error('已取消高风险操作')
53:   if (!/^\d{6}$/.test(totp)) throw Error('动态口令必须是六位数字')
54:   await api('/owner/v1/step-up', { method: 'POST', body: { totp } })
55:   feedback('所有者高风险操作的 MFA 再认证已通过')
56: }
57: async function strongOperation(operation, label) {
58:   if (!window.confirm('确认' + label + '？此操作会被审计，可能改变现有租户使用权限。')) return false
59:   try {
60:     await operation()
61:   } catch (error) {
62:     if (error.code !== 'OWNER_STEP_UP_REQUIRED') throw error
63:     await stepUp()
64:     await operation()
65:   }
66:   return true
67: }
68: function policyFromForm() {
69:   const field = $('policy-form')
70:   return parsePolicy({
71:     expiry: field.elements.namedItem('expiry').value,
72:     seats: field.elements.namedItem('seats').value,
73:     unlimited: field.elements.namedItem('unlimited').checked,
74:     installations: field.elements.namedItem('installations').value,
75:     modules: field.elements.namedItem('modules').value
76:   })
77: }
78: function credentialsFromForm() {
79:   const field = $('policy-form')
80:   return {
81:     username: String(field.elements.namedItem('adminUsername').value || '').trim(),
82:     password: String(field.elements.namedItem('adminPassword').value || '')
83:   }
84: }
85: const tenantId = () => requireUuid(String(formData('tenant-form').tenantId ?? '').trim())
86: const applicationStatusLabel = {
87:   PENDING: '待审核',
88:   NEEDS_INFO: '待补充',
89:   APPROVED: '已授权',
90:   REJECTED: '已驳回'
91: }
92: const provisioningStatusLabel = {
93:   NOT_CONFIGURED: '未接入主系统',
94:   PROVISIONING: '主系统开通中',
95:   ACTIVE: '主系统已开通',
96:   FAILED: '主系统开通失败'
97: }
98: function applyTenant(id) {
99:   $('tenant-form').elements.namedItem('tenantId').value = id
100:   $('offline-form').elements.namedItem('tenantId').value = id
101: }
102: async function refreshApplications() {
103:   const { applications } = await api('/owner/v1/applications')
104:   const body = $('applications')
105:   body.replaceChildren()
106:   $('application-count').textContent = applications.length + ' 条申请'
107:   selected = selected && applications.some(x => x.id === selected.id && ['PENDING', 'NEEDS_INFO'].includes(x.status))
108:     ? applications.find(x => x.id === selected.id) : null
109:   for (const app of applications) {
110:     const row = node('tr')
111:     if (selected?.id === app.id) row.className = 'selected'
112:     const name = node('td', app.organization)
113:     const ids = node('td')
114:     ids.append(node('small', app.tenantId), node('div', app.installationId))
115:     if (app.contactName || app.contactPhone || app.contactEmail) {
116:       ids.append(node('div', [app.contactName, app.contactPhone, app.contactEmail].filter(Boolean).join(' · '), 'muted'))
117:     }
118:     if (app.region) ids.append(node('div', app.region, 'muted'))
119:     const status = node('td', applicationStatusLabel[app.status] ?? app.status)
120:     if (app.provisioningStatus) status.append(node('div', provisioningStatusLabel[app.provisioningStatus] ?? app.provisioningStatus, 'muted'))
121:     if (app.provisioningError) status.append(node('small', app.provisioningError, 'muted'))
122:     const actions = node('td')
123:     if (['PENDING', 'NEEDS_INFO'].includes(app.status)) {
124:       const choose = node('button', '审核')
125:       choose.type = 'button'
126:       choose.addEventListener('click', () => {
127:         selected = app
128:         applyTenant(app.tenantId)
129:         $('offline-form').elements.namedItem('installationId').value = app.installationId
130:         $('approve').disabled = false
131:         $('reject').disabled = false
132:         for (const tr of body.children) tr.classList.remove('selected')
133:         row.classList.add('selected')
134:         feedback('已选中 ' + app.organization + '：请核实机构身份、部署实例和策略')
135:       })
136:       actions.append(choose)
137:       const requestInfo = node('button', '要求补充')
138:       requestInfo.type = 'button'
139:       requestInfo.className = 'outline'
140:       requestInfo.addEventListener('click', () => void handle(async () => {
141:         const reason = window.prompt('请填写需要机构补充的资料：')
142:         if (!reason || reason.trim().length < 5) throw Error('补充说明至少需要五个字符')
143:         await strongOperation(() => api('/owner/v1/applications/' + requireUuid(app.id) + '/request-info', {
144:           method: 'POST', body: { reason: reason.trim() }
145:         }), '要求机构补充资料')
146:         await refreshAll()
147:       }))
148:       actions.append(requestInfo)
149:     }
150:     if (app.status === 'APPROVED' && app.provisioningStatus === 'FAILED') {
151:       const retry = node('button', '重试开通')
152:       retry.type = 'button'
153:       retry.className = 'warning'
154:       retry.addEventListener('click', () => void handle(async () => {
155:         const credentials = credentialsFromForm()
156:         if (!credentials.username || !credentials.password) throw Error('重试前请重新填写机构管理员账号和临时密码')
157:         await strongOperation(() => api('/owner/v1/applications/' + requireUuid(app.id) + '/retry-provision', {
158:           method: 'POST', body: { credentials }
159:         }), '重试主系统租户开通')
160:         await refreshAll()
161:       }))
162:       actions.append(retry)
163:     }
164:     row.append(name, ids, status, actions)
165:     body.append(row)
166:   }
167:   $('approve').disabled = !selected
168:   $('reject').disabled = !selected
169: }
170: async function refreshAudit() {
171:   const { audit } = await api('/owner/v1/audit')
172:   const root = $('audit')
173:   root.replaceChildren()
174:   for (const event of audit) {
175:     const a = redactAuditItem(event)
176:     const item = node('div', undefined, 'event')
177:     item.append(node('strong', a.action), node('small', a.time + ' · ' + a.actor),
178:       node('div', '对象：' + a.subject), node('small', '审计哈希：' + a.chainHash))
179:     if (a.reason) item.append(node('div', '审核理由：' + a.reason))
180:     root.append(item)
181:   }
182: }
183: async function refreshAll() {
184:   await Promise.all([refreshApplications(), refreshAudit()])
185:   feedback('申请与独立授权审计已刷新')
186: }
187: async function handle(callback) {
188:   try { await callback() } catch (error) { feedback(error.message || '未知授权错误', true) }
189: }
190: function downloadLicense(signed, tenant, installation) {
191:   const safe = requireUuid(tenant) + '-' + requireUuid(installation)
192:   const contents = JSON.stringify(signed, null, 2)
193:   if (contents.length > 15000) throw Error('离线许可证文件超过限制')
194:   const objectUrl = URL.createObjectURL(new Blob([contents], { type: 'application/json' }))
195:   const link = document.createElement('a')
196:   link.download = safe + '.rehab-license'
197:   link.href = objectUrl
198:   link.click()
199:   setTimeout(() => URL.revokeObjectURL(objectUrl), 60000)
200: }
201: 
202: $('login-form').addEventListener('submit', event => {
203:   event.preventDefault()
204:   void handle(async () => {
205:     const data = formData('login-form')
206:     const result = await api('/owner/v1/login', {
207:       method: 'POST', auth: false,
208:       body: { username: String(data.username), password: String(data.password), totp: String(data.totp) }
209:     })
210:     session = { token: result.token }
211:     $('login-form').reset()
212:     displaySession()
213:     await refreshAll()
214:   })
215: })
216: $('logout').addEventListener('click', () => void handle(async () => {
217:   try { await api('/owner/v1/logout', { method: 'POST' }) }
218:   finally { session = null; selected = null; displaySession(); feedback('已注销此浏览器会话') }
219: }))
220: $('stepup').addEventListener('click', () => void handle(stepUp))
221: $('refresh').addEventListener('click', () => void handle(refreshAll))
222: $('approve').addEventListener('click', () => void handle(async () => {
223:   if (!selected) throw Error('请先选中待审核申请')
224:   const policy = policyFromForm()
225:   const reason = String(formData('policy-form').reason || '').trim()
226:   if (reason.length < 5) throw Error('人工批准必须填写至少五个字符的审核依据')
227:   const confirmed = await strongOperation(async () => {
228:     await api('/owner/v1/applications/' + requireUuid(selected.id) + '/approve', {
229:       method: 'POST', body: { reason, policy, credentials: credentialsFromForm() }
230:     })
231:   }, '批准工作室，并签署租户额度、实例和功能授权')
232:   if (!confirmed) return
233:   selected = null
234:   await refreshAll()
235: }))
236: $('reject').addEventListener('click', () => void handle(async () => {
237:   if (!selected) throw Error('请先选中待审核申请')
238:   const reason = String(formData('policy-form').reason || '').trim()
239:   if (reason.length < 5) throw Error('驳回需要填写清晰审核理由')
240:   const confirmed = await strongOperation(async () => {
241:     await api('/owner/v1/applications/' + requireUuid(selected.id) + '/reject', {
242:       method: 'POST', body: { reason }
243:     })
244:   }, '拒绝此工作室的申请')
245:   if (!confirmed) return
246:   selected = null
247:   await refreshAll()
248: }))
249: $('tenant-form').addEventListener('submit', event => {
250:   event.preventDefault()
251:   void handle(async () => {
252:     const id = tenantId()
253:     const result = await api('/owner/v1/tenants/' + id)
254:     $('tenant-status').textContent = result.tenant
255:       ? '状态：' + result.tenant.status + ' · 席位：' +
256:         (result.tenant.seatLimit === null ? '无限' : result.tenant.seatLimit) +
257:         ' · 部署实例：' + result.tenant.maxInstallations +
258:         ' · 撤销代际：' + result.tenant.revocationEpoch +
259:         ' · 合同截止：' + result.tenant.expiresAt
260:       : '无已批准许可证'
261:   })
262: })
263: $('update-policy').addEventListener('click', () => void handle(async () => {
264:   const id = tenantId()
265:   const policy = policyFromForm()
266:   await strongOperation(() => api('/owner/v1/tenants/' + id + '/policy', {
267:     method: 'POST', body: { policy }
268:   }), '更新此租户的合同、席位、实例和模块范围')
269:   await refreshAudit()
270: }))
271: for (const [id, status] of [['suspend', 'SUSPENDED'], ['resume', 'ACTIVE'], ['revoke', 'REVOKED']]) {
272:   $(id).addEventListener('click', () => void handle(async () => {
273:     const uuid = tenantId()
274:     if (status === 'REVOKED' && window.prompt(
275:       '撤销是不可逆操作。请输入租户 UUID 确认：') !== uuid) throw Error('撤销确认编号不匹配')
276:     await strongOperation(() => api('/owner/v1/tenants/' + uuid + '/status', {
277:       method: 'POST', body: { status }
278:     }), status === 'REVOKED' ? '不可逆撤销租户授权' : '更改租户状态为 ' + status)
279:     await refreshAudit()
280:   }))
281: }
282: $('offline-form').addEventListener('submit', event => {
283:   event.preventDefault()
284:   void handle(async () => {
285:     const data = formData('offline-form')
286:     const tenant = requireUuid(String(data.tenantId).trim())
287:     const installation = requireUuid(String(data.installationId).trim())
288:     await strongOperation(async () => {
289:       const issued = await api('/owner/v1/leases/offline', {
290:         method: 'POST', body: { tenantId: tenant, installationId: installation }
291:       })
292:       downloadLicense(issued.licenseFile, tenant, installation)
293:       feedback('所有者签署的短期离线许可证已生成；只能通过独立安全渠道交给该工作室')
294:     }, '为指定已批准设备签发 ≤72 小时离线许可证')
295:     await refreshAudit()
296:   })
297: })
298: $('replace-form').addEventListener('submit', event => {
299:   event.preventDefault()
300:   void handle(async () => {
301:     const text = String(formData('replace-form').request)
302:     if (text.length > 4096) throw Error('换机请求过大')
303:     const data = JSON.parse(text)
304:     const allowed = ['tenantId', 'oldInstallationId', 'newInstallationId', 'newPublicKeyPem', 'proof']
305:     if (Object.keys(data).length !== allowed.length || Object.keys(data).some(k => !allowed.includes(k))) {
306:       throw Error('换机请求字段无效')
307:     }
308:     for (const id of allowed.slice(0, 3)) requireUuid(data[id])
309:     await strongOperation(() => api('/owner/v1/installations/replace', { method: 'POST', body: data }),
310:       '将批准的新设备替换旧设备，并撤销旧租约代际')
311:     $('replace-form').reset()
312:     await refreshAudit()
313:   })
314: })
315: $('checkpoint').addEventListener('click', () => void handle(async () => {
316:   await strongOperation(async () => {
317:     const proof = await api('/owner/v1/audit/checkpoint', { method: 'POST', body: {} })
318:     await navigator.clipboard.writeText(JSON.stringify(proof))
319:     feedback('签名审计链头已复制；请存入独立 WORM 审计保留系统，不要只保留本浏览器副本')
320:   }, '生成所有者签名的审计链头')
321: }))
322: displaySession()