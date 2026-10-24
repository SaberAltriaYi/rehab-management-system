import assert from 'node:assert/strict'
import { test } from 'node:test'
import { randomBytes, randomUUID } from 'node:crypto'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { activationMailConfig, sendActivationNotice } from './mailer.mjs'
import { OwnerLicenseStore } from './store.mjs'
import { OwnerLicensingService } from './service.mjs'
import { SecretVault, totpAt } from './security.mjs'

const config = activationMailConfig({
  REHAB_OWNER_EMAIL_RESEND_KEY: 're_synthetic_api_key_for_unit_testing_only',
  REHAB_OWNER_EMAIL_FROM: 'noreply@example.test',
  REHAB_MAIN_PUBLIC_LOGIN_URL: 'https://login.example.test/login'
})
const exampleApp = {
  id: randomUUID(), organization: '合成康复机构', status: 'APPROVED',
  provisioning_status: 'ACTIVE', main_tenant_id: 41,
  contact_email: 'contact@example.test'
}

test('activation emails are opt-in, require public HTTPS URL and real sender config', () => {
  assert.equal(activationMailConfig({}).configured, false)
  assert.equal(activationMailConfig({ ...{
    REHAB_OWNER_EMAIL_RESEND_KEY: config.apiKey,
    REHAB_OWNER_EMAIL_FROM: config.from,
    REHAB_MAIN_PUBLIC_LOGIN_URL: 'https://127.0.0.1:5173/login'
  } }).configured, false)
  assert.equal(config.configured, true)
})

test('activation email cannot be sent for a license-only tenant', async () => {
  let calls = 0
  await assert.rejects(sendActivationNotice({ ...exampleApp, main_tenant_id: null }, config,
    () => { calls++; throw Error('must not send') }), /MAIN_SYSTEM_NOT_ACTIVE/)
  assert.equal(calls, 0)
})

test('provider accepts idempotent activation notice with no credentials', async () => {
  let calls = 0
  const result = await sendActivationNotice(exampleApp, config, async (url, options) => {
    calls++
    assert.equal(url, 'https://api.resend.com/emails')
    assert.equal(options.headers['Idempotency-Key'], 'rehab-owner-activation/' + exampleApp.id)
    const message = JSON.parse(options.body)
    assert.deepEqual(message.to, [exampleApp.contact_email])
    assert.match(message.text, /主系统已完成开通/)
    assert.doesNotMatch(options.body, /password|totp|bearer|临时密码：/i)
    assert.equal(options.redirect, 'error')
    return { ok: true, json: async () => ({ id: 'accepted-provider-id' }) }
  })
  assert.equal(calls, 1)
  assert.equal(result.providerId, 'accepted-provider-id')
})

test('email dispatch enforces Owner MFA, main-tenant activation and deduplicates', async () => {
  const root = mkdtempSync(join(tmpdir(), 'owner-mail-test-'))
  const store = new OwnerLicenseStore(join(root, 'owner.sqlite'))
  const svc = new OwnerLicensingService(store, new SecretVault(randomBytes(32)))
  const originalFetch = globalThis.fetch
  let sent = 0
  try {
    const nowMs = Date.now()
    const secret = (await svc.provisionOwner({
      username: 'mailowner', password: 'Synthetic-Test-Only-Long-Password!', nowMs
    })).mfaSecret
    const token = (await svc.login({
      username: 'mailowner', password: 'Synthetic-Test-Only-Long-Password!',
      totp: totpAt(secret, Math.floor(nowMs / 30_000)), nowMs
    })).token
    const created = svc.submitPublicApplication({
      organization: exampleApp.organization, contactName: '测试联系人',
      contactEmail: exampleApp.contact_email, nowMs
    })
    svc.activationMail = config
    await assert.rejects(svc.sendActivationMail({ token, applicationId: created.applicationId, nowMs }),
      error => error.code === 'MAIN_SYSTEM_NOT_ACTIVE')
    assert.equal(sent, 0)
    store.db.prepare("UPDATE applications SET status='APPROVED',provisioning_status='ACTIVE',main_tenant_id=41 WHERE id=?")
      .run(created.applicationId)
    globalThis.fetch = async () => {
      sent++
      return { ok: true, json: async () => ({ id: 'provider-accepted-1' }) }
    }
    assert.equal((await svc.sendActivationMail({ token, applicationId: created.applicationId, nowMs })).status, 'ACCEPTED')
    assert.equal((await svc.sendActivationMail({ token, applicationId: created.applicationId, nowMs })).status, 'ACCEPTED')
    assert.equal(sent, 1)
    assert.equal(svc.applicationList({ token, nowMs })[0].notificationStatus, 'ACCEPTED')
    await assert.rejects(svc.sendActivationMail({ token: 'bad', applicationId: created.applicationId, nowMs }),
      error => error.code === 'OWNER_UNAUTHORIZED')
  } finally {
    globalThis.fetch = originalFetch
    store.close()
    rmSync(root, { force: true, recursive: true })
  }
})
