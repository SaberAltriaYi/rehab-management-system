import { parsePolicy, requireUuid, redactAuditItem } from './policy.mjs'

// Dedicated owner application: NO localStorage, sessionStorage, cookies or
// reuse of the clinic's Vue admin bearer. Every owner API call is server MFA.
let session = null
let selected = null
const $ = (id) => document.getElementById(id)
const formData = (id) => Object.fromEntries(new FormData($(id)))
const node = (tag, value, className) => {
  const result = document.createElement(tag)
  if (value !== undefined) result.textContent = String(value)
  if (className) result.className = className
  return result
}
function feedback(message, isError = false) {
  const element = $('feedback')
  element.textContent = message
  element.classList.toggle('error', isError)
}
function displaySession() {
  $('auth-panel').hidden = Boolean(session)
  $('owner-content').hidden = !session
  $('connection').textContent = session ? '所有者 MFA 会话已验证' : '尚未登录'
}
async function api(path, { method = 'GET', body, auth = true } = {}) {
  if (auth && !session) throw Error('请先完成所有者双因素登录')
  if (!/^\/(owner|license)\/v1\/[a-zA-Z0-9/_-]+$/.test(path)) throw Error('非法的授权操作')
  const res = await fetch(path, {
    method, credentials: 'omit', mode: 'same-origin',
    cache: 'no-store', redirect: 'error',
    headers: {
      Accept: 'application/json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(auth ? { Authorization: 'Bearer ' + session.token } : {})
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  })
  const result = await res.json()
  if (!res.ok) {
    if (result.error === 'OWNER_UNAUTHORIZED') {
      session = null
      displaySession()
    }
    const error = Error('授权操作拒绝：' + String(result.error || res.status))
    error.code = result.error
    throw error
  }
  return result
}
async function stepUp() {
  const totp = window.prompt('输入认证器新生成的六位 TOTP（不能与刚才登录用的验证码相同）：')
  if (totp === null) throw Error('已取消高风险操作')
  if (!/^\d{6}$/.test(totp)) throw Error('动态口令必须是六位数字')
  await api('/owner/v1/step-up', { method: 'POST', body: { totp } })
  feedback('所有者高风险操作的 MFA 再认证已通过')
}
async function strongOperation(operation, label) {
  if (!window.confirm('确认' + label + '？此操作会被审计，可能改变现有租户使用权限。')) return false
  try {
    await operation()
  } catch (error) {
    if (error.code !== 'OWNER_STEP_UP_REQUIRED') throw error
    await stepUp()
    await operation()
  }
  return true
}
function policyFromForm() {
  const field = $('policy-form')
  return parsePolicy({
    expiry: field.elements.namedItem('expiry').value,
    seats: field.elements.namedItem('seats').value,
    unlimited: field.elements.namedItem('unlimited').checked,
    installations: field.elements.namedItem('installations').value,
    modules: field.elements.namedItem('modules').value
  })
}
const tenantId = () => requireUuid(String(formData('tenant-form').tenantId ?? '').trim())
const applicationStatusLabel = {
  PENDING: '待审核',
  NEEDS_INFO: '待补充',
  APPROVED: '已授权',
  REJECTED: '已驳回'
}
function applyTenant(id) {
  $('tenant-form').elements.namedItem('tenantId').value = id
  $('offline-form').elements.namedItem('tenantId').value = id
}
async function refreshApplications() {
  const { applications } = await api('/owner/v1/applications')
  const body = $('applications')
  body.replaceChildren()
  $('application-count').textContent = applications.length + ' 条申请'
  selected = selected && applications.some(x => x.id === selected.id && ['PENDING', 'NEEDS_INFO'].includes(x.status))
    ? applications.find(x => x.id === selected.id) : null
  for (const app of applications) {
    const row = node('tr')
    if (selected?.id === app.id) row.className = 'selected'
    const name = node('td', app.organization)
    const ids = node('td')
    ids.append(node('small', app.tenantId), node('div', app.installationId))
    if (app.contactName || app.contactPhone || app.contactEmail) {
      ids.append(node('div', [app.contactName, app.contactPhone, app.contactEmail].filter(Boolean).join(' · '), 'muted'))
    }
    if (app.region) ids.append(node('div', app.region, 'muted'))
    const status = node('td', applicationStatusLabel[app.status] ?? app.status)
    const actions = node('td')
    if (['PENDING', 'NEEDS_INFO'].includes(app.status)) {
      const choose = node('button', '审核')
      choose.type = 'button'
      choose.addEventListener('click', () => {
        selected = app
        applyTenant(app.tenantId)
        $('offline-form').elements.namedItem('installationId').value = app.installationId
        $('approve').disabled = false
        $('reject').disabled = false
        for (const tr of body.children) tr.classList.remove('selected')
        row.classList.add('selected')
        feedback('已选中 ' + app.organization + '：请核实机构身份、部署实例和策略')
      })
      actions.append(choose)
      const requestInfo = node('button', '要求补充')
      requestInfo.type = 'button'
      requestInfo.className = 'outline'
      requestInfo.addEventListener('click', () => void handle(async () => {
        const reason = window.prompt('请填写需要机构补充的资料：')
        if (!reason || reason.trim().length < 5) throw Error('补充说明至少需要五个字符')
        await strongOperation(() => api('/owner/v1/applications/' + requireUuid(app.id) + '/request-info', {
          method: 'POST', body: { reason: reason.trim() }
        }), '要求机构补充资料')
        await refreshAll()
      }))
      actions.append(requestInfo)
    }
    row.append(name, ids, status, actions)
    body.append(row)
  }
  $('approve').disabled = !selected
  $('reject').disabled = !selected
}
async function refreshAudit() {
  const { audit } = await api('/owner/v1/audit')
  const root = $('audit')
  root.replaceChildren()
  for (const event of audit) {
    const a = redactAuditItem(event)
    const item = node('div', undefined, 'event')
    item.append(node('strong', a.action), node('small', a.time + ' · ' + a.actor),
      node('div', '对象：' + a.subject), node('small', '审计哈希：' + a.chainHash))
    if (a.reason) item.append(node('div', '审核理由：' + a.reason))
    root.append(item)
  }
}
async function refreshAll() {
  await Promise.all([refreshApplications(), refreshAudit()])
  feedback('申请与独立授权审计已刷新')
}
async function handle(callback) {
  try { await callback() } catch (error) { feedback(error.message || '未知授权错误', true) }
}
function downloadLicense(signed, tenant, installation) {
  const safe = requireUuid(tenant) + '-' + requireUuid(installation)
  const contents = JSON.stringify(signed, null, 2)
  if (contents.length > 15000) throw Error('离线许可证文件超过限制')
  const objectUrl = URL.createObjectURL(new Blob([contents], { type: 'application/json' }))
  const link = document.createElement('a')
  link.download = safe + '.rehab-license'
  link.href = objectUrl
  link.click()
  setTimeout(() => URL.revokeObjectURL(objectUrl), 60000)
}

$('login-form').addEventListener('submit', event => {
  event.preventDefault()
  void handle(async () => {
    const data = formData('login-form')
    const result = await api('/owner/v1/login', {
      method: 'POST', auth: false,
      body: { username: String(data.username), password: String(data.password), totp: String(data.totp) }
    })
    session = { token: result.token }
    $('login-form').reset()
    displaySession()
    await refreshAll()
  })
})
$('logout').addEventListener('click', () => void handle(async () => {
  try { await api('/owner/v1/logout', { method: 'POST' }) }
  finally { session = null; selected = null; displaySession(); feedback('已注销此浏览器会话') }
}))
$('stepup').addEventListener('click', () => void handle(stepUp))
$('refresh').addEventListener('click', () => void handle(refreshAll))
$('approve').addEventListener('click', () => void handle(async () => {
  if (!selected) throw Error('请先选中待审核申请')
  const policy = policyFromForm()
  const reason = String(formData('policy-form').reason || '').trim()
  if (reason.length < 5) throw Error('人工批准必须填写至少五个字符的审核依据')
  const confirmed = await strongOperation(async () => {
    await api('/owner/v1/applications/' + requireUuid(selected.id) + '/approve', {
      method: 'POST', body: { reason, policy }
    })
  }, '批准工作室，并签署租户额度、实例和功能授权')
  if (!confirmed) return
  selected = null
  await refreshAll()
}))
$('reject').addEventListener('click', () => void handle(async () => {
  if (!selected) throw Error('请先选中待审核申请')
  const reason = String(formData('policy-form').reason || '').trim()
  if (reason.length < 5) throw Error('驳回需要填写清晰审核理由')
  const confirmed = await strongOperation(async () => {
    await api('/owner/v1/applications/' + requireUuid(selected.id) + '/reject', {
      method: 'POST', body: { reason }
    })
  }, '拒绝此工作室的申请')
  if (!confirmed) return
  selected = null
  await refreshAll()
}))
$('tenant-form').addEventListener('submit', event => {
  event.preventDefault()
  void handle(async () => {
    const id = tenantId()
    const result = await api('/owner/v1/tenants/' + id)
    $('tenant-status').textContent = result.tenant
      ? '状态：' + result.tenant.status + ' · 席位：' +
        (result.tenant.seatLimit === null ? '无限' : result.tenant.seatLimit) +
        ' · 部署实例：' + result.tenant.maxInstallations +
        ' · 撤销代际：' + result.tenant.revocationEpoch +
        ' · 合同截止：' + result.tenant.expiresAt
      : '无已批准许可证'
  })
})
$('update-policy').addEventListener('click', () => void handle(async () => {
  const id = tenantId()
  const policy = policyFromForm()
  await strongOperation(() => api('/owner/v1/tenants/' + id + '/policy', {
    method: 'POST', body: { policy }
  }), '更新此租户的合同、席位、实例和模块范围')
  await refreshAudit()
}))
for (const [id, status] of [['suspend', 'SUSPENDED'], ['resume', 'ACTIVE'], ['revoke', 'REVOKED']]) {
  $(id).addEventListener('click', () => void handle(async () => {
    const uuid = tenantId()
    if (status === 'REVOKED' && window.prompt(
      '撤销是不可逆操作。请输入租户 UUID 确认：') !== uuid) throw Error('撤销确认编号不匹配')
    await strongOperation(() => api('/owner/v1/tenants/' + uuid + '/status', {
      method: 'POST', body: { status }
    }), status === 'REVOKED' ? '不可逆撤销租户授权' : '更改租户状态为 ' + status)
    await refreshAudit()
  }))
}
$('offline-form').addEventListener('submit', event => {
  event.preventDefault()
  void handle(async () => {
    const data = formData('offline-form')
    const tenant = requireUuid(String(data.tenantId).trim())
    const installation = requireUuid(String(data.installationId).trim())
    await strongOperation(async () => {
      const issued = await api('/owner/v1/leases/offline', {
        method: 'POST', body: { tenantId: tenant, installationId: installation }
      })
      downloadLicense(issued.licenseFile, tenant, installation)
      feedback('所有者签署的短期离线许可证已生成；只能通过独立安全渠道交给该工作室')
    }, '为指定已批准设备签发 ≤72 小时离线许可证')
    await refreshAudit()
  })
})
$('replace-form').addEventListener('submit', event => {
  event.preventDefault()
  void handle(async () => {
    const text = String(formData('replace-form').request)
    if (text.length > 4096) throw Error('换机请求过大')
    const data = JSON.parse(text)
    const allowed = ['tenantId', 'oldInstallationId', 'newInstallationId', 'newPublicKeyPem', 'proof']
    if (Object.keys(data).length !== allowed.length || Object.keys(data).some(k => !allowed.includes(k))) {
      throw Error('换机请求字段无效')
    }
    for (const id of allowed.slice(0, 3)) requireUuid(data[id])
    await strongOperation(() => api('/owner/v1/installations/replace', { method: 'POST', body: data }),
      '将批准的新设备替换旧设备，并撤销旧租约代际')
    $('replace-form').reset()
    await refreshAudit()
  })
})
$('checkpoint').addEventListener('click', () => void handle(async () => {
  await strongOperation(async () => {
    const proof = await api('/owner/v1/audit/checkpoint', { method: 'POST', body: {} })
    await navigator.clipboard.writeText(JSON.stringify(proof))
    feedback('签名审计链头已复制；请存入独立 WORM 审计保留系统，不要只保留本浏览器副本')
  }, '生成所有者签名的审计链头')
}))
displaySession()
