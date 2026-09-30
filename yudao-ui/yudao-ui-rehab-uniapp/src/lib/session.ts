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

export function getSession(): MobileSession | null {
  try {
    const value: unknown = uni.getStorageSync(STORAGE_KEY)
    if (value === '' || value === null || value === undefined) return null
    // Storage is untrusted. Legacy patient sessions and malformed staff values
    // are discarded before any request can reuse their credentials.
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
  if (!isRecord(session) || session.mode !== 'staff') {
    clearSession()
    throw new Error('患者凭据尚无安全存储方案，禁止持久化。')
  }
  if (!isValidStaffSession(session)) {
    clearSession()
    throw new Error('会话无效或已过期，请重新登录。')
  }
  uni.setStorageSync(STORAGE_KEY, session)
}

export function clearSession(): void {
  try {
    uni.removeStorageSync(STORAGE_KEY)
  } catch {
    // Logout should still return to the login page if storage is unavailable.
  }
}
