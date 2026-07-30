import request from '@/config/axios'

export interface RehabTriggerPageReqVO {
  pageNo: number
  pageSize: number
  planId?: number
  patientId?: number
  episodeId?: number
  triggerType?: string
  triggerLevel?: string
  triggerStatus?: string
  createTime?: string[]
}

export const getRehabTriggerPage = async (params: RehabTriggerPageReqVO) => {
  return await request.get({ url: '/rehab/reassessment-trigger/page', params })
}

export const getRehabTrigger = async (id: number) => {
  return await request.get({ url: '/rehab/reassessment-trigger/get', params: { id } })
}

export const createRehabTrigger = async (data: Record<string, any>) => {
  return await request.post({ url: '/rehab/reassessment-trigger/create', data })
}

export const acknowledgeRehabTrigger = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/reassessment-trigger/acknowledge', data })
}

export const convertRehabTrigger = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/reassessment-trigger/convert-to-reassessment', data })
}

export const dismissRehabTrigger = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/reassessment-trigger/dismiss', data })
}
