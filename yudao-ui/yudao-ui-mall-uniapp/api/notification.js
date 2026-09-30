import { request } from '../utils/request'

export function getNotifications(params) {
  return request({ url: '/app-patient/notifications/page', data: params })
}

export function readNotification(id) {
  return request({ url: '/app-patient/notifications/read', method: 'POST', data: { id } })
}
