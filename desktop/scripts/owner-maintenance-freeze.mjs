/**
 * Read-only prerequisite for a controlled Core maintenance window.
 * An Owner HTTPS target is expected after repair; the explicit activation
 * flag must remain false throughout migrations and Core restarts.
 */
import { readFileSync, lstatSync } from 'node:fs'
import { homedir } from 'node:os'
import { join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const ownerEnvPath = join(homedir(), 'my-project/rehab-runtime/owner-local/owner.env')

export function inspectOwnerFreeze(contents) {
  const keys = new Map()
  for (const raw of contents.split(/\r?\n/)) {
    const line = raw.trim().replace(/^export\s+/, '')
    if (!line || line.startsWith('#')) continue
    const matched = /^([A-Z][A-Z0-9_]*)=(.*)$/.exec(line)
    if (!matched) continue
    if (keys.has(matched[1])) throw Error('DUPLICATE_OWNER_ENV_KEY')
    const value = matched[2].trim().replace(/^['"]|['"]$/g, '')
    keys.set(matched[1], value)
  }
  const checks = {
    explicitDisabled: keys.get('REHAB_MAIN_PROVISIONING_ENABLED') === 'false',
    fixedHttpsLoopback: keys.get('REHAB_MAIN_PROVISIONING_URL') ===
      'https://127.0.0.1:18443'
  }
  return {
    safeForPreMigration: Object.values(checks).every(Boolean),
    checks,
    blockers: Object.entries(checks).filter(([, value]) => !value).map(([key]) => key)
  }
}

export function verifyOwnerFreeze(path = ownerEnvPath) {
  const stat = lstatSync(path)
  if (!stat.isFile() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0) {
    throw Error('OWNER_ENV_NOT_PRIVATE_REGULAR_FILE')
  }
  return inspectOwnerFreeze(readFileSync(path, 'utf8'))
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  if (process.argv.length !== 3 || process.argv[2] !== '--verify-owner-disabled') {
    process.stderr.write('Usage: node owner-maintenance-freeze.mjs --verify-owner-disabled\n')
    process.exitCode = 2
  } else {
    try {
      const report = verifyOwnerFreeze()
      process.stdout.write(JSON.stringify(report, null, 2) + '\n')
      if (!report.safeForPreMigration) process.exitCode = 2
    } catch {
      process.stderr.write('OWNER_MAINTENANCE_FREEZE_NOT_VERIFIED\n')
      process.exitCode = 2
    }
  }
}
