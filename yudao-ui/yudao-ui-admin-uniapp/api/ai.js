import { request } from '../utils/request'

export function getLatestAiSummary(patientId) {
  return request({ url: '/app-admin/ai/summary/latest', data: { patientId } })
}

export function getLatestAiFollowup(patientId) {
  return request({ url: '/app-admin/ai/followup/latest', data: { patientId } })
}
