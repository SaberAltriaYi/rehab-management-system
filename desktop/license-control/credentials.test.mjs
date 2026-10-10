import { test } from 'node:test'
import assert from 'node:assert/strict'
import { randomBytes } from 'node:crypto'
import { mkdtempSync, rmSync, writeFileSync, symlinkSync, chmodSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { execFileSync } from 'node:child_process'
import { loadOwnerVaultKey, loadOwnerTLS } from './credentials.mjs'

const makeVault = () => randomBytes(32).toString('base64url')

test('strict Owner cloud mode rejects environment-only secrets and missing credential directory', () => {
  assert.throws(() => loadOwnerVaultKey({ REHAB_OWNER_REQUIRE_CREDENTIALS: '1', REHAB_OWNER_VAULT_KEY_B64: makeVault() }), /SYSTEMD_OWNER_CREDENTIAL_REQUIRED/)
  assert.throws(() => loadOwnerTLS({ REHAB_OWNER_REQUIRE_CREDENTIALS: '1' }), /SYSTEMD_OWNER_CREDENTIAL_REQUIRED/)
  assert.throws(() => loadOwnerVaultKey({ REHAB_OWNER_REQUIRE_CREDENTIALS: 'yes' }), /INVALID_OWNER_CREDENTIAL_POLICY/)
})

test('vault key accepts only private canonical 32-byte systemd credential', () => {
  const dir = mkdtempSync(join(tmpdir(), 'rehab-owner-cred-test-'))
  try {
    const encoded = makeVault()
    const file = join(dir, 'owner-vault-key')
    writeFileSync(file, encoded + '\n', { mode: 0o400 })
    assert.deepEqual(loadOwnerVaultKey({ CREDENTIALS_DIRECTORY: dir, REHAB_OWNER_REQUIRE_CREDENTIALS: '1' }), Buffer.from(encoded, 'base64url'))
    assert.throws(() => loadOwnerVaultKey({ CREDENTIALS_DIRECTORY: dir, REHAB_OWNER_VAULT_KEY_B64: encoded }), /AMBIGUOUS/)
    chmodSync(file, 0o644)
    assert.throws(() => loadOwnerVaultKey({ CREDENTIALS_DIRECTORY: dir }), /NOT_PRIVATE/)
    rmSync(file)
    symlinkSync(join(dir, 'missing'), file)
    assert.throws(() => loadOwnerVaultKey({ CREDENTIALS_DIRECTORY: dir }), /NOT_PRIVATE/)
  } finally { rmSync(dir, { recursive: true, force: true }) }
})

test('cloud TLS credential checks real certificate and exact private file modes', () => {
  const dir = mkdtempSync(join(tmpdir(), 'rehab-owner-tls-test-'))
  try {
    const cert = join(dir, 'owner-tls-cert')
    const key = join(dir, 'owner-tls-key')
    execFileSync('openssl', ['req', '-x509', '-newkey', 'ed25519', '-nodes',
      '-keyout', key, '-out', cert, '-days', '1',
      '-subj', '/CN=owner-upstream.internal', '-addext', 'subjectAltName=DNS:owner-upstream.internal'], { stdio: 'ignore', timeout: 10000 })
    chmodSync(key, 0o400)
    assert.ok(loadOwnerTLS({ CREDENTIALS_DIRECTORY: dir, REHAB_OWNER_REQUIRE_CREDENTIALS: '1' }).key.length > 100)
    chmodSync(key, 0o644)
    assert.throws(() => loadOwnerTLS({ CREDENTIALS_DIRECTORY: dir }), /NOT_PRIVATE/)
    assert.throws(() => loadOwnerTLS({ REHAB_OWNER_REQUIRE_CREDENTIALS: '1', REHAB_OWNER_TLS_CERT: cert, REHAB_OWNER_TLS_KEY: key }), /SYSTEMD_OWNER_CREDENTIAL_REQUIRED/)
  } finally { rmSync(dir, { recursive: true, force: true }) }
})

test('local legacy environment key remains compatible with L0-L5 development tests', () => {
  const encoded = makeVault()
  assert.deepEqual(loadOwnerVaultKey({ REHAB_OWNER_VAULT_KEY_B64: encoded }), Buffer.from(encoded, 'base64url'))
  assert.throws(() => loadOwnerVaultKey({ REHAB_OWNER_VAULT_KEY_B64: encoded + '=' }), /INVALID_OWNER_VAULT_KEY/)
})
