import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { compileAcceptanceWorklist } from './mvp306-acceptance-worklist.mjs'

const scope = JSON.parse(readFileSync(new URL('../../docs/qa/mvp306-release-scope.json', import.meta.url)))
const sourceRevision = 'c'.repeat(40)

test('each of 306 original IDs preserved, 182 independently unverified until acceptance', () => {
  const report = compileAcceptanceWorklist(scope, { sourceRevision, dirty: true })
  assert.equal(report.counts.total, 306)
  assert.equal(report.counts.businessRequired, 97)
  assert.equal(report.counts.safetyRequired, 85)
  assert.equal(report.counts.mandatory, 182)
  assert.equal(report.counts.deferred, 124)
  assert.equal(report.counts.clinicalHumanReviewRequired, 5)
  assert.equal(report.cases.length, 306)
  assert.equal(report.cases.filter(x => x.evidenceStatus === 'AWAITING_INDEPENDENT_EVIDENCE').length, 182)
  assert.deepEqual(report.cases.filter(x => x.clinicalHumanReview).map(x => x.id),
    ['T-107','T-113','T-114','T-129','T-189'])
  assert.equal(report.releaseDecision, 'NO_GO_UNTIL_FORMAL_MVP_GATE')
})

test('an incomplete or version-mismatched submission cannot silently promote a case', () => {
  const report = compileAcceptanceWorklist(scope, { sourceRevision, dirty: false,
    approval: { schema: 'rehab-mvp306-evidence/1',
      sourceRevision: 'd'.repeat(40), scopeSha256: '0'.repeat(64),
      cases: [{ id: 'T-001', status: 'PASS' }] } })
  assert.equal(report.approvalMatchesSourceAndScope, false)
  assert.equal(report.counts.submittedNotYetVerified, 0)
  assert.equal(report.cases[0].evidenceStatus, 'AWAITING_INDEPENDENT_EVIDENCE')
})
