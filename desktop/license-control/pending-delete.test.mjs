import { test } from 'node:test'
import assert from 'node:assert/strict'
import { randomBytes } from 'node:crypto'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { OwnerLicenseStore } from './store.mjs'
import { OwnerLicensingService } from './service.mjs'
import { SecretVault, totpAt } from './security.mjs'
import { createOwnerControlServer } from './http.mjs'

test('pending deletion requires MFA step-up, is audited and never deletes an approved license',
  { timeout: 20_000 }, async t => {
    const directory = mkdtempSync(join(tmpdir(), 'rehab-owner-delete-synthetic-'))
    const store = new OwnerLicenseStore(join(directory, 'isolated-owner.sqlite'))
    const service = new OwnerLicensingService(store, new SecretVault(randomBytes(32)))
    const server = createOwnerControlServer(service, { allowInsecureLoopbackForTests: true })
    t.after(async () => {
      if (server.listening) await new Promise(resolve => server.close(resolve))
      store.close()
      rmSync(directory, { recursive: true, force: true })
    })
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
    const base = 'http://127.0.0.1:' + server.address().port
    const ownerPassword = 'Synthetic-Owner-Password-2026!'
    // Logging in includes a 2-minute step-up window; begin far enough in
    // the past that the bearer is valid but its privileged window expired.
    const start = Date.now() - 181_000
    const secret = (await service.provisionOwner({
      username: 'synthetic-owner', password: ownerPassword, nowMs: start
    })).mfaSecret
    const ownerToken = (await service.login({
      username: 'synthetic-owner', password: ownerPassword,
      totp: totpAt(secret, Math.floor(start / 30_000)), nowMs: start
    })).token
    const intake = service.submitPublicApplication({
      organization: 'Synthetic pending rehab studio',
      contactName: 'Synthetic contact', nowMs: start
    })
    const approved = service.submitPublicApplication({
      organization: 'Synthetic approved rehab studio',
      contactName: 'Synthetic approved contact', nowMs: start + 1
    })
    const route = id => '/owner/v1/applications/' + id + '/delete'
    async function request(path, { token = ownerToken, reason = 'Synthetic cleanup request',
      body = { reason } } = {}) {
      const res = await fetch(base + path, {
        method: 'POST',
        headers: { 'content-type': 'application/json',
          ...(token ? { authorization: 'Bearer ' + token } : {}) },
        body: JSON.stringify(body)
      })
      return { status: res.status, body: await res.json() }
    }
    assert.equal((await request(route(intake.applicationId), { token: null })).status, 401)
    const first = await request(route(intake.applicationId))
    assert.equal(first.status, 401)
    assert.equal(first.body.error, 'OWNER_STEP_UP_REQUIRED')
    assert.equal(service.applicationList({ token: ownerToken, nowMs: Date.now() }).length, 2)

    service.stepUp({
      token: ownerToken,
      totp: totpAt(secret, Math.floor(Date.now() / 30_000)), nowMs: Date.now()
    })
    const missingReason = await request(route(intake.applicationId), { reason: 'bad' })
    assert.equal(missingReason.status, 400)
    assert.equal(missingReason.body.error, 'INVALID_REASON')
    const unknownField = await request(route(intake.applicationId), {
      body: { reason: 'Synthetic cleanup', admin: true }
    })
    assert.equal(unknownField.status, 400)
    assert.equal(unknownField.body.error, 'UNRECOGNIZED_INPUT_FIELD')

    const approvedPolicy = {
      expiresAtMs: Date.now() + 90 * 86400000,
      seatLimit: null, maxInstallations: 1, modules: ['motion', 'report']
    }
    service.decideApplication({
      token: ownerToken, applicationId: approved.applicationId,
      decision: 'APPROVED', reason: 'Synthetic approved', policy: approvedPolicy,
      nowMs: Date.now()
    })
    const rejectedApproved = await request(route(approved.applicationId))
    assert.equal(rejectedApproved.status, 409)
    assert.equal(rejectedApproved.body.error, 'APPLICATION_NOT_PENDING')
    const existing = service.status({
      token: ownerToken, tenantId: approved.tenantId, nowMs: Date.now()
    })
    assert.equal(existing.status, 'ACTIVE')
    assert.equal(service.applicationList({ token: ownerToken, nowMs: Date.now() }).length, 2)

    const deletion = await request(route(intake.applicationId))
    assert.equal(deletion.status, 200)
    assert.deepEqual(deletion.body, { applicationId: intake.applicationId, status: 'DELETED' })
    const remaining = service.applicationList({ token: ownerToken, nowMs: Date.now() })
    assert.equal(remaining.length, 1)
    assert.equal(remaining[0].id, approved.applicationId)
    assert.equal((await request(route(intake.applicationId))).status, 409,
      'Deletion cannot be replayed or target another applicant')
    const history = service.audit({ token: ownerToken, nowMs: Date.now() })
    const entry = history.find(event => event.action === 'APPLICATION_DELETED')
    assert.ok(entry, 'Every deletion must retain a tamper-evident audit entry')
    assert.equal(entry.subject, intake.tenantId)
    assert.equal(entry.details.previousStatus, 'PENDING')
    assert.equal(store.verifyAuditChain(), true)
    assert.equal(store.db.prepare(
      'SELECT COUNT(*) AS n FROM entitlements WHERE tenant_uuid=?'
    ).get(approved.tenantId).n, 1)

    // Public registration may resubmit after pending intake was removed;
    // old deletion and submission audit events remain in the hash chain.
    const replacement = service.submitPublicApplication({
      organization: 'Synthetic replacement application',
      contactName: 'Synthetic new contact', nowMs: Date.now()
    })
    assert.equal(replacement.status, 'PENDING')
    assert.equal(store.verifyAuditChain(), true)
  })
