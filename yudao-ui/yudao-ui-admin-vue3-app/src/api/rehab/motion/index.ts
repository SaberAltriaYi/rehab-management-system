import request from '@/config/axios'

/** 智能动作评估（OpenCap + 规则引擎 + AI 解释 + 治疗师审核） */

export interface MotionAssessmentVO {
  id: number
  patientId: number
  patientName?: string
  patientNo?: string
  episodeId?: number
  assessmentRecordId?: number
  baselineId?: number
  visitType: string
  protocolFamilies?: string[]
  title?: string
  captureTime?: string
  status: string
  statusLabel?: string
  dataSource: string
  opencapSessionId?: string
  cameraCount?: number
  modelName?: string
  videoConsent?: boolean
  aiAllowed?: boolean
  sexGroup?: string
  limbLengthLeftCm?: number
  limbLengthRightCm?: number
  tjaVariant?: string
  inputRevision?: number
  analyzedRevision?: number
  stale?: boolean
  engineVersion?: string
  ruleVersion?: string
  resultSchemaVersion?: string
  sessionQualityStatus?: string
  analyzedTime?: string
  reviewStatus?: string
  therapistUserId?: number
  signedUserId?: number
  signedTime?: string
  remark?: string
  createTime?: string
  fmsTotal?: number | null
}

export interface MotionSaveReqVO {
  id?: number
  patientId: number
  episodeId?: number
  assessmentRecordId?: number
  baselineId?: number
  visitType: string
  protocolFamilies: string[]
  title?: string
  captureTime?: string | number
  dataSource: string
  opencapSessionId?: string
  cameraCount: number
  videoConsent?: boolean
  aiAllowed?: boolean
  sexGroup?: string
  limbLengthLeftCm?: number
  limbLengthRightCm?: number
  tjaVariant?: string
  therapistUserId?: number
  remark?: string
}

export interface MotionFileVO {
  id: number
  fileKind: string
  trialName?: string
  cameraKey?: string
  relativePath: string
  fileSize: number
  contentType?: string
  source?: string
  createTime?: string
}

export interface MotionTrialItem {
  id?: number
  trialKey: string
  testCode: string
  side: string
  conditionCode?: string
  attemptNo?: number
  opencapTrialName?: string
  opencapTrialId?: string
  sortNo?: number
}

export interface MotionTaskVO {
  id: number
  assessmentId: number
  taskType: string
  state: string
  stateLabel?: string
  progress?: number
  stepMessage?: string
  attempts?: number
  maxAttempts?: number
  nextRunTime?: string
  cancelRequested?: boolean
  errorCode?: string
  errorMessage?: string
  failedState?: string
  startedTime?: string
  finishedTime?: string
  createTime?: string
}

const BASE = '/rehab/motion'

