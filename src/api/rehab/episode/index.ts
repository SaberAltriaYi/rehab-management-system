import request from '@/config/axios'

export interface RehabEpisodePageReqVO {
  pageNo: number
  pageSize: number
  patientId?: number
  status?: string
  currentStage?: string
}

export const createRehabEpisode = async (data: Record<string, any>) => {
  return await request.post({ url: '/rehab/episode/create', data })
}

export const getRehabEpisodePage = async (params: RehabEpisodePageReqVO) => {
  return await request.get({ url: '/rehab/episode/page', params })
}

export const getRehabEpisode = async (id: number) => {
  return await request.get({ url: '/rehab/episode/get', params: { id } })
}

export const updateRehabEpisode = async (data: Record<string, any>) => {
  return await request.put({ url: '/rehab/episode/update', data })
}

export const changeRehabEpisodeStage = async (data: Record<string, any>) => {
  return await request.post({ url: '/rehab/episode/change-stage', data })
}
