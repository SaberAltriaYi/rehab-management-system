// Copyright (c) 2026 杨玺龙
// This regression test never uses the real Docker CLI or touches business volumes.
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'

const script = join(dirname(fileURLToPath(import.meta.url)), 'test-runtime-e2e.sh')

test('E2E volume collision fails without Compose down or any volume removal', () => {
  const root = mkdtempSync(join(tmpdir(), 'rehab-e2e-volume-test-'))
  const log = join(root, 'docker.log')
  try {
    writeFileSync(
      join(root, 'docker'),
      `#!/usr/bin/env bash
printf '%s\n' "$*" >> "$MOCK_DOCKER_LOG"
if [[ "$1" == volume && "$2" == inspect ]]; then exit 0; fi
exit 99
`,
      { mode: 0o755 }
    )
    const result = spawnSync('bash', [script], {
      encoding: 'utf8',
      timeout: 15000,
      env: { ...process.env, PATH: `${root}:${process.env.PATH}`, MOCK_DOCKER_LOG: log }
    })
    assert.equal(result.status, 1, result.stderr || result.error?.message)
    assert.match(result.stderr, /refusing to run because volume already exists/)
    const commands = readFileSync(log, 'utf8')
    assert.match(commands, /volume inspect rehab-desktop-e2e-[0-9a-f]+-mysql-data/)
    assert.doesNotMatch(commands, /compose|volume rm/)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})
