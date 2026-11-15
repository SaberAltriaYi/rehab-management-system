// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
// Read-only preflight. No database access, authentication, certificate changes,
// service restarts, private-key access or deployment actions are performed.
import { readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { get } from 'node:https'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { validateRuntime } from './runtime-tools.mjs'
import { observeInstalledHep } from './hep-installed-evidence.mjs'

const DEFAULT_CORE_ORIGIN = 'https://127.0.0.1:18443'

export function evaluateHepReleasePreflight({
  candidateLedger, deployedLedger, bridgeEnabled, hepCoreUrl,
  coreOrigin = DEFAULT_CORE_ORIGIN, tlsTrusted, coreReachable
}) {
  const blockers = []
  const candidateHas037 = /(?:^|-)037$/.test(candidateLedger || '')
  if (!candidateHas037) blockers.push('CANDIDATE_037_NOT_VERIFIED')
  if (!/(?:^|-)037$/.test(deployedLedger || '')) blockers.push('INSTALLED_037_NOT_VERIFIED')
  if (bridgeEnabled !== true) blockers.push('CORE_HEP_BRIDGE_NOT_VERIFIED_ENABLED')

  let matchesCore = false
  let loopbackOnly = false
  try {
    const target = new URL(hepCoreUrl)
    const expected = new URL(coreOrigin)
    loopbackOnly = ['127.0.0.1', 'localhost', '[::1]'].includes(target.hostname)
    matchesCore = target.origin === expected.origin &&
      (target.pathname === '/' || target.pathname === '') &&
      !target.username && !target.password && !target.search && !target.hash
  } catch { /* fail closed on an absent or malformed URL */ }
  if (!loopbackOnly) blockers.push('HEP_CORE_TARGET_NOT_LOOPBACK')
  if (!matchesCore) blockers.push('HEP_CORE_TARGET_NOT_CURRENT_CORE')
  if (tlsTrusted !== true) blockers.push('STRICT_TLS_NOT_TRUSTED')
  if (coreReachable !== true) blockers.push('CORE_NOT_REACHABLE_WITH_STRICT_TLS')

  return {
    schema: 'rehab-hep-release-preflight/1',
    ready: blockers.length === 0,
    formalClinicalApproval: false,
    scope: 'technical-preflight-only',
    checks: { candidateHas037, deployedHas037: !blockers.includes('INSTALLED_037_NOT_VERIFIED'),
      bridgeEnabled: bridgeEnabled === true, loopbackOnly, matchesCore,
      tlsTrusted: tlsTrusted === true, coreReachable: coreReachable === true },
    blockers
  }
}

export function probeStrictLocalTls(origin = DEFAULT_CORE_ORIGIN, timeoutMs = 5000) {
  return new Promise(resolveResult => {
    let url
    try {
      url = new URL('/login', origin)
      if (url.protocol !== 'https:' || !['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)) {
        resolveResult({ tlsTrusted: false, coreReachable: false, probe: 'NON_LOOPBACK_TARGET_REJECTED' })
        return
      }
    } catch {
      resolveResult({ tlsTrusted: false, coreReachable: false, probe: 'BAD_TARGET' })
      return
    }
    const req = get(url, { rejectUnauthorized: true, timeout: timeoutMs }, res => {
      const ok = res.statusCode === 200
      res.resume()
      resolveResult({ tlsTrusted: true, coreReachable: ok, probe: ok ? 'HTTP_200' : 'UNEXPECTED_HTTP_STATUS' })
    })
    req.on('timeout', () => req.destroy(new Error('timeout')))
    req.on('error', error => {
      resolveResult({ tlsTrusted: false, coreReachable: false,
        probe: ['DEPTH_ZERO_SELF_SIGNED_CERT', 'SELF_SIGNED_CERT_IN_CHAIN', 'UNABLE_TO_VERIFY_LEAF_SIGNATURE', 'UNABLE_TO_GET_ISSUER_CERT_LOCALLY'].includes(error.code)
          ? 'CERTIFICATE_UNTRUSTED' : 'CONNECTION_OR_TLS_ERROR' })
    })
  })
}

// Probe the macOS Keychain via strict system curl. Node's built-in CA roots
// may not match macOS trust, and Java HEP trust needs its own E2E test.
export function probeStrictSystemTls(origin = DEFAULT_CORE_ORIGIN, run = spawnSync) {
  let url
  try {
    url = new URL('/login', origin)
    if (url.protocol !== 'https:' ||
        !['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname) ||
        url.username || url.password) {
      return { tlsTrusted: false, coreReachable: false, probe: 'NON_LOOPBACK_TARGET_REJECTED' }
    }
  } catch {
    return { tlsTrusted: false, coreReachable: false, probe: 'BAD_TARGET' }
  }
  const result = run('/usr/bin/curl', [
    '--silent', '--show-error', '--noproxy', '*', '--proto', '=https',
    '--max-time', '5', '--output', '/dev/null',
    '--write-out', '%{http_code},%{ssl_verify_result}', url.href
  ], { encoding: 'utf8', timeout: 6500, maxBuffer: 512 })
  const parts = String(result?.stdout || '').trim().match(/^(\d{3}),(\d+)$/)
  const tlsTrusted = result?.status === 0 && !!parts && parts[2] === '0'
  const coreReachable = tlsTrusted && parts[1] === '200'
  return { tlsTrusted, coreReachable,
    probe: coreReachable ? 'HTTP_200' :
      (result?.status === 60 || (parts && parts[2] !== '0'))
        ? 'CERTIFICATE_UNTRUSTED' : 'CONNECTION_OR_HTTP_ERROR' }
}

function flag(args, name) {
  const i = args.indexOf(name)
  return i >= 0 ? args[i + 1] : undefined
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2)
  if (args.includes('--help')) {
    process.stdout.write('Usage: node desktop/scripts/hep-release-preflight.mjs --candidate DIR --live (read actual installed DB/runtime/bridge/HEP configuration, no writes)\n')
  } else {
    let ledger = ''
    try {
      const candidate = flag(args, '--candidate')
      if (candidate) {
        const root = resolve(candidate)
        validateRuntime(root)
        ledger = JSON.parse(readFileSync(resolve(root, 'VERSION.json'), 'utf8')).migrationLedger
      }
    }
    catch { /* missing candidate evidence is a blocker */ }
    const live = process.platform === 'darwin'
      ? probeStrictSystemTls(DEFAULT_CORE_ORIGIN)
      : await probeStrictLocalTls(DEFAULT_CORE_ORIGIN)
    const liveMode = args.includes('--live')
    let installed = null
    if (liveMode && !['--deployed-ledger', '--bridge-enabled', '--hep-core-url'].some(name => args.includes(name))) {
      try { installed = observeInstalledHep() } catch { /* fail closed, never log credentials or system error output */ }
    }
    const report = evaluateHepReleasePreflight({ candidateLedger: ledger,
      deployedLedger: installed?.deployedLedger || (liveMode ? undefined : flag(args, '--deployed-ledger')),
      bridgeEnabled: installed?.bridgeEnabled ?? (liveMode ? false : flag(args, '--bridge-enabled') === 'true'),
      hepCoreUrl: installed?.hepCoreUrl || (liveMode ? undefined : flag(args, '--hep-core-url')),
      ...live })
    const extra = installed ? Object.entries(installed.checks).filter(([, passed]) => passed !== true)
      .map(([name]) => `LIVE_${name.toUpperCase()}`)
      : [liveMode ? 'LIVE_EVIDENCE_UNAVAILABLE' : 'MANUAL_EVIDENCE_UNVERIFIED']
    const blockers = [...new Set([...report.blockers, ...extra])]
    const result = { ...report, ready: blockers.length === 0, blockers,
      source: installed?.observation || 'unverified-manual-input',
      liveProbe: live.probe,
      installedStatistics: installed?.statistics || null,
      installedChecks: installed?.checks || null }
    process.stdout.write(JSON.stringify(result, null, 2) + '\n')
    process.exitCode = result.ready ? 0 : 2
  }
}
