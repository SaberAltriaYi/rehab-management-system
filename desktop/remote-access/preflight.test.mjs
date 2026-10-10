import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { inspectL6Remote } from './preflight.mjs'

const policy = JSON.parse(readFileSync(new URL('./l6-policy.example.json', import.meta.url), 'utf8'))
test('no Tailscale installation or exposure defaults to blocked remote activation', () => {
  const result = inspectL6Remote({ policy, state: null, serve: null })
  assert.equal(result.locallyConfigured, false)
  assert.equal(result.productionGo, false)
  assert.ok(result.blockers.includes('TAILNET_SERVE_NOT_CONFIGURED'))
})
test('even healthy configuration never claims provider MFA/clinical audit complete', () => {
  const state = { BackendState: 'Running', Self: { Online: true },
    TailscaleIPs: ['100.101.2.3'], Health: {} }
  const serve = { Web: { 'site.tailnet.ts.net:443': {
    Handlers: { '/': { Proxy: 'https+insecure://127.0.0.1:8443' } }
  } } }
  const result = inspectL6Remote({ policy, state, serve })
  assert.equal(result.locallyConfigured, true)
  assert.equal(result.productionGo, false)
  assert.ok(result.requiredExternalChecks.includes('IDP_SSO_MFA_AND_TAILNET_ACL_REVIEW'))
  assert.equal(inspectL6Remote({ policy, state,
    serve: { tcp: ['0.0.0.0:3306'] } }).locallyConfigured, false)
})
