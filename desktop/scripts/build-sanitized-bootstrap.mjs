#!/usr/bin/env node
// Copyright (c) 2026 杨玺龙

import { randomBytes } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { basename, dirname, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(scriptDir, '../..')
const output =
  process.argv[2] === '--output' && process.argv[3]
    ? resolve(process.argv[3])
    : resolve(projectRoot, 'desktop/build/desktop-bootstrap.sql')
const docker = process.env.DOCKER_CLI_PATH || 'docker'
const image = 'mysql:8.4.10'
const container = `rehab-desktop-bootstrap-${process.pid}`
const rootPassword = randomBytes(32).toString('base64url')
const database = 'ruoyi-vue-pro'

const initializationScripts = [
  'sql/mysql/ruoyi-vue-pro.sql',
  'sql/mysql/quartz.sql',
  'sql/mysql/rehab-init-v1.sql',
  'sql/mysql/rehab-step2-v1.sql',
  'sql/mysql/rehab-step3-v1.sql',
  'sql/mysql/rehab-step4-v1.sql',
  'sql/mysql/rehab-step5-v1.sql',
  'sql/mysql/rehab-step6-v1.sql',
  'sql/mysql/rehab-step7-v1.sql',
  'sql/mysql/rehab-step8-assessment-type-v2.sql',
  'sql/mysql/rehab-crm-member-bootstrap-v1.sql',
  'sql/mysql/rehab-crm-member-bridge-v1.sql',
  'sql/mysql/rehab-step9-tenant-v1.sql',
  'sql/mysql/rehab-disable-optional-module-menus-v1.sql',
  'deploy/internal/disable-ai.sql',
  'deploy/internal/internal-hardening.sql',
  'deploy/internal/clean-demo-rehab-data.sql',
  'sql/mysql/rehab-step10-integrity-v1.sql',
  'sql/mysql/rehab-step11-auth-hardening-v1.sql',
  'sql/mysql/rehab-step12-internal-login-client-v1.sql',
  'sql/mysql/rehab-step13-disable-undelivered-menus-v1.sql',
  'deploy/internal/init-schema-history.sql',
  'desktop/sql/sanitize-bootstrap.sql'
]

function run(args, options = {}) {
  const result = spawnSync(docker, args, {
    cwd: projectRoot,
    encoding: options.encoding ?? 'utf8',
    input: options.input,
    maxBuffer: 1024 * 1024 * 512,
    stdio: options.stdio
  })
  if (result.error) throw result.error
  if (result.status !== 0) {
    const safeArgs = args
      .slice(0, 4)
      .map((value) => (String(value).startsWith('MYSQL_PWD=') ? 'MYSQL_PWD=[已脱敏]' : value))
    const detail = String(result.stderr || result.stdout || '')
      .replaceAll(rootPassword, '[已脱敏]')
      .trim()
      .slice(0, 2000)
    throw new Error(`Docker 操作失败：docker ${safeArgs.join(' ')}\n${detail}`)
  }
  return result
}

function removeContainer() {
  spawnSync(docker, ['rm', '--force', container], {
    cwd: projectRoot,
    stdio: 'ignore'
  })
}

function waitForMySql() {
  for (let attempt = 1; attempt <= 60; attempt += 1) {
    const result = spawnSync(
      docker,
      [
        'exec',
        '--env',
        `MYSQL_PWD=${rootPassword}`,
        container,
        'mysqladmin',
        '--user=root',
        '--host=127.0.0.1',
        'ping',
        '--silent'
      ],
      { cwd: projectRoot, stdio: 'ignore' }
    )
    if (result.status === 0) return
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1000)
  }
  throw new Error('临时 MySQL 在 60 秒内未就绪')
}

function applySql(relativePath) {
  const path = resolve(projectRoot, relativePath)
  if (!existsSync(path)) throw new Error(`初始化脚本不存在：${relativePath}`)
  process.stdout.write(`应用 ${relativePath}\n`)
  run(
    [
      'exec',
      '--interactive',
      '--env',
      `MYSQL_PWD=${rootPassword}`,
      container,
      'mysql',
      '--user=root',
      '--database',
      database,
      '--default-character-set=utf8mb4',
      '--binary-mode'
    ],
    { input: readFileSync(path) }
  )
}

