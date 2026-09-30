import { request } from '../utils/request'

export function getLatestAiSummary() {
  return request({ url: '/app-patient/ai/summary/latest' })
}

export function getLatestAiFollowup() {
  return request({ url: '/app-patient/ai/followup/latest' })
}
