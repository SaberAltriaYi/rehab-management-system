import request from '@/config/axios'

export type LeaveVO = {
  id: number
  status: number
  type: number
  reason: string
  processInstanceId: string
  startTime: string
  endTime: string
  createTime: string
}

// A new request has no server-assigned id, state, process instance or creation time.
export type LeaveCreateReqVO = Pick<LeaveVO, 'type' | 'reason' | 'startTime' | 'endTime'> & {
  startUserSelectAssignees?: Record<string, number[]>
}

// 创建请假申请
export const createLeave = async (data: LeaveCreateReqVO) => {
  return await request.post({ url: '/bpm/oa/leave/create', data: data })
}

// 获得请假申请
export const getLeave = async (id: number) => {
  return await request.get({ url: '/bpm/oa/leave/get?id=' + id })
}

// 获得请假申请分页
export const getLeavePage = async (params: PageParam) => {
  return await request.get({ url: '/bpm/oa/leave/page', params })
}
