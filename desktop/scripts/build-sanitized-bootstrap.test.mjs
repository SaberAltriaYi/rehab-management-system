import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync, chmodSync, existsSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve, dirname } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const root = resolve(scriptDir, '../..')

test('Docker daemon unavailable fails closed before run, rm, SQL import or output creation', () => {
  const fixture = mkdtempSync(join(tmpdir(), 'rehab-bootstrap-probe-'))
  try {
    const docker = join(fixture, 'mock-docker')
    const log = join(fixture, 'docker-commands.log')
    const output = join(fixture, 'bootstrap.sql')
    writeFileSync(docker, '#!/bin/sh\nprintf "%s\\n" "$1" >> "$PROBE_LOG"\nexit 42\n')
    chmodSync(docker, 0o700)
    const result = spawnSync(process.execPath, [
      join(scriptDir, 'build-sanitized-bootstrap.mjs'), '--output', output
    ], {
      cwd: root,
      encoding: 'utf8',
      timeout: 10_000,
      env: { ...process.env, DOCKER_CLI_PATH: docker, PROBE_LOG: log }
    })
    assert.equal(result.status, 1)
    assert.match(result.stderr, /DOCKER_DAEMON_UNAVAILABLE/)
    assert.deepEqual(readFileSync(log, 'utf8').trim().split('\n'), ['version'])
    assert.equal(existsSync(output), false)
  } finally {
    rmSync(fixture, { recursive: true, force: true })
  }
})

test('baseline-only requires an explicit output, and never starts Docker without it', () => {
  const fixture = mkdtempSync(join(tmpdir(), 'rehab-baseline-cli-'))
  try {
    const docker = join(fixture, 'mock-docker')
    const log = join(fixture, 'docker-commands.log')
    writeFileSync(docker, '#!/bin/sh\nprintf "%s\\n" "$1" >> "$PROBE_LOG"\nexit 42\n')
    chmodSync(docker, 0o700)
    const env = { ...process.env, DOCKER_CLI_PATH: docker, PROBE_LOG: log }
    const script = join(scriptDir, 'build-sanitized-bootstrap.mjs')
    const unsafe = spawnSync(process.execPath, [script, '--baseline-only'], {
      cwd: root, encoding: 'utf8', timeout: 10_000, env
    })
    assert.equal(unsafe.status, 1)
    assert.match(unsafe.stderr, /--output/)
    assert.equal(existsSync(log), false, 'invalid option must fail before contacting Docker')

    const output = join(fixture, 'baseline-019.sql')
    const isolated = spawnSync(process.execPath, [
      script, '--output', output, '--baseline-only'
    ], { cwd: root, encoding: 'utf8', timeout: 10_000, env })
    assert.equal(isolated.status, 1)
    assert.match(isolated.stderr, /DOCKER_DAEMON_UNAVAILABLE/)
    assert.deepEqual(readFileSync(log, 'utf8').trim().split('\n'), ['version'])
    assert.equal(existsSync(output), false)
  } finally {
    rmSync(fixture, { recursive: true, force: true })
  }
})
