import { apiRequest } from './api'

export interface PageResult<T> { list: T[]; total: number }
export interface PatientMini {
  id: number; patientNo: string; name: string; currentStage?: string; activePlanStatus?: string;
  latestCheckinDate?: string; hasHighRiskTrigger?: boolean
}
export interface PatientTask {
  id: number
  taskName?: string
  moduleType?: string
  dosageText?: string
  sets?: number
  repetitions?: number
  instructionText?: string
  painLimitRule?: string
}

export interface NotificationItem {
  id: number; title?: string; content?: string; readStatus?: string; notificationType?: string;
  severity?: string; patientName?: string; createTime?: string
}

const staffBase = '/admin-api/app-admin'
const patientBase = '/app-api/app-patient'

export const staffApi = {
  dashboard: () => apiRequest<Record<string, number>>(`${staffBase}/dashboard/summary`),
  patients: (pageNo = 1, pageSize = 30) => apiRequest<PageResult<PatientMini>>(`${staffBase}/patients/my-page`, { data: { pageNo, pageSize } }),
  patientSummary: (id: number) => apiRequest<Record<string, any>>(`${staffBase}/patients/summary`, { data: { id } }),
  notifications: (pageNo = 1, pageSize = 50) => apiRequest<PageResult<NotificationItem>>(`${staffBase}/notifications/page`, { data: { pageNo, pageSize } }),
  readNotification: (id: number) => apiRequest<boolean>(`${staffBase}/notifications/read`, { method: 'POST', data: { id } }),
}

export const patientApi = {
  home: () => apiRequest<Record<string, any>>(`${patientBase}/home/summary`),
  currentPlan: () => apiRequest<Record<string, any> | null>(`${patientBase}/plan/current`),
  todayTasks: () => apiRequest<PatientTask[]>(`${patientBase}/tasks/today`),
  createCheckin: (payload: Record<string, unknown>) => apiRequest<number>(`${patientBase}/checkin/create`, { method: 'POST', data: payload }),
  reports: (pageNo = 1, pageSize = 30) => apiRequest<PageResult<Record<string, any>>>(`${patientBase}/reports/page`, { data: { pageNo, pageSize } }),
  notifications: (pageNo = 1, pageSize = 50) => apiRequest<PageResult<NotificationItem>>(`${patientBase}/notifications/page`, { data: { pageNo, pageSize } }),
  readNotification: (id: number) => apiRequest<boolean>(`${patientBase}/notifications/read`, { method: 'POST', data: { id } }),
}
