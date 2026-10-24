import { parsePolicy, requireUuid, redactAuditItem } from './policy.mjs'

// Dedicated owner application: NO localStorage, sessionStorage, cookies or
// reuse of the clinic's Vue admin bearer. Every owner API call is server MFA.
let session = null
let selected = null
let selectedTenant = null
let provisioningConfigured = null
let operationBusy = false
const $ = (id) => document.getElementById(id)
const formData = (id) => Object.fromEntries(new FormData($(id)))
const errorLabels = {
  OWNER_STEP_UP_REQUIRED: '高风险操作需要重新验证 TOTP。',
  CROSS_ORIGIN_FORBIDDEN: '登录地址与 Owner 服务允许的 HTTPS 来源不一致。请在本机浏览器地址栏直接打开 https://127.0.0.1:9444/owner/console 后重新登录；不要从主系统、端口转发或不受信任的域名提交。',

  OWNER_UNAUTHORIZED: '所有者会话已失效，请重新登录。',
  INVALID_OWNER_CREDENTIALS: '所有者账号、密码或 TOTP 不正确。',
  APPLICATION_NOT_PENDING: '此申请已经处理，先刷新申请列表。',
  APPLICATION_NOT_APPROVED: '只有已授权申请才可以重试主系统开通。',
  PROVISIONING_NOT_CONFIGURED: '本地尚未配置主系统自动开通，先完成许可授权即可。',
  TENANT_NOT_APPROVED: '租户尚未处于可维护状态。',
  TENANT_NOT_ACTIVE: '租户当前未处于启用状态。',
  NO_STATUS_CHANGE: '租户已经是目标状态，不需要重复操作。',
  REVOKED_IS_FINAL: '已撤销的租户不能恢复。',
  UNKNOWN_TENANT: '找不到这个租户，请从申请列表选择“维护”。',
  ENTITLEMENT_NOT_FOUND: '没有找到该租户的授权记录。',
  INVALID_UUID: '租户编号无效，请从申请列表点击“维护”自动带入。',
  INVALID_REASON: '审核理由长度或内容不符合要求。',
  INVALID_EXPIRY: '到期时间必须晚于当前时间。',
  INVALID_SEATS: '员工席位必须是正整数，或选择无限席位。',
  INVALID_INSTALLATIONS: '部署实例数必须是 1–100000 的整数。',
  INVALID_MODULES: '授权模块名称无效或重复。'
}
function normalizeTotp(value) {
  return String(value ?? '').replace(/[^0-9]/g, '').slice(0, 6)
}
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
  if (!session) {
    provisioningConfigured = null
    selected = null
    selectedTenant = null
    $('workflow-status').textContent = ''
  }
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
    const code = String(result.error || res.status)
    const error = Error(errorLabels[code] || '授权操作未完成：' + code)
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

