import { apiRequest } from './api'
import { clearSession, getSession, saveSession } from './session'

interface TokenResponse {
  userId?: number
  accessToken: string
  refreshToken?: string
  expiresTime?: string
}

async function resolveTenantId(tenantName: string): Promise<number> {
  const result = await apiRequest<number | string | null>('/admin-api/system/tenant/get-id-by-name', {
    method: 'GET', data: { name: tenantName.trim() }, auth: false,
  })
  const tenantId = Number(result)
  if (!Number.isSafeInteger(tenantId) || tenantId <= 0) throw new Error('无法识别该租户，请检查租户名称。')
  return tenantId
}

export async function loginStaff(input: {
  tenantName: string; username: string; password: string; captchaVerification: string
}): Promise<void> {
  const tenantId = await resolveTenantId(input.tenantName)
  const token = await apiRequest<TokenResponse>('/admin-api/app-admin/auth/login', {
    method: 'POST', auth: false, tenantId,
    data: { username: input.username, password: input.password, captchaVerification: input.captchaVerification },
  })
  if (!token?.accessToken) throw new Error('登录响应缺少访问令牌。')
  saveSession({ mode: 'staff', accessToken: token.accessToken, refreshToken: token.refreshToken,
    expiresTime: token.expiresTime, tenantId, userId: token.userId })
}

export async function sendPatientSmsCode(tenantName: string, phone: string): Promise<void> {
  const tenantId = await resolveTenantId(tenantName)
  await apiRequest('/app-api/member/auth/send-sms-code', {
    method: 'POST', auth: false, tenantId, data: { mobile: phone, scene: 1 },
  })
}

export async function loginPatient(input: {
  tenantName: string; phone: string; code: string; patientNo?: string; invitationCode?: string; nickname?: string
}): Promise<void> {
  const tenantId = await resolveTenantId(input.tenantName)
  const token = await apiRequest<TokenResponse>('/app-api/member/auth/sms-login', {
    method: 'POST', auth: false, tenantId, data: { mobile: input.phone, code: input.code },
  })
  if (!token?.accessToken || !token.expiresTime) throw new Error('会员登录响应缺少短期访问令牌。')
  // Patient credentials remain memory-only; no refresh token is persisted.
  saveSession({ mode: 'patient', accessToken: token.accessToken, expiresTime: token.expiresTime,
    tenantId, userId: token.userId })
  try {
    if (input.invitationCode?.trim()) {
      await apiRequest('/app-api/app-patient/auth/bind', {
        method: 'POST', data: { patientNo: input.patientNo?.trim(), phone: input.phone,
          invitationCode: input.invitationCode.trim(), bindType: 'self', nickname: input.nickname || '' },
      })
    }
    // Returning patients log in with SMS without reusing a consumed invite.
    // Member login alone never establishes patient access: verify live binding.
    await apiRequest('/app-api/app-patient/profile')
  } catch (error) {
    await logoutSession()
    throw error
  }
}

/** Best-effort server revocation; local credentials are always cleared. */
export async function logoutSession(): Promise<boolean> {
  const session = getSession()
  try {
    if (session) await apiRequest(session.mode === 'patient'
      ? '/app-api/member/auth/logout' : '/admin-api/system/auth/logout', { method: 'POST' })
    return true
  } catch {
    return false
  } finally {
    clearSession()
  }
}
