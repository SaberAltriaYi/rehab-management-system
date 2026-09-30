import { request } from '../utils/request'

export function createCheckin(data) {
  return request({ url: '/app-patient/checkin/create', method: 'POST', data })
}

export function getCheckinHistory(params) {
  return request({ url: '/app-patient/checkin/history', data: params })
}
