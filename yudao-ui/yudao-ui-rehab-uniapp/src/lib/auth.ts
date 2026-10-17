import { apiRequest } from './api'
import { clearSession, saveSession } from './session'

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
  const tenantId = Number(result || 0)
  if (!Number.isFinite(tenantId) || tenantId < 0) throw new Error('无法识别该租户，请检查租户名称。')
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
  tenantName: string; phone: string; code: string; patientNo: string; invitationCode: string; nickname?: string
}): Promise<void> {
  const tenantId = await resolveTenantId(input.tenantName)
  const token = await apiRequest<TokenResponse>('/app-api/member/auth/sms-login', {
    method: 'POST', auth: false, tenantId, data: { mobile: input.phone, code: input.code },
  })
  if (!token?.accessToken || !token.expiresTime) throw new Error('会员登录响应缺少短期访问令牌。')
  // 患者会话不保存 refreshToken；绑定失败时立即清除令牌。
  saveSession({ mode: 'patient', accessToken: token.accessToken, expiresTime: token.expiresTime,
    tenantId, userId: token.userId })
  try {
    await apiRequest('/app-patient/auth/bind', {
      method: 'POST', data: { patientNo: input.patientNo, phone: input.phone,
        invitationCode: input.invitationCode, bindType: 'self', nickname: input.nickname || '' },
    })
  } catch (error) {
    clearSession()
    throw error
  }
}
