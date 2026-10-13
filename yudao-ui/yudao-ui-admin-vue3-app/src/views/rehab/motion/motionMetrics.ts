/** 仅计算已加载的当前页，避免把分页内数量误标为患者/评估全量统计。 */
export interface MotionPageStatus {
  status?: string
  stale?: boolean
  sessionQualityStatus?: string
}

export const summarizeMotionPage = (rows: readonly MotionPageStatus[]) => ({
  waitingUpload: rows.filter((row) => row.status === 'WAITING_UPLOAD').length,
  pendingReview: rows.filter((row) => row.status === 'PENDING_REVIEW').length,
  completed: rows.filter((row) => row.status === 'COMPLETED').length,
  needsAttention: rows.filter(
    (row) => row.status === 'FAILED' || row.stale === true || row.sessionQualityStatus === 'fail'
  ).length
})
