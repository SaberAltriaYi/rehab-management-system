// Copyright (c) 2026 杨玺龙

import { createHash, randomUUID } from 'node:crypto'
import { verifyFrontendReceipt } from './frontend-receipt.mjs'
import {
  cpSync,
  existsSync,
  lstatSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  renameSync,
  rmSync,
  writeFileSync
} from 'node:fs'
import { basename, join, relative, resolve, sep } from 'node:path'

export const VERSION = '1.0.0'
export const REQUIRED_FILES = [
  'docker-compose.yml',
  'server/Dockerfile',
  'server/yudao-server.jar',
  'admin/Dockerfile',
  'admin/nginx.conf.template',
  'admin/web/index.html',
  'sql/desktop-bootstrap.sql',
  'VERSION.json',
  'LICENSE'
]

const forbiddenNames = new Set([
  '.git',
  '.env',
  'server.key',
  'backup.passphrase',
  'id_rsa',
  'id_ed25519',
  'node_modules',
  'target',
  '.idea',
  '.vscode'
])

export function sha256(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex')
}

export function listFiles(root) {
  const result = []
  function visit(current) {
    for (const name of readdirSync(current).sort()) {
      const absolute = join(current, name)
      const stat = lstatSync(absolute)
      if (stat.isSymbolicLink()) throw new Error(`运行资源不允许符号链接：${absolute}`)
      if (stat.isDirectory()) visit(absolute)
      else if (stat.isFile()) result.push(absolute)
      else throw new Error(`运行资源不允许特殊文件：${absolute}`)
    }
  }
  visit(root)
  return result
}

export function validateRuntime(root) {
  for (const required of REQUIRED_FILES) {
    if (!existsSync(join(root, required))) throw new Error(`运行资源缺少：${required}`)
  }
  const files = listFiles(root)
  for (const absolute of files) {
    const normalized = relative(root, absolute).split(sep)
    if (normalized.some((part) => forbiddenNames.has(part))) {
      throw new Error(`运行资源包含禁止文件：${relative(root, absolute)}`)
    }
  }

  const compose = readFileSync(join(root, 'docker-compose.yml'), 'utf8')
  for (const pinned of [
    'mysql:8.4.10@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6',
    'redis:7.4.10-alpine3.21@sha256:e7723ff73d963f5cc6d9c4643ea3d989527a402a319239054e9472a7fb9219a2',
    'rehab-desktop-server:1.0.0',
    'rehab-desktop-admin:1.0.0',
    '${BIND_ADDRESS:-127.0.0.1}'
  ]) {
    if (!compose.includes(pinned)) throw new Error(`Compose 缺少固定配置：${pinned}`)
  }
  if (compose.includes(':latest')) throw new Error('Compose 不允许 latest 镜像')
  if (compose.includes('0.0.0.0')) throw new Error('Compose 不允许默认绑定 0.0.0.0')
  if (compose.includes('down -v') || compose.includes('--volumes')) {
    throw new Error('运行资源不允许普通流程删除数据卷')
  }
  const serverDockerfile = readFileSync(join(root, 'server/Dockerfile'), 'utf8')
  const adminDockerfile = readFileSync(join(root, 'admin/Dockerfile'), 'utf8')
  if (!serverDockerfile.includes('eclipse-temurin:8u492-b09-jre-jammy@sha256:2dd448')) {
    throw new Error('后端运行镜像未固定到明确的 Temurin 8 更新版本')
  }
  if (!adminDockerfile.includes('nginx:1.30.4-alpine3.24@sha256:97d490')) {
    throw new Error('管理端运行镜像未固定到明确的 Nginx/Alpine 版本')
  }

  verifyRuntimeManifest(root, files)
  return true
}

export function verifyRuntimeManifest(root, files = listFiles(root)) {
  const manifest = readFileSync(join(root, 'runtime-manifest.sha256'), 'utf8')
  if (!manifest.endsWith('\n')) throw new Error('校验清单缺少结尾换行')
  const expectedPaths = new Set(
    files
      .map((path) => relative(root, path).split(sep).join('/'))
      .filter((path) => path !== 'runtime-manifest.sha256')
  )
  const observed = new Set()
  for (const line of manifest.trimEnd().split(/\r?\n/)) {
    const match = line.match(/^([a-f0-9]{64}) {2}(.+)$/)
    if (!match) throw new Error(`校验清单格式错误：${line}`)
    const [, expected, path] = match
    if (
      path.startsWith('/') ||
      path.includes('\\') ||
      path.includes('\0') ||
      path.split('/').some((part) => !part || part === '.' || part === '..') ||
      !expectedPaths.has(path)
    ) {
      throw new Error(`校验清单包含非法或未归档路径：${path}`)
    }
    if (observed.has(path)) throw new Error(`校验清单重复列出文件：${path}`)
    observed.add(path)
    if (sha256(join(root, ...path.split('/'))) !== expected) throw new Error(`校验失败：${path}`)
  }
  for (const path of expectedPaths) {
    if (!observed.has(path)) throw new Error(`校验清单遗漏运行资源文件：${path}`)
  }
  return true
}

