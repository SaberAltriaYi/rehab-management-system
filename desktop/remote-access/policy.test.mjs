import { test } from 'node:test'
import assert from 'node:assert/strict'
import { validateL6Policy, validateTailnetStatus, validateServeSnapshot,
  validatePublicUrl, isTailnetIPv4 } from './policy.mjs'

const secure = Object.freeze({
  mode: 'tailscale-private-serve', publicInternet: false,
  organizationMfaEnforced: true, requireDeviceApproval: true,
  requireDevicePosture: true, revokeLostDevices: true,
  explicitStaffAcl: true, patientDataToOwnerControlPlane: false,
  requireJavaTenantRbac: true, requireSignedStudioLicense: true,
  auditGateways: true, shortBrowserSessions: true,
  bindAddress: '127.0.0.1', localHttpsPort: 8443,
  allowedRemoteGroups: ['group:rehab-staff'],
  ownerGatewaySharedWithPatient: false,
  exportDatabasePort: false, exportRedisPort: false,
  allowUnauthenticatedFallback: false
})
test('policy must demand SSO MFA, device, RBAC and no clinical leakage', () => {
  assert.equal(validateL6Policy(secure).ready, true)
  for (const [key, value] of [
    ['mode', 'public-internet'], ['publicInternet', true],
    ['organizationMfaEnforced', false], ['requireDeviceApproval', false],
    ['requireDevicePosture', false], ['revokeLostDevices', false],
    ['explicitStaffAcl', false], ['patientDataToOwnerControlPlane', true],
    ['requireJavaTenantRbac', false], ['requireSignedStudioLicense', false],
    ['auditGateways', false], ['shortBrowserSessions', false],
    ['bindAddress', '0.0.0.0'], ['localHttpsPort', 48080],
    ['allowedRemoteGroups', []], ['ownerGatewaySharedWithPatient', true],
    ['exportDatabasePort', true], ['exportRedisPort', true],
    ['allowUnauthenticatedFallback', true]
  ]) assert.equal(validateL6Policy({ ...secure, [key]: value }).ready, false, key)
})
test('tailnet health and permitted IPv4 range required', () => {
  const state = { BackendState: 'Running', Self: { Online: true },
    TailscaleIPs: ['100.101.102.103'], Health: {} }
  assert.equal(validateTailnetStatus(state, secure).ready, true)
  assert.equal(validateTailnetStatus({ ...state, BackendState: 'Stopped' }, secure).ready, false)
  assert.equal(validateTailnetStatus({ ...state, TailscaleIPs: ['192.168.1.3'] }, secure).ready, false)
  assert.equal(validateTailnetStatus({ ...state, Health: { ping: 'unreachable' } }, secure).ready, false)
  assert.equal(isTailnetIPv4('100.127.0.1'), true)
  assert.equal(isTailnetIPv4('100.128.0.1'), false)
})
test('serve snapshot denies public funnel or direct database exposure', () => {
  assert.equal(validateServeSnapshot({}).safe, true)
  assert.equal(validateServeSnapshot({ tcp: ['0.0.0.0:3306'] }).safe, false)
  assert.equal(validateServeSnapshot({ serve: { Funnel: true } }).safe, false)
  assert.equal(validateServeSnapshot({ target: 'https+insecure://10.0.0.5:443' }).safe, false)
  assert.equal(validateServeSnapshot({ target: 'https+insecure://127.0.0.1:8443' }).safe, true)
})
test('remote URL must be tailnet HTTPS FQDN with no credentials/query', () => {
  assert.equal(validatePublicUrl('https://rehab.example-tailnet.ts.net/'), true)
  for (const url of [
    'https://rehab.example.com/', 'https://127.0.0.1:8443/',
    'https://rehab.example-tailnet.ts.net:8443/',
    'http://rehab.example-tailnet.ts.net/',
    'https://rehab.example-tailnet.ts.net/?access_token=foo',
    'https://admin@rehab.example-tailnet.ts.net/'
  ]) assert.equal(validatePublicUrl(url), false, url)
})