function assertSanitized(sql) {
  const forbidden = [
    /3TvrJ70gl2Gt6IBe7_IZT1F6i_k0iMuRtyEv4EyS/,
    /wd0tbVBYlp0S-ihA8Qg2hPLncoP83wyrIq24OZuY/,
    /AKIDAF6WSh1uiIjwqtrOsGSN3WryqTM6cTMt/,
    /LTAI5tEQLgnDyjh3WpNcdMKA/,
    /PVDONDEIOTW88LF8DC4U/,
    /18818260272/,
    /17321315478/,
    /15601691300/,
    /13900000001/,
    /13800000001/,
    /演示客户-康复01/,
    /演示会员01/,
    /13aoteman@126\.com/
  ]
  for (const pattern of forbidden) {
    if (pattern.test(sql)) throw new Error(`脱敏快照仍包含禁止内容：${pattern}`)
  }
  if (!sql.includes('internal_schema_history')) {
    throw new Error('脱敏快照缺少固定迁移账本 internal_schema_history')
  }
  if (!sql.includes('!desktop-runtime-sets-password!')) {
    throw new Error('脱敏快照缺少首次启动密码占位符')
  }
}

function verifyFreshImport(dump) {
  process.stdout.write('验证脱敏快照可在空数据库中恢复\n')
  run([
    'exec',
    '--env',
    `MYSQL_PWD=${rootPassword}`,
    container,
    'mysql',
    '--user=root',
    '--execute',
    `DROP DATABASE \`${database}\`; CREATE DATABASE \`${database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
  ])
  run(
    [
      'exec',
      '--interactive',
      '--env',
      `MYSQL_PWD=${rootPassword}`,
      container,
      'mysql',
      '--user=root',
      '--database',
      database,
      '--default-character-set=utf8mb4',
      '--binary-mode'
    ],
    { input: dump }
  )
  const result = run([
    'exec',
    '--env',
    `MYSQL_PWD=${rootPassword}`,
    container,
    'mysql',
    '--user=root',
    '--database',
    database,
    '--batch',
    '--skip-column-names',
    '--execute',
    "SELECT CONCAT((SELECT COUNT(*) FROM system_users),'|',(SELECT COUNT(*) FROM rehab_patient),'|',(SELECT COUNT(*) FROM internal_schema_history),'|',(SELECT COUNT(*) FROM system_users WHERE username='admin' AND password='!desktop-runtime-sets-password!'));"
  ]).stdout.trim()
  if (result !== '1|0|19|1') {
    throw new Error(`脱敏快照恢复后验收失败，预期 1|0|19|1，实际 ${result}`)
  }
}

try {
  for (const relativePath of initializationScripts) {
    if (!existsSync(resolve(projectRoot, relativePath))) {
      throw new Error(`构建输入缺失：${relativePath}`)
    }
  }
  removeContainer()
  run([
    'run',
    '--detach',
    '--rm',
    '--name',
    container,
    '--env',
    `MYSQL_ROOT_PASSWORD=${rootPassword}`,
    '--env',
    `MYSQL_DATABASE=${database}`,
    image,
    '--character-set-server=utf8mb4',
    '--collation-server=utf8mb4_unicode_ci',
    '--default-time-zone=+08:00'
  ])
  waitForMySql()
  for (const relativePath of initializationScripts) applySql(relativePath)

  const dump = run([
    'exec',
    '--env',
    `MYSQL_PWD=${rootPassword}`,
    container,
    'mysqldump',
    '--user=root',
    '--default-character-set=utf8mb4',
    '--single-transaction',
    '--routines',
    '--triggers',
    '--events',
    '--hex-blob',
    '--skip-comments',
    '--set-gtid-purged=OFF',
    '--column-statistics=0',
    database
  ]).stdout
  assertSanitized(dump)
  verifyFreshImport(dump)
  mkdirSync(dirname(output), { recursive: true })
  rmSync(output, { force: true })
  writeFileSync(
    output,
    `-- 康复管理系统 V1.0 桌面端脱敏初始化快照\n-- 构建输入：固定迁移账本 001-019；不包含患者、演示账号或云端密钥\n${dump}`,
    { mode: 0o644 }
  )
  process.stdout.write(`已生成脱敏数据库快照：${basename(output)}\n`)
} finally {
  removeContainer()
}
