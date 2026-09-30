// Copyright (c) 2026 杨玺龙

import assert from 'node:assert/strict'
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { randomUUID } from 'node:crypto'
import { createFrontendReceipt } from './frontend-receipt.mjs'
import {
  buildRuntime,
  incrementalMigrations,
  listFiles,
  migrationLedgerRange,
  sha256,
  validateRuntime,
  verifyRuntimeManifest,
  verifyRuntimeMigrations
} from './runtime-tools.mjs'

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

test('校验清单覆盖每个文件，拒绝遗漏、重复、越界和被修改的资源', (t) => {
  const root = join(tmpdir(), `rehab-manifest-test-${randomUUID()}`)
  t.after(() => rmSync(root, { recursive: true, force: true }))
  mkdirSync(root)
  const first = join(root, 'index.html')
  writeFileSync(first, '<html>safe</html>')
  const entry = `${sha256(first)}  index.html\n`
  const manifest = join(root, 'runtime-manifest.sha256')
  writeFileSync(manifest, entry)
  assert.equal(verifyRuntimeManifest(root), true)

  writeFileSync(join(root, 'extra.txt'), 'not listed')
  assert.throws(() => verifyRuntimeManifest(root), /遗漏运行资源文件：extra.txt/)
  writeFileSync(manifest, `${entry}${sha256(join(root, 'extra.txt'))}  extra.txt\n`)
  assert.equal(verifyRuntimeManifest(root), true)
  writeFileSync(manifest, entry + entry)
  assert.throws(() => verifyRuntimeManifest(root), /重复列出文件：index.html/)
  writeFileSync(manifest, `${sha256(first)}  ../secrets.txt\n`)
  assert.throws(() => verifyRuntimeManifest(root), /非法或未归档路径/)
  writeFileSync(manifest, `${'0'.repeat(64)}  index.html\n${sha256(join(root, 'extra.txt'))}  extra.txt\n`)
  assert.throws(() => verifyRuntimeManifest(root), /校验失败：index.html/)
})

test('前端构建未成功时，运行资源构建拒绝覆盖已有版本', (t) => {
  const projectRoot = join(tmpdir(), `rehab-runtime-preflight-${randomUUID()}`)
  t.after(() => rmSync(projectRoot, { recursive: true, force: true }))
  const outputRoot = join(projectRoot, 'desktop/runtime')
  const previous = join(outputRoot, '1.0.0/keep.txt')
  mkdirSync(join(outputRoot, '1.0.0'), { recursive: true })
  writeFileSync(previous, 'previous-good-version')
  assert.throws(
    () => buildRuntime({ projectRoot, outputRoot, commitSha: 'a'.repeat(40) }),
    /未找到内部前端构建凭据/
  )
  assert.equal(readFileSync(previous, 'utf8'), 'previous-good-version')
})

