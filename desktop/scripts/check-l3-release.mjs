/**
 * Fail-closed P0 L3 packaging guard. NEVER issues keys, accesses the owner
 * center, modifies clinic data, performs a migration or changes release gates.
 * This is an additional check; original prepackage gates remain mandatory.
 */
import { createHash } from 'node:crypto'
import { readFileSync, realpathSync, statSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

function safeRead(root, relative) {
  try {
    const path = resolve(root, relative)
    const realRoot = realpathSync(root)
    const real = realpathSync(path)
    if (!(real === join(realRoot, relative)) || !statSync(real).isFile()) return null
    const bytes = readFileSync(real)
    if (bytes.length > 64 * 1024) return null
    return bytes.toString('utf8')
  } catch {
    return null
  }
}

const KID = /^[A-Za-z0-9._-]{1,48}$/
const KEY = /^[A-Za-z0-9_-]{43}$/

export function evaluateL3Release(root) {
  const blockers = []
  const conf = safeRead(root, 'desktop/launcher/src-tauri/resources/license/trusted-issuers.json')
  let parsed = null
  try { parsed = JSON.parse(conf ?? '') } catch { /* reject below */ }
  if (!parsed || !Array.isArray(parsed.issuers) || parsed.issuers.length === 0 ||
      parsed.issuers.length > 12) {
    blockers.push('PUBLISHER_ROOTS_MISSING')
  } else {
    const seen = new Set()
    for (const item of parsed.issuers) {
      if (!item || !KID.test(item.kid ?? '') || seen.has(item.kid) ||
          !KEY.test(item.publicKeyBase64url ?? '')) {
        blockers.push('INVALID_PUBLISHER_KEY')
        break
      }
      seen.add(item.kid)
      const key = Buffer.from(item.publicKeyBase64url, 'base64url')
      if (key.length !== 32 || key.toString('base64url') !== item.publicKeyBase64url) {
        blockers.push('INVALID_PUBLISHER_KEY')
        break
      }
    }
  }
  try {
    const url = new URL(parsed?.ownerBaseUrl)
    if (url.protocol !== 'https:' || !url.hostname || url.username || url.password ||
        url.search || url.hash || url.pathname !== '/' ||
        /^(localhost|127\.|0\.0\.0\.0|\[::1\]|.*\.invalid|.*\.example)$/i.test(url.hostname)) {
      blockers.push('PUBLISHER_HTTPS_ORIGIN_MISSING')
    }
  } catch {
    blockers.push('PUBLISHER_HTTPS_ORIGIN_MISSING')
  }

  const manifest = safeRead(root, 'deploy/internal/migrations.manifest')
  const source = safeRead(root, 'sql/mysql/rehab-tenant-license-v1.sql')
  const entry = manifest?.split(/\r?\n/).find(line =>
    /^\d+\|[a-f0-9]{64}\|sql\/mysql\/rehab-tenant-license-v1\.sql\|/.test(line))
  if (!entry || !source) {
    blockers.push('TENANT_LICENSE_SCHEMA_MIGRATION_NOT_INTEGRATED')
  } else {
    const sha = createHash('sha256').update(source).digest('hex')
    if (entry.split('|')[1] !== sha) blockers.push('TENANT_LICENSE_SCHEMA_HASH_MISMATCH')
  }

  const rust = safeRead(root, 'desktop/launcher/src-tauri/Cargo.toml')
  const workflow = safeRead(root, '.github/workflows/desktop-release.yml')
  if (!rust?.includes('license-required = []')) blockers.push('RUST_ENFORCEMENT_FEATURE_MISSING')
  // Only two signed release branches (Windows NSIS + macOS DMG) are allowed.
  // No unsigned fallback may be packaged or uploaded.
  const builds = workflow?.split(/\r?\n/).filter(line =>
    /run:\s*pnpm tauri build\s+--bundles\s+(nsis|dmg)\b/.test(line)) ?? []
  if (builds.length !== 2 ||
      !builds.some(line => /--bundles nsis\b/.test(line)) ||
      !builds.some(line => /--bundles dmg\b/.test(line)) ||
      builds.some(line => !line.includes('--features license-required'))) {
    blockers.push('PACKAGING_CAN_EMIT_UNLICENSED_APPLICATION')
  }
  const requiredSigningChecks = [
    'Require Windows code-signing credentials',
    'Get-AuthenticodeSignature',
    "signature.Status -ne 'Valid'",
    'Require Apple signing and notarization credentials',
    'codesign --verify --deep --strict',
    'xcrun notarytool submit',
    "'.status == \"Accepted\"'",
    'xcrun stapler staple',
    'xcrun stapler validate',
    'spctl -a -t open',
    'notarization=accepted-and-stapled'
  ]
  if (!workflow || requiredSigningChecks.some(phrase => !workflow.includes(phrase)) ||
      /name:\s*Build unsigned\b/.test(workflow) || /_unsigned/.test(workflow)) {
    blockers.push('UNSIGNED_OR_UNNOTARIZED_RELEASE_PATH')
  }
  return { ready: blockers.length === 0, blockerCount: blockers.length, blockers }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')
  const result = evaluateL3Release(root)
  process.stdout.write(JSON.stringify(result, null, 2) + '\n')
  process.exitCode = result.ready ? 0 : 1
}
