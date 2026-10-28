import assert from 'node:assert/strict'
import test from 'node:test'
import { loadTypescript } from './load-typescript.mjs'

const token = { accessToken: 'SYNTHETIC_ACCESS', refreshToken: 'SYNTHETIC_REFRESH',
  expiresTime: '2999-01-01T00:00:00.000Z', userId: 501 }
const login = { tenantName: '合成隔离机构', phone: '13800000001', code: '000000' }

function setup({ tenant = 71, failure = '', failLogout = false } = {}) {
  const calls = []
  let session = null
  const auth = loadTypescript('../src/lib/auth.ts', {
    './api': { async apiRequest(path, options) {
      calls.push({ path, options })
      if (path.endsWith('/logout') && failLogout) throw new Error('synthetic network failure')
      if (path === failure) throw new Error('synthetic access denied')
      if (path.endsWith('/get-id-by-name')) return tenant
      if (path.endsWith('/sms-login') || path.endsWith('/auth/login')) return token
      return { patientId: 801 }
    } },
    './session': { getSession: () => session, saveSession: (next) => { session = next },
      clearSession: () => { session = null } }
  })
  return { auth, calls, get session() { return session } }
}

test('first binding uses the real app API prefix then verifies live patient access', async () => {
  const context = setup()
  await context.auth.loginPatient({ ...login, patientNo: 'SYNTHETIC-801', invitationCode: 'a'.repeat(43) })
  assert.deepEqual(context.calls.map(({ path }) => path), [
    '/admin-api/system/tenant/get-id-by-name', '/app-api/member/auth/sms-login',
    '/app-api/app-patient/auth/bind', '/app-api/app-patient/profile'
  ])
  assert.equal(context.session.mode, 'patient')
  assert.equal(context.session.tenantId, 71)
  assert.equal(context.session.refreshToken, undefined)
  assert.equal(context.calls[2].options.data.bindType, 'self')
})

test('returning patient authenticates with SMS and live binding without reusing invitation', async () => {
  const context = setup()
  await context.auth.loginPatient(login)
  assert.equal(context.calls.filter(({ path }) => path.endsWith('/auth/bind')).length, 0)
  assert.equal(context.calls.at(-1).path, '/app-api/app-patient/profile')
  assert.equal(context.session.mode, 'patient')
})

test('unbound member never becomes patient and is logged out at server and client', async () => {
  const context = setup({ failure: '/app-api/app-patient/profile' })
  await assert.rejects(context.auth.loginPatient(login), /synthetic access denied/)
  assert.equal(context.session, null)
  assert.equal(context.calls.at(-1).path, '/app-api/member/auth/logout')
})

test('binding failure clears credentials even when server logout is unavailable', async () => {
  const context = setup({ failure: '/app-api/app-patient/auth/bind', failLogout: true })
  await assert.rejects(context.auth.loginPatient({ ...login, invitationCode: 'a'.repeat(43) }), /synthetic access denied/)
  assert.equal(context.session, null)
  assert.equal(context.calls.some(({ path }) => path.endsWith('/profile')), false)
})

test('missing malformed fractional and unsafe tenants never request credentials or SMS', async () => {
  for (const tenant of [null, '', 'invalid', -1, 0, 1.5, Number.MAX_SAFE_INTEGER + 1]) {
    const context = setup({ tenant })
    await assert.rejects(context.auth.loginPatient(login), /租户/)
    assert.equal(context.calls.length, 1)
    assert.equal(context.session, null)
  }
})

test('SMS scene matches the member login contract', async () => {
  const context = setup()
  await context.auth.sendPatientSmsCode(login.tenantName, login.phone)
  assert.equal(context.calls.at(-1).path, '/app-api/member/auth/send-sms-code')
  assert.equal(context.calls.at(-1).options.data.scene, 1)
  assert.equal(context.calls.at(-1).options.tenantId, 71)
})

test('logout uses the correct identity API and always clears the client session', async () => {
  const context = setup()
  await context.auth.loginPatient(login)
  assert.equal(await context.auth.logoutSession(), true)
  assert.equal(context.calls.at(-1).path, '/app-api/member/auth/logout')
  assert.equal(context.session, null)
  await context.auth.loginStaff({ tenantName: login.tenantName, username: 'SYNTHETIC_STAFF',
    password: 'SYNTHETIC_NOT_A_CREDENTIAL', captchaVerification: 'SYNTHETIC' })
  assert.equal(await context.auth.logoutSession(), true)
  assert.equal(context.calls.at(-1).path, '/admin-api/system/auth/logout')
  assert.equal(context.session, null)
})

test('failed server revocation is reported but cannot preserve local patient access', async () => {
  const context = setup({ failLogout: true })
  await context.auth.loginPatient(login)
  assert.equal(await context.auth.logoutSession(), false)
  assert.equal(context.session, null)
})
