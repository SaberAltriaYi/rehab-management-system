import assert from 'node:assert/strict'
import test from 'node:test'

// Run with: node --experimental-strip-types --test tests/patient-session-gate.test.mjs
// Only synthetic tokens are used; no API endpoint or persistent patient data is contacted.
const { clearSession, getSession, saveSession } = await import('../src/lib/session.ts')

function storage(initial = null) {
  let value = initial
  let writes = 0
  let removals = 0
  globalThis.uni = {
    getStorageSync: () => value,
    setStorageSync: (_key, next) => { value = next; writes++ },
    removeStorageSync: () => { value = null; removals++ }
  }
  return { get value() { return value }, get writes() { return writes }, get removals() { return removals } }
}

const legacyPatient = { mode: 'patient', accessToken: 'SYNTHETIC_PATIENT_TOKEN', tenantId: 1 }
const staff = { mode: 'staff', accessToken: 'SYNTHETIC_STAFF_TOKEN', tenantId: 1 }

test('a persisted legacy patient session is discarded before any API can read its token', () => {
  const state = storage(legacyPatient)
  assert.equal(getSession(), null)
  assert.equal(state.value, null)
  assert.equal(state.removals, 1)
})

test('patient tokens cannot be saved even when code calls saveSession directly', () => {
  const state = storage(legacyPatient)
  assert.throws(() => saveSession(legacyPatient), /患者凭据/)
  assert.equal(state.writes, 0)
  assert.equal(state.value, null)
})

test('the existing staff session remains usable', () => {
  const state = storage()
  saveSession(staff)
  assert.deepEqual(getSession(), staff)
  assert.equal(state.writes, 1)
  clearSession()
  assert.equal(state.value, null)
})

test('malformed stored credentials and tenant identifiers fail closed', () => {
  for (const value of [
    [], 1, 'invalid', false, {},
    { ...staff, mode: 'administrator' },
    { ...staff, accessToken: 123 },
    { ...staff, accessToken: '   ' },
    { ...staff, accessToken: 'x'.repeat(8193) },
    { ...staff, tenantId: 0 },
    { ...staff, tenantId: -1 },
    { ...staff, tenantId: '1' },
    { ...staff, tenantId: 1.5 },
    { ...staff, tenantId: Number.MAX_SAFE_INTEGER + 1 },
    { ...staff, userId: -1 },
    { ...staff, refreshToken: {} }
  ]) {
    const state = storage(value)
    assert.equal(getSession(), null)
    assert.equal(state.value, null)
    assert.equal(state.writes, 0)
  }
})

test('invalid and expired timestamps cannot turn into permanent sessions', () => {
  for (const expiresTime of ['', 'not-a-date', '2000-01-01T00:00:00.000Z', null, 0]) {
    const state = storage({ ...staff, expiresTime })
    assert.equal(getSession(), null)
    assert.equal(state.value, null)
  }
  storage({ ...staff, expiresTime: '2999-01-01T00:00:00.000Z' })
  assert.equal(getSession().mode, 'staff')
})

test('invalid staff sessions cannot overwrite storage', () => {
  for (const next of [
    { ...staff, accessToken: '' },
    { ...staff, tenantId: 0 },
    { ...staff, expiresTime: 'invalid' }
  ]) {
    const state = storage(staff)
    assert.throws(() => saveSession(next), /会话无效/)
    assert.equal(state.writes, 0)
    assert.equal(state.value, null)
  }
})

test('storage failures cannot expose a credential or break logout', () => {
  globalThis.uni = {
    getStorageSync() { throw new Error('synthetic read failure') },
    removeStorageSync() { throw new Error('synthetic delete failure') }
  }
  assert.equal(getSession(), null)
  assert.doesNotThrow(() => clearSession())
})
