import assert from 'node:assert/strict'
import { test } from 'node:test'
import { approvedProvisionState, formatOwnerSubmissionTime, parsePolicy, requireUuid,
  redactAuditItem, sortOwnerApplicationsNewestFirst } from './policy.mjs'

const NOW = Date.parse('2026-10-10T04:00:00Z')
const EXP = '2026-12-31T23:00:00+08:00'
const BASE = { expiry: EXP, seats: '5', unlimited: false, installations: '2', modules: 'report,rehab' }
test('owner application dates render in explicit Beijing time and missing dates remain unknown', () => {
  assert.equal(formatOwnerSubmissionTime(Date.parse('2026-10-21T05:07:50Z')),
    '2026-10-21 13:07:50')
  assert.equal(formatOwnerSubmissionTime(undefined), '日期未记录')
  assert.equal(formatOwnerSubmissionTime(null), '日期未记录')
  assert.equal(formatOwnerSubmissionTime('1792559270012'), '日期未记录')
  assert.equal(formatOwnerSubmissionTime(Number.MAX_VALUE), '日期未记录')
})
test('owner application display sorts newest first without mutating API response', () => {
  const source = [
    { id: 'older', submittedAt: 1792559270012 },
    { id: 'unknown' },
    { id: 'newer', submittedAt: 1792812000000 }
  ]
  assert.deepEqual(sortOwnerApplicationsNewestFirst(source).map(app => app.id),
    ['newer', 'older', 'unknown'])
  assert.deepEqual(source.map(app => app.id), ['older', 'unknown', 'newer'])
})
test('staff limits come only from explicit signed owner approval policy', () => {
  assert.deepEqual(parsePolicy(BASE, NOW), {
    expiresAtMs: Date.parse(EXP), seatLimit: 5, maxInstallations: 2, modules: ['rehab', 'report']
  })
  assert.equal(parsePolicy({ ...BASE, unlimited: true }, NOW).seatLimit, null)
})
test('main-system checkbox maps exclusively to the signed rehab permission', () => {
  assert.deepEqual(parsePolicy({ ...BASE, modules: 'motion,report', mainSystem: true }, NOW).modules,
    ['motion', 'rehab', 'report'])
  assert.deepEqual(parsePolicy({ ...BASE, modules: 'rehab,motion', mainSystem: false }, NOW).modules,
    ['motion'])
  assert.deepEqual(parsePolicy({ ...BASE, modules: '', mainSystem: true }, NOW).modules,
    ['rehab'])
  assert.throws(() => parsePolicy({ ...BASE, modules: '', mainSystem: false }, NOW),
    /模块名称无效/)
})

test('approved tenants can provision Core only when service and authorization are ready', () => {
  const id = 'f12b2687-09a1-40b5-b47a-747d75ce5551'
  const app = { tenantId: id, status: 'APPROVED', provisioningStatus: 'NOT_CONFIGURED' }
  const tenant = { tenantId: id, status: 'ACTIVE', modules: ['rehab', 'motion'] }
  assert.equal(approvedProvisionState(app, tenant, false).ready, false)
  assert.match(approvedProvisionState(app, tenant, false).message, /未配置/)
  assert.equal(approvedProvisionState(app, tenant, true).ready, true)
  assert.equal(approvedProvisionState({ ...app, provisioningStatus: 'FAILED' }, tenant, true).ready, true)
  assert.equal(approvedProvisionState({ ...app, provisioningStatus: 'PROVISIONING' }, tenant, true).ready, false)
  assert.equal(approvedProvisionState({ ...app, provisioningStatus: 'ACTIVE', mainTenantId: 4 }, tenant, true).ready, false)
  assert.equal(approvedProvisionState(app, { ...tenant, modules: ['motion'] }, true).ready, false)
  assert.equal(approvedProvisionState(app, { ...tenant, status: 'SUSPENDED' }, true).ready, false)
  assert.equal(approvedProvisionState({ ...app, status: 'PENDING' }, tenant, true).visible, false)
})

test('invalid entitlement inputs fail closed before owner API calls', () => {
  for (const edit of [
    { seats: '-1' }, { seats: '0' }, { seats: '1.1' }, { seats: 'Infinity' },
    { installations: '0' }, { installations: '1e4' }, { modules: 'rehab,rehab' },
    { modules: 'rehab,\n<script>' }, { expiry: '2025-01-01' }
  ]) {
    assert.throws(() => parsePolicy({ ...BASE, ...edit }, NOW))
  }
})
test('UUID validation and audit projection hide arbitrary/clinical properties', () => {
  assert.equal(requireUuid('f12b2687-09a1-40b5-b47a-747d75ce5551'),
    'f12b2687-09a1-40b5-b47a-747d75ce5551')
  assert.throws(() => requireUuid('../patients'))
  const projection = redactAuditItem({
    action: 'APPLICATION_APPROVED', patientName: 'Synthetic patient secret',
    bearer: 'Synthetic owner bearer', occurredAt: NOW, chainHash: 'f'.repeat(64)
  })
  assert.equal(JSON.stringify(projection).includes('Synthetic'), false)
  assert.equal(projection.action, 'APPLICATION_APPROVED')
})
