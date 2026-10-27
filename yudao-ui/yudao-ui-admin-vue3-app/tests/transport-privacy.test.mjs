// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
import test from 'node:test'
import assert from 'node:assert/strict'
import { inspect } from 'node:util'
import { readFileSync } from 'node:fs'
import http from 'node:http'
import axios, { AxiosError } from 'axios'
import { safeRequestError, refreshRequest } from '../src/config/axios/privacy.ts'
import { defaultIsRetryable } from '../src/views/rehab/motion/uploadQueue.ts'

test('transport errors retain retry metadata without credentials, patient body or original cause', () => {
  const secret = 'SYNTHETIC_CREDENTIAL_DO_NOT_RETAIN'
  const clinical = 'SYNTHETIC_CLINICAL_BODY_DO_NOT_RETAIN'
  const original = new AxiosError(
    `${secret} ${clinical}`,
    'ERR_BAD_RESPONSE',
    {
      headers: { Authorization: `Bearer ${secret}` },
      data: clinical,
      url: `/synthetic?refreshToken=${secret}`
    },
    { token: secret },
    { status: 503, data: clinical, headers: { token: secret } }
  )
  original.cause = new Error(secret)
  const safe = safeRequestError(original)
  assert.equal(axios.isAxiosError(safe), true)
  assert.equal(defaultIsRetryable(safe), true)
  assert.equal(safe.response.status, 503)
  for (const output of [
    JSON.stringify(safe),
    inspect(safe, { showHidden: true, depth: 10 }),
    safe.stack
  ]) {
    assert.equal(output.includes(secret), false)
    assert.equal(output.includes(clinical), false)
  }
  assert.equal('config' in safe, false)
  assert.equal('request' in safe, false)
  assert.equal('cause' in safe, false)
  assert.deepEqual(Object.keys(safe.response), ['status'])
})

for (const [status, retry] of [
  [400, false],
  [401, false],
  [403, false],
  [429, true],
  [500, true]
]) {
  test(`HTTP ${status} keeps existing upload retry classification`, () => {
    const safe = safeRequestError({
      isAxiosError: true,
      response: { status },
      message: 'SYNTHETIC_PRIVATE'
    })
    assert.equal(defaultIsRetryable(safe), retry)
  })
}

test('unknown error codes/messages and invalid status cannot become diagnostics', () => {
  const safe = safeRequestError({
    name: 'Error',
    code: 'SYNTHETIC_SECRET',
    message: 'SYNTHETIC_PATIENT',
    response: { status: 'SYNTHETIC_SECRET' }
  })
  assert.equal(safe.code, undefined)
  assert.equal(safe.response, undefined)
  assert.equal(defaultIsRetryable(safe), false)
  assert.equal(inspect(safe).includes('SYNTHETIC_'), false)
})

test('timeouts, cancellation and a second normalization stay safe', () => {
  for (const code of ['ECONNABORTED', 'ETIMEDOUT', 'ERR_CANCELED', 'ERR_NETWORK']) {
    const safe = safeRequestError(
      safeRequestError({ isAxiosError: true, code, message: 'SYNTHETIC_SECRET' })
    )
    assert.equal(safe.code, code)
    assert.equal(axios.isCancel(safe), code === 'ERR_CANCELED')
    assert.equal(inspect(safe).includes('SYNTHETIC_SECRET'), false)
  }
})

