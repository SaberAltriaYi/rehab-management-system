import request from '@/config/axios'

export interface RehabAlertPageReqVO {
  pageNo: number
  pageSize: number
  patientId?: number
  episodeId?: number
  planId?: number
  alertType?: string
  severity?: string
  status?: string
  createTime?: string[]
}

export const getRehabAlertPage = async (params: RehabAlertPageReqVO) => {
  return await request.get({ url: '/rehab/alert/page', params })
}

export const refreshRehabAlert = async (data: { patientId?: number; planId?: number }) => {
  return await request.post({ url: '/rehab/alert/refresh', data })
}

export const acknowledgeRehabAlert = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/alert/acknowledge', data })
}

export const resolveRehabAlert = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/alert/resolve', data })
}

export const ignoreRehabAlert = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/alert/ignore', data })
}
