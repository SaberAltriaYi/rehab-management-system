import test from 'node:test'
import assert from 'node:assert/strict'
import { hepLoadError } from '../src/views/rehab/hep-console/hepErrors.ts'

test('HEP missing 037 endpoint explains 404 without implying business success', () => {
  const result = hepLoadError(new Error('请求未完成（HTTP 404）'))
  assert.match(result, /HTTP 404/)
  assert.match(result, /Core 037/)
  assert.match(result, /菜单可见不代表服务已就绪/)
})

test('HEP unavailable bridge explains 503 and keeps the original code', () => {
  assert.match(hepLoadError(new Error('Request failed HTTP 503')), /HTTP 503/)
})

test('HEP authorization errors are not falsely diagnosed as missing migration', () => {
  const result = hepLoadError(new Error('HTTP 403 forbidden'))
  assert.equal(result, 'HTTP 403 forbidden')
})

test('HEP unexpected thrown values produce a bounded user-facing fallback', () => {
  assert.equal(hepLoadError(null), 'HEP 请求失败')
})
