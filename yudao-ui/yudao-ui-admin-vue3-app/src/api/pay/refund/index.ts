import request from '@/config/axios'

// 与 PayRefundDetailsRespVO/PayRefundBaseVO 对应。
export interface RefundDetailVO {
  id: number
  merchantRefundId: string
  channelRefundNo?: string
  merchantOrderId: string
  channelOrderNo?: string
  appId: number
  appName: string
  payPrice: number
  refundPrice: number
  status: number
  successTime?: string
  createTime: string
  updateTime: string
  channelCode: string
  reason: string
  userIp: string
  notifyUrl: string
  channelErrorCode?: string
  channelErrorMsg?: string
  channelNotifyData?: string
}

export interface RefundVO {
  id: number
  merchantId: number
  appId: number
  channelId: number
  channelCode: string
  orderId: string
  tradeNo: string
  merchantOrderId: string
  merchantRefundNo: string
  notifyUrl: string
  notifyStatus: number
  status: number
  type: number
  payAmount: number
  refundAmount: number
  reason: string
  userIp: string
  channelOrderNo: string
  channelRefundNo: string
  channelErrorCode: string
  channelErrorMsg: string
  channelExtras: string
  expireTime: Date
  successTime: Date
  notifyTime: Date
  createTime: Date
}

export interface RefundPageReqVO extends PageParam {
  merchantId?: number
  appId?: number
  channelId?: number
  channelCode?: string
  orderId?: string
  tradeNo?: string
  merchantOrderId?: string
  merchantRefundNo?: string
  notifyUrl?: string
  notifyStatus?: number
  status?: number
  type?: number
  payAmount?: number
  refundAmount?: number
  reason?: string
  userIp?: string
  channelOrderNo?: string
  channelRefundNo?: string
  channelErrorCode?: string
  channelErrorMsg?: string
  channelExtras?: string
  expireTime?: Date[]
  successTime?: Date[]
  notifyTime?: Date[]
  createTime?: Date[]
}

export interface PayRefundExportReqVO {
  merchantId?: number
  appId?: number
  channelId?: number
  channelCode?: string
  orderId?: string
  tradeNo?: string
  merchantOrderId?: string
  merchantRefundNo?: string
  notifyUrl?: string
  notifyStatus?: number
  status?: number
  type?: number
  payAmount?: number
  refundAmount?: number
  reason?: string
  userIp?: string
  channelOrderNo?: string
  channelRefundNo?: string
  channelErrorCode?: string
  channelErrorMsg?: string
  channelExtras?: string
  expireTime?: Date[]
  successTime?: Date[]
  notifyTime?: Date[]
  createTime?: Date[]
}

// 查询列表退款订单
export const getRefundPage = (params: RefundPageReqVO) => {
  return request.get({ url: '/pay/refund/page', params })
}

// 查询详情退款订单
export const getRefund = (id: number) => {
  return request.get<RefundDetailVO>({ url: '/pay/refund/get?id=' + id })
}

// 新增退款订单
export const createRefund = (data: RefundVO) => {
  return request.post({ url: '/pay/refund/create', data })
}

// 修改退款订单
export const updateRefund = (data: RefundVO) => {
  return request.put({ url: '/pay/refund/update', data })
}

// 删除退款订单
export const deleteRefund = (id: number) => {
  return request.delete({ url: '/pay/refund/delete?id=' + id })
}

// 导出退款订单
export const exportRefund = (params: PayRefundExportReqVO) => {
  return request.download({ url: '/pay/refund/export-excel', params })
}
