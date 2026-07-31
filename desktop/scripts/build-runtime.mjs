#!/usr/bin/env node
// Copyright (c) 2026 杨玺龙

import { resolve } from 'node:path'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildRuntime } from './runtime-tools.mjs'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(scriptDir, '../..')
const outputIndex = process.argv.indexOf('--output')
const outputRoot =
  outputIndex >= 0 && process.argv[outputIndex + 1]
    ? resolve(process.argv[outputIndex + 1])
    : resolve(projectRoot, 'desktop/runtime')
const commitSha = process.env.GITHUB_SHA || process.env.REHAB_BUILD_COMMIT || 'local-development'

const root = buildRuntime({ projectRoot, outputRoot, commitSha })
process.stdout.write(`运行资源已生成并验证：${root}\n`)