export function buildRuntime({ projectRoot, outputRoot, commitSha }) {
  // A failed internal build must never be packaged from a pre-existing dist-internal.
  const frontendReceipt = verifyFrontendReceipt(projectRoot, commitSha)
  for (const required of [
    'desktop/runtime-template/docker-compose.yml',
    'desktop/runtime-template/server/Dockerfile',
    'desktop/runtime-template/admin/Dockerfile',
    'desktop/runtime-template/admin/nginx.conf.template',
    'desktop/runtime-template/RUNTIME-README.txt',
    'yudao-server/target/yudao-server.jar',
    'desktop/build/desktop-bootstrap.sql',
    'LICENSE'
  ]) {
    const input = resolve(projectRoot, required)
    if (!existsSync(input) || !lstatSync(input).isFile() || lstatSync(input).size === 0) {
      throw new Error(`运行资源构建输入缺失或为空：${required}`)
    }
  }
  const serverJarSha256 = sha256(resolve(projectRoot, 'yudao-server/target/yudao-server.jar'))

  const root = resolve(outputRoot, VERSION)
  const staging = resolve(outputRoot, `.${VERSION}.${randomUUID()}.tmp`)
  const previous = resolve(outputRoot, `.${VERSION}.${randomUUID()}.previous`)
  mkdirSync(staging, { recursive: true })
  try {
    cpSync(resolve(projectRoot, 'desktop/runtime-template/docker-compose.yml'), join(staging, 'docker-compose.yml'))
    cpSync(resolve(projectRoot, 'desktop/runtime-template/server'), join(staging, 'server'), {
      recursive: true
    })
    cpSync(resolve(projectRoot, 'desktop/runtime-template/admin'), join(staging, 'admin'), {
      recursive: true
    })
    cpSync(
      resolve(projectRoot, 'desktop/runtime-template/RUNTIME-README.txt'),
      join(staging, 'RUNTIME-README.txt')
    )
    cpSync(resolve(projectRoot, 'yudao-server/target/yudao-server.jar'), join(staging, 'server/yudao-server.jar'))
    cpSync(
      resolve(projectRoot, 'yudao-ui/yudao-ui-admin-vue3-app/dist-internal'),
      join(staging, 'admin/web'),
      { recursive: true }
    )
    mkdirSync(join(staging, 'sql'), { recursive: true })
    cpSync(
      resolve(projectRoot, 'desktop/build/desktop-bootstrap.sql'),
      join(staging, 'sql/desktop-bootstrap.sql')
    )
    cpSync(resolve(projectRoot, 'LICENSE'), join(staging, 'LICENSE'))
    if (existsSync(resolve(projectRoot, 'NOTICE.md'))) {
      cpSync(resolve(projectRoot, 'NOTICE.md'), join(staging, 'NOTICE.md'))
    }
    if (existsSync(resolve(projectRoot, 'THIRD_PARTY_NOTICES.md'))) {
      cpSync(resolve(projectRoot, 'THIRD_PARTY_NOTICES.md'), join(staging, 'THIRD_PARTY_NOTICES.md'))
    }

    writeFileSync(
      join(staging, 'VERSION.json'),
      `${JSON.stringify(
        {
          productName: '运动康复评估与业务管理系统',
          shortName: '康复管理系统',
          version: VERSION,
          bundleIdentifier: 'com.saberaltriayi.rehab',
          commitSha,
          frontendSourceSha256: frontendReceipt.sourceSha256,
          frontendOutputSha256: frontendReceipt.outputSha256,
          serverJarSha256,
          migrationLedger: '001-019',
          dataFormat: 1
        },
        null,
        2
      )}\n`
    )
    writeFileSync(
      join(staging, 'BUILD-INFO.txt'),
      `version=${VERSION}\ncommit=${commitSha}\nfrontend_source_sha256=${frontendReceipt.sourceSha256}\nfrontend_output_sha256=${frontendReceipt.outputSha256}\nserver_jar_sha256=${serverJarSha256}\nruntime=tauri-v2-docker-compose\n`
    )

    const files = listFiles(staging).filter((path) => basename(path) !== 'runtime-manifest.sha256')
    const manifest = files
      .map((path) => `${sha256(path)}  ${relative(staging, path).split(sep).join('/')}`)
      .join('\n')
    writeFileSync(join(staging, 'runtime-manifest.sha256'), `${manifest}\n`)
    validateRuntime(staging)

    // Replace the version directory only after a full integrity check; preserve the previous
    // directory if staging fails. A schema/database rollback is a separate controlled operation.
    if (existsSync(root)) renameSync(root, previous)
    try {
      renameSync(staging, root)
    } catch (error) {
      if (existsSync(previous)) renameSync(previous, root)
      throw error
    }
    if (existsSync(previous)) rmSync(previous, { recursive: true, force: true })
    return root
  } finally {
    rmSync(staging, { recursive: true, force: true })
  }
}
