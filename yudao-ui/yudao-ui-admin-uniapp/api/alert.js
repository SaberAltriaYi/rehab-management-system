import { request } from '../utils/request'

export function getAlertPage(params) {
  return request({ url: '/app-admin/alerts/page', data: params })
}

export function acknowledgeAlert(id, remark = '') {
  return request({ url: '/rehab/alert/acknowledge', method: 'POST', data: { id, remark } })
}
