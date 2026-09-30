import request from '@/config/axios'

// 与 PayNotifyTaskDetailRespVO 对应。
export interface NotifyTaskDetailVO {
  id: number
  status: number
  merchantOrderId?: string
  merchantRefundId?: string
  merchantTransferId?: string
  appId: number
  appName?: string
  dataId: number
  type: number
  notifyTimes: number
  maxNotifyTimes: number
  lastExecuteTime?: string
  nextNotifyTime?: string
  createTime: string
  updateTime: string
  logs?: Array<{ id: number; status: number; notifyTimes: number; response: string; createTime: string }>
}

// 获得支付通知明细
export const getNotifyTaskDetail = (id: number) => {
  return request.get<NotifyTaskDetailVO>({
    url: '/pay/notify/get-detail?id=' + id
  })
}

// 获得支付通知分页
export const getNotifyTaskPage = (query) => {
  return request.get({
    url: '/pay/notify/page',
    params: query
  })
}
