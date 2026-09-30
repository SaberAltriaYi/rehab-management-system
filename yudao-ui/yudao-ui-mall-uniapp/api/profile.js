import { request } from '../utils/request'

export function getProfile() {
  return request({ url: '/app-patient/profile' })
}
