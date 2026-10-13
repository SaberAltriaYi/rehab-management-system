import assert from 'node:assert/strict'
import test from 'node:test'
import { summarizeMotionPage } from '../../src/views/rehab/motion/motionMetrics.ts'

test('empty page has zero operational indicators', () => {
  assert.deepEqual(summarizeMotionPage([]), {
    waitingUpload: 0,
    pendingReview: 0,
    completed: 0,
    needsAttention: 0
  })
})

test('metrics count only the provided page and never interpret pending scores as complete', () => {
  assert.deepEqual(
    summarizeMotionPage([
      { status: 'WAITING_UPLOAD' },
      { status: 'PENDING_REVIEW' },
      { status: 'COMPLETED', sessionQualityStatus: 'pass' },
      { status: 'FAILED', sessionQualityStatus: 'fail' },
      { status: 'COMPLETED', stale: true },
      { status: 'PARSING', sessionQualityStatus: 'warn' }
    ]),
    { waitingUpload: 1, pendingReview: 1, completed: 2, needsAttention: 2 }
  )
})

test('multiple attention signals on the same assessment count once', () => {
  const rows = [{ status: 'FAILED', stale: true, sessionQualityStatus: 'fail' }]
  assert.equal(summarizeMotionPage(rows).needsAttention, 1)
})