export const MotionApi = {
  create: (data: MotionSaveReqVO) => request.post<number>({ url: `${BASE}/create`, data }),
  update: (data: MotionSaveReqVO) => request.put<boolean>({ url: `${BASE}/update`, data }),
  remove: (id: number) => request.delete<boolean>({ url: `${BASE}/delete`, params: { id } }),
  page: (params: { pageNo: number; pageSize: number; patientId?: number; status?: string; visitType?: string }) =>
    request.get({ url: `${BASE}/page`, params }),
  get: (id: number) => request.get<MotionAssessmentVO>({ url: `${BASE}/get`, params: { id } }),
  listByPatient: (patientId: number) =>
    request.get<MotionAssessmentVO[]>({ url: `${BASE}/list-by-patient`, params: { patientId } }),

  uploadPolicy: () => request.get({ url: `${BASE}/upload-policy` }),
  /** 单文件上传；由上传队列串行调用。超时放宽到 5 分钟（16 MB 上限）。 */
  uploadFile: (assessmentId: number, relativePath: string, file: File) => {
    const form = new FormData()
    form.append('assessmentId', String(assessmentId))
    form.append('relativePath', relativePath)
    form.append('file', file)
    return request.upload({ url: `${BASE}/file/upload`, data: form, timeout: 300000 })
  },
  listFiles: (assessmentId: number) =>
    request.get<MotionFileVO[]>({ url: `${BASE}/file/list`, params: { assessmentId } }),
  deleteFile: (id: number) => request.delete<boolean>({ url: `${BASE}/file/delete`, params: { id } }),
  downloadFile: (id: number) => request.download({ url: `${BASE}/file/download`, params: { id } }),

  listTrials: (assessmentId: number) => request.get({ url: `${BASE}/trial/list`, params: { assessmentId } }),
  saveTrials: (assessmentId: number, trials: MotionTrialItem[]) =>
    request.post<boolean>({ url: `${BASE}/trial/save`, data: { assessmentId, trials } }),
  updateTrialManual: (data: {
    trialId: number
    valid?: boolean
    invalidReason?: string
    pain?: boolean | null
    manualCriteria?: Record<string, any>
    manualValues?: Record<string, any>
    reason?: string
  }) => request.post<boolean>({ url: `${BASE}/trial/manual`, data }),
  saveManualInputs: (data: { assessmentId: number; inputs: Record<string, any>; reason?: string }) =>
    request.post<boolean>({ url: `${BASE}/manual-inputs`, data }),

  process: (assessmentId: number, idempotencyKey: string) =>
    request.post<MotionTaskVO>({ url: `${BASE}/process`, data: { assessmentId, idempotencyKey } }),
  opencapTrials: (assessmentId: number, sessionId: string) =>
    request.post({ url: `${BASE}/opencap/trials`, data: { assessmentId, sessionId } }),
  listTasks: (assessmentId: number) =>
    request.get<MotionTaskVO[]>({ url: `${BASE}/task/list`, params: { assessmentId } }),
  cancelTask: (id: number) => request.post<boolean>({ url: `${BASE}/task/cancel`, params: { id } }),
  retryTask: (id: number, idempotencyKey: string) =>
    request.post<MotionTaskVO>({ url: `${BASE}/task/retry`, params: { id, idempotencyKey } }),

  result: (assessmentId: number) => request.get({ url: `${BASE}/result`, params: { assessmentId } }),
  series: (assessmentId: number, trialName?: string) =>
    request.get({ url: `${BASE}/series`, params: { assessmentId, trialName } }),

  reviewScore: (data: { scoreId: number; finalStatus: string; finalScore?: number | null; reason?: string }) =>
    request.post<boolean>({ url: `${BASE}/score/review`, data }),
  reviewAi: (data: { draftId: number; action: 'accept' | 'reject'; editedText?: string }) =>
    request.post<boolean>({ url: `${BASE}/ai/review`, data }),
  regenerateAi: (assessmentId: number, idempotencyKey: string) =>
    request.post<MotionTaskVO>({ url: `${BASE}/ai/regenerate`, params: { assessmentId, idempotencyKey } }),
  sign: (assessmentId: number) =>
    request.post<boolean>({ url: `${BASE}/sign`, data: { assessmentId, confirmed: true } }),
  amend: (assessmentId: number, reason: string) =>
    request.post<boolean>({ url: `${BASE}/amend`, data: { assessmentId, reason } }),

  reports: (assessmentId: number) => request.get({ url: `${BASE}/report/list`, params: { assessmentId } }),
  report: (id: number) => request.get({ url: `${BASE}/report/get`, params: { id } }),
  reportPdf: (id: number) => request.download({ url: `${BASE}/report/pdf`, params: { id } }),
  compare: (baseId: number, currentId: number) =>
    request.get({ url: `${BASE}/compare`, params: { baseId, currentId } }),
  trend: (patientId: number) => request.get({ url: `${BASE}/trend`, params: { patientId } }),
  manualEdits: (assessmentId: number) => request.get({ url: `${BASE}/manual-edits`, params: { assessmentId } }),
  auditLogs: (assessmentId: number) => request.get({ url: `${BASE}/audit-logs`, params: { assessmentId } }),
  protocols: () => request.get({ url: `${BASE}/protocols` })
}

/** 客户端幂等键：同一次点击的重复提交返回同一任务 */
export const newIdempotencyKey = (prefix = 'ui') =>
  `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
