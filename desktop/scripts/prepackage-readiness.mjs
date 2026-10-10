/**
 * Read-only fail-closed release evidence audit. It never compiles, signs,
 * publishes, deletes data or changes any repository state.
 */
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, readFileSync, realpathSync, statSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, isAbsolute, join, relative, resolve, sep } from 'node:path'

export const REQUIRED_GATES = [
  'source_integration', 'java_full_test', 'frontend_build', 'database_migration',
  'tenant_permission', 'biomechanics_contract', 'clinical_report_b',
  'patient_identity', 'e2e_synthetic', 'security_license',
  'desktop_restore_upgrade', 'code_signing'
]

/** No evidence file or unverified evidence can silently become a green gate. */
export function evaluateReadiness({ root, revision, dirty, evidence }) {
  const blockers = []
  if (dirty) blockers.push('SOURCE_DIRTY: 当前源码包含未提交修改')
  if (!evidence || evidence.schema !== 'prepackage-evidence/1.0.0') {
    blockers.push('EVIDENCE_MISSING: 未提供有效的冻结版本门禁清单')
  }
  if (!revision || !evidence || evidence.revision !== revision) {
    blockers.push('REVISION_MISMATCH: 门禁证据未绑定当前源码提交')
  }
  for (const gate of REQUIRED_GATES) {
    const item = evidence?.gates?.[gate]
    if (!item || item.status !== 'passed' || typeof item.log !== 'string' || !item.log.trim()
        || !/^[a-f0-9]{64}$/.test(item.sha256 || '')) {
      blockers.push(`GATE_PENDING: ${gate}`)
      continue
    }
    const file = resolve(root, item.log)
    if (!existsSync(file)) {
      blockers.push(`GATE_EVIDENCE_NOT_FOUND: ${gate}`)
      continue
    }
    const realFile = realpathSync(file)
    const outside = relative(realpathSync(root), realFile)
    if (isAbsolute(outside) || outside === '..' || outside.startsWith(`..${sep}`) || !statSync(realFile).isFile()) {
      blockers.push(`GATE_EVIDENCE_OUTSIDE_ROOT: ${gate}`)
      continue
    }
    // Keep log evidence bounded and prevent silently accepting substituted files.
    if (statSync(realFile).size > 20 * 1024 * 1024 ||
        createHash('sha256').update(readFileSync(realFile)).digest('hex') !== item.sha256) {
      blockers.push(`GATE_EVIDENCE_HASH_MISMATCH: ${gate}`)
    }
  }
  return { ready: blockers.length === 0, revision, blockerCount: blockers.length, blockers }
}

function main() {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')
  const evidenceFile = process.argv[2] || join(root, 'docs', 'releases', 'prepackage-evidence.example.json')
  let revision = null
  let dirty = true
  try {
    revision = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim()
    dirty = Boolean(execFileSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' }).trim())
  } catch {
    // Missing Git evidence must block, not bypass, release approval.
  }
  let evidence = null
  try { evidence = JSON.parse(readFileSync(evidenceFile, 'utf8')) } catch { /* fail closed */ }
  const result = evaluateReadiness({ root, revision, dirty, evidence })
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`)
  process.exitCode = result.ready ? 0 : 1
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main()
