import request from '@/config/axios'

export interface RehabCheckinPageReqVO {
  pageNo: number
  pageSize: number
  patientId?: number
  episodeId?: number
  planId?: number
  submitRoleType?: string
  checkinDate?: string[]
}

export const getRehabCheckinPage = async (params: RehabCheckinPageReqVO) => {
  return await request.get({ url: '/rehab/checkin/page', params })
}

export const getRehabCheckin = async (id: number) => {
  return await request.get({ url: '/rehab/checkin/get', params: { id } })
}

export const createRehabCheckin = async (data: Record<string, any>) => {
  return await request.post({ url: '/rehab/checkin/create', data })
}

export const createRehabCheckinManual = async (data: Record<string, any>) => {
  return await request.post({ url: '/rehab/checkin/create-manual', data })
}

export interface RehabTrainingAttendanceCreateReqVO {
  patientId: number
  planId: number
  trainingDate: string
  note?: string
}

export const createRehabTrainingAttendance = async (data: RehabTrainingAttendanceCreateReqVO) => {
  return await request.post({ url: '/rehab/checkin/create-attendance', data })
}

export const getRehabCheckinTaskExecutions = async (checkinId: number) => {
  return await request.get({ url: '/rehab/checkin/task-executions', params: { checkinId } })
}
