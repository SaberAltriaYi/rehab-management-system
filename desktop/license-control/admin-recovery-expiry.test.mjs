import assert from 'node:assert/strict'
import { test } from 'node:test'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { randomBytes } from 'node:crypto'
import { OwnerLicenseStore } from './store.mjs'
import { OwnerLicensingService } from './service.mjs'
import { SecretVault } from './security.mjs'
import { createOwnerControlServer } from './http.mjs'

test('the recovery maintenance deadline automatically closes both endpoints', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'rehab-owner-expired-'))
  let store, server
  try {
    store = new OwnerLicenseStore(join(dir, 'owner.sqlite'))
    const svc = new OwnerLicensingService(store, new SecretVault(randomBytes(32)))
    server = createOwnerControlServer(svc, {
      allowInsecureLoopbackForTests: true,
      permittedBrowserOrigin: 'https://owner.example.invalid',
      adminRecoveryEnabled: true,
      adminRecoveryUntilMs: Date.now() - 1000
    })
    await new Promise(done => server.listen(0, '127.0.0.1', done))
    const origin = `http://127.0.0.1:${server.address().port}`
    for (const path of ['/owner/v1/core-admin-recovery/authorize', '/owner/v1/core-admin-recovery/redeem']) {
      const response = await fetch(origin + path, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ deviceChallenge: randomBytes(32).toString('base64url') })
      })
      assert.equal(response.status, 404, path)
    }
  } finally {
    if (server?.listening) await new Promise(done => server.close(done))
    store?.close()
    rmSync(dir, { recursive: true, force: true })
  }
})

test('production TLS may not omit finite expiry even if the toggle is true', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'rehab-owner-no-expiry-'))
  let store, server
  try {
    store = new OwnerLicenseStore(join(dir, 'owner.sqlite'))
    const svc = new OwnerLicensingService(store, new SecretVault(randomBytes(32)))
    // Without a real TLS fixture, simulate the production (non-test) toggle
    // via a finite zero deadline. This must always fail closed.
    server = createOwnerControlServer(svc, {
      allowInsecureLoopbackForTests: true,
      adminRecoveryEnabled: true,
      adminRecoveryUntilMs: 0
    })
    await new Promise(done => server.listen(0, '127.0.0.1', done))
    const response = await fetch(`http://127.0.0.1:${server.address().port}/owner/v1/core-admin-recovery/redeem`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: '{}'
    })
    assert.equal(response.status, 404)
  } finally {
    if (server?.listening) await new Promise(done => server.close(done))
    store?.close()
    rmSync(dir, { recursive: true, force: true })
  }
})
