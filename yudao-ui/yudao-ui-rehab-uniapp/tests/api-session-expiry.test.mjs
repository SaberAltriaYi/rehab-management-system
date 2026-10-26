import assert from 'node:assert/strict'
import test from 'node:test'
import { loadTypescript } from './load-typescript.mjs'

function setup(response) {
  let cleared = 0
  let request
  const api = loadTypescript('../src/lib/api.ts', {
    './config': { API_ORIGIN: '' },
    './session': {
      getSession: () => ({ mode: 'patient', accessToken: 'SYNTHETIC_ACCESS', tenantId: 71 }),
      clearSession: () => { cleared++ }
    }
  }, { uni: { request(options) { request = options; options.success(response) } } })
  return { api, get cleared() { return cleared }, get request() { return request } }
}

test('HTTP 401 removes the local session instead of leaving an unusable login', async () => {
  const context = setup({ statusCode: 401, data: { msg: 'synthetic expired' } })
  await assert.rejects(context.api.apiRequest('/app-api/app-patient/profile'), /synthetic expired/)
  assert.equal(context.cleared, 1)
})

test('application-level 401 also removes the local session', async () => {
  const context = setup({ statusCode: 200, data: { code: 401, msg: 'synthetic expired' } })
  await assert.rejects(context.api.apiRequest('/app-api/app-patient/profile'), /synthetic expired/)
  assert.equal(context.cleared, 1)
})

test('member requests include identity and tenant headers; anonymous SMS never gets access token', async () => {
  const context = setup({ statusCode: 200, data: { code: 0, data: true } })
  assert.equal(await context.api.apiRequest('/app-api/app-patient/profile'), true)
  assert.equal(context.request.header.Authorization, 'Bearer SYNTHETIC_ACCESS')
  assert.equal(context.request.header['tenant-id'], '71')
  await context.api.apiRequest('/app-api/member/auth/send-sms-code', { auth: false, tenantId: 72 })
  assert.equal(context.request.header.Authorization, undefined)
  assert.equal(context.request.header['tenant-id'], '72')
  assert.equal(context.cleared, 0)
})