function localDateTimeValue(value) {
  const date = value instanceof Date ? value : new Date(value)
  if (!Number.isFinite(date.getTime())) return ''
  const pad = number => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function fillDefaultPolicy() {
  const form = $('policy-form')
  const expiry = new Date(Date.now() + 365 * 86400000)
  form.elements.namedItem('expiry').value = localDateTimeValue(expiry)
  form.elements.namedItem('seats').value = '6'
  form.elements.namedItem('unlimited').checked = true
  form.elements.namedItem('installations').value = '1'
  form.elements.namedItem('modules').value = 'rehab,motion,report'
  if (!String(form.elements.namedItem('reason').value || '').trim()) {
    form.elements.namedItem('reason').value = '已核验机构资料与授权范围，按标准策略批准。'
  }
  feedback('已填入标准策略；提交前仍可修改并需要所有者 MFA 再认证')
}

function fillPolicyFromTenant(tenant) {
  const form = $('policy-form')
  form.elements.namedItem('expiry').value = localDateTimeValue(tenant.expiresAt)
  form.elements.namedItem('unlimited').checked = tenant.seatLimit === null
  form.elements.namedItem('seats').value = tenant.seatLimit === null ? '6' : String(tenant.seatLimit)
  form.elements.namedItem('installations').value = String(tenant.maxInstallations)
  form.elements.namedItem('modules').value = tenant.modules.join(',')
  form.elements.namedItem('reason').value = '已复核租户合同与用量，调整续期或扩容策略。'
}

function setTenantControls(status) {
  const active = status === 'ACTIVE'
  const suspended = status === 'SUSPENDED'
  const revoked = status === 'REVOKED'
  $('update-policy').disabled = !active
  $('suspend').disabled = !active
  $('resume').disabled = !suspended
  $('revoke').disabled = revoked
  $('offline-form').querySelector('button[type="submit"]').disabled = !active
}

function setWorkflowStatus(configured) {
  provisioningConfigured = Boolean(configured)
  $('provisioning-fields').hidden = !provisioningConfigured
  $('workflow-status').textContent = provisioningConfigured
    ? '主系统自动开通已配置：批准后会先签发许可，再显示主系统开通进度。'
    : '当前是本地许可模式：批准、续期和扩容会立即保存授权；主系统自动开通尚未配置，因此不会凭空创建主系统租户。'
  $('workflow-status').classList.toggle('warning', !provisioningConfigured)
}

function setSelectionStatus(message, state = '') {
  const element = $('selection-status')
  element.textContent = message
  element.className = 'selection-status' + (state ? ' ' + state : '')
}

function setOperationBusy(value) {
  operationBusy = value
  $('owner-content').setAttribute('aria-busy', value ? 'true' : 'false')
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
function credentialsFromForm() {
  const field = $('policy-form')
  return {
    username: String(field.elements.namedItem('adminUsername').value || '').trim(),
    password: String(field.elements.namedItem('adminPassword').value || '')
  }
}
const tenantId = () => requireUuid(String(formData('tenant-form').tenantId ?? '').trim())
const applicationStatusLabel = {
  PENDING: '待审核',
  NEEDS_INFO: '待补充',
  APPROVED: '已授权',
  REJECTED: '已驳回'
}
const provisioningStatusLabel = {
  NOT_CONFIGURED: '未接入主系统',
  PROVISIONING: '主系统开通中',
  ACTIVE: '主系统已开通',
  FAILED: '主系统开通失败'
}
function applyTenant(id, installationId = '') {
  $('tenant-form').elements.namedItem('tenantId').value = id
  $('offline-form').elements.namedItem('tenantId').value = id
  if (installationId) $('offline-form').elements.namedItem('installationId').value = installationId
}
async function loadTenantStatus(id, { updatePolicy = true } = {}) {
  const result = await api('/owner/v1/tenants/' + requireUuid(id))
  const tenant = result.tenant
  if (!tenant) {
    selectedTenant = null
    $('tenant-status').textContent = '没有已批准许可证'
    setTenantControls(null)
    setSelectionStatus('这个申请还没有已批准许可证，请先完成审批。', 'warning')
    return null
  }
  selectedTenant = tenant
  $('tenant-status').textContent = '状态：' + tenant.status + ' · 席位：' +
    (tenant.seatLimit === null ? '无限' : tenant.seatLimit) +
    ' · 部署实例：' + tenant.maxInstallations +
    ' · 撤销代际：' + tenant.revocationEpoch +
    ' · 合同截止：' + tenant.expiresAt
  setTenantControls(tenant.status)
  if (updatePolicy) fillPolicyFromTenant(tenant)
  setSelectionStatus('已载入 ' + tenant.tenantId + ' 的当前策略，可直接续期 / 扩容。', 'ready')
  return tenant
}
async function waitForProvisioning(applicationId) {
  let latest = null
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const applications = await refreshApplications({ announce: false })
    latest = applications.find(item => item.id === applicationId) || null
    if (!latest || latest.provisioningStatus !== 'PROVISIONING') return latest
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  return latest
}
async function refreshApplications({ announce = true } = {}) {
  const result = await api('/owner/v1/applications')
  const applications = result.applications || []
  setWorkflowStatus(result.provisioningConfigured)
  const body = $('applications')
  body.replaceChildren()
  $('application-count').textContent = applications.length + ' 条申请'
  selected = selected && applications.some(x => x.id === selected.id && ['PENDING', 'NEEDS_INFO'].includes(x.status))
    ? applications.find(x => x.id === selected.id) : null
  selectedTenant = selectedTenant && applications.some(x => x.tenantId === selectedTenant.tenantId && x.status === 'APPROVED')
    ? selectedTenant : null
  for (const app of applications) {
    const row = node('tr')
    if (selected?.id === app.id || selectedTenant?.tenantId === app.tenantId) row.className = 'selected'
    const name = node('td', app.organization)
    const ids = node('td')
    ids.append(node('small', app.tenantId), node('div', app.installationId))
    if (app.contactName || app.contactPhone || app.contactEmail) {
      ids.append(node('div', [app.contactName, app.contactPhone, app.contactEmail].filter(Boolean).join(' · '), 'muted'))
    }
    if (app.region) ids.append(node('div', app.region, 'muted'))
    const status = node('td', applicationStatusLabel[app.status] ?? app.status)
    if (app.provisioningStatus) status.append(node('div', provisioningStatusLabel[app.provisioningStatus] ?? app.provisioningStatus, 'muted'))
    if (app.provisioningError) status.append(node('small', app.provisioningError, 'muted'))
    const actions = node('td')
    if (['PENDING', 'NEEDS_INFO'].includes(app.status)) {
      const choose = node('button', '审核')
      choose.type = 'button'
      choose.addEventListener('click', () => {
        selected = app
        selectedTenant = null
        applyTenant(app.tenantId)
        fillDefaultPolicy()
        setTenantControls(null)
        setSelectionStatus('已选中 ' + app.organization + '：核实资料后可直接批准或要求补充。', 'ready')
        $('approve').disabled = false
        $('reject').disabled = false
        for (const tr of body.children) tr.classList.remove('selected')
        row.classList.add('selected')
        $('policy-form').scrollIntoView({ behavior: 'smooth', block: 'center' })
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
    if (app.status === 'APPROVED') {
      const manage = node('button', '维护')
      manage.type = 'button'
      manage.className = 'outline'
      manage.addEventListener('click', () => void handle(async () => {
        selected = null
        selectedTenant = app
        applyTenant(app.tenantId, app.installationId)
        $('approve').disabled = true
        $('reject').disabled = true
        await loadTenantStatus(app.tenantId)
        $('tenant-form').scrollIntoView({ behavior: 'smooth', block: 'center' })
      }))
      actions.append(manage)
    }
    if (app.status === 'APPROVED' && app.provisioningStatus === 'FAILED') {
      const retry = node('button', '重试开通')
      retry.type = 'button'
      retry.className = 'warning'
      retry.disabled = !provisioningConfigured
      retry.title = provisioningConfigured ? '重新提交主系统租户开通' : '先配置主系统自动开通'
      retry.addEventListener('click', () => void handle(async () => {
        selectedTenant = app
        applyTenant(app.tenantId, app.installationId)
        const credentials = credentialsFromForm()
        if (!credentials.username || !credentials.password) throw Error('重试前请重新填写机构管理员账号和临时密码')
        await strongOperation(() => api('/owner/v1/applications/' + requireUuid(app.id) + '/retry-provision', {
          method: 'POST', body: { credentials }
        }), '重试主系统租户开通')
        const latest = await waitForProvisioning(app.id)
        await refreshAudit()
        feedback(latest?.provisioningStatus === 'ACTIVE'
          ? '主系统租户已开通'
          : latest?.provisioningError
            ? '主系统开通失败：' + latest.provisioningError
            : '已提交主系统开通重试，请稍后刷新状态')
      }))
      actions.append(retry)
    }
    row.append(name, ids, status, actions)
    body.append(row)
  }
  $('approve').disabled = !selected
  $('reject').disabled = !selected
  if (announce) feedback('申请与独立授权审计已刷新')
  return applications
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
  const results = await Promise.allSettled([refreshApplications({ announce: false }), refreshAudit()])
  const failed = results.find(result => result.status === 'rejected')
  if (failed) throw failed.reason
  feedback('申请与独立授权审计已刷新')
}
async function handle(callback) {
  if (operationBusy) {
    feedback('已有操作正在处理，请等待当前状态刷新')
    return
  }
  setOperationBusy(true)
  feedback('正在处理，请稍候…')
  try { await callback() } catch (error) { feedback(error.message || '未知授权错误', true) }
  finally { setOperationBusy(false) }
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
    const totp = normalizeTotp(data.totp)
    if (totp.length !== 6) throw Error('TOTP 动态验证码必须是六位数字')
    const result = await api('/owner/v1/login', {
      method: 'POST', auth: false,
      body: { username: String(data.username), password: String(data.password), totp }
    })
    session = { token: result.token }
    $('login-form').reset()
    displaySession()
    await refreshAll()
  })
})
$('login-form').elements.namedItem('totp').addEventListener('input', event => {
  event.target.value = normalizeTotp(event.target.value)
})
$('logout').addEventListener('click', () => void handle(async () => {
  try { await api('/owner/v1/logout', { method: 'POST' }) }
  finally { session = null; selected = null; selectedTenant = null; displaySession(); feedback('已注销此浏览器会话') }
}))
$('stepup').addEventListener('click', () => void handle(stepUp))
$('refresh').addEventListener('click', () => void handle(refreshAll))
$('use-default-policy').addEventListener('click', () => void handle(async () => {
  fillDefaultPolicy()
  setSelectionStatus('已填入标准策略。选择待审核申请后再点击批准。', 'ready')
}))
$('approve').addEventListener('click', () => void handle(async () => {
  if (!selected) throw Error('请先选中待审核申请')
  const applicationId = selected.id
  const policy = policyFromForm()
  const reason = String(formData('policy-form').reason || '').trim()
  if (reason.length < 5) throw Error('人工批准必须填写至少五个字符的审核依据')
  const confirmed = await strongOperation(async () => {
    await api('/owner/v1/applications/' + requireUuid(selected.id) + '/approve', {
      method: 'POST', body: { reason, policy, credentials: credentialsFromForm() }
    })
  }, '批准工作室，并签署租户额度、实例和功能授权')
  if (!confirmed) return
  selected = null
  const latest = await waitForProvisioning(applicationId)
  await refreshAudit()
  feedback(latest?.provisioningStatus === 'ACTIVE'
    ? '审批完成，主系统租户已开通'
    : latest?.provisioningStatus === 'FAILED'
      ? '许可已批准，但主系统开通失败：' + latest.provisioningError
      : latest?.provisioningStatus === 'NOT_CONFIGURED'
        ? '审批完成，许可已保存；当前本地未配置主系统自动开通'
        : '审批已保存，正在等待主系统开通状态')
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
    await loadTenantStatus(id)
  })
})
$('update-policy').addEventListener('click', () => void handle(async () => {
  const id = tenantId()
  const policy = policyFromForm()
  const reason = String(formData('policy-form').reason || '').trim()
  if (reason.length < 5) throw Error('续期或扩容需要填写至少五个字符的变更理由')
  const confirmed = await strongOperation(() => api('/owner/v1/tenants/' + id + '/policy', {
    method: 'POST', body: { policy, reason }
  }), '更新此租户的合同、席位、实例和模块范围')
  if (!confirmed) return
  await loadTenantStatus(id)
  await refreshAudit()
  feedback('续期 / 扩容策略已保存，旧许可证缓存已失效')
}))
for (const [id, status] of [['suspend', 'SUSPENDED'], ['resume', 'ACTIVE'], ['revoke', 'REVOKED']]) {
  $(id).addEventListener('click', () => void handle(async () => {
    const uuid = tenantId()
    if (status === 'REVOKED' && window.prompt(
      '撤销是不可逆操作。请输入租户 UUID 确认：') !== uuid) throw Error('撤销确认编号不匹配')
    const confirmed = await strongOperation(() => api('/owner/v1/tenants/' + uuid + '/status', {
      method: 'POST', body: { status }
    }), status === 'REVOKED' ? '不可逆撤销租户授权' : '更改租户状态为 ' + status)
    if (!confirmed) return
    await loadTenantStatus(uuid, { updatePolicy: false })
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
setTenantControls(null)
displaySession()