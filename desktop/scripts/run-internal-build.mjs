#!/usr/bin/env node
// Copyright (c) 2026 杨玺龙
// Standard `pnpm build:internal`: run Vite with reviewed env files, then stamp its output.

import { spawnSync } from 'node:child_process'
import { rmSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  createFrontendReceipt,
  invalidateFrontendReceipt,
  resolveBuildCommit,
  sanitizedInternalBuildEnv,
  stageInternalEnv
} from './frontend-receipt.mjs'

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const frontendRoot = resolve(projectRoot, 'yudao-ui/yudao-ui-admin-vue3-app')
invalidateFrontendReceipt(projectRoot)
const envDir = stageInternalEnv(projectRoot)
try {
  const environment = { ...sanitizedInternalBuildEnv(), REHAB_INTERNAL_ENV_DIR: envDir }
  const result = spawnSync(
    process.execPath,
    [
      '--max_old_space_size=4096',
      resolve(frontendRoot, 'node_modules/vite/bin/vite.js'),
      'build',
      '--mode',
      'internal'
    ],
    { cwd: frontendRoot, env: environment, stdio: 'inherit' }
  )
  if (result.error) throw result.error
  if (result.status !== 0) {
    process.exitCode = result.status || 1
  } else {
    const receipt = createFrontendReceipt(projectRoot, resolveBuildCommit(projectRoot, environment))
    process.stdout.write(
      `内部前端构建凭据：${receipt.commitSha}，${receipt.outputFileCount} 个文件；可生成桌面运行资源\n`
    )
  }
} finally {
  rmSync(envDir, { recursive: true, force: true })
}