test('新运行资源先在临时目录完整校验，再原子替换旧版本', (t) => {
  const projectRoot = join(tmpdir(), `rehab-runtime-atomic-${randomUUID()}`)
  t.after(() => rmSync(projectRoot, { recursive: true, force: true }))
  function file(path, content) {
    const absolute = join(projectRoot, path)
    mkdirSync(join(absolute, '..'), { recursive: true })
    writeFileSync(absolute, content)
  }
  const frontend = 'yudao-ui/yudao-ui-admin-vue3-app/'
  for (const path of [
    '.eslintignore', '.eslintrc.js', '.env', '.env.internal', 'build/vite/index.ts',
    'index.html', 'package.json',
    'pnpm-lock.yaml', 'postcss.config.js', 'public/favicon.ico', 'src/App.vue',
    'tsconfig.json', 'types/env.d.ts', 'uno.config.ts', 'vite.config.ts'
  ]) file(frontend + path, `source:${path}`)
  file(frontend + 'dist-internal/index.html', '<html>fresh build</html>')
  file('desktop/runtime-template/docker-compose.yml', [
    'mysql:8.4.10@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6',
    'redis:7.4.10-alpine3.21@sha256:e7723ff73d963f5cc6d9c4643ea3d989527a402a319239054e9472a7fb9219a2',
    'rehab-desktop-server:1.0.0', 'rehab-desktop-admin:1.0.0', '${BIND_ADDRESS:-127.0.0.1}'
  ].join('\n'))
  file('desktop/runtime-template/server/Dockerfile', 'FROM eclipse-temurin:8u492-b09-jre-jammy@sha256:2dd448')
  file('desktop/runtime-template/admin/Dockerfile', 'FROM nginx:1.30.4-alpine3.24@sha256:97d490')
  file('desktop/runtime-template/admin/nginx.conf.template', 'server { listen 443; }')
  file('desktop/runtime-template/RUNTIME-README.txt', 'desktop runtime')
  file('desktop/build/desktop-bootstrap.sql', 'CREATE TABLE sample(id INT);')
  file('sql/mysql/motion.sql', 'CREATE TABLE motion_sample(id INT);')
  const motionSha = sha256(join(projectRoot, 'sql/mysql/motion.sql'))
  file('deploy/internal/migrations.manifest', [
    '# header',
    ...Array.from({ length: 19 }, (_, index) => `${String(index + 1).padStart(3, '0')}|${'a'.repeat(64)}|sql/mysql/base.sql|基线`),
    `025|${motionSha}|sql/mysql/motion.sql|动作评估`,
    ''
  ].join('\n'))
  file('yudao-server/target/yudao-server.jar', 'fake jar for unit test only')
  file('LICENSE', 'Apache-2.0')
  const outputRoot = join(projectRoot, 'desktop/runtime')
  file('desktop/runtime/1.0.0/keep.txt', 'previous version')
  const commitSha = 'a'.repeat(40)
  const receipt = createFrontendReceipt(projectRoot, commitSha)
  const root = buildRuntime({ projectRoot, outputRoot, commitSha })
  assert.equal(validateRuntime(root), true)
  assert.match(readFileSync(join(root, 'admin/web/index.html'), 'utf8'), /fresh build/)
  const version = JSON.parse(readFileSync(join(root, 'VERSION.json'), 'utf8'))
  assert.equal(version.commitSha, commitSha)
  assert.equal(version.migrationLedger, '001-025')
  assert.equal(readFileSync(join(root, 'sql/migrations/025.sql'), 'utf8'), 'CREATE TABLE motion_sample(id INT);')
  assert.match(readFileSync(join(root, 'sql/migrations.manifest'), 'utf8'), /^025\|/m)
  assert.equal(version.frontendSourceSha256, receipt.sourceSha256)
  assert.equal(version.frontendOutputSha256, receipt.outputSha256)
  assert.equal(version.serverJarSha256, sha256(join(projectRoot, 'yudao-server/target/yudao-server.jar')))
  assert.throws(() => readFileSync(join(root, 'keep.txt')), /ENOENT/)
  file(frontend + 'dist-internal/index.html', '<html>modified after successful build</html>')
  assert.throws(() => buildRuntime({ projectRoot, outputRoot, commitSha }), /outputSha256 已改变/)
  assert.match(readFileSync(join(root, 'admin/web/index.html'), 'utf8'), /fresh build/)
})

test('运行资源增量迁移必须与清单一一对应，且拒绝被篡改或漂移的脚本', (t) => {
  const projectRoot = join(tmpdir(), `rehab-migrations-${randomUUID()}`)
  t.after(() => rmSync(projectRoot, { recursive: true, force: true }))
  mkdirSync(join(projectRoot, 'sql/mysql'), { recursive: true })
  mkdirSync(join(projectRoot, 'deploy/internal'), { recursive: true })
  writeFileSync(join(projectRoot, 'sql/mysql/motion.sql'), 'CREATE TABLE m(id INT);')
  const good = sha256(join(projectRoot, 'sql/mysql/motion.sql'))
  const manifest = (checksum, path = 'sql/mysql/motion.sql') =>
    `001|${'a'.repeat(64)}|sql/mysql/base.sql|基线\n024|${checksum}|${path}|动作评估\n`
  writeFileSync(join(projectRoot, 'deploy/internal/migrations.manifest'), manifest(good))
  assert.deepEqual(incrementalMigrations(projectRoot).map((row) => row.version), ['024'])
  writeFileSync(join(projectRoot, 'deploy/internal/migrations.manifest'), manifest('b'.repeat(64)))
  assert.throws(() => incrementalMigrations(projectRoot), /校验和漂移/)
  writeFileSync(join(projectRoot, 'deploy/internal/migrations.manifest'), manifest(good, 'deploy/internal/x.sql'))
  assert.throws(() => incrementalMigrations(projectRoot), /只允许 sql\/mysql/)

  const runtime = join(projectRoot, 'runtime')
  mkdirSync(join(runtime, 'sql/migrations'), { recursive: true })
  writeFileSync(join(runtime, 'sql/migrations.manifest'), manifest(good))
  assert.throws(() => verifyRuntimeMigrations(runtime), /与清单不一致/)
  writeFileSync(join(runtime, 'sql/migrations/024.sql'), 'CREATE TABLE m(id INT);')
  assert.equal(verifyRuntimeMigrations(runtime), true)
  writeFileSync(join(runtime, 'sql/migrations/024.sql'), 'DROP TABLE rehab_patient;')
  assert.throws(() => verifyRuntimeMigrations(runtime), /校验失败：024/)
  writeFileSync(join(runtime, 'sql/migrations/024.sql'), 'CREATE TABLE m(id INT);')
  writeFileSync(join(runtime, 'sql/migrations/099.sql'), 'extra')
  assert.throws(() => verifyRuntimeMigrations(runtime), /与清单不一致/)
})
