import { BASE_URL, APP_API_PREFIX } from '../common/config'
import { clearAuth, getToken } from './auth'

export function request(options) {
  const { url, method = 'GET', data = {}, auth = true } = options
  const header = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (auth && token) {
    header.Authorization = `Bearer ${token}`
  }
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}${APP_API_PREFIX}${url}`,
      method,
      data,
      header,
      success: (res) => {
        const payload = res.data || {}
        if (payload.code === 0) {
          resolve(payload.data)
          return
        }
        if (res.statusCode === 401 || payload.code === 401) {
          clearAuth()
          uni.reLaunch({ url: '/pages/login/index' })
        }
        uni.showToast({ title: payload.msg || '请求失败', icon: 'none' })
        reject(payload)
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常', icon: 'none' })
        reject(err)
      }
    })
  })
}
