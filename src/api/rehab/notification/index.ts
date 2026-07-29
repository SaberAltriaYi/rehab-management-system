import request from '@/config/axios'

export interface RehabNotificationPageReqVO {
  pageNo: number
  pageSize: number
  patientId?: number
  notificationType?: string
  severity?: string
  readStatus?: string
  sendStatus?: string
  targetType?: string
  targetUserId?: number
  onlyMine?: boolean
  createTime?: string[]
}

export const getRehabNotificationPage = async (params: RehabNotificationPageReqVO) => {
  return await request.get({ url: '/rehab/notification/page', params })
}

export const readRehabNotification = async (id: number) => {
  return await request.post({ url: '/rehab/notification/read', data: { id } })
}

export const readAllRehabNotification = async () => {
  return await request.post({ url: '/rehab/notification/read-all' })
}

export const createRehabNotification = async (data: any) => {
  return await request.post({ url: '/rehab/notification/create', data })
}

export const deleteRehabNotification = async (id: number) => {
  return await request.delete({ url: '/rehab/notification/delete', params: { id } })
}
