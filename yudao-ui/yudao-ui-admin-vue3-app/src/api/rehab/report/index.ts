import request from '@/config/axios'

export interface RehabReportPageReqVO {
  pageNo: number
  pageSize: number
  patientId?: number
  episodeId?: number
  assessmentId?: number
  keyword?: string
  reportType?: string
  reportStatus?: string
  generationMode?: string
  generatedBy?: number
  createTime?: string[]
}

export interface RehabReportPatientRespVO {
  patientId: number
  patientNo: string
  patientName: string
  reportCount: number
  assessmentCount: number
  latestReportTime?: string
}

export const getRehabReportPage = async (params: RehabReportPageReqVO) => {
  return await request.get({ url: '/rehab/report/page', params })
}

export const getRehabReportPatientPage = async (params: RehabReportPageReqVO) => {
  return await request.get({ url: '/rehab/report/patient-page', params })
}

export const getRehabReport = async (id: number) => {
  return await request.get({ url: '/rehab/report/get', params: { id } })
}

export const generateRehabReport = async (data: {
  assessmentId: number
  reportType?: string
  generationMode?: string
  note?: string
}) => {
  return await request.post({ url: '/rehab/report/generate', data, timeout: 60000 })
}

export const previewRehabReport = async (id: number) => {
  return await request.get({ url: '/rehab/report/preview', params: { id } })
}

export const reviewRehabReport = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/report/review', data })
}

export const approveRehabReport = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/report/approve', data })
}

export const lockRehabReport = async (data: { id: number; reason: string }) => {
  return await request.post({ url: '/rehab/report/lock', data })
}

export const unlockRehabReport = async (data: { id: number; reason: string }) => {
  return await request.post({ url: '/rehab/report/unlock', data })
}

export const exportRehabReportDocx = async (id: number) => {
  return await request.download({
    url: '/rehab/report/export-docx',
    params: { id },
    timeout: 120000
  })
}

export const exportRehabReportPdf = async (id: number) => {
  return await request.download({
    url: '/rehab/report/export-pdf',
    params: { id },
    timeout: 120000
  })
}

export const getRehabReportByAssessment = async (assessmentId: number) => {
  return await request.get({ url: '/rehab/report/by-assessment', params: { assessmentId } })
}

export const getRehabReportVersionPage = async (params: {
  reportId: number
  pageNo: number
  pageSize: number
}) => {
  return await request.get({ url: '/rehab/report/version/page', params })
}

export const getRehabReportAuditLogs = async (reportId: number) => {
  return await request.get({ url: '/rehab/report/audit-log', params: { reportId } })
}
