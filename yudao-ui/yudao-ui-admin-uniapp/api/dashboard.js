import { request } from '../utils/request'

export function getDashboardSummary() {
  return request({ url: '/app-admin/dashboard/summary' })
}
