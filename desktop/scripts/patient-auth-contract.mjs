// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

/** Match the existing Java transport contract, not merely any failed request. */
export function assertPatientAuthDenied(action, httpStatus, body) {
  const expected = action === 'login' ? [403, 403] : action === 'bind' ? [200, 401] : null
  if (!expected || !body || typeof body !== 'object' || Array.isArray(body)
      || Number(httpStatus) !== expected[0] || body.code !== expected[1]
      || (body.data !== undefined && body.data !== null)
      || ['token', 'accessToken', 'refreshToken', 'authorization'].some(key => key in body)) {
    // Never include body, invitation, tokens or patient fields in diagnostics.
    throw new Error('患者匿名认证拒绝契约不符；检查认证过滤器、接口前缀及业务码，不记录响应原文。')
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [, , action, status, path] = process.argv
    const raw = readFileSync(path, 'utf8')
    if (raw.length > 64 * 1024) throw new Error('response too large')
    assertPatientAuthDenied(action, status, JSON.parse(raw))
  } catch {
    process.stderr.write('患者匿名认证拒绝契约检查失败；响应内容已省略。\n')
    process.exitCode = 1
  }
}
