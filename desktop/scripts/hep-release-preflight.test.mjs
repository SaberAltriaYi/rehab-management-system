import { test } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { evaluateHepReleasePreflight, probeStrictLocalTls, probeStrictSystemTls } from './hep-release-preflight.mjs'

const good = {
  candidateLedger: '001-037', deployedLedger: '001-037', bridgeEnabled: true,
  hepCoreUrl: 'https://127.0.0.1:18443', coreOrigin: 'https://127.0.0.1:18443',
  tlsTrusted: true, coreReachable: true
}

test('a fully proven local integration passes technical preflight only', () => {
  const result = evaluateHepReleasePreflight(good)
  assert.equal(result.ready, true)
  assert.equal(result.formalClinicalApproval, false)
})

test('deployed 036 and disabled HEP reject release even if candidate is 037', () => {
  const result = evaluateHepReleasePreflight({ ...good, deployedLedger: '001-036', bridgeEnabled: false })
  assert.ok(result.blockers.includes('INSTALLED_037_NOT_VERIFIED'))
  assert.ok(result.blockers.includes('CORE_HEP_BRIDGE_NOT_VERIFIED_ENABLED'))
})

test('old 48080 service target, different tenant environment and remote target fail closed', () => {
  for (const bad of ['http://127.0.0.1:48080', 'http://127.0.0.1:18080', 'https://127.0.0.1:9444',
    'https://example.org', 'https://user:secret@127.0.0.1:18443', undefined]) {
    const result = evaluateHepReleasePreflight({ ...good, hepCoreUrl: bad })
    assert.equal(result.ready, false)
    assert.ok(result.blockers.includes('HEP_CORE_TARGET_NOT_CURRENT_CORE'))
  }
})

test('untrusted TLS or absent live evidence cannot be counted as success', () => {
  for (const key of ['tlsTrusted', 'coreReachable', 'deployedLedger']) {
    const result = evaluateHepReleasePreflight({ ...good, [key]: undefined })
    assert.equal(result.ready, false)
  }
})

test('strict TLS network probe never connects to a remote/public target', async () => {
  const result = await probeStrictLocalTls('https://example.com')
  assert.equal(result.probe, 'NON_LOOPBACK_TARGET_REJECTED')
  assert.equal(result.coreReachable, false)
})

test('system TLS probe uses strict platform CA and refuses off-machine addresses', () => {
  let called = false
  const success = probeStrictSystemTls('https://127.0.0.1:18443', (program, args, options) => {
    called = true
    assert.equal(program, '/usr/bin/curl')
    assert.ok(args.includes('--proto') && args.includes('=https'))
    assert.ok(!args.some(arg => ['-k', '--insecure', '--cacert', '-L', '--location'].includes(arg)))
    assert.ok(args.includes('https://127.0.0.1:18443/login'))
    assert.equal(options.timeout, 6500)
    return { status: 0, stdout: '200,0' }
  })
  assert.equal(called, true)
  assert.equal(success.tlsTrusted, true)
  assert.equal(success.coreReachable, true)
  const failed = probeStrictSystemTls('https://127.0.0.1:18443', () => ({ status: 60, stdout: '000,18' }))
  assert.equal(failed.probe, 'CERTIFICATE_UNTRUSTED')
  assert.equal(failed.tlsTrusted, false)
  const rejected = probeStrictSystemTls('https://example.com', () => { throw Error('must not connect') })
  assert.equal(rejected.probe, 'NON_LOOPBACK_TARGET_REJECTED')
})

test('CLI rejects an unverified candidate directory even if operator claims all gates passed', () => {
  const folder = mkdtempSync(join(tmpdir(), 'rehab-hep-guard-'))
  try {
    writeFileSync(join(folder, 'VERSION.json'), JSON.stringify({ migrationLedger: '001-037' }))
    const result = spawnSync(process.execPath, [new URL('./hep-release-preflight.mjs', import.meta.url).pathname,
      '--candidate', folder, '--deployed-ledger', '001-037', '--bridge-enabled', 'true',
      '--hep-core-url', 'https://127.0.0.1:18443'], { encoding: 'utf8', timeout: 10000 })
    assert.equal(result.status, 2)
    assert.ok(JSON.parse(result.stdout).blockers.includes('CANDIDATE_037_NOT_VERIFIED'))
    assert.ok(JSON.parse(result.stdout).blockers.includes('MANUAL_EVIDENCE_UNVERIFIED'))
  } finally { rmSync(folder, { recursive: true, force: true }) }
})

test('live mode rejects manually supplied versions or bridge state', () => {
  const result = spawnSync(process.execPath, [new URL('./hep-release-preflight.mjs', import.meta.url).pathname,
    '--live', '--deployed-ledger', '001-037', '--bridge-enabled', 'true',
    '--hep-core-url', 'https://127.0.0.1:18443'], { encoding: 'utf8', timeout: 10000 })
  assert.equal(result.status, 2)
  assert.ok(JSON.parse(result.stdout).blockers.includes('LIVE_EVIDENCE_UNAVAILABLE'))
})
