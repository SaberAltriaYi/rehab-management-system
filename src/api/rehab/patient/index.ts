import request from '@/config/axios'

export interface RehabPatientVO {
  id: number
  patientNo: string
  name: string
  gender?: number
  birthday?: string
  age?: number
  phone?: string
  currentStage?: string
  currentStatus?: string
  currentTherapistUserId?: number
  currentTherapistName?: string
  crmBindStatus?: string
  updateTime?: string
}

export interface RehabPatientPageReqVO {
  pageNo: number
  pageSize: number
  keyword?: string
  currentTherapistUserId?: number
  currentStage?: string
  crmBindStatus?: string
  gender?: number
  sourceChannel?: string
  createTime?: string[]
}

export interface RehabPatientCreateReqVO {
  name: string
  gender?: number
  birthday?: string
  age?: number
  phone?: string
  idCardMasked?: string
  contactPerson?: string
  contactPhone?: string
  emergencyContact?: string
  emergencyPhone?: string
  heightCm?: number
  weightKg?: number
  bmi?: number
  dominantSide?: string
  sportType?: string
  schoolOrCompany?: string
  chiefComplaint?: string
  painArea?: string
  painScore?: number
  medicalHistory?: string
  injuryHistory?: string
  trainingHistory?: string
  sourceChannel?: string
  remark?: string
  currentStatus?: string
  currentStage?: string
  currentTherapistUserId?: number
  initEpisode?: boolean
  episodeType?: string
  episodePrimaryGoal?: string
}

export interface RehabPatientUpdateReqVO extends RehabPatientCreateReqVO {
  id: number
}

export interface RehabPatientBindCrmReqVO {
  patientId: number
  crmCustomerId: number
  bindSource?: string
  syncStatus?: string
  syncMessage?: string
}

export interface RehabPatientCrmBindingVO {
  id?: number
  patientId?: number
  crmCustomerId?: number
  crmCustomerName?: string
  crmCustomerMobile?: string
  bindStatus: string
  bindSource?: string
  syncStatus?: string
  syncMessage?: string
  bindTime?: string
  lastSyncTime?: string
  updateTime?: string
}

export interface RehabPatientMemberBindingVO {
  id?: number
  patientId?: number
  appUserId?: number
  bindType?: string
  bindStatus?: string
  phone?: string
  nickname?: string
  lastLoginTime?: string
  updateTime?: string
  memberNickname?: string
  memberMobile?: string
  memberStatus?: number
}

export interface RehabPatientAssignmentReqVO {
  patientId: number
  therapistUserId: number
  roleType: 'primary' | 'collaborator'
  assignReason?: string
  remark?: string
}

export interface RehabPatientTransferReqVO {
  patientId: number
  fromTherapistUserId?: number
  toTherapistUserId: number
  reason?: string
  remark?: string
}

export interface RehabPatientDetailVO {
  patient: RehabPatientVO
  crmBinding?: RehabPatientCrmBindingVO
  memberBinding?: RehabPatientMemberBindingVO
  currentPrimaryAssignment?: Record<string, any>
  currentEpisode?: Record<string, any>
  assignmentHistory?: Record<string, any>[]
  operationLogs?: Record<string, any>[]
}

export const getRehabPatientPage = async (params: RehabPatientPageReqVO) => {
  return await request.get({ url: '/rehab/patient/page', params })
}

export const getRehabPatient = async (id: number) => {
  return await request.get<RehabPatientDetailVO>({ url: '/rehab/patient/get', params: { id } })
}

export const createRehabPatient = async (data: RehabPatientCreateReqVO) => {
  return await request.post({ url: '/rehab/patient/create', data })
}

export const updateRehabPatient = async (data: RehabPatientUpdateReqVO) => {
  return await request.put({ url: '/rehab/patient/update', data })
}

export const deleteRehabPatient = async (id: number) => {
  return await request.delete({ url: '/rehab/patient/delete', params: { id } })
}

export const exportRehabPatient = async (params: RehabPatientPageReqVO) => {
  return await request.download({ url: '/rehab/patient/export', params })
}

export const bindRehabPatientCrm = async (data: RehabPatientBindCrmReqVO) => {
  return await request.post<RehabPatientCrmBindingVO>({ url: '/rehab/patient/bind-crm', data })
}

export const unbindRehabPatientCrm = async (data: { patientId: number; remark?: string }) => {
  return await request.post<RehabPatientCrmBindingVO>({ url: '/rehab/patient/unbind-crm', data })
}

export const getRehabPatientCrmBinding = async (id: number) => {
  return await request.get<RehabPatientCrmBindingVO>({ url: '/rehab/patient/crm-binding', params: { id } })
}

export const getRehabPatientMemberBinding = async (id: number) => {
  return await request.get<RehabPatientMemberBindingVO>({ url: '/rehab/patient/member-binding', params: { id } })
}

export const checkRehabPatientCrmConflict = async (data: {
  patientId?: number
  crmCustomerId: number
}) => {
  return await request.post({ url: '/rehab/patient/check-crm-conflict', data })
}

export const assignRehabTherapist = async (data: RehabPatientAssignmentReqVO) => {
  return await request.post({ url: '/rehab/patient/assign-therapist', data })
}

export const transferRehabTherapist = async (data: RehabPatientTransferReqVO) => {
  return await request.post({ url: '/rehab/patient/transfer-therapist', data })
}

export const getRehabAssignmentHistory = async (patientId: number) => {
  return await request.get({ url: '/rehab/patient/assignment-history', params: { patientId } })
}

export const getRehabPatientOperationLog = async (patientId: number) => {
  return await request.get({ url: '/rehab/patient/operation-log', params: { patientId } })
}