test('real Axios POST carries refresh parameter in form body and isolates tenant headers', async () => {
  const observations = []
  const server = http.createServer(async (req, res) => {
    let body = ''
    for await (const chunk of req) body += chunk
    observations.push({ url: req.url, headers: req.headers, body })
    res.setHeader('Content-Type', 'application/json')
    res.end(JSON.stringify({ code: 0, data: { accepted: true } }))
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const headerNames = ['tenant-id', 'visit-tenant-id', 'Authorization']
  const originalHeaders = headerNames.map((key) => [key, axios.defaults.headers.common[key]])
  try {
    const token = 'SYNTHETIC_REFRESH_+&?=安全'
    const base = `http://127.0.0.1:${server.address().port}/admin-api`
    axios.defaults.headers.common['tenant-id'] = '999'
    axios.defaults.headers.common['visit-tenant-id'] = '998'
    axios.defaults.headers.common.Authorization = 'Bearer SYNTHETIC_STALE_ACCESS'
    for (const tenant of [71, 72, undefined]) {
      const request = refreshRequest(base, token, tenant)
      const result = await axios.post(request.url, request.data, {
        ...request.config,
        proxy: false
      })
      assert.equal(result.data.data.accepted, true)
    }
    assert.equal(axios.defaults.headers.common['tenant-id'], '999')
    assert.equal(axios.defaults.headers.common['visit-tenant-id'], '998')
    assert.equal(axios.defaults.headers.common.Authorization, 'Bearer SYNTHETIC_STALE_ACCESS')
    assert.deepEqual(
      observations.map((row) => row.headers['tenant-id']),
      ['71', '72', undefined]
    )
    for (const row of observations) {
      assert.equal(row.url, '/admin-api/system/auth/refresh-token')
      assert.equal(row.headers['content-type'], 'application/x-www-form-urlencoded')
      assert.equal(new URLSearchParams(row.body).get('refreshToken'), token)
      assert.equal(row.headers.authorization, undefined)
      assert.equal(row.headers['visit-tenant-id'], undefined)
    }
  } finally {
    for (const [key, value] of originalHeaders) {
      if (value === undefined) delete axios.defaults.headers.common[key]
      else axios.defaults.headers.common[key] = value
    }
    await new Promise((resolve) => server.close(resolve))
  }
})

test('real Axios failure can be safely inspected without server or request bodies', async () => {
  const server = http.createServer((req, res) => {
    res.statusCode = 503
    res.end('SYNTHETIC_PRIVATE_RESPONSE')
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  try {
    await assert.rejects(
      async () => {
        try {
          await axios.post(
            `http://127.0.0.1:${server.address().port}`,
            'SYNTHETIC_PRIVATE_REQUEST',
            {
              headers: { Authorization: 'Bearer SYNTHETIC_PRIVATE_TOKEN' },
              proxy: false
            }
          )
        } catch (error) {
          throw safeRequestError(error)
        }
      },
      (error) =>
        error.response.status === 503 &&
        !inspect(error, { depth: 10, showHidden: true }).includes('SYNTHETIC_PRIVATE')
    )
  } finally {
    await new Promise((resolve) => server.close(resolve))
  }
})

test('interceptors use redaction, no raw console dumps, and refresh controller accepts form parameters', () => {
  const service = readFileSync(new URL('../src/config/axios/service.ts', import.meta.url), 'utf8')
  assert.doesNotMatch(service, /console\.(log|error)\s*\(/)
  assert.doesNotMatch(service, /refresh-token\?refreshToken=|axios\.defaults\.headers/)
  assert.match(service, /Promise\.reject\(safeRequestError\(error\)\)/)
  assert.match(service, /Promise\.reject\(safeError\)/)
  assert.match(service, /Promise\.reject\(new Error\('可选模块未启用'\)\)/)
  const backend = readFileSync(
    new URL(
      '../../../yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/controller/admin/auth/AuthController.java',
      import.meta.url
    ),
    'utf8'
  )
  assert.match(backend, /refreshToken\(@RequestParam\("refreshToken"\) String refreshToken\)/)
})

test('underlying encryption helpers cannot print or rethrow original private failures', () => {
  const encryption = readFileSync(new URL('../src/utils/encrypt.ts', import.meta.url), 'utf8')
  assert.doesNotMatch(encryption, /console\.(log|warn|error)\s*\(|throw error|cause:/)
  assert.equal((encryption.match(/catch \{/g) || []).length, 7)
  assert.match(encryption, /mode: CryptoJS\.mode\.ECB/)
  assert.match(encryption, /padding: CryptoJS\.pad\.Pkcs7/)
})
