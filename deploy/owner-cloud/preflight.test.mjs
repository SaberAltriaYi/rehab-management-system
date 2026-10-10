import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { reviewOwnerCloudTemplates } from './preflight.mjs'

const text = file => readFileSync(new URL(file, import.meta.url), 'utf8')
const inputs = {
  nginx: text('./nginx.conf.template'),
  acl: text('./rehab-owner-admin-acl.conf'),
  unit: text('./rehab-owner.service.template')
}
test('two independent TLS hostnames, mTLS owner login and verified internal upstream', () => {
  const checked = reviewOwnerCloudTemplates(inputs)
  assert.equal(checked.templatesValid, true)
  assert.equal(checked.productionReady, false)
  assert.ok(checked.blockers.includes('CLOUD_ACCOUNT_AND_REGION_NOT_APPROVED'))
})
test('unverified TLS upstream, public owner ACL or unencrypted master key fails template gate', () => {
  assert.equal(reviewOwnerCloudTemplates({ ...inputs, nginx: inputs.nginx.replaceAll('proxy_ssl_verify on', 'proxy_ssl_verify off') }).templatesValid, false)
  assert.equal(reviewOwnerCloudTemplates({ ...inputs, acl: inputs.acl.replace('deny all;', 'allow all;') }).templatesValid, false)
  assert.equal(reviewOwnerCloudTemplates({ ...inputs, unit: inputs.unit.replace('LoadCredentialEncrypted=owner-vault-key:', 'Environment=REHAB_OWNER_VAULT_KEY_B64=') }).templatesValid, false)
})
