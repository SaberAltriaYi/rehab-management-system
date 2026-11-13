import { test } from 'node:test'
import assert from 'node:assert/strict'
import { reconcileOwnerCoreBindings } from './owner-core-link-audit.mjs'

test('matched historical bindings are not misreported as missing', () => {
  assert.deepEqual(reconcileOwnerCoreBindings([31, 44], 2), {
    ready:true, ownerActiveBindings:2, currentCoreTenantMatches:2,
    unmatchedOwnerActive:0, requiresHumanReconciliation:false
  })
})

test('orphaned active owner bindings require human reconciliation', () => {
  const result = reconcileOwnerCoreBindings([31,44,65],0)
  assert.equal(result.ready,false)
  assert.equal(result.unmatchedOwnerActive,3)
  assert.equal(result.requiresHumanReconciliation,true)
  assert.equal(JSON.stringify(result).includes('31'),false)
})

test('unsafe, duplicate and invalid owner IDs fail closed', () => {
  for (const ids of [[0],[1,1],[-1],['abc'],[Number.MAX_SAFE_INTEGER+1]]) {
    assert.throws(()=>reconcileOwnerCoreBindings(ids,0))
  }
  assert.throws(()=>reconcileOwnerCoreBindings([31],2))
})
