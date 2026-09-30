const TOKEN_KEY = 'REHAB_PATIENT_ACCESS_TOKEN'
const USER_KEY = 'REHAB_PATIENT_USER'

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY)
}

export function setToken(token) {
  uni.setStorageSync(TOKEN_KEY, token || '')
}

export function setLoginUser(user) {
  uni.setStorageSync(USER_KEY, user || {})
}

export function getLoginUser() {
  return uni.getStorageSync(USER_KEY) || {}
}

export function clearAuth() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}
