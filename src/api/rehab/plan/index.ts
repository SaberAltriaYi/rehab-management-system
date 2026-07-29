import request from '@/config/axios'

export interface RehabCarePlanPageReqVO {
  pageNo: number
  pageSize: number
  patientId?: number
  episodeId?: number
  primaryTherapistUserId?: number
  status?: string
  planType?: string
  createTime?: string[]
}

export const getRehabPlanPage = async (params: RehabCarePlanPageReqVO) => {
  return await request.get({ url: '/rehab/plan/page', params })
}

export const getRehabPlan = async (id: number) => {
  return await request.get({ url: '/rehab/plan/get', params: { id } })
}

export const createRehabPlan = async (data: Record<string, any>) => {
  return await request.post({ url: '/rehab/plan/create', data })
}

export const updateRehabPlan = async (data: Record<string, any>) => {
  return await request.put({ url: '/rehab/plan/update', data })
}

export const activateRehabPlan = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/plan/activate', data })
}

export const pauseRehabPlan = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/plan/pause', data })
}

export const completeRehabPlan = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/plan/complete', data })
}

export const copyRehabPlan = async (data: { id: number; planName?: string; activate?: boolean; remark?: string }) => {
  return await request.post({ url: '/rehab/plan/copy', data })
}

export const deleteRehabPlan = async (id: number) => {
  return await request.delete({ url: '/rehab/plan/delete', params: { id } })
}

export const getRehabPlanOperationLog = async (planId: number) => {
  return await request.get({ url: '/rehab/plan/operation-log', params: { planId } })
}
