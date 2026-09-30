import { request } from '../utils/request'

export function getHomeSummary() {
  return request({ url: '/app-patient/home/summary' })
}
