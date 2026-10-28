import axios, { AxiosError, AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'

import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import qs from 'qs'
import { config } from '@/config/axios/config'
import {
  getAccessToken,
  getRefreshToken,
  getTenantId,
  getVisitTenantId,
  removeToken,
  setToken
} from '@/utils/auth'
import errorCode from './errorCode'

import { resetRouter } from '@/router'
import { deleteUserCache } from '@/hooks/web/useCache'
import { ApiEncrypt } from '@/utils/encrypt'
import { isOptionalReportContract404, refreshRequest, safeRequestError } from './privacy'
import { authSessionScope } from '@/utils/authSession'
import {
  AuthRefreshError,
  createAuthRefreshCoordinator,
  waitForAuthRefresh
} from './refreshCoordinator'

const tenantEnable = import.meta.env.VITE_APP_TENANT_ENABLE
const ownerPortal = import.meta.env.VITE_PORTAL_MODE === 'owner'
const ownerTenantId = Number(import.meta.env.VITE_OWNER_TENANT_ID || 0)
const { result_code, base_url, request_timeout } = config

// Known refresh failures use the normal login flow without echoing server text.
const ignoreMsgs = [
  '无效的刷新令牌', // 刷新令牌被删除时，不用提示
  '刷新令牌已过期' // 使用刷新令牌，刷新获取新的访问令牌时，结果因为过期失败，此时需要忽略。否则，会导致继续 401，无法跳转到登出界面
]

const isDisabledModuleMsg = (msg: unknown) =>
  typeof msg === 'string' &&
  msg.includes('yudao-module-') &&
  (msg.includes('已禁用') || msg.includes('表结构未导入'))

// 是否显示重新登录
export const isRelogin = { show: false }
type SessionRequestConfig = InternalAxiosRequestConfig & {
  authSessionContext?: string
  authReplayAttempt?: boolean
}
const sessionContext = () =>
  JSON.stringify([authSessionScope.version, getTenantId() ?? null, getVisitTenantId() ?? null])
const assertSession = (request: SessionRequestConfig) => {
  if (request.authSessionContext !== sessionContext()) throw new AuthRefreshError('CONTEXT')
}
// 请求白名单，无须 token 的接口
const whiteList: string[] = ['/login', '/refresh-token']
const skipsToken = (request: InternalAxiosRequestConfig) =>
  request.headers.isToken === false || whiteList.some((path) => request.url?.includes(path))

// 创建axios实例
const service: AxiosInstance = axios.create({
  baseURL: base_url, // api 的 base_url
  timeout: request_timeout, // 请求超时时间
  withCredentials: false, // 禁用 Cookie 等信息
  // 自定义参数序列化函数
  paramsSerializer: (params) => {
    return qs.stringify(params, { allowDots: true })
  }
})

const refreshCoordinator = createAuthRefreshCoordinator({
  context: sessionContext,
  subscribe: authSessionScope.subscribe,
  refresh: (signal) => refreshToken(signal),
  apply: (token) => setToken(token, { refresh: true })
})

// request拦截器
service.interceptors.request.use(
  (config: SessionRequestConfig) => {
    if (config.authSessionContext === undefined) config.authSessionContext = sessionContext()
    assertSession(config)
    // 是否需要设置 token
    if (getAccessToken() && !skipsToken(config)) {
      config.headers.Authorization = 'Bearer ' + getAccessToken() // 让每个请求携带自定义token
    } else delete config.headers.Authorization
    // 设置租户
    if (tenantEnable && tenantEnable === 'true') {
      const tenantId = getTenantId()
      if (tenantId) config.headers['tenant-id'] = tenantId
      else delete config.headers['tenant-id']
      // 只有登录时，才设置 visit-tenant-id 访问租户
      const visitTenantId = getVisitTenantId()
      if (config.headers.Authorization && visitTenantId) {
        config.headers['visit-tenant-id'] = visitTenantId
      } else delete config.headers['visit-tenant-id']
    }
    // The owner portal hides tenant selection from the user, but the backend
    // still requires the system-owner tenant context on every admin request.
    if (ownerPortal && ownerTenantId > 0) {
      config.headers['tenant-id'] = ownerTenantId
    }
    const method = config.method?.toUpperCase()
    // 防止 GET 请求缓存
    if (method === 'GET') {
      config.headers['Cache-Control'] = 'no-cache'
      config.headers['Pragma'] = 'no-cache'
    }
    // 自定义参数序列化函数
    else if (method === 'POST') {
      const contentType = config.headers['Content-Type'] || config.headers['content-type']
      if (contentType === 'application/x-www-form-urlencoded') {
        if (config.data && typeof config.data !== 'string') {
          config.data = qs.stringify(config.data)
        }
      }
    }
    // 是否 API 加密
    if ((config!.headers || {}).isEncrypt && !(config!.headers || {}).isEncrypted) {
      try {
        // 加密请求数据
        if (config.data) {
          config.data = ApiEncrypt.encryptRequest(config.data)
          // 设置加密标识头
          config.headers[ApiEncrypt.getEncryptHeader()] = 'true'
        }
      } catch {
        throw new Error('请求数据加密失败，请检查本地配置')
      }
    }
    return config
  },
  (error: AxiosError) => {
    if (error instanceof AuthRefreshError) return Promise.reject(error)
    return Promise.reject(safeRequestError(error))
  }
)

// response 拦截器
service.interceptors.response.use(
  async (response: AxiosResponse<any>) => {
    let { data } = response
    const config = response.config as SessionRequestConfig
    assertSession(config)
    if (!data) {
      // 返回“[HTTP]请求没有返回值”;
      throw new Error()
    }

    // 检查是否需要解密响应数据
    const encryptHeader = ApiEncrypt.getEncryptHeader()
    const isEncryptResponse =
      response.headers[encryptHeader] === 'true' ||
      response.headers[encryptHeader.toLowerCase()] === 'true'
    if (isEncryptResponse && typeof data === 'string') {
      try {
        // 解密响应数据
        data = ApiEncrypt.decryptResponse(data)
      } catch {
        throw new Error('响应数据解密失败，请检查本地配置')
      }
    }

    const { t } = useI18n()
    // 未设置状态码则默认成功状态
    // 二进制数据则直接返回，例如说 Excel 导出
    if (
      response.request.responseType === 'blob' ||
      response.request.responseType === 'arraybuffer'
    ) {
      // 注意：如果导出的响应为 json，说明可能失败了，不直接返回进行下载
      if (response.data.type !== 'application/json') {
        return response.data
      }
      data = await new Response(response.data).json()
      assertSession(config)
    }
    const code = data.code || result_code
    // 获取错误信息
    const msg = data.msg || errorCode[code] || errorCode['default']
    if (ignoreMsgs.indexOf(msg) !== -1) {
      return handleAuthorized(config.authSessionContext)
    } else if (isDisabledModuleMsg(msg)) {
      // 可选模块未启用时，不再频繁弹窗打断主业务流程
      console.warn('[disabled-module] 可选模块未启用')
      return Promise.reject(new Error('可选模块未启用'))
    } else if (code === 401) {
      if (config.authReplayAttempt) {
        refreshCoordinator.block(config.authSessionContext!)
        return handleAuthorized(config.authSessionContext)
      }
      if (skipsToken(config)) return handleAuthorized(config.authSessionContext)
      try {
        if (config.signal?.aborted) throw new AuthRefreshError('CANCELED')
        // A late 401 for an already-rotated token needs one replay, not another refresh.
        if (!getAccessToken() || config.headers.Authorization === 'Bearer ' + getAccessToken()) {
          await waitForAuthRefresh(
            refreshCoordinator.run(config.authSessionContext!),
            config.signal
          )
        }
        assertSession(config)
        if (config.signal?.aborted) throw new AuthRefreshError('CANCELED')
        config.authReplayAttempt = true
        if (config.headers.isEncrypt) config.headers.isEncrypted = true
        return service(config)
      } catch (error) {
        if (
          error instanceof AuthRefreshError &&
          (error.code === 'CONTEXT' || error.code === 'CANCELED')
        )
          throw error
        assertSession(config)
        return handleAuthorized(config.authSessionContext)
      }
    } else if (code === 500) {
      ElMessage.error(t('sys.api.errMsg500'))
      return Promise.reject(new Error(t('sys.api.errMsg500')))
    } else if (code === 901) {
      ElMessage.error({
        offset: 300,
        dangerouslyUseHTMLString: true,
        message:
          '<div>' +
          t('sys.api.errMsg901') +
          '</div>' +
          '<div> &nbsp; </div>' +
          '<div>参考 https://doc.iocoder.cn/ 教程</div>' +
          '<div> &nbsp; </div>' +
          '<div>5 分钟搭建本地环境</div>'
      })
      return Promise.reject(new Error(t('sys.api.errMsg901')))
    } else if (code !== 200) {
      if (msg === '无效的刷新令牌') {
        // hard coding：忽略这个提示，直接登出
        return handleAuthorized()
      } else {
        ElNotification.error({ title: msg })
      }
      return Promise.reject('error')
    } else {
      return data
    }
  },
  (error: AxiosError) => {
    if (error instanceof AuthRefreshError) return Promise.reject(error)
    const failedConfig = error.config as SessionRequestConfig | undefined
    if (
      failedConfig?.authSessionContext !== undefined &&
      failedConfig.authSessionContext !== sessionContext()
    ) {
      return Promise.reject(new AuthRefreshError('CONTEXT'))
    }
    const safeError = safeRequestError(error)
    const { t } = useI18n()
    let message = safeError.message
    if (safeError.code === 'ERR_NETWORK') {
      message = t('sys.api.errorMessage')
    } else if (safeError.code === 'ECONNABORTED' || safeError.code === 'ETIMEDOUT') {
      message = t('sys.api.apiTimeoutMessage')
    } else if (safeError.response?.status) {
      message = t('sys.api.apiRequestFailed') + safeError.response.status
    }
    if (!isOptionalReportContract404(error)) ElMessage.error(message)
    return Promise.reject(safeError)
  }
)

const refreshToken = async (signal: AbortSignal) => {
  const credential = getRefreshToken()
  if (typeof credential !== 'string' || !credential) throw new AuthRefreshError('FAILED')
  // Capture tenant per request; do not mutate global Axios defaults or put a
  // credential in the URL (browser/proxy/access logs may retain that URL).
  const refresh = refreshRequest(
    base_url,
    credential,
    ownerPortal && ownerTenantId > 0 ? ownerTenantId : getTenantId()
  )
  const response = await axios.post(refresh.url, refresh.data, {
    ...refresh.config,
    timeout: 30000,
    signal
  })
  const token = response.data?.data
  if (
    response.data?.code !== result_code ||
    !token ||
    typeof token.accessToken !== 'string' ||
    !token.accessToken ||
    typeof token.refreshToken !== 'string' ||
    !token.refreshToken
  ) {
    throw new AuthRefreshError('FAILED')
  }
  return { accessToken: token.accessToken as string, refreshToken: token.refreshToken as string }
}
const handleAuthorized = (expected = sessionContext()) => {
  if (expected !== sessionContext()) return Promise.reject(new AuthRefreshError('CONTEXT'))
  const { t } = useI18n()
  if (!isRelogin.show) {
    // 如果已经到登录页面则不进行弹窗提示
    if (window.location.href.includes('login')) {
      return Promise.reject(new AuthRefreshError('FAILED'))
    }
    isRelogin.show = true
    ElMessageBox.confirm(t('sys.api.timeoutMessage'), t('common.confirmTitle'), {
      showCancelButton: false,
      closeOnClickModal: false,
      showClose: false,
      closeOnPressEscape: false,
      confirmButtonText: t('login.relogin'),
      type: 'warning'
    })
      .then(() => {
        if (expected !== sessionContext()) return
        resetRouter() // 重置静态路由表
        deleteUserCache() // 删除用户缓存
        removeToken()
        // 干掉token后再走一次路由让它过router.beforeEach的校验
        window.location.href = window.location.href
      })
      .catch(() => {
        /* no raw dialog failures in diagnostics */
      })
      .finally(() => {
        isRelogin.show = false
      })
  }
  return Promise.reject(new AuthRefreshError('FAILED'))
}
export { service }
