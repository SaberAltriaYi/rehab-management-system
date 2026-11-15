// Verify the synthetic-only rehearsal entrypoint is safe to invoke directly.
// Invalid invocations exit before Docker, SQL or any business data access.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { statSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const runner = fileURLToPath(new URL('./internal-pilot-selected-plan-isolated.sh', import.meta.url))

test('disposable pilot plan rehearsal is executable as documented', () => {
  assert.notEqual(statSync(runner).mode & 0o111, 0,
    'runbook command must be executable without a manual chmod')
})

test('invalid argument stops before any Docker or production connection', () => {
  for (const args of [[], ['--live'], ['--synthetic-only', '--live']]) {
    try {
      execFileSync(runner, args, { timeout: 4000, encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'] })
      assert.fail('unsafe invocation accepted')
    } catch (error) {
      if (error?.code === 'ERR_ASSERTION') throw error
      assert.equal(error.status, 2)
      assert.match(error.stderr || '', /--synthetic-only/)
    }
  }
})
