import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { test } from 'node:test'
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { evaluateReadiness, REQUIRED_GATES } from './prepackage-readiness.mjs'

test('no evidence, dirty tree and wrong revision fail closed', () => {
  const report = evaluateReadiness({ root: '/tmp', revision: 'abc', dirty: true, evidence: null })
  assert.equal(report.ready, false)
  assert.match(report.blockers.join(' '), /SOURCE_DIRTY/)
  assert.match(report.blockers.join(' '), /EVIDENCE_MISSING/)
  assert.match(report.blockers.join(' '), /GATE_PENDING/)
})

test('all gates require corresponding existing logs tied to current revision', () => {
  const root = mkdtempSync(join(tmpdir(), 'rehab-release-evidence-'))
  try {
    const gates = {}
    const sample = 'Synthetic evidence only; not a release approval'
    const sha256 = createHash('sha256').update(sample).digest('hex')
    for (const id of REQUIRED_GATES) gates[id] = { status: 'passed', log: 'test.log', sha256 }
    const evidence = { schema: 'prepackage-evidence/1.0.0', revision: 'abc', gates }
    let report = evaluateReadiness({ root, revision: 'abc', dirty: false, evidence })
    assert.equal(report.ready, false)
    assert.match(report.blockers.join(' '), /GATE_EVIDENCE_NOT_FOUND/)
    writeFileSync(join(root, 'test.log'), sample)
    report = evaluateReadiness({ root, revision: 'abc', dirty: false, evidence })
    assert.equal(report.ready, true)
    writeFileSync(join(root, 'test.log'), 'tampered evidence')
    report = evaluateReadiness({ root, revision: 'abc', dirty: false, evidence })
    assert.match(report.blockers.join(' '), /GATE_EVIDENCE_HASH_MISMATCH/)
    gates.code_signing.log = '../../outside.log'
    report = evaluateReadiness({ root, revision: 'abc', dirty: false, evidence })
    assert.equal(report.ready, false)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})
