import { request } from '../utils/request'

export function login(data) {
  return request({ url: '/app-admin/auth/login', method: 'POST', data, auth: false })
}
