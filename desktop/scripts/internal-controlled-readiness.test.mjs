import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { evaluateReadiness, REQUIRED_GATES } from './prepackage-readiness.mjs'
import { evaluateInternalReadiness } from './internal-controlled-readiness.mjs'

function fixture(callback) {
  const root = mkdtempSync(join(tmpdir(), 'rehab-internal-gates-'))
  const text = 'Synthetic evidence, not a clinical or installation approval.'
  writeFileSync(join(root, 'synthetic.log'), text)
  const gates = Object.fromEntries(REQUIRED_GATES.filter(id => id !== 'code_signing')
    .map(id => [id, { status: 'passed', log: 'synthetic.log',
      sha256: createHash('sha256').update(text).digest('hex') }]))
  const evidence = {
    schema: 'internal-controlled-evidence/1', revision: 'synthetic-commit',
    scope: { purpose: 'single-studio-internal', publicDistribution: false, network: 'loopback' },
    gates
  }
  const args = { root, revision: 'synthetic-commit', dirty: false, evidence }
  try { callback(args) } finally { rmSync(root, { recursive: true, force: true }) }
}

test('explicit local scope can omit signing but never approve a formal release', () => fixture(args => {
  const result = evaluateInternalReadiness(args)
  assert.equal(result.ready, true)
  assert.equal(result.formalReleaseApproved, false)
  assert.equal(evaluateReadiness(args).ready, false)
  const formal = evaluateReadiness({ ...args,
    evidence: { ...args.evidence, schema: 'prepackage-evidence/1.0.0' } })
  assert.ok(formal.blockers.includes('GATE_PENDING: code_signing'))
}))

for (const [name, change] of [
  ['public distribution', scope => { scope.publicDistribution = true }],
  ['LAN or public binding', scope => { scope.network = '0.0.0.0' }],
  ['wrong purpose', scope => { scope.purpose = 'commercial' }]
]) test(name + ' cannot use internal approval', () => fixture(args => {
  change(args.evidence.scope)
  assert.ok(evaluateInternalReadiness(args).blockers.includes('INTERNAL_SCOPE_REQUIRED'))
}))

test('every non-signing gate remains mandatory', () => fixture(args => {
  for (const id of REQUIRED_GATES.filter(id => id !== 'code_signing')) {
    const original = args.evidence.gates[id]
    args.evidence.gates[id] = { status: 'pending' }
    assert.ok(evaluateInternalReadiness(args).blockers.includes('GATE_PENDING: ' + id))
    args.evidence.gates[id] = original
  }
}))

test('dirty or different sources cannot reuse internal acceptance', () => fixture(args => {
  assert.equal(evaluateInternalReadiness({ ...args, dirty: true }).ready, false)
  assert.equal(evaluateInternalReadiness({ ...args, revision: 'different' }).ready, false)
}))

test('tampered logs and signing claims fail even for internal use', () => fixture(args => {
  args.evidence.gates.code_signing = { ...args.evidence.gates.java_full_test,
    sha256: '0'.repeat(64) }
  assert.equal(evaluateInternalReadiness(args).ready, false)
  delete args.evidence.gates.code_signing
  writeFileSync(join(args.root, 'synthetic.log'), 'tampered')
  assert.equal(evaluateInternalReadiness(args).ready, false)
}))
