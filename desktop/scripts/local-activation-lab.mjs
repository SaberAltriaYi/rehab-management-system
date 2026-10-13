#!/usr/bin/env node
/**
 * Strictly local, synthetic Owner issuer for a *separate* test launcher.
 * Not for production. No patient data, original license key, Git or Docker.
 *
 * Usage: node desktop/scripts/local-activation-lab.mjs init|serve|approve LAB_PATH
 * LAB_PATH must be within validation-evidence/2026-10-10/lab-activation-20261010.*
 */
import { readFileSync, writeFileSync, existsSync, realpathSync } from 'node:fs'
import { basename, join, resolve } from 'node:path'
import { randomBytes, createPublicKey } from 'node:crypto'
import { request as httpsRequest } from 'node:https'
import { OwnerLicenseStore } from '../license-control/store.mjs'
import { OwnerLicensingService } from '../license-control/service.mjs'
import { SecretVault, totpAt } from '../license-control/security.mjs'
import { createOwnerControlServer } from '../license-control/http.mjs'

const [mode, supplied] = process.argv.slice(2)
if (!['init', 'serve', 'approve'].includes(mode) || !supplied) throw Error('INVALID_LAB_COMMAND')
const lab = realpathSync(resolve(supplied))
if (!/^lab-activation-20261010\.[a-zA-Z0-9]{8}$/.test(basename(lab)) ||
    !lab.includes('/rehab/validation-evidence/2026-10-10/')) {
  throw Error('NOT_AN_APPROVED_PRIVATE_SYNTHETIC_LAB')
}
const owner = join(lab, 'owner')
const db = join(owner, 'owner-test.sqlite')
const vaultFile = join(owner, 'test-vault.key')
const credsFile = join(owner, 'test-owner.json')
const trustFile = join(owner, 'test-issuer-public.json')
const port = 19444

function openOwner() {
  const key = Buffer.from(readFileSync(vaultFile, 'utf8').trim(), 'base64url')
  const store = new OwnerLicenseStore(db)
  return { store, service: new OwnerLicensingService(store, new SecretVault(key)) }
}
if (mode === 'init') {
  if ([vaultFile, db, trustFile, credsFile].some(existsSync)) {
    throw Error('SYNTHETIC_OWNER_ALREADY_EXISTS_REFUSING_OVERWRITE')
  }
  writeFileSync(vaultFile, randomBytes(32).toString('base64url') + '\n', { mode: 0o600, flag: 'wx' })
  const { store, service } = openOwner()
  try {
    const username = 'lab-owner'
    const password = 'TEST-ONLY-' + randomBytes(24).toString('base64url')
    const { mfaSecret } = await service.provisionOwner({ username, password, nowMs: Date.now() })
    const { token } = await service.login({
      username, password, totp: totpAt(mfaSecret, Math.floor(Date.now() / 30_000)), nowMs: Date.now()
    })
    const { kid, publicKeyPem } = service.rotateKey({ token, nowMs: Date.now() })
    const raw = createPublicKey(publicKeyPem).export({ type: 'spki', format: 'der' })
      .subarray(-32).toString('base64url')
    writeFileSync(credsFile, JSON.stringify({ username, password, mfaSecret }) + '\n',
      { mode: 0o600, flag: 'wx' })
    writeFileSync(trustFile, JSON.stringify({
      ownerBaseUrl: 'https://127.0.0.1:19444',
      issuers: [{ kid, publicKeyBase64url: raw }]
    }, null, 2) + '\n', { mode: 0o600, flag: 'wx' })
    if (!store.verifyAuditChain()) throw Error('OWNER_TEST_AUDIT_CHAIN_FAILED')
    console.log('ISOLATED_TEST_OWNER_CREATED=1 PUBLIC_KID=' + kid)
  } finally { store.close() }
} else if (mode === 'serve') {
  const { store, service } = openOwner()
  const server = createOwnerControlServer(service, {
    tlsOptions: {
      cert: readFileSync(join(lab, 'tls/server.pem')),
      key: readFileSync(join(lab, 'tls/server.key')),
      minVersion: 'TLSv1.2'
    },
    permittedBrowserOrigin: 'https://127.0.0.1:19444'
  })
  server.headersTimeout = 15_000
  server.requestTimeout = 10_000
  server.listen(port, '127.0.0.1', () => {
    console.log('ISOLATED_TEST_OWNER_LOOPBACK_HTTPS_READY=127.0.0.1:' + port)
  })
  const shutdown = () => server.close(() => { store.close(); process.exit(0) })
  process.once('SIGINT', shutdown)
  process.once('SIGTERM', shutdown)
} else if (mode === 'approve') {
  const credentials = JSON.parse(readFileSync(credsFile, 'utf8'))
  const ca = readFileSync(join(lab, 'tls/ca.pem'))
  function call(path, method, body, token) {
    return new Promise((success, fail) => {
      const data = method === 'GET' ? '' : JSON.stringify(body ?? {})
      const headers = {
        ...(data ? { 'content-type': 'application/json', 'content-length': Buffer.byteLength(data) } : {}),
        ...(token ? { authorization: 'Bearer ' + token } : {})
      }
      const req = httpsRequest({ hostname: '127.0.0.1', port, path, method, ca,
        rejectUnauthorized: true, headers, timeout: 7000 }, response => {
        const chunks = []
        response.on('data', part => chunks.push(part))
        response.on('end', () => {
          const obj = JSON.parse(Buffer.concat(chunks).toString('utf8'))
          if (response.statusCode < 200 || response.statusCode >= 300) {
            fail(Error('OWNER_HTTPS_DENIED_' + response.statusCode + '_' + (obj.error ?? '')))
          } else success(obj)
        })
      })
      req.on('error', fail)
      req.end(data)
    })
  }
  const { token } = await call('/owner/v1/login', 'POST', {
    username: credentials.username, password: credentials.password,
    totp: totpAt(credentials.mfaSecret, Math.floor(Date.now() / 30_000))
  })
  const { applications } = await call('/owner/v1/applications', 'GET', null, token)
  const pending = applications.filter(item => item.status === 'PENDING')
  if (pending.length !== 1) throw Error('EXPECTED_EXACTLY_ONE_SYNTHETIC_PENDING_STUDIO')
  const selected = pending[0]
  const response = await call('/owner/v1/applications/' + selected.id + '/approve',
    'POST', {
      policy: { expiresAtMs: Date.now() + 14 * 86_400_000,
        seatLimit: null, maxInstallations: 1, modules: ['rehab', 'report'] },
      reason: 'TEST ONLY local synthetic studio'
    }, token)
  console.log('SYNTHETIC_STUDIO_APPROVAL=' + response.status +
    ' tenant=' + selected.tenantId + ' installation=' + selected.installationId)
}
