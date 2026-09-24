import { API_ORIGIN } from './config'
import { clearSession, getSession } from './session'

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'
export interface RequestOptions {
  method?: HttpMethod
  data?: Record<string, unknown> | unknown[]
  auth?: boolean
  tenantId?: number
}

function buildHeaders(options: RequestOptions): Record<string, string> {
  const session = getSession()
  const headers: Record<string, string> = { Accept: 'application/json', 'Content-Type': 'application/json' }
  if (options.auth !== false && session?.accessToken) headers.Authorization = `Bearer ${session.accessToken}`
  const tenantId = options.tenantId ?? session?.tenantId
  if (tenantId !== undefined && tenantId !== null && tenantId > 0) headers['tenant-id'] = String(tenantId)
  return headers
}

export function apiRaw<T = unknown>(path: string, options: RequestOptions = {}): Promise<T> {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const url = `${API_ORIGIN}${normalizedPath}`
  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method: options.method || 'GET',
      data: options.data as UniApp.RequestOptions['data'],
      header: buildHeaders(options),
      timeout: 20000,
      success: (response) => {
        if (response.statusCode < 200 || response.statusCode >= 300) {
          const body = response.data as { msg?: string } | undefined
          reject(new Error(body?.msg || `服务返回 HTTP ${response.statusCode}`))
          return
        }
        resolve(response.data as T)
      },
      fail: (error) => reject(new Error(error.errMsg || '无法连接康复服务，请检查网络与 API 域名配置。')),
    })
  })
}

export async function apiRequest<T = unknown>(path: string, options: RequestOptions = {}): Promise<T> {
  const body = await apiRaw<{ code?: number | string; data?: T; msg?: string }>(path, options)
  if (body && typeof body === 'object' && body.code !== undefined) {
    const code = Number(body.code)
    if (code !== 0) {
      if (code === 401) clearSession()
      throw new Error(body.msg || `请求失败（${String(body.code)}）`)
    }
    return body.data as T
  }
  return body as T
}
