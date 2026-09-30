import { request } from '../utils/request'

export function getReports(params) {
  return request({ url: '/app-patient/reports/page', data: params })
}

export function getReportDetail(id) {
  return request({ url: '/app-patient/reports/get', data: { id } })
}
