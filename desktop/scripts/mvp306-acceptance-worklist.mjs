// Read-only planning aid for the formal 306-case release gate. NEVER grants
// PASS or substitutes module tests for 182 independent acceptance assertions.
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { validateScope } from './mvp306-release-gate.mjs'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
export function compileAcceptanceWorklist(scope, { sourceRevision, dirty, approval = null } = {}) {
  const groups = validateScope(scope)
  const scopeSha256 = createHash('sha256').update(JSON.stringify(scope)).digest('hex')
  const matching = approval?.schema === 'rehab-mvp306-evidence/1'
    && approval.sourceRevision === sourceRevision && approval.scopeSha256 === scopeSha256
    && Array.isArray(approval.cases) && approval.cases.length === 306
    && new Set(approval.cases.map(item => item?.id)).size === 306
    && groups.all.every(id => approval.cases.some(item => item?.id === id))
  const indexed = matching ? new Map(approval.cases.map(item => [item.id, item])) : new Map()
  const rows = groups.all.map(id => {
    const n = Number(id.slice(2))
    const required = groups.required.has(id)
    const review = scope.manualClinicalReviewCases.includes(id)
    const submitted = indexed.get(id)
    return {
      id,
      lane: required ? groups.business.has(n) ? 'business' : 'safety' : 'deferred',
      required,
      clinicalHumanReview: review,
      // This remains UNVERIFIED even if the applicant claims PASS.
      evidenceStatus: !required ? 'DEFERRED'
        : submitted?.status === 'PASS' ? 'SUBMITTED_UNVERIFIED'
          : submitted?.status === 'BLOCKED' ? 'BLOCKED'
            : 'AWAITING_INDEPENDENT_EVIDENCE'
    }
  })
  const submitted = rows.filter(row => row.evidenceStatus === 'SUBMITTED_UNVERIFIED').length
  return {
    schema: 'rehab-mvp306-worklist/1', advisoryOnly: true,
    sourceRevision: sourceRevision ?? null, sourceDirty: dirty !== false,
    scopeSha256, approvalMatchesSourceAndScope: Boolean(matching),
    counts: { total: 306, businessRequired: 97, safetyRequired: 85, mandatory: 182,
      deferred: 124, clinicalHumanReviewRequired: 5,
      submittedNotYetVerified: submitted, awaitingEvidence: 182 - submitted },
    releaseDecision: 'NO_GO_UNTIL_FORMAL_MVP_GATE',
    cases: rows
  }
}

function main() {
  const scope = JSON.parse(readFileSync(join(root, 'docs/qa/mvp306-release-scope.json'), 'utf8'))
  const sourceRevision = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim()
  const dirty = Boolean(execFileSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' }).trim())
  const evidenceArg = process.argv.find(arg => arg.startsWith('--evidence='))
  const file = evidenceArg?.slice('--evidence='.length)
  const approval = file && existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : null
  const worklist = compileAcceptanceWorklist(scope, { sourceRevision, dirty, approval })
  if (!process.argv.includes('--markdown')) {
    process.stdout.write(JSON.stringify(worklist, null, 2) + '\n')
    return
  }
  const lines = [
    '# 306 项 MVP 验收工作清单（不等于审核通过）', '',
    'HEAD: ' + sourceRevision + '; source dirty: ' + dirty,
    '正式发布：需要独立审查 182 条逐项证据、5 条临床签发，不能因本表生成而放行。', '',
    '| ID | 类别 | 证据状态 | 必须人工临床审核 |',
    '|---|---|---|---|'
  ]
  for (const row of worklist.cases) {
    lines.push('| ' + row.id + ' | ' + row.lane + ' | ' + row.evidenceStatus + ' | ' +
      (row.clinicalHumanReview ? '是' : '否') + ' |')
  }
  process.stdout.write(lines.join('\n') + '\n')
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main()
