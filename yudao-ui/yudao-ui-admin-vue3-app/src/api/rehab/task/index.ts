import request from '@/config/axios'

export interface RehabTaskPageReqVO {
  pageNo: number
  pageSize: number
  planId?: number
  patientId?: number
  episodeId?: number
  status?: string
}

export const getRehabTaskPage = async (params: RehabTaskPageReqVO) => {
  return await request.get({ url: '/rehab/task/page', params })
}

export const getRehabTask = async (id: number) => {
  return await request.get({ url: '/rehab/task/get', params: { id } })
}

export const createRehabTask = async (data: Record<string, any>) => {
  return await request.post({ url: '/rehab/task/create', data })
}

export const updateRehabTask = async (data: Record<string, any>) => {
  return await request.put({ url: '/rehab/task/update', data })
}

export const sortRehabTasks = async (data: { planId: number; items: Array<{ id: number; sortOrder: number }> }) => {
  return await request.post({ url: '/rehab/task/sort', data })
}

export const disableRehabTask = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/task/disable', data })
}

export const enableRehabTask = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/task/enable', data })
}

export const getRehabTaskListByPlan = async (planId: number) => {
  return await request.get({ url: '/rehab/task/list-by-plan', params: { planId } })
}
