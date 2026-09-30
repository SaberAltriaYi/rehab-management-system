import { request } from '../utils/request'

export function createFollowup(data) {
  return request({ url: '/app-admin/followup-note/create', method: 'POST', data })
}

export function getFollowupPage(params) {
  return request({ url: '/app-admin/followup-note/page', data: params })
}
