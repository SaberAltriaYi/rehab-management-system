// Read-only host-side Owner -> Core provisioning readiness audit.
// No bearer/password output, no registry/database mutations, no public network
// and no HTTP request containing a machine token. Run: node <script> --live
import { readFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { resolve } from 'node:path'
import { execFileSync } from 'node:child_process'
import { Socket } from 'node:net'
import { timingSafeEqual } from 'node:crypto'
import { pathToFileURL } from 'node:url'

const defaultOwnerEnv = resolve(homedir(), 'my-project/rehab-runtime/owner-local/owner.env')
const defaultCoreSettings = resolve(homedir(), 'Library/Application Support/com.saberaltriayi.rehab.internal/config/settings.json')

export function evaluateProvisioningReadiness(value) {
  const blockers = []
  if (!value.ownerConfigured) blockers.push('OWNER_PROVISIONING_CONFIG_INCOMPLETE')
  if (!value.targetMatchesCurrentCore) blockers.push('OWNER_TARGET_NOT_CURRENT_CORE_HTTPS')
  if (!value.targetReachable) blockers.push('OWNER_TARGET_UNREACHABLE')
  if (!value.coreProvisioningEnabled) blockers.push('CORE_PROVISIONING_DISABLED_OR_UNVERIFIED')
  if (!value.coreTokenMatched) blockers.push('OWNER_CORE_MACHINE_TOKEN_NOT_VERIFIED')
  if (!value.schemaV3Ready) blockers.push('SAAS_PROVISIONING_V3_SCHEMA_MISSING')
  if (!value.schemaV4Ready) blockers.push('SAAS_ENTITLEMENT_V4_SCHEMA_MISSING')
  if (!value.livePlanPackageVerified) blockers.push('CORE_PLAN_PACKAGE_NOT_VERIFIED')
  return {
    ready: blockers.length === 0,
    blockers,
    // Only non-sensitive booleans are published; no URL, UUID, token, tenant
    // names, data rows, account IDs, environment variables or passwords.
    checks: Object.fromEntries(Object.entries(value).filter(([key]) => [
      'ownerConfigured', 'targetMatchesCurrentCore', 'targetReachable',
      'coreProvisioningEnabled', 'coreTokenMatched',
      'schemaV3Ready', 'schemaV4Ready', 'livePlanPackageVerified'
    ].includes(key)))
  }
}

function privateEnv(contents) {
  const result = {}
  for (const line of contents.split(/\r?\n/)) {
    if (!line.trim() || line.trim().startsWith('#')) continue
    const assign = line.trim().replace(/^export\s+/, '')
    const match = /^([A-Z0-9_]+)=(.*)$/.exec(assign)
    if (match) result[match[1]] = match[2].trim().replace(/^['"]|['"]$/g, '')
  }
  return result
}

function equalSecret(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string' ||
      a.length < 24 || b.length < 24) return false
  const left = Buffer.from(a)
  const right = Buffer.from(b)
  return left.length === right.length && timingSafeEqual(left, right)
}

function dockerCoreEnv() {
  const json = execFileSync('docker', ['inspect', 'rehab-studio-internal-server-1'], {
    encoding: 'utf8', timeout: 12000, maxBuffer: 2 * 1024 * 1024
  })
  const values = JSON.parse(json)[0]?.Config?.Env ?? []
  return Object.fromEntries(values.map(item => {
    const i = item.indexOf('=')
    return i >= 0 ? [item.slice(0, i), item.slice(i + 1)] : [item, '']
  }))
}

function dbMetadata(planId, packageId) {
  // Never query a patient, any tenant row, credentials or clinical data.
  const sql = [
    "SELECT",
    "(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_tenant' AND column_name='tenant_uuid'),",
    "(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='saas_provisioning_requests'),",
    "(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_provisioning_requests' AND column_name='entitlement_revision'),",
    "(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='saas_plans'),",
    "(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='system_tenant_package'),",
    "(SELECT COUNT(*) FROM saas_plans WHERE id=" + planId + ' AND status=1),',
    "(SELECT COUNT(*) FROM system_tenant_package WHERE id=" + packageId + ' AND deleted=0);'
  ].join(' ')
  const result = execFileSync('docker', [
    'exec', 'rehab-studio-internal-mysql-1', 'mysql',
    '--defaults-extra-file=/run/rehab-secrets/mysql-client.cnf',
    '--batch', '--skip-column-names', 'ruoyi-vue-pro', '--execute', sql
  ], { encoding: 'utf8', timeout: 15000, maxBuffer: 16000 })
  const values = result.trim().split(/\s+/).map(Number)
  if (values.length !== 7 || values.some(n => !Number.isSafeInteger(n))) throw Error('DB metadata query did not return 7 counts')
  return values
}

function probeLocalTcp(target) {
  return new Promise(resolveProbe => {
    let parsed
    try { parsed = new URL(target) } catch { resolveProbe(false); return }
    if (!['127.0.0.1', 'localhost', '[::1]'].includes(parsed.hostname) ||
        !['http:', 'https:'].includes(parsed.protocol)) {
      resolveProbe(false); return
    }
    const port = Number(parsed.port || (parsed.protocol === 'https:' ? 443 : 80))
    if (!Number.isSafeInteger(port) || port < 1 || port > 65535) {
      resolveProbe(false); return
    }
    const socket = new Socket()
    let done = false
    const finish = success => {
      if (done) return
      done = true
      socket.destroy()
      resolveProbe(success)
    }
    socket.setTimeout(1800)
    socket.once('connect', () => finish(true))
    socket.once('error', () => finish(false))
    socket.once('timeout', () => finish(false))
    socket.connect(port, parsed.hostname === 'localhost' ? '127.0.0.1' : parsed.hostname)
  })
}

export async function inspectLocalProvisioning({
  ownerEnv = defaultOwnerEnv, coreSettings = defaultCoreSettings
} = {}) {
  const owner = privateEnv(readFileSync(ownerEnv, 'utf8'))
  const core = JSON.parse(readFileSync(coreSettings, 'utf8'))
  const planId = Number(owner.REHAB_MAIN_PROVISIONING_PLAN_ID)
  const packageId = Number(owner.REHAB_MAIN_PROVISIONING_PACKAGE_ID)
  const target = owner.REHAB_MAIN_PROVISIONING_URL || ''
  const expected = 'https://127.0.0.1:' + core.httpsPort
  const ownerConfigured = Boolean(target && owner.REHAB_MAIN_PROVISIONING_TOKEN &&
    Number.isSafeInteger(planId) && planId > 0 &&
    Number.isSafeInteger(packageId) && packageId > 0)
  const container = dockerCoreEnv()
  let counts = Array(7).fill(0)
  if (ownerConfigured) {
    try { counts = dbMetadata(planId, packageId) } catch { /* fail closed */ }
  }
  return evaluateProvisioningReadiness({
    ownerConfigured,
    targetMatchesCurrentCore: target === expected,
    targetReachable: await probeLocalTcp(target),
    coreProvisioningEnabled: container.YUDAO_PROVISIONING_ENABLED === 'true',
    coreTokenMatched: equalSecret(owner.REHAB_MAIN_PROVISIONING_TOKEN, container.YUDAO_PROVISIONING_TOKEN),
    schemaV3Ready: counts[0] === 1 && counts[1] === 1,
    schemaV4Ready: counts[2] === 1,
    livePlanPackageVerified: counts[3] === 1 && counts[4] === 1 && counts[5] === 1 && counts[6] === 1
  })
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  if (process.argv[2] !== '--live') {
    process.stderr.write('Usage: node owner-core-provisioning-preflight.mjs --live\n')
    process.exitCode = 2
  } else {
    try {
      const result = await inspectLocalProvisioning()
      process.stdout.write(JSON.stringify(result, null, 2) + '\n')
      if (!result.ready) process.exitCode = 2
    } catch {
      process.stderr.write('Owner/Core read-only preflight failed closed; inspect local runtime configuration.\n')
      process.exitCode = 2
    }
  }
}
