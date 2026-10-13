export type HealthState = 'healthy' | 'starting' | 'stopped' | 'unavailable' | 'error'

export interface ServiceState {
  name: 'mysql' | 'redis' | 'server' | 'admin'
  label: string
  state: HealthState
  detail: string
}

export interface LauncherOverview {
  appVersion: string
  dockerState: HealthState
  dockerDetail: string
  services: ServiceState[]
  accessUrl: string
  dataDirectory: string
  lastBackupAt: string | null
  lastError: string | null
  operation: string | null
  ready: boolean
  firstLoginPassword?: string | null
  license?: LicenseOverview | null
  releaseRequiresLicense?: boolean
}

export type LicenseStatus =
  | 'UNLICENSED' | 'PENDING' | 'ACTIVE' | 'GRACE'
  | 'EXPIRED' | 'SUSPENDED' | 'REVOKED' | 'REPLACED' | 'INVALID'

export interface LicenseOverview {
  status: LicenseStatus
  detail: string
  tenantId: string | null
  installationId: string | null
  organization: string | null
  applicationId: string | null
  expiry: string | null
  /** Signed commercial contract expiry; the 72h lease is expiry. */
  entitlementExpiry?: string | null
  seatLimit: number | null
  modules: string[]
  pendingReplacement: boolean
  publisherConfigured: boolean
  localVerificationOnly: boolean
}

export function licenseLabel(status: LicenseStatus): string {
  const labels: Record<LicenseStatus, string> = {
    UNLICENSED: '尚未授权',
    PENDING: '等待审批',
    ACTIVE: '签名许可有效',
    GRACE: '离线有效期内',
    EXPIRED: '许可证已到期',
    SUSPENDED: '许可证已暂停',
    REVOKED: '许可证已撤销',
    REPLACED: '安装实例已更换',
    INVALID: '许可证无效'
  }
  return labels[status]
}

export function licenseAllowsBusiness(license: LicenseOverview | null | undefined): boolean {
  return license?.status === 'ACTIVE' || license?.status === 'GRACE'
}

export function licenseExpiryNotice(
  license: LicenseOverview | null | undefined, nowMs = Date.now()
): string | null {
  if (!license?.expiry || !licenseAllowsBusiness(license)) return null
  const end = Date.parse(license.expiry)
  if (!Number.isFinite(end)) return '授权到期时间格式异常，请重新核验许可证'
  const remainingMs = end - nowMs
  if (remainingMs <= 0) return '许可证已到期，须获得所有者重新授权'
  if (remainingMs > 7 * 24 * 60 * 60 * 1000) return null
  const hours = Math.ceil(remainingMs / (60 * 60 * 1000))
  return hours <= 24 ? `本次签名租约约 ${hours} 小时后到期；联网可续签`
    : `本次签名租约约 ${Math.ceil(hours / 24)} 天后到期；联网可续签`
}

/** Never extend a signed lease locally. Renew only through the pinned Owner.
 * The longer commercial entitlement and a short RHL1 lease are distinct. */
export function licenseNeedsOnlineRenewal(
  license: LicenseOverview | null | undefined, nowMs = Date.now()
): boolean {
  if (!license || !license.tenantId || !license.publisherConfigured ||
      !['ACTIVE', 'GRACE', 'EXPIRED'].includes(license.status) ||
      !license.expiry || !license.entitlementExpiry) return false
  const leaseEnd = Date.parse(license.expiry)
  const contractEnd = Date.parse(license.entitlementExpiry)
  return Number.isFinite(leaseEnd) && Number.isFinite(contractEnd) &&
    contractEnd > nowMs && leaseEnd - nowMs <= 24 * 60 * 60 * 1000
}

export interface LauncherSettings {
  bindAddress: string
  httpPort: number
  httpsPort: number
  lanEnabled: boolean
}

export const EMPTY_OVERVIEW: LauncherOverview = {
  appVersion: '1.0.0',
  dockerState: 'unavailable',
  dockerDetail: '正在检查 Docker…',
  services: [
    { name: 'mysql', label: 'MySQL', state: 'stopped', detail: '未启动' },
    { name: 'redis', label: 'Redis', state: 'stopped', detail: '未启动' },
    { name: 'server', label: '后端服务', state: 'stopped', detail: '未启动' },
    { name: 'admin', label: '管理端', state: 'stopped', detail: '未启动' }
  ],
  accessUrl: 'https://127.0.0.1:8443',
  dataDirectory: '正在解析…',
  lastBackupAt: null,
  lastError: null,
  operation: null,
  ready: false,
  firstLoginPassword: null,
  license: null,
  releaseRequiresLicense: false
}

export function redactDiagnosticText(value: string): string {
  return value
    .replace(
      /(password|secret|token|authorization|cookie)\s*([:=])\s*[^\s,;]+/gi,
      '$1$2[已脱敏]'
    )
    .replace(/Bearer\s+[A-Za-z0-9._~+/=-]+/gi, 'Bearer [已脱敏]')
    .replace(/\b1[3-9]\d{9}\b/g, '[手机号已脱敏]')
}

export function serviceBadgeClass(state: HealthState): string {
  return `status status--${state}`
}

export function formatBackupTime(value: string | null): string {
  if (!value) return '尚未创建'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('zh-CN')
}

export function deletionConfirmationMatches(value: string): boolean {
  return value.trim() === '删除所有本地数据'
}

export function importConfirmationMatches(value: string): boolean {
  return value.trim() === '覆盖导入全部数据'
}

export function isValidTransferPassword(value: string): boolean {
  const length = Array.from(value).length
  return length >= 12 && length <= 128
}

export function isValidAdminCredentialInput(username: string, password: string): boolean {
  return /^[A-Za-z0-9._-]{4,30}$/.test(username.trim()) && Array.from(password).length >= 12 && Array.from(password).length <= 16
}
