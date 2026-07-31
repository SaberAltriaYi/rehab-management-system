import request from '@/config/axios'

export interface RehabAuditLogPageReqVO {
  pageNo: number
  pageSize: number
  moduleType?: string
  moduleId?: number
  operationType?: string
  operatorUserId?: number
  resultStatus?: string
  createTime?: string[]
}

export const getRehabAuditLogPage = async (params: RehabAuditLogPageReqVO) => {
  return await request.get({ url: '/rehab/audit-log/page', params })
}
