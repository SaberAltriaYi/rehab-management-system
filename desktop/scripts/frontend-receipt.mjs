// Copyright (c) 2026 杨玺龙
// A successful internal frontend build writes a receipt outside the public web root.

import { execFileSync } from 'node:child_process'
import { createHash, randomUUID } from 'node:crypto'
import {
  copyFileSync,
  existsSync,
  lstatSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  renameSync,
  rmSync,
  writeFileSync
} from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'

export const FRONTEND_RECEIPT = 'desktop/build/frontend-build-receipt.json'
const FRONTEND_DIR = 'yudao-ui/yudao-ui-admin-vue3-app'
const SOURCE_PATHS = [
  '.eslintignore',
  '.eslintrc.js',
  '.env',
  '.env.internal',
  'build',
  'index.html',
  'package.json',
  'pnpm-lock.yaml',
  'postcss.config.js',
  'public',
  'src',
  'tsconfig.json',
  'types',
  'uno.config.ts',
  'vite.config.ts'
]
const COMMIT_RE = /^[0-9a-f]{40}$/
// Finder/Explorer metadata is not build source and can change after Vite exits.
const sourceMetadata = new Set(['.DS_Store', 'Thumbs.db', 'desktop.ini'])

function requireCommit(commitSha) {
  if (typeof commitSha !== 'string' || !COMMIT_RE.test(commitSha)) {
    throw new Error('运行资源要求明确的 40 位 Git commit SHA，不能使用 local-development 或旧预览版本')
  }
  return commitSha
}

export function resolveBuildCommit(projectRoot, env = process.env) {
  const commitSha = (env.GITHUB_SHA || env.REHAB_BUILD_COMMIT || execFileSync('git', ['rev-parse', 'HEAD'], {
    cwd: projectRoot,
    encoding: 'utf8'
  })).trim()
  return requireCommit(commitSha)
}

function regularFiles(base, requiredPaths, { excludeSourceMetadata = false } = {}) {
  const files = []
  function visit(path) {
    const stat = lstatSync(path)
    if (stat.isSymbolicLink()) throw new Error(`构建输入/产物不允许符号链接：${path}`)
    if (stat.isDirectory()) {
      for (const name of readdirSync(path).sort()) {
        if (excludeSourceMetadata && (sourceMetadata.has(name) || name.startsWith('._'))) continue
        visit(join(path, name))
      }
    } else if (stat.isFile()) {
      files.push(path)
    } else {
      throw new Error(`构建输入/产物不允许特殊文件：${path}`)
    }
  }
  for (const path of requiredPaths) {
    const absolute = resolve(base, path)
    if (!existsSync(absolute)) throw new Error(`缺少前端构建输入/产物：${path}`)
    visit(absolute)
  }
  return files.sort()
}

function digestPaths(base, paths, options) {
  const files = regularFiles(base, paths, options)
  if (!files.length) throw new Error('前端构建输入/产物为空')
  const digest = createHash('sha256')
  for (const path of files) {
    const name = relative(base, path).split(sep).join('/')
    const fileHash = createHash('sha256').update(readFileSync(path)).digest('hex')
    digest.update(`${name}\0${fileHash}\n`)
  }
  return { sha256: digest.digest('hex'), fileCount: files.length }
}

function snapshot(projectRoot) {
  const frontendRoot = resolve(projectRoot, FRONTEND_DIR)
  const outputRoot = resolve(frontendRoot, 'dist-internal')
  // The standard build stages only .env and .env.internal into a separate envDir.
  // Local override files are intentionally excluded both from this digest and the release build.
  const source = digestPaths(frontendRoot, SOURCE_PATHS, { excludeSourceMetadata: true })
  // Fail if Vite did not generate the entry page, then hash all assets as one artifact set.
  regularFiles(outputRoot, ['index.html'])
  const assets = digestPaths(outputRoot, ['.'])
  return {
    sourceSha256: source.sha256,
    sourceFileCount: source.fileCount,
    outputSha256: assets.sha256,
    outputFileCount: assets.fileCount
  }
}

export function stageInternalEnv(projectRoot) {
  const frontendRoot = resolve(projectRoot, FRONTEND_DIR)
  const temporary = resolve(projectRoot, 'desktop/build', `frontend-env-${randomUUID()}`)
  mkdirSync(temporary, { recursive: true })
  try {
    for (const file of ['.env', '.env.internal']) {
      const source = join(frontendRoot, file)
      if (!lstatSync(source).isFile()) throw new Error(`内部环境文件必须为普通文件：${file}`)
      copyFileSync(source, join(temporary, file))
    }
    return temporary
  } catch (error) {
    rmSync(temporary, { recursive: true, force: true })
    throw error
  }
}

export function sanitizedInternalBuildEnv(env = process.env) {
  const cleaned = { ...env }
  for (const key of Object.keys(cleaned)) {
    if (key.startsWith('VITE_') || key === 'NODE_ENV' || key === 'REHAB_INTERNAL_ENV_DIR') {
      delete cleaned[key]
    }
  }
  return cleaned
}

export function invalidateFrontendReceipt(projectRoot) {
  rmSync(resolve(projectRoot, FRONTEND_RECEIPT), { force: true })
}

export function createFrontendReceipt(projectRoot, commitSha) {
  requireCommit(commitSha)
  const receipt = {
    schemaVersion: 1,
    buildMode: 'isolated-internal-env',
    commitSha,
    ...snapshot(projectRoot),
    builtAt: new Date().toISOString()
  }
  const destination = resolve(projectRoot, FRONTEND_RECEIPT)
  mkdirSync(dirname(destination), { recursive: true })
  const temporary = `${destination}.${process.pid}.${randomUUID()}.tmp`
  try {
    writeFileSync(temporary, `${JSON.stringify(receipt, null, 2)}\n`, { mode: 0o600 })
    renameSync(temporary, destination)
  } finally {
    rmSync(temporary, { force: true })
  }
  return receipt
}

export function verifyFrontendReceipt(projectRoot, commitSha) {
  requireCommit(commitSha)
  const destination = resolve(projectRoot, FRONTEND_RECEIPT)
  if (!existsSync(destination)) {
    throw new Error('未找到内部前端构建凭据：请先成功执行原样 pnpm build:internal；禁止复用旧 dist-internal')
  }
  const receipt = JSON.parse(readFileSync(destination, 'utf8'))
  if (receipt.schemaVersion !== 1 || receipt.buildMode !== 'isolated-internal-env' || receipt.commitSha !== commitSha) {
    throw new Error('前端构建凭据与目标 commit 不匹配，请重新构建')
  }
  const current = snapshot(projectRoot)
  for (const key of ['sourceSha256', 'sourceFileCount', 'outputSha256', 'outputFileCount']) {
    if (receipt[key] !== current[key]) {
      throw new Error(`前端构建后 ${key} 已改变：请重新执行 pnpm build:internal`)
    }
  }
  return receipt
}
