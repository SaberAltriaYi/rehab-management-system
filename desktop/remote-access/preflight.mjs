#!/usr/bin/env node
// Read-only L6 operator gate. NEVER launches VPN, Serve/Funnel or a listener.
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { validateL6Policy, validateServeSnapshot, validateTailnetStatus } from './policy.mjs'

export function inspectL6Remote({ policy, state, serve }) {
  const base = validateL6Policy(policy)
  const tailnet = validateTailnetStatus(state, policy)
  const service = validateServeSnapshot(serve)
  const blockers = [...new Set([
    ...base.blockers, ...tailnet.blockers, ...service.blockers
  ])]
  if (!serve || Object.keys(serve).length === 0) {
    blockers.push('TAILNET_SERVE_NOT_CONFIGURED')
  }
  // Status files cannot attest to real SSO/ACL configuration. A human
  // security review with provider-sourced signed operational evidence is
  // mandatory, even when this local CLI exits successfully.
  return { locallyConfigured: blockers.length === 0,
    productionGo: false, blockers: [...new Set(blockers)],
    requiredExternalChecks: [
      'IDP_SSO_MFA_AND_TAILNET_ACL_REVIEW',
      'REAL_DEVICE_APPROVAL_REVOCATION_E2E',
      'CLINICAL_TENANT_RBAC_AND_PATIENT_ACCESS_E2E',
      'PRODUCTION_TLS_AND_SECURITY_AUDIT',
      'ALL_P0_RELEASE_GATES'
    ] }
}
function tailscaleReadOnly(args) {
  return JSON.parse(execFileSync('tailscale', args, {
    encoding: 'utf8', timeout: 7000, maxBuffer: 1024 * 1024,
    stdio: ['ignore', 'pipe', 'ignore']
  }))
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  let state = null, serve = null, policy = null
  try {
    policy = JSON.parse(readFileSync(new URL('./l6-policy.example.json', import.meta.url), 'utf8'))
    state = tailscaleReadOnly(['status', '--json'])
    serve = tailscaleReadOnly(['serve', 'status', '--json'])
  } catch { /* absence or non-JSON output must fail closed */ }
  const result = inspectL6Remote({ policy, state, serve })
  process.stdout.write(JSON.stringify(result, null, 2) + '\n')
  process.exitCode = result.locallyConfigured ? 0 : 1
}
