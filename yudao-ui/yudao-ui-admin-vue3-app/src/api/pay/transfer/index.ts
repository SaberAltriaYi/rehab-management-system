import request from '@/config/axios'

// 与 PayTransferRespVO 对应。
export interface TransferDetailVO {
  id: number
  no: string
  appId: number
  merchantTransferId: string
  status: number
  price: number
  successTime?: string
  createTime: string
  userName?: string
  userAccount: string
  channelCode: string
  userIp: string
  channelTransferNo?: string
  notifyUrl: string
  channelNotifyData?: string
}

// 查询转账单列表
export const getTransferPage = async (params: PageParam) => {
  return await request.get({ url: `/pay/transfer/page`, params })
}

// 查询转账单详情
export const getTransfer = async (id: number) => {
  return await request.get<TransferDetailVO>({ url: '/pay/transfer/get?id=' + id })
}

// 导出转账单
export const exportTransfer = async (params: PageParam) => {
  return await request.download({ url: '/pay/transfer/export-excel', params })
}
