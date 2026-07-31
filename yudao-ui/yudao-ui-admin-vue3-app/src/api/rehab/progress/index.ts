import request from '@/config/axios'

export interface RehabProgressPageReqVO {
  pageNo: number
  pageSize: number
  planId?: number
  patientId?: number
  episodeId?: number
  createTime?: string[]
}

export const getRehabProgressPage = async (params: RehabProgressPageReqVO) => {
  return await request.get({ url: '/rehab/progress/page', params })
}

export const getRehabProgress = async (id: number) => {
  return await request.get({ url: '/rehab/progress/get', params: { id } })
}

export const recalculateRehabProgress = async (data: { planId: number; periodStart?: string; periodEnd?: string; remark?: string }) => {
  return await request.post({ url: '/rehab/progress/recalculate', data })
}
