// Copyright (c) 2026 杨玺龙

import assert from 'node:assert/strict'
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { randomUUID } from 'node:crypto'
import { listFiles, migrationLedgerRange, sha256 } from './runtime-tools.mjs'

test('sha256 结果稳定', () => {
  const root = join(tmpdir(), `rehab-runtime-test-${randomUUID()}`)
  mkdirSync(root)
  const file = join(root, 'sample.txt')
  writeFileSync(file, 'rehab')
  assert.equal(sha256(file), sha256(file))
  rmSync(root, { recursive: true })
})

test('运行资源遍历拒绝符号链接或返回普通文件', () => {
  const root = join(tmpdir(), `rehab-runtime-test-${randomUUID()}`)
  mkdirSync(join(root, 'nested'), { recursive: true })
  writeFileSync(join(root, 'nested/file.txt'), 'safe')
  assert.deepEqual(listFiles(root), [join(root, 'nested/file.txt')])
  rmSync(root, { recursive: true })
})

test('Windows 卸载钩子默认保留用户数据', () => {
  const hook = readFileSync(
    new URL('../launcher/src-tauri/windows/installer-hooks.nsh', import.meta.url),
    'utf8'
  )
  assert.match(hook, /NSIS_HOOK_PREUNINSTALL/)
  assert.match(hook, /DeleteAppDataCheckboxState 0/)
  assert.doesNotMatch(hook, /RmDir/)
})

test('desktop VERSION.json 迁移范围取自发布清单（含实际执行的增量迁移）', () => {
  const root = join(tmpdir(), `rehab-ledger-${randomUUID()}`)
  try {
    mkdirSync(join(root, 'deploy/internal'), { recursive: true })
    writeFileSync(
      join(root, 'deploy/internal/migrations.manifest'),
      '# header\n001|a|sql/mysql/a.sql|a\n019|b|sql/mysql/b.sql|b\n024|c|sql/mysql/c.sql|c\n'
    )
    assert.equal(migrationLedgerRange(root), '001-024')
    const real = migrationLedgerRange(new URL('../..', import.meta.url).pathname)
    assert.match(real, /^001-\d{3}$/)
    assert.ok(Number(real.slice(4)) >= 19)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})
