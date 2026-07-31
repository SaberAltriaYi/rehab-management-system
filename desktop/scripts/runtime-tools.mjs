// Copyright (c) 2026 杨玺龙

import { createHash } from 'node:crypto'
import {
  cpSync,
  existsSync,
  lstatSync,
  mkdirSync,
  readFileSync,
  readdirSync,
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
  for (const absolute of listFiles(root)) {
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

  const manifest = readFileSync(join(root, 'runtime-manifest.sha256'), 'utf8')
  for (const line of manifest.trim().split(/\r?\n/)) {
    const match = line.match(/^([a-f0-9]{64}) {2}(.+)$/)
    if (!match) throw new Error(`校验清单格式错误：${line}`)
    const [, expected, path] = match
    if (sha256(join(root, path)) !== expected) throw new Error(`校验失败：${path}`)
  }
  return true
}

export function buildRuntime({ projectRoot, outputRoot, commitSha }) {
  const root = resolve(outputRoot, VERSION)
  rmSync(root, { recursive: true, force: true })
  mkdirSync(root, { recursive: true })

  cpSync(resolve(projectRoot, 'desktop/runtime-template/docker-compose.yml'), join(root, 'docker-compose.yml'))
  cpSync(resolve(projectRoot, 'desktop/runtime-template/server'), join(root, 'server'), {
    recursive: true
  })
  cpSync(resolve(projectRoot, 'desktop/runtime-template/admin'), join(root, 'admin'), {
    recursive: true
  })
  cpSync(
    resolve(projectRoot, 'desktop/runtime-template/RUNTIME-README.txt'),
    join(root, 'RUNTIME-README.txt')
  )
  cpSync(resolve(projectRoot, 'yudao-server/target/yudao-server.jar'), join(root, 'server/yudao-server.jar'))
  cpSync(
    resolve(projectRoot, 'yudao-ui/yudao-ui-admin-vue3-app/dist-internal'),
    join(root, 'admin/web'),
    { recursive: true }
  )
  mkdirSync(join(root, 'sql'), { recursive: true })
  cpSync(
    resolve(projectRoot, 'desktop/build/desktop-bootstrap.sql'),
    join(root, 'sql/desktop-bootstrap.sql')
  )
  cpSync(resolve(projectRoot, 'LICENSE'), join(root, 'LICENSE'))
  if (existsSync(resolve(projectRoot, 'NOTICE.md'))) {
    cpSync(resolve(projectRoot, 'NOTICE.md'), join(root, 'NOTICE.md'))
  }
  if (existsSync(resolve(projectRoot, 'THIRD_PARTY_NOTICES.md'))) {
    cpSync(resolve(projectRoot, 'THIRD_PARTY_NOTICES.md'), join(root, 'THIRD_PARTY_NOTICES.md'))
  }

  writeFileSync(
    join(root, 'VERSION.json'),
    `${JSON.stringify(
      {
        productName: '运动康复评估与业务管理系统',
        shortName: '康复管理系统',
        version: VERSION,
        bundleIdentifier: 'com.saberaltriayi.rehab',
        commitSha,
        migrationLedger: '001-019',
        dataFormat: 1
      },
      null,
      2
    )}\n`
  )
  writeFileSync(
    join(root, 'BUILD-INFO.txt'),
    `version=${VERSION}\ncommit=${commitSha}\nruntime=tauri-v2-docker-compose\n`
  )

  const files = listFiles(root).filter((path) => basename(path) !== 'runtime-manifest.sha256')
  const manifest = files
    .map((path) => `${sha256(path)}  ${relative(root, path).split(sep).join('/')}`)
    .join('\n')
  writeFileSync(join(root, 'runtime-manifest.sha256'), `${manifest}\n`)
  validateRuntime(root)
  return root
}
