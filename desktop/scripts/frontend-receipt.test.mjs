// Copyright (c) 2026 杨玺龙

import assert from 'node:assert/strict'
import { existsSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import {
  FRONTEND_RECEIPT,
  createFrontendReceipt,
  invalidateFrontendReceipt,
  sanitizedInternalBuildEnv,
  stageInternalEnv,
  verifyFrontendReceipt
} from './frontend-receipt.mjs'

const COMMIT = 'a'.repeat(40)
const FRONTEND_DIR = 'yudao-ui/yudao-ui-admin-vue3-app'

function fixture(t) {
  const projectRoot = mkdtempSync(join(tmpdir(), 'rehab-frontend-receipt-'))
  t.after(() => rmSync(projectRoot, { recursive: true, force: true }))
  const frontendRoot = join(projectRoot, FRONTEND_DIR)
  const entries = [
    '.eslintignore', '.eslintrc.js', '.env', '.env.internal', 'build/vite/index.ts',
    'index.html', 'package.json', 'pnpm-lock.yaml',
    'postcss.config.js', 'public/favicon.ico', 'src/App.vue', 'tsconfig.json',
    'types/env.d.ts', 'uno.config.ts', 'vite.config.ts', 'dist-internal/index.html',
    'dist-internal/assets/main.js'
  ]
  for (const entry of entries) {
    const path = join(frontendRoot, entry)
    mkdirSync(join(path, '..'), { recursive: true })
    writeFileSync(path, `fresh:${entry}`)
  }
  return { projectRoot, frontendRoot }
}

test('标准内部构建凭据绑定 commit、源码和全部前端产物', (t) => {
  const { projectRoot, frontendRoot } = fixture(t)
  assert.throws(() => verifyFrontendReceipt(projectRoot, COMMIT), /未找到内部前端构建凭据/)
  const receipt = createFrontendReceipt(projectRoot, COMMIT)
  assert.equal(receipt.commitSha, COMMIT)
  assert.equal(receipt.outputFileCount, 2)
  assert.deepEqual(verifyFrontendReceipt(projectRoot, COMMIT), receipt)
  assert.equal(JSON.parse(readFileSync(join(projectRoot, FRONTEND_RECEIPT))).sourceSha256, receipt.sourceSha256)
  writeFileSync(join(frontendRoot, 'dist-internal/assets/main.js'), 'tampered JS')
  assert.throws(() => verifyFrontendReceipt(projectRoot, COMMIT), /outputSha256 已改变/)
})

test('目标 commit 或源码变化禁止复用旧构建；未参与构建的本地环境文件不影响凭据', (t) => {
  const { projectRoot, frontendRoot } = fixture(t)
  createFrontendReceipt(projectRoot, COMMIT)
  assert.throws(() => verifyFrontendReceipt(projectRoot, 'b'.repeat(40)), /目标 commit 不匹配/)
  writeFileSync(join(frontendRoot, '.env.local'), 'VITE_REHAB_AI_ENABLED=true')
  assert.equal(verifyFrontendReceipt(projectRoot, COMMIT).commitSha, COMMIT)
  writeFileSync(join(frontendRoot, 'src/.DS_Store'), 'Finder changed a view setting')
  writeFileSync(join(frontendRoot, 'src/._App.vue'), 'AppleDouble metadata')
  assert.equal(verifyFrontendReceipt(projectRoot, COMMIT).commitSha, COMMIT)
  writeFileSync(join(frontendRoot, 'src/App.vue'), 'changed component')
  assert.throws(() => verifyFrontendReceipt(projectRoot, COMMIT), /sourceSha256 已改变/)
})

test('重新开始构建先作废旧凭据；失败构建不能沿用先前输出', (t) => {
  const { projectRoot } = fixture(t)
  createFrontendReceipt(projectRoot, COMMIT)
  invalidateFrontendReceipt(projectRoot)
  assert.throws(() => verifyFrontendReceipt(projectRoot, COMMIT), /未找到内部前端构建凭据/)
  assert.throws(() => createFrontendReceipt(projectRoot, 'local-development'), /40 位 Git commit SHA/)
})

test('ESLint 构建配置改变必须使前端凭据失效', (t) => {
  const { projectRoot, frontendRoot } = fixture(t)
  createFrontendReceipt(projectRoot, COMMIT)
  writeFileSync(join(frontendRoot, '.eslintrc.js'), 'changed lint rules')
  assert.throws(() => verifyFrontendReceipt(projectRoot, COMMIT), /sourceSha256 已改变/)
})

test('内部环境目录仅复制已审的 .env 和 .env.internal，不继承本机 VITE 覆盖', (t) => {
  const { projectRoot, frontendRoot } = fixture(t)
  writeFileSync(join(frontendRoot, '.env.local'), 'VITE_REHAB_AI_ENABLED=true')
  const envDir = stageInternalEnv(projectRoot)
  t.after(() => rmSync(envDir, { recursive: true, force: true }))
  assert.equal(existsSync(join(envDir, '.env.local')), false)
  assert.equal(existsSync(join(envDir, '.env.internal.local')), false)
  assert.equal(readFileSync(join(envDir, '.env.internal'), 'utf8'), 'fresh:.env.internal')
  assert.equal(readFileSync(join(envDir, '.env'), 'utf8'), 'fresh:.env')
  const environment = sanitizedInternalBuildEnv({
    CI: 'true', VITE_REHAB_AI_ENABLED: 'true', NODE_ENV: 'development',
    REHAB_INTERNAL_ENV_DIR: '/untrusted'
  })
  assert.deepEqual(environment, { CI: 'true' })
})
