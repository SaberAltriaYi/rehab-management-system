// Browser-only input normalization. Server performs authoritative validation.
export const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
export function parsePolicy({ expiry, seats, unlimited, installations, modules, mainSystem }, nowMs = Date.now()) {
  const expiresAtMs = Date.parse(expiry)
  if (!Number.isSafeInteger(expiresAtMs) || expiresAtMs <= nowMs ||
      expiresAtMs > nowMs + 5 * 366 * 86400000) throw Error('授权到期时间须在未来五年内')
  const seatLimit = unlimited ? null : Number(seats)
  if (!unlimited && (!/^\d+$/.test(String(seats)) || !Number.isSafeInteger(seatLimit) ||
      seatLimit < 1 || seatLimit > 2147483647)) throw Error('员工席位必须为正整数，或显式选择无限')
  if (!/^\d+$/.test(String(installations)) || !Number.isSafeInteger(Number(installations)) ||
      Number(installations) < 1 || Number(installations) > 100000) {
    throw Error('设备实例数须为 1–100000')
  }
  const raw = modules.split(/[\s,，]+/).filter(Boolean)
  // Existing callers with no explicit selection retain the historical rehab
  // module. When the checkbox is supplied it is the one source of truth.
  const list = mainSystem === undefined ? raw : [
    ...raw.filter(module => module !== 'rehab'), ...(mainSystem ? ['rehab'] : [])
  ]
  if (list.length < 1 || list.length > 64 ||
      list.some(s => !/^[a-z][a-z0-9_-]{0,31}$/.test(s)) ||
      new Set(list).size !== list.length) throw Error('模块名称无效或存在重复')
  return Object.freeze({
    expiresAtMs, seatLimit, maxInstallations: Number(installations), modules: list.sort()
  })
}
export function requireUuid(value) {
  if (!UUID.test(value ?? '')) throw Error('请输入合法的小写 UUID')
  return value
}
// Distinguish an approved license from an actually provisioned Rehab Core tenant.
// This controls presentation only; the Owner API independently authorizes MFA,
// the signed rehab module and provisioning eligibility on every POST.
export function approvedProvisionState(application, tenant, configured) {
  if (!application || application.status !== 'APPROVED' || !tenant ||
      application.tenantId !== tenant.tenantId) {
    return { visible: false, ready: false, message: '' }
  }
  if (tenant.status !== 'ACTIVE') {
    return { visible: true, ready: false, message: '许可证已停用或撤销，不能开通主系统。' }
  }
  if (!tenant.modules?.includes('rehab')) {
    return { visible: true, ready: false,
      message: '当前许可证没有主系统 rehab 权限。请先在左侧勾选「授权主系统」，按「续期 / 扩容」保存权限，再开通。' }
  }
  if (application.provisioningStatus === 'ACTIVE') {
    return { visible: true, ready: false,
      message: `主系统已开通（租户 ID：${application.mainTenantId ?? '待核实'}）。请从主系统登录，不要重复创建管理员账号。` }
  }
  if (application.provisioningStatus === 'PROVISIONING') {
    return { visible: true, ready: false, message: '主系统正在开通，请刷新等待结果，不能重复提交。' }
  }
  if (!['NOT_CONFIGURED', 'FAILED'].includes(application.provisioningStatus)) {
    return { visible: true, ready: false, message: '当前状态不允许重复开通主系统，请刷新机构状态。' }
  }
  if (!configured) {
    return { visible: true, ready: false,
      message: '当前未配置 Owner → Rehab Core 自动开通（接口地址、内部令牌、计划 ID、套餐 ID）。许可证已批准并包含 rehab 权限，但主系统机构账号尚未创建。请先完成本机主系统对接配置。' }
  }
  return { visible: true, ready: true,
    message: application.provisioningStatus === 'FAILED'
      ? '上次开通失败。请核对错误原因，重新填写管理员账号与新的初始密码，然后 MFA 重试。'
      : '已批准且授权主系统，尚未开通。填写管理员账号和新的初始密码后，执行独立开通（需要 Owner MFA）。' }
}
export function redactAuditItem(item) {
  // Display only recognized metadata; never render token, mfa, patient fields
  const details = item?.details && typeof item.details === 'object' ? item.details : {}
  const reason = typeof details.reason === 'string'
    ? details.reason.slice(0, 240) : ''
  return {
    eventId: String(item.eventId ?? ''),
    time: Number.isFinite(item.occurredAt) ? new Date(item.occurredAt).toLocaleString() : '未知',
    action: String(item.action ?? '').slice(0, 100),
    actor: String(item.actor ?? '').slice(0, 100),
    subject: String(item.subject ?? '').slice(0, 100),
    chainHash: String(item.chainHash ?? '').slice(0, 64),
    reason
  }
}
