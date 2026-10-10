#!/usr/bin/env node
// True L1 owner signer output consumed by Rust and Java verifier tests.
// Writes PUBLIC issuer metadata and synthetic leases only to a private caller
// temp folder. Never saves MFA seed, passwords, vault keys or signing PEM.
import { createPublicKey, generateKeyPairSync, randomBytes, randomUUID, sign } from 'node:crypto'
import { writeFileSync, lstatSync } from 'node:fs'
import { resolve } from 'node:path'
import { OwnerLicenseStore } from '../license-control/store.mjs'
import { OwnerLicensingService } from '../license-control/service.mjs'
import { SecretVault, totpAt } from '../license-control/security.mjs'
import { enrollmentMessage, renewalMessage } from './tenant-license-owner-core.mjs'
import { verifyTenantLicenseLease } from './tenant-license-lease.mjs'

const destination = process.argv[2]
if (!destination || !resolve(destination).endsWith('/l5-node-fixture.json')) {
  throw Error('Require an explicit private temporary l5-node-fixture.json output path')
}
try { lstatSync(destination); throw Error('Refusing to overwrite an existing fixture') }
catch (e) { if (e.code !== 'ENOENT') throw e }
const nowMs = Date.parse('2026-10-10T07:00:00.000Z')
const dir = resolve(destination, '..')
const store = new OwnerLicenseStore(resolve(dir, 'l5-owner-ephemeral.sqlite'))
const vault = new SecretVault(randomBytes(32))
const service = new OwnerLicensingService(store, vault)
try {
  const { mfaSecret } = await service.provisionOwner({
    username: 'l5bridgeowner', password: 'Only-Synthetic-L5-Owner-Password-123!',
    nowMs
  })
  const ownerToken = (await service.login({
    username: 'l5bridgeowner', password: 'Only-Synthetic-L5-Owner-Password-123!',
    totp: totpAt(mfaSecret, Math.floor(nowMs / 30000)), nowMs
  })).token
  const { kid, publicKeyPem } = service.rotateKey({ token: ownerToken, nowMs })
  const key = createPublicKey(publicKeyPem)
  const rawKey = key.export({ type: 'spki', format: 'der' }).subarray(-32).toString('base64url')
  const studios = []
  for (const [name, seatLimit] of [['finite', 2], ['unlimited', null]]) {
    const tenantId = randomUUID()
    const installationId = randomUUID()
    const installation = generateKeyPairSync('ed25519')
    const app = service.submitApplication({
      tenantId, installationId, organization: 'L5 独立签名合成工作室',
      installationPublicKeyPem: installation.publicKey.export({ type: 'spki', format: 'pem' }),
      possessionSignature: sign(null, enrollmentMessage(tenantId, installationId),
        installation.privateKey).toString('base64url'), nowMs
    })
    service.decideApplication({
      token: ownerToken, applicationId: app.applicationId,
      decision: 'APPROVED', reason: 'L5 合成验证已批准',
      policy: { expiresAtMs: nowMs + 365 * 86400000,
        seatLimit, maxInstallations: 1, modules: ['rehab', 'report'] }, nowMs
    })
    const challenge = service.challenge({ tenantId, installationId, nowMs })
    const lease = service.issueOnline({
      tenantId, installationId, nonce: challenge.nonce,
      possessionSignature: sign(null, renewalMessage(challenge.nonce, tenantId, installationId),
        installation.privateKey).toString('base64url'), nowMs
    })
    const checked = verifyTenantLicenseLease(lease.token, {
      trustedKeys: new Map([[kid, key]]), tenantId, installationId, nowMs,
      minimumRevocationEpoch: 0, lastTrustedTimeMs: nowMs
    })
    if (checked.seatLimit !== seatLimit) throw Error('Synthetic owner signed wrong seatLimit')
    studios.push({ name, tenantId, installationId,
      token: lease.token, statusProof: lease.statusProof, seatLimit })
  }
  service.setTenantStatus({
    token: ownerToken, tenantId: studios[1].tenantId,
    status: 'REVOKED', nowMs: nowMs + 1000
  })
  const revokedStatusProof = service.issueStatusProof({
    tenantId: studios[1].tenantId, installationId: studios[1].installationId,
    nowMs: nowMs + 1000
  })
  const output = {
    protocol: 'rehab-l5-node-cross-runtime/1',
    issuerKid: kid, publicKeyBase64url: rawKey, nowMs,
    studios, revokedStatusProof
  }
  writeFileSync(destination, JSON.stringify(output) + '\n', { flag: 'wx', mode: 0o600 })
  if (!store.verifyAuditChain()) throw Error('Owner audit chain failed')
  process.stdout.write('L5_SYNTHETIC_NODE_SIGNED=2 PUBLIC_ONLY=1 AUDIT_OK=1\n')
} finally {
  store.close()
}
