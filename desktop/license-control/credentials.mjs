// Owner-only secret loading. Production mode requires systemd-managed credentials
// rather than long-lived secret material in the environment or command line.
import { lstatSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { createPrivateKey, X509Certificate } from 'node:crypto'

function credentialFile(directory, filename, { privateMaterial = true, maxBytes = 64 * 1024 } = {}) {
  if (!directory || typeof directory !== 'string') throw new Error('CREDENTIALS_DIRECTORY_REQUIRED')
  const path = join(directory, filename)
  const info = lstatSync(path)
  if (!info.isFile() || info.isSymbolicLink() || info.nlink !== 1 || info.size === 0 ||
      info.size > maxBytes || (privateMaterial && (info.mode & 0o077) !== 0)) {
    throw new Error('OWNER_CREDENTIAL_FILE_NOT_PRIVATE')
  }
  return readFileSync(path)
}

export function loadOwnerVaultKey(env = process.env) {
  const strict = env.REHAB_OWNER_REQUIRE_CREDENTIALS === '1'
  if (env.REHAB_OWNER_REQUIRE_CREDENTIALS && !['0', '1'].includes(env.REHAB_OWNER_REQUIRE_CREDENTIALS)) {
    throw new Error('INVALID_OWNER_CREDENTIAL_POLICY')
  }
  let encoded
  if (env.CREDENTIALS_DIRECTORY) {
    if (env.REHAB_OWNER_VAULT_KEY_B64) throw new Error('AMBIGUOUS_OWNER_VAULT_KEY_SOURCE')
    encoded = credentialFile(env.CREDENTIALS_DIRECTORY, 'owner-vault-key', { maxBytes: 128 }).toString('ascii').trim()
  } else {
    if (strict) throw new Error('SYSTEMD_OWNER_CREDENTIAL_REQUIRED')
    encoded = env.REHAB_OWNER_VAULT_KEY_B64
  }
  if (!encoded || !/^[A-Za-z0-9_-]{43}$/.test(encoded)) throw new Error('INVALID_OWNER_VAULT_KEY')
  const key = Buffer.from(encoded, 'base64url')
  if (key.length !== 32 || key.toString('base64url') !== encoded) {
    throw new Error('INVALID_OWNER_VAULT_KEY')
  }
  return key
}

export function loadOwnerTLS(env = process.env) {
  const strict = env.REHAB_OWNER_REQUIRE_CREDENTIALS === '1'
  let cert, key
  if (env.CREDENTIALS_DIRECTORY) {
    if (env.REHAB_OWNER_TLS_KEY || env.REHAB_OWNER_TLS_CERT) {
      throw new Error('AMBIGUOUS_OWNER_TLS_SOURCE')
    }
    cert = credentialFile(env.CREDENTIALS_DIRECTORY, 'owner-tls-cert', { privateMaterial: false })
    key = credentialFile(env.CREDENTIALS_DIRECTORY, 'owner-tls-key')
  } else {
    if (strict) throw new Error('SYSTEMD_OWNER_CREDENTIAL_REQUIRED')
    if (!env.REHAB_OWNER_TLS_CERT || !env.REHAB_OWNER_TLS_KEY) {
      throw new Error('OWNER_TLS_CREDENTIAL_MISSING')
    }
    cert = readFileSync(env.REHAB_OWNER_TLS_CERT)
    key = readFileSync(env.REHAB_OWNER_TLS_KEY)
  }
  const certificate = new X509Certificate(cert)
  if (!certificate.checkPrivateKey(createPrivateKey(key))) {
    throw new Error('OWNER_TLS_CERT_KEY_MISMATCH')
  }
  if (strict && !certificate.checkHost('owner-upstream.internal')) {
    throw new Error('OWNER_UPSTREAM_TLS_IDENTITY_MISMATCH')
  }
  if (new Date(certificate.validTo).getTime() <= Date.now()) {
    throw new Error('OWNER_TLS_CERT_EXPIRED')
  }
  return { cert, key }
}
