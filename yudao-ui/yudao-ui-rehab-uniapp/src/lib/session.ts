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

export function getSession(): MobileSession | null {
  try {
    const value = uni.getStorageSync(STORAGE_KEY) as MobileSession | '' | null
    if (!value || typeof value !== 'object' || !value.accessToken || !value.mode) return null
    if (value.expiresTime) {
      const expiresAt = new Date(value.expiresTime).getTime()
      if (Number.isFinite(expiresAt) && expiresAt <= Date.now()) {
        clearSession()
        return null
      }
    }
    return value
  } catch {
    return null
  }
}

export function saveSession(session: MobileSession): void {
  uni.setStorageSync(STORAGE_KEY, session)
}

export function clearSession(): void {
  try {
    uni.removeStorageSync(STORAGE_KEY)
  } catch {
    // Logout should still return to the login page if storage is unavailable.
  }
}
