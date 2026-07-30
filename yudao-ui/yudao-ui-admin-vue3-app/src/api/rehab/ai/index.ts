import request from '@/config/axios'

export interface RehabAiJobPageReqVO {
  pageNo: number
  pageSize: number
  patientId?: number
  jobType?: string
  status?: string
  triggeredByUserId?: number
  createTime?: string[]
}

export interface RehabAiOutputPageReqVO {
  pageNo: number
  pageSize: number
  patientId?: number
  outputType?: string
  targetObjectType?: string
  reviewStatus?: string
  safetyStatus?: string
  createTime?: string[]
}

export interface RehabAiGenerateRespVO {
  jobId: number
  jobStatus: string
  outputId?: number
  reviewStatus?: string
  fallbackUsed?: boolean
  renderedText?: string
  contentJson?: string
  asyncMode?: boolean
}

export const getRehabAiJobPage = async (params: RehabAiJobPageReqVO) => {
  return await request.get({ url: '/rehab/ai/job/page', params })
}

export const getRehabAiJob = async (id: number) => {
  return await request.get({ url: '/rehab/ai/job/get', params: { id } })
}

export const getRehabAiOutputPage = async (params: RehabAiOutputPageReqVO) => {
  return await request.get({ url: '/rehab/ai/output/page', params })
}

export const getRehabAiOutput = async (id: number) => {
  return await request.get({ url: '/rehab/ai/output/get', params: { id } })
}

export const generateAssessmentInterpretation = async (data: { assessmentId: number; asyncMode?: boolean }) => {
  return await request.post({ url: '/rehab/ai/generate/assessment-interpretation', data })
}

export const generateReportSummary = async (data: { reportId: number; asyncMode?: boolean }) => {
  return await request.post({ url: '/rehab/ai/generate/report-summary', data })
}

export const generateRiskExplanation = async (data: {
  patientId?: number
  alertId?: number
  triggerId?: number
  progressId?: number
  asyncMode?: boolean
}) => {
  return await request.post({ url: '/rehab/ai/generate/risk-explanation', data })
}

export const generatePlanDraft = async (data: {
  patientId?: number
  episodeId?: number
  assessmentId?: number
  reportId?: number
  progressId?: number
  asyncMode?: boolean
}) => {
  return await request.post({ url: '/rehab/ai/generate/plan-draft', data })
}

export const generateFollowupMessage = async (data: {
  patientId?: number
  episodeId?: number
  progressId?: number
  triggerId?: number
  asyncMode?: boolean
}) => {
  return await request.post({ url: '/rehab/ai/generate/followup-message', data })
}

export const generateProgressSummary = async (data: { progressId: number; asyncMode?: boolean }) => {
  return await request.post({ url: '/rehab/ai/generate/progress-summary', data })
}

export const acceptRehabAiOutput = async (data: { outputId: number; patientVisible?: boolean; reviewNote?: string }) => {
  return await request.post({ url: '/rehab/ai/output/accept', data })
}

export const editRehabAiOutput = async (data: {
  outputId: number
  editedText: string
  patientVisible?: boolean
  reviewNote?: string
}) => {
  return await request.post({ url: '/rehab/ai/output/edit', data })
}

export const rejectRehabAiOutput = async (data: { outputId: number; reviewNote?: string }) => {
  return await request.post({ url: '/rehab/ai/output/reject', data })
}

export const regenerateRehabAiOutput = async (data: { outputId: number; asyncMode?: boolean }) => {
  return await request.post({ url: '/rehab/ai/output/regenerate', data })
}

export const getRehabAiConfig = async () => {
  return await request.get({ url: '/rehab/ai/config/get' })
}

export const updateRehabAiConfig = async (data: Record<string, unknown>) => {
  return await request.put({ url: '/rehab/ai/config/update', data })
}

export const getRehabAiPromptTemplatePage = async (params: {
  pageNo: number
  pageSize: number
  templateCode?: string
  templateName?: string
  moduleScope?: string
  roleScope?: string
  enabled?: boolean
}) => {
  return await request.get({ url: '/rehab/ai/prompt-template/page', params })
}

export const getRehabAiPromptTemplate = async (id: number) => {
  return await request.get({ url: '/rehab/ai/prompt-template/get', params: { id } })
}

export const createRehabAiPromptTemplate = async (data: Record<string, unknown>) => {
  return await request.post({ url: '/rehab/ai/prompt-template/create', data })
}

export const updateRehabAiPromptTemplate = async (data: Record<string, unknown>) => {
  return await request.put({ url: '/rehab/ai/prompt-template/update', data })
}

export const enableRehabAiPromptTemplate = async (data: { id: number; enabled: boolean }) => {
  return await request.post({ url: '/rehab/ai/prompt-template/enable', data })
}

export const setDefaultRehabAiPromptTemplate = async (data: { id: number }) => {
  return await request.post({ url: '/rehab/ai/prompt-template/set-default', data })
}
