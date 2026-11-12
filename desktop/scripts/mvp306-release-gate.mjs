/**
 * Read-only MVP case gate: 306 original IDs, 182 release-required assertions.
 * Fail-closed; never treats prior unit tests, Mode-A demos, or a package build
 * as clinical/product acceptance. No credentials or patient data are read.
 */
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync, realpathSync, statSync } from 'node:fs'
import { isAbsolute, join, relative, resolve, sep, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const rootDefault = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')
export const idFor = i => `T-${String(i).padStart(3, '0')}`
const digest = bytes => createHash('sha256').update(bytes).digest('hex')

export function expandRanges(spec) {
  if (typeof spec !== 'string' || !spec.trim()) throw Error('Scope ranges missing')
  const result = new Set()
  for (const part of spec.split(',')) {
    if (!/^\d{1,3}(?:-\d{1,3})?$/.test(part)) throw Error('Malformed range')
    const [start, end = start] = part.split('-').map(Number)
    if (start < 1 || start > 306 || end < start || end > 306) throw Error('Range out of bounds')
    for (let i = start; i <= end; i++) {
      if (result.has(i)) throw Error('Duplicate scope id')
      result.add(i)
    }
  }
  return result
}

export function validateScope(scope) {
  if (scope?.schema !== 'rehab-mvp306-scope/2026-11-12' || scope.totalCases !== 306) {
    throw Error('Unknown 306 scope schema')
  }
  const business = expandRanges(scope.businessRanges)
  const safety = expandRanges(scope.safetyRanges)
  if (business.size !== 97 || safety.size !== 85 ||
      scope.businessRequired !== 97 || scope.safetyRequired !== 85 ||
      [...business].some(id => safety.has(id))) {
    throw Error('MVP release scope was not reviewed or has overlapping cases')
  }
  const required = new Set([...business, ...safety].map(idFor))
  const all = Array.from({ length: 306 }, (_, i) => idFor(i + 1))
  const deferred = all.filter(id => !required.has(id))
  const mandatoryClinical = ['T-107', 'T-113', 'T-114', 'T-129', 'T-189']
  if (deferred.length !== 124 || !Array.isArray(scope.manualClinicalReviewCases) ||
      mandatoryClinical.some(id => !scope.manualClinicalReviewCases.includes(id)) ||
      scope.manualClinicalReviewCases.length !== mandatoryClinical.length ||
      scope.manualClinicalReviewCases.some(id => !required.has(id))) {
    throw Error('MVP scope is inconsistent')
  }
  return { business, safety, required, all, deferred }
}

function verifyLog(base, evidence, caseId) {
  const file = evidence?.file
  if (typeof file !== 'string' || !file || isAbsolute(file) ||
      file.split(/[\\/]+/).includes('..')) return false
  const path = resolve(base, file)
  if (!existsSync(path)) return false
  let realBase, realFile
  try { realBase = realpathSync(base); realFile = realpathSync(path) } catch { return false }
  const offset = relative(realBase, realFile)
  if (offset === '..' || offset.startsWith(`..${sep}`) || isAbsolute(offset)) return false
  const stats = statSync(realFile)
  if (!stats.isFile() || stats.size > 2_000_000 || stats.size < 8) return false
  if (!/^[a-f0-9]{64}$/.test(evidence.sha256 ?? '')) return false
  const bytes = readFileSync(realFile)
  // Every independent assertion must have an explicit identifier in the log.
  return digest(bytes) === evidence.sha256 && bytes.toString('utf8').includes(caseId)
}

/**
 * sourceRevision must be the exact git commit under release review. This
 * record cannot assert a PASS without a real, hash-bound case-specific log.
 */
export function evaluateMvp306({ root, sourceRevision, dirty, scope, approval }) {
  const result = { ready: false, totalCases: 306, required: 182, pass: 0,
    notAccepted: 182, deferred: 124, blockerCount: 0, blockers: [] }
  const block = message => { result.blockerCount++; if (result.blockers.length < 24) result.blockers.push(message) }
  let plan
  try { plan = validateScope(scope) } catch (error) {
    block(`SCOPE_INVALID: ${error.message}`); return result
  }
  if (dirty) block('SOURCE_DIRTY: commit required before release')
  if (!sourceRevision || !/^[a-f0-9]{40}$/.test(sourceRevision)) block('REVISION_UNKNOWN')
  if (approval?.schema !== 'rehab-mvp306-evidence/1' ||
      approval.sourceRevision !== sourceRevision ||
      approval.scopeSha256 !== digest(Buffer.from(JSON.stringify(scope)))) {
    block('APPROVAL_MISSING_OR_MISMATCH: requires exact revision and scope digest')
    return result
  }
  if (!Array.isArray(approval.cases) || approval.cases.length !== 306) {
    block('ALL_306_CASES_REQUIRED: preserve deferred and required IDs')
    return result
  }
  const found = new Map()
  for (const item of approval.cases) {
    if (!item || typeof item.id !== 'string' || found.has(item.id) ||
        !/^T-\d{3}$/.test(item.id)) { block('MALFORMED_OR_DUPLICATE_CASE'); continue }
    found.set(item.id, item)
  }
  if (found.size !== 306 || plan.all.some(id => !found.has(id))) {
    block('CASE_ID_MISMATCH: exactly T-001 through T-306 required')
    return result
  }
  for (const id of plan.required) {
    const item = found.get(id)
    if (item.status !== 'PASS' ||
        !['authorized_sandbox', 'supervised_clinical_e2e', 'formal_local'].includes(item.tier) ||
        !/^[A-Za-z0-9_.-]{3,80}$/.test(item.reviewedBy ?? '') ||
        !/^\d{4}-\d{2}-\d{2}T/.test(item.reviewedAt ?? '') ||
        !verifyLog(root, item.evidence, id)) {
      block(`${id}_PENDING_OR_UNVERIFIED`)
      continue
    }
    if (scope.manualClinicalReviewCases.includes(id) &&
        (!/^[A-Za-z0-9_.-]{3,80}$/.test(item.clinicianReviewedBy ?? '') ||
         item.tier !== 'supervised_clinical_e2e')) {
      block(`${id}_CLINICAL_SIGNOFF_REQUIRED`)
      continue
    }
    result.pass++
  }
  result.notAccepted = result.required - result.pass
  result.ready = result.blockerCount === 0 && result.pass === result.required
  return result
}

function main() {
  const root = rootDefault
  const scope = JSON.parse(readFileSync(join(root, 'docs/qa/mvp306-release-scope.json')))
  const filename = process.argv[2] ?? join(root, 'docs/releases/mvp306-evidence.json')
  let sourceRevision = null, dirty = true, approval = null
  try {
    sourceRevision = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim()
    dirty = Boolean(execFileSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' }).trim())
  } catch { /* fail-closed */ }
  try { approval = JSON.parse(readFileSync(filename, 'utf8')) } catch { /* fail-closed */ }
  const result = evaluateMvp306({ root, sourceRevision, dirty, scope, approval })
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`)
  process.exitCode = result.ready ? 0 : 2
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main()
