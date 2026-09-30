import { apiRequest } from './api'
import { saveSession } from './session'

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

export async function loginPatient(_input: { tenantName: string; phone: string; patientNo: string }): Promise<void> {
  // Do not send identifiable fields or save tokens until the new enrollment
  // protocol and per-platform secure storage have been reviewed and tested.
  throw new Error('患者登录尚未开放，请联系机构完成线下身份核验。')
}
