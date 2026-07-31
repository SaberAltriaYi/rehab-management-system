#!/usr/bin/env node
// Copyright (c) 2026 杨玺龙

import { resolve } from 'node:path'
import { validateRuntime } from './runtime-tools.mjs'

const root = resolve(process.argv[2] || 'desktop/runtime/1.0.0')
validateRuntime(root)
process.stdout.write(`运行资源完整性检查通过：${root}\n`)
