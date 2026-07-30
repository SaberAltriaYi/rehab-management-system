import request from '@/config/axios'

// 工作台摘要
export const getRehabDashboardSummary = async () => {
  return await request.get({ url: '/rehab/dashboard/summary' })
}

// 工作台最近事项
export const getRehabDashboardRecentItems = async () => {
  return await request.get({ url: '/rehab/dashboard/recent-items' })
}

// 运营看板摘要
export const getRehabOpsDashboardSummary = async () => {
  return await request.get({ url: '/rehab/ops-dashboard/summary' })
}

// 运营看板负载
export const getRehabOpsDashboardWorkload = async () => {
  return await request.get({ url: '/rehab/ops-dashboard/workload' })
}

// 运营看板风险分布
export const getRehabOpsDashboardRiskOverview = async () => {
  return await request.get({ url: '/rehab/ops-dashboard/risk-overview' })
}
