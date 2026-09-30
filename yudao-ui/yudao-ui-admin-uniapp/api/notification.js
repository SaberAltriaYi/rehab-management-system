import { request } from '../utils/request'

export function getNotificationPage(params) {
  return request({ url: '/app-admin/notifications/page', data: params })
}

export function readNotification(id) {
  return request({ url: '/app-admin/notifications/read', method: 'POST', data: { id } })
}

