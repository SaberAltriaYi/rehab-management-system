// 运行：node --experimental-strip-types --test tests/motion/sides.test.mjs
// 侧别规则：前端下拉、后端保存校验与引擎 tests_registry.sides 必须一致，否则异步任务在规则计算阶段才报 INVALID_SIDE。
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { BILATERAL_TESTS, TEST_OPTIONS, normalizeSide, sideOptionsFor } from '../../src/views/rehab/motion/constants.ts'

const here = dirname(fileURLToPath(import.meta.url))
const javaConstants = readFileSync(
  join(here, '../../../../yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/enums/RehabMotionConstants.java'),
  'utf8'
)

test('bilateral test set matches backend RehabMotionConstants.BILATERAL_TESTS', () => {
  const block = javaConstants.slice(javaConstants.indexOf('BILATERAL_TESTS ='))
  const java = [...block.slice(0, block.indexOf(';')).matchAll(/"([A-Z_]+)"/g)].map((m) => m[1])
  assert.deepEqual([...BILATERAL_TESTS].sort(), java.sort())
  for (const code of BILATERAL_TESTS) assert.ok(TEST_OPTIONS.some((t) => t.value === code), code)
})

test('side options per test', () => {
  assert.deepEqual(sideOptionsFor('LESS').map((o) => o.value), ['bilateral'])
  assert.deepEqual(sideOptionsFor('YBT_LQ').map((o) => o.value), ['left', 'right'])
  assert.deepEqual(sideOptionsFor('NASM_SLS').map((o) => o.value), ['left', 'right'])
  assert.equal(sideOptionsFor(undefined).length, 3)
})

test('normalizeSide never guesses a side for unilateral tests', () => {
  assert.equal(normalizeSide('LESS', 'left'), 'bilateral')
  assert.equal(normalizeSide('FMS_DEEP_SQUAT', ''), 'bilateral')
  assert.equal(normalizeSide('YBT_LQ', 'bilateral'), '')
  assert.equal(normalizeSide('NASM_SLS', undefined), '')
  assert.equal(normalizeSide('NASM_SLS', 'right'), 'right')
})
