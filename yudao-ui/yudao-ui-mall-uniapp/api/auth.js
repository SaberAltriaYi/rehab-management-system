import { request } from '../utils/request'

export function login(data) {
  return request({ url: '/app-patient/auth/login', method: 'POST', data, auth: false })
}

export function bind(data) {
  return request({ url: '/app-patient/auth/bind', method: 'POST', data, auth: false })
}
