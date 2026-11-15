import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  allowSelfRegistration,
  getFixedLoginTenantName,
  getLoginTenantName
} from '../src/utils/loginExperience.ts'

test('fixed organization is used only when tenant mode and fixed mode are both enabled', () => {
  assert.equal(getFixedLoginTenantName('true', 'true', ' 工作室内部 '), '工作室内部')
  assert.equal(getFixedLoginTenantName('false', 'true', '工作室内部'), null)
  assert.equal(getFixedLoginTenantName('true', 'false', '工作室内部'), null)
  assert.equal(getFixedLoginTenantName(undefined, 'true', '工作室内部'), null)
  assert.equal(getFixedLoginTenantName('true', 'true', '  '), null)
})

test('fixed studio wins over a remembered organization from another account', () => {
  assert.equal(getLoginTenantName('true', 'true', '工作室内部', '其他机构'), '工作室内部')
})

test('nonfixed tenant mode retains editable and remembered organization selection', () => {
  assert.equal(getLoginTenantName('true', 'false', '默认机构', '已选择机构'), '已选择机构')
  assert.equal(getLoginTenantName('true', 'true', '', '用户输入机构'), '用户输入机构')
  assert.equal(getLoginTenantName('true', 'false', ' 默认机构 ', undefined), '默认机构')
  assert.equal(getLoginTenantName('true', 'false', '默认机构', '   '), '默认机构')
  assert.equal(getLoginTenantName('true', 'false', '', ''), '')
})

test('tenant-disabled mode ignores stale organization cache', () => {
  assert.equal(getLoginTenantName('false', 'true', '工作室内部', '过期机构'), '')
})

test('staff public self-registration is disabled by default, with deliberate legacy opt-in', () => {
  assert.equal(allowSelfRegistration(undefined), false)
  assert.equal(allowSelfRegistration('false'), false)
  assert.equal(allowSelfRegistration('true'), true)
  assert.equal(allowSelfRegistration('TRUE'), false)
})

test('login form retains Yudao tenant lookup, captcha, account login and token handling', () => {
  const source = readFileSync(new URL('../src/views/Login/components/LoginForm.vue', import.meta.url), 'utf8')
  assert.match(source, /getLoginTenantName\(/)
  assert.match(source, /LoginApi\.getTenantIdByName\(loginData\.loginForm\.tenantName\)/)
  assert.match(source, /LoginApi\.login\(loginDataLoginForm\)/)
  assert.match(source, /authUtil\.setToken\(res\)/)
  assert.match(source, /@success="handleLogin"/)
})
