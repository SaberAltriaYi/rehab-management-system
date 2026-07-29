import request from '@/config/axios'

export interface RehabAssessmentPageReqVO {
  pageNo: number
  pageSize: number
  patientId?: number
  episodeId?: number
  keyword?: string
  assessmentType?: string
  assessmentDate?: string[]
  assessorUserId?: number
  status?: string
}

export interface RehabAssessmentModuleDataItemVO {
  moduleType: string
  moduleStatus?: string
  dataJson?: any
  sourceType?: string
  version?: string
  note?: string
}

export interface RehabAssessmentCreateReqVO {
  patientId: number
  episodeId: number
  assessmentType: string
  assessmentDate: string
  assessorUserId?: number
  locationType?: string
  status?: string
  chiefFocus?: string
  painScore?: number
  redFlagNotes?: string
  sourceSummary?: string
  note?: string
  moduleDataList?: RehabAssessmentModuleDataItemVO[]
}

export interface RehabAssessmentUpdateReqVO extends RehabAssessmentCreateReqVO {
  id: number
}

export const getRehabAssessmentPage = async (params: RehabAssessmentPageReqVO) => {
  return await request.get({ url: '/rehab/assessment/page', params })
}

export const getRehabAssessment = async (id: number) => {
  return await request.get({ url: '/rehab/assessment/get', params: { id } })
}

export const createRehabAssessment = async (data: RehabAssessmentCreateReqVO) => {
  return await request.post({ url: '/rehab/assessment/create', data })
}

export const updateRehabAssessment = async (data: RehabAssessmentUpdateReqVO) => {
  return await request.put({ url: '/rehab/assessment/update', data })
}

export const deleteRehabAssessment = async (id: number) => {
  return await request.delete({ url: '/rehab/assessment/delete', params: { id } })
}

export const archiveRehabAssessment = async (data: { id: number; remark?: string }) => {
  return await request.post({ url: '/rehab/assessment/archive', data })
}

export const getRehabAssessmentModuleData = async (assessmentId: number) => {
  return await request.get({ url: '/rehab/assessment/module-data', params: { assessmentId } })
}

export const saveRehabAssessmentModuleData = async (data: {
  assessmentId: number
  moduleType: string
  moduleStatus?: string
  dataJson?: any
  sourceType?: string
  version?: string
  note?: string
}) => {
  return await request.post({ url: '/rehab/assessment/module-data/save', data })
}

export const getRehabSfmaBookProtocol = async () => {
  return await request.get({ url: '/rehab/assessment/sfma/protocol' })
}

export const getRehabAssessmentAttachments = async (assessmentId: number) => {
  return await request.get({ url: '/rehab/assessment/attachments', params: { assessmentId } })
}

export const uploadRehabAssessmentAttachment = async (data: FormData) => {
  return await request.upload({ url: '/rehab/assessment/upload-attachment', data })
}

export const downloadRehabAssessmentAttachment = async (id: number) => {
  return await request.download({ url: '/rehab/assessment/download-attachment', params: { id } })
}

export const getRehabAssessmentOperationLog = async (assessmentId: number) => {
  return await request.get({ url: '/rehab/assessment/operation-log', params: { assessmentId } })
}
