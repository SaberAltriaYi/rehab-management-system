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
