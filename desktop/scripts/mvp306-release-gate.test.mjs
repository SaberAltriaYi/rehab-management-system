import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { test } from 'node:test'
import { mkdtempSync, rmSync, writeFileSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { evaluateMvp306, expandRanges, idFor, validateScope } from './mvp306-release-gate.mjs'
import { buildMvp306Template } from './mvp306-evidence-template.mjs'

const scope = JSON.parse(readFileSync(
  new URL('../../docs/qa/mvp306-release-scope.json', import.meta.url), 'utf8'))
const revision = '9'.repeat(40)
const scopeSha256 = createHash('sha256').update(JSON.stringify(scope)).digest('hex')
const idList = Array.from({ length: 306 }, (_, i) => idFor(i + 1))

function fixture(root) {
  const plan = validateScope(scope)
  const text = idList.map(id => `${id}: synthetic reviewer fixture, not real production acceptance`).join('\n')
  writeFileSync(join(root, 'evidence.txt'), text)
  const checksum = createHash('sha256').update(text).digest('hex')
  const cases = idList.map(id => plan.required.has(id) ? {
    id, status: 'PASS', tier: 'formal_local', reviewedBy: 'qa.synthetic',
    reviewedAt: '2026-11-12T08:00:00.000Z',
    evidence: { file: 'evidence.txt', sha256: checksum }
  } : { id, status: 'DEFERRED' })
  return { schema: 'rehab-mvp306-evidence/1', sourceRevision: revision,
    scopeSha256, cases }
}

test('scope is exactly 306 IDs, 97 business and 85 release gates with no overlapping IDs', () => {
  const found = validateScope(scope)
  assert.equal(found.all.length, 306)
  assert.equal(found.business.size, 97)
  assert.equal(found.safety.size, 85)
  assert.equal(found.required.size, 182)
  assert.equal(found.deferred.length, 124)
  assert.deepEqual([idFor(1), idFor(306)], ['T-001', 'T-306'])
  assert.throws(() => expandRanges('1,1'), /Duplicate/)
  assert.throws(() => expandRanges('307'), /out of bounds/)
  assert.throws(() => validateScope({ ...scope, safetyRanges: '1-85' }), /overlapping/)
})

test('template preserves all case IDs but authorizes no completed test by default', () => {
  const template = buildMvp306Template(scope, revision)
  assert.equal(template.cases.length, 306)
  assert.equal(template.cases.filter(item => item.status === 'NOT_RUN').length, 182)
  assert.equal(template.cases.filter(item => item.status === 'DEFERRED').length, 124)
  assert.equal(template.cases.some(item => item.status === 'PASS'), false)
  assert.equal(evaluateMvp306({ root: '/tmp', scope, sourceRevision: revision,
    dirty: false, approval: template }).ready, false)
})

test('no acceptance, missing cases, changed code, fake grade and skipped test deny packaging', () => {
  const dir = mkdtempSync(join(tmpdir(), 'rehab-mvp306-gate-'))
  try {
    const approval = fixture(dir)
    const run = (overrides = {}) => evaluateMvp306({
      root: dir, sourceRevision: revision, dirty: false, scope, approval, ...overrides
    })
    assert.equal(run({ approval: null }).ready, false)
    assert.equal(run({ dirty: true }).ready, false)
    assert.equal(run({ sourceRevision: '8'.repeat(40) }).ready, false)
    assert.equal(run({ approval: { ...approval, cases: approval.cases.slice(1) } }).ready, false)
    const wrong = structuredClone(approval)
    wrong.cases[0].status = 'BLOCKED'
    assert.equal(run({ approval: wrong }).ready, false)
    const missingLog = structuredClone(approval)
    missingLog.cases[0].evidence.file = '../unsafe.txt'
    assert.equal(run({ approval: missingLog }).ready, false)
    const modified = structuredClone(approval)
    modified.cases[0].evidence.sha256 = '0'.repeat(64)
    assert.equal(run({ approval: modified }).ready, false)
    const firstClinical = scope.manualClinicalReviewCases[0]
    const denied = run()
    assert.equal(denied.ready, false)
    assert.match(denied.blockers.join(' '), new RegExp(`${firstClinical}_CLINICAL_SIGNOFF_REQUIRED`))
    assert.equal(denied.pass, 177)
  } finally { rmSync(dir, { force: true, recursive: true }) }
})

test('synthetic all-PASS still requires explicit clinician-review metadata on five cases', () => {
  const dir = mkdtempSync(join(tmpdir(), 'rehab-mvp306-approval-'))
  try {
    const approved = fixture(dir)
    for (const id of scope.manualClinicalReviewCases) {
      const item = approved.cases.find(row => row.id === id)
      item.tier = 'supervised_clinical_e2e'
      item.clinicianReviewedBy = 'clinician.synthetic'
    }
    const full = evaluateMvp306({ root: dir, sourceRevision: revision,
      dirty: false, scope, approval: approved })
    assert.equal(full.ready, true)
    assert.equal(full.pass, 182)
    const substituted = structuredClone(approved)
    substituted.cases[20].id = 'T-002'
    assert.equal(evaluateMvp306({ root: dir, sourceRevision: revision,
      dirty: false, scope, approval: substituted }).ready, false)
    const bypass = structuredClone(approved)
    bypass.cases.find(row => row.id === 'T-189').tier = 'authorized_sandbox'
    assert.equal(evaluateMvp306({ root: dir, sourceRevision: revision,
      dirty: false, scope, approval: bypass }).ready, false)
  } finally { rmSync(dir, { force: true, recursive: true }) }
})
