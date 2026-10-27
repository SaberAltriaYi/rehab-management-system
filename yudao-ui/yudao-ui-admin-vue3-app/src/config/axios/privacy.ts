// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
// A diagnostic error must never retain Axios config, headers, bodies or causes.
const allowedCodes = new Set([
  'ECONNABORTED',
  'ETIMEDOUT',
  'ERR_NETWORK',
  'ERR_CANCELED',
  'ERR_BAD_REQUEST',
  'ERR_BAD_RESPONSE',
  'ERR_INVALID_URL',
  'ERR_NOT_SUPPORT'
])

export type SafeRequestError = Error & {
  isAxiosError: boolean
  code?: string
  __CANCEL__?: true
  response?: { status: number }
}

export function safeRequestError(value: unknown): SafeRequestError {
  const source = value && typeof value === 'object' ? (value as Record<string, any>) : {}
  const status = source.response?.status
  const safeStatus = Number.isInteger(status) && status >= 100 && status <= 599 ? status : undefined
  const code = allowedCodes.has(source.code) ? (source.code as string) : undefined
  const message =
    code === 'ERR_CANCELED'
      ? '请求已取消'
      : code === 'ECONNABORTED' || code === 'ETIMEDOUT'
        ? '请求超时，请稍后确认结果或重试'
        : safeStatus
          ? `请求未完成（HTTP ${safeStatus}）`
          : '请求未完成，请检查连接或稍后重试'
  const error = new Error(message) as SafeRequestError
  error.isAxiosError = source.isAxiosError === true || source.name === 'AxiosError'
  error.name = error.isAxiosError ? 'AxiosError' : 'RequestError'
  if (code) error.code = code
  if (code === 'ERR_CANCELED') error.__CANCEL__ = true
  if (safeStatus) error.response = { status: safeStatus }
  return error
}

/** Spring @RequestParam accepts URL-encoded POST form fields, without URL credentials. */
export function refreshRequest(
  baseUrl: string,
  token: string,
  tenantId: string | number | undefined
) {
  return {
    url: baseUrl + '/system/auth/refresh-token',
    data: new URLSearchParams({ refreshToken: token }).toString(),
    config: {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'tenant-id': tenantId === undefined || tenantId === null ? undefined : String(tenantId),
        // Clear any stale global defaults, not only headers added by this caller.
        Authorization: undefined,
        'visit-tenant-id': undefined
      },
      withCredentials: false
    }
  }
}
