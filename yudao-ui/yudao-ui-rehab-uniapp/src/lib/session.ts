export type SessionMode = 'staff' | 'patient'

export interface MobileSession {
  mode: SessionMode
  accessToken: string
  refreshToken?: string
  expiresTime?: string
  tenantId: number
  userId?: number
}

const STORAGE_KEY = 'rehab.mobile.session.v1'
// Patient credentials live only for this process/page lifecycle. Never put
// health-data access or refresh tokens into generic persistent UniApp storage.
let patientSession: MobileSession | null = null

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function isToken(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= 8192
}

function isPositiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

function isValidStaffSession(value: unknown): value is MobileSession {
  if (!isRecord(value) || value.mode !== 'staff' || !isToken(value.accessToken) ||
      !isPositiveId(value.tenantId)) return false
  if (value.userId !== undefined && !isPositiveId(value.userId)) return false
  if (value.refreshToken !== undefined && !isToken(value.refreshToken)) return false
  // Preserve compatibility with staff sessions without an expiry field. When an
  // expiry is supplied, malformed values must not silently become non-expiring.
  if (value.expiresTime !== undefined) {
    if (typeof value.expiresTime !== 'string' || !value.expiresTime.trim()) return false
    const expiresAt = new Date(value.expiresTime).getTime()
    if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) return false
  }
  return true
}

function isValidPatientSession(value: unknown): value is MobileSession {
  if (!isRecord(value) || value.mode !== 'patient' || !isToken(value.accessToken) ||
      !isPositiveId(value.tenantId) || typeof value.expiresTime !== 'string' || !value.expiresTime.trim()) return false
  if (value.refreshToken !== undefined) return false
  const expiresAt = new Date(value.expiresTime).getTime()
  return Number.isFinite(expiresAt) && expiresAt > Date.now()
}

export function getSession(): MobileSession | null {
  if (patientSession) {
    if (isValidPatientSession(patientSession)) return { ...patientSession }
    patientSession = null
  }
  try {
    const value: unknown = uni.getStorageSync(STORAGE_KEY)
    if (value === '' || value === null || value === undefined) return null
    // All persisted patient credentials, including previously valid short-lived
    // ones, are legacy data and must be removed rather than silently restored.
    if (!isValidStaffSession(value)) {
      clearSession()
      return null
    }
    return value
  } catch {
    clearSession()
    return null
  }
}

export function saveSession(session: MobileSession): void {
  if (!isRecord(session) || (session.mode !== 'staff' && session.mode !== 'patient')) {
    clearSession()
    throw new Error('会话类型不受支持。')
  }
  if (!isValidStaffSession(session) && !isValidPatientSession(session)) {
    clearSession()
    throw new Error('会话无效或已过期，请重新登录。')
  }
  if (session.mode === 'patient') {
    uni.removeStorageSync(STORAGE_KEY)
    patientSession = { ...session }
  } else {
    patientSession = null
    uni.setStorageSync(STORAGE_KEY, session)
  }
}

export function clearSession(): void {
  patientSession = null
  try {
    uni.removeStorageSync(STORAGE_KEY)
  } catch {
    // Logout should still return to the login page if storage is unavailable.
  }
}
