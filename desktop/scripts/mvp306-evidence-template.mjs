/**
 * Print a non-sensitive, explicitly NOT_RUN evidence template. It never marks
 * cases as accepted and never reads the Core/HEP/Owner clinical stores.
 */
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { validateScope } from './mvp306-release-gate.mjs'

export function buildMvp306Template(scope, revision) {
  const plan = validateScope(scope)
  if (typeof revision !== 'string' || !/^[a-f0-9]{40}$/.test(revision)) {
    throw new Error('Full git revision required')
  }
  return {
    schema: 'rehab-mvp306-evidence/1',
    sourceRevision: revision,
    scopeSha256: createHash('sha256').update(JSON.stringify(scope)).digest('hex'),
    notice: 'Never promote a case to PASS without authorized E2E and independently reviewed evidence.',
    cases: plan.all.map(id => plan.required.has(id)
      ? { id, status: 'NOT_RUN' }
      : { id, status: 'DEFERRED' })
  }
}

function main() {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')
  const scope = JSON.parse(readFileSync(join(root, 'docs/qa/mvp306-release-scope.json'), 'utf8'))
  const revision = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim()
  process.stdout.write(`${JSON.stringify(buildMvp306Template(scope, revision), null, 2)}\n`)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main()
