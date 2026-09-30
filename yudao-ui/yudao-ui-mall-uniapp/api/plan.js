import { request } from '../utils/request'

export function getCurrentPlan() {
  return request({ url: '/app-patient/plan/current' })
}

export function getTodayTasks() {
  return request({ url: '/app-patient/tasks/today' })
}
