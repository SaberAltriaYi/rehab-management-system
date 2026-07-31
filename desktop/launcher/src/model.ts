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
  firstLoginPassword: null
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
