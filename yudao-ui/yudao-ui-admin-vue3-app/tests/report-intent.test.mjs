// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Synthetic fixtures only.
import test from 'node:test'
import assert from 'node:assert/strict'
import { inspect } from 'node:util'
import { readFileSync } from 'node:fs'
import {
  createReportIntentCoordinator,
  createReportIntentScope,
  negotiateReportContract,
  secureReportRequestKey,
  reportAssessmentId
} from '../src/utils/reportIntent.ts'
import { isOptionalReportContract404 } from '../src/config/axios/privacy.ts'

const deferred = () => {
  let resolve, reject
  const promise = new Promise((a, b) => {
    resolve = a
    reject = b
  })
  return { promise, resolve, reject }
}
const yes = async () => true
function fixture(options = {}) {
  const scope = createReportIntentScope()
  let context = 'SYNTHETIC_USER_1_TENANT_1',
    sequence = 0
  const sent = []
  const client = createReportIntentCoordinator({
    scope,
    context: () => context,
    probe: async () => 'ready',
    key: () => `00000000-0000-4000-8000-${String(++sequence).padStart(12, '0')}`,
    send: async (body) => {
      sent.push(body)
      return { id: 42 }
    },
    ...options
  })
  return {
    client,
    scope,
    sent,
    setContext: (value) => {
      context = value
    }
  }
}
const code = (expected) => (error) =>
  error.code === expected && !inspect(error).includes('SYNTHETIC_SECRET')

test('missing platform random source fails closed without an insecure fallback', () => {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'crypto')
  try {
    Object.defineProperty(globalThis, 'crypto', { configurable: true, value: undefined })
    assert.throws(() => secureReportRequestKey(), code('RANDOM'))
  } finally {
    if (descriptor) Object.defineProperty(globalThis, 'crypto', descriptor)
    else delete globalThis.crypto
  }
})

test('one failing scope subscriber cannot stop other identity cleanup subscribers', () => {
  const scope = createReportIntentScope()
  let cleaned = 0
  scope.subscribe(() => {
    throw new Error('SYNTHETIC_SECRET')
  })
  const unsubscribe = scope.subscribe(() => {
    cleaned++
  })
  scope.reset()
  assert.equal(scope.version, 1)
  assert.equal(cleaned, 1)
  unsubscribe()
  scope.reset()
  assert.equal(cleaned, 1)
})

test('single flight starts before probe/confirmation and only success starts a new intent', async () => {
  const gate = deferred()
  let confirmations = 0,
    probes = 0
  const f = fixture({
    probe: async () => {
      probes++
      return 'ready'
    }
  })
  const confirm = async () => {
    confirmations++
    return gate.promise
  }
  const first = f.client.run({ assessmentId: 7 }, confirm)
  const double = f.client.run({ assessmentId: '7' }, confirm)
  assert.equal(first, double)
  await Promise.resolve()
  await Promise.resolve()
  assert.equal(probes, 1)
  assert.equal(confirmations, 1)
  assert.equal(f.sent.length, 0)
  gate.resolve(true)
  await first
  await f.client.run({ assessmentId: 7 }, yes)
  assert.equal(f.sent.length, 2)
  assert.notEqual(f.sent[0].requestKey, f.sent[1].requestKey)
})

test('unknown result reuses UUID and hides original errors; retry confirms original result', async () => {
  const requests = []
  let calls = 0
  const f = fixture({
    send: async (body) => {
      requests.push(body)
      if (!calls++) throw new Error('SYNTHETIC_SECRET')
      return { id: 42 }
    }
  })
  await assert.rejects(f.client.run({ assessmentId: 7 }, yes), code('UNCERTAIN'))
  assert.equal(f.client.hasUncertain(7), true)
  const result = await f.client.run({ assessmentId: '7' }, async (state) => {
    assert.deepEqual(state, { retry: true, legacy: false })
    return true
  })
  assert.equal(result.retried, true)
  assert.equal(result.value.id, 42)
  assert.deepEqual(requests[0], requests[1])
  assert.equal(f.client.hasUncertain(7), false)
})

test('changed parameters cannot replace either in-flight or uncertain intent', async () => {
  const gate = deferred()
  const f = fixture({ send: () => gate.promise })
  const work = f.client.run({ assessmentId: 7, note: 'SYNTHETIC_A' }, yes)
  await assert.rejects(f.client.run({ assessmentId: 7, note: 'SYNTHETIC_B' }, yes), code('CHANGED'))
  gate.reject(new Error('SYNTHETIC_SECRET'))
  await assert.rejects(work, code('UNCERTAIN'))
  await assert.rejects(f.client.run({ assessmentId: 7 }, yes), code('CHANGED'))
})

test('cancellation before send frees new slot but preserves uncertain intent', async () => {
  const f = fixture({
    limit: 1,
    send: async () => {
      throw new Error('SYNTHETIC_SECRET')
    }
  })
  assert.equal(await f.client.run({ assessmentId: 7 }, async () => false), undefined)
  await assert.rejects(f.client.run({ assessmentId: 8 }, yes), code('UNCERTAIN'))
  assert.equal(await f.client.run({ assessmentId: 8 }, async () => false), undefined)
  assert.equal(f.client.hasUncertain(8), true)
  await assert.rejects(f.client.run({ assessmentId: 7 }, yes), code('LIMIT'))
})

for (const status of [401, 403, 500, undefined]) {
  test(`probe ${status ?? 'network'} failure cannot downgrade to legacy or send`, async () => {
    const f = fixture({
      probe: () =>
        negotiateReportContract(async () => {
          throw { isAxiosError: true, response: { status } }
        })
    })
    await assert.rejects(f.client.run({ assessmentId: 7 }, yes), code('PREPARE'))
    assert.equal(f.sent.length, 0)
  })
}
test('only an actual HTTP 404 is legacy; malformed/not-ready/unknown contracts fail closed', async () => {
  assert.equal(
    await negotiateReportContract(async () => {
      throw { isAxiosError: true, response: { status: 404 } }
    }),
    'legacy'
  )
  await assert.rejects(
    negotiateReportContract(async () => {
      throw { response: { status: 404 } }
    }),
    code('PREPARE')
  )
  assert.equal(
    await negotiateReportContract(async () => ({ contract: 'report-idempotency-v1', ready: true })),
    'ready'
  )
  for (const reply of [
    null,
    {},
    { contract: 'report-idempotency-v1', ready: 'true' },
    { contract: 'report-idempotency-v1', ready: false },
    { contract: 'future', ready: true }
  ]) {
    await assert.rejects(
      negotiateReportContract(async () => reply),
      code('CONTRACT')
    )
  }
})
test('legacy requires confirmation and never sends a fake deduplication key', async () => {
  const f = fixture({ probe: async () => 'legacy' })
  await f.client.run({ assessmentId: 7 }, async (state) => {
    assert.deepEqual(state, { retry: false, legacy: true })
    return true
  })
  assert.equal('requestKey' in f.sent[0], false)
})
test('v1 unknown result cannot silently downgrade to old backend', async () => {
  let mode = 'ready'
  const calls = []
  const f = fixture({
    probe: async () => mode,
    send: async (body) => {
      calls.push(body)
      throw new Error('SYNTHETIC_SECRET')
    }
  })
  await assert.rejects(f.client.run({ assessmentId: 7 }, yes), code('UNCERTAIN'))
  mode = 'legacy'
  await assert.rejects(f.client.run({ assessmentId: 7 }, yes), code('CONTRACT'))
  assert.equal(calls.length, 1)
  assert.equal(f.client.hasUncertain(7), true)
})
test('unknown legacy request followed by v1 still warns about a possible new revision', async () => {
  let mode = 'legacy',
    calls = 0
  const f = fixture({
    probe: async () => mode,
    send: async () => {
      if (!calls++) throw new Error('SYNTHETIC_SECRET')
      return {}
    }
  })
  await assert.rejects(f.client.run({ assessmentId: 7 }, yes), code('UNCERTAIN'))
  mode = 'ready'
  await f.client.run({ assessmentId: 7 }, async (state) => {
    assert.deepEqual(state, { retry: true, legacy: true })
    return true
  })
})
test('pending limit does not evict, retries remain possible, explicit reset makes a new key', async () => {
  const requests = []
  const f = fixture({
    limit: 1,
    send: async (body) => {
      requests.push(body)
      throw new Error('SYNTHETIC_SECRET')
    }
  })
  await assert.rejects(f.client.run({ assessmentId: 7 }, yes), code('UNCERTAIN'))
  await assert.rejects(f.client.run({ assessmentId: 8 }, yes), code('LIMIT'))
  await assert.rejects(f.client.run({ assessmentId: 7 }, yes), code('UNCERTAIN'))
  assert.equal(requests[0].requestKey, requests[1].requestKey)
  f.client.forgetUncertain(7)
  await assert.rejects(f.client.run({ assessmentId: 7 }, yes), code('UNCERTAIN'))
  assert.notEqual(requests[1].requestKey, requests[2].requestKey)
})
test('explicit reset cannot discard an in-flight request', async () => {
  const gate = deferred(),
    f = fixture({ send: () => gate.promise })
  const work = f.client.run({ assessmentId: 7 }, yes)
  assert.throws(() => f.client.forgetUncertain(7), code('BUSY'))
  gate.resolve({})
  await work
})
test('IDs preserve Java Long strings, reject unsafe JS numbers and do not coerce garbage', async () => {
  assert.equal(reportAssessmentId('9223372036854775807'), '9223372036854775807')
  for (const id of [
    0,
    -1,
    1.5,
    NaN,
    Infinity,
    Number.MAX_SAFE_INTEGER + 1,
    '',
    '01',
    ' 7',
    '7e0',
    '9223372036854775808',
    undefined,
    {}
  ]) {
    assert.throws(() => reportAssessmentId(id), code('INVALID'))
  }
  const f = fixture()
  for (const input of [
    { assessmentId: 0 },
    { assessmentId: 7, note: {} },
    { assessmentId: 7, note: 'a'.repeat(1001) },
    { assessmentId: 7, generationMode: 'x'.repeat(33) }
  ]) {
    await assert.rejects(f.client.run(input, yes), code('INVALID'))
  }
  assert.equal(f.sent.length, 0)
})
test('only projected parameters go to transport; random failure never sends or leaks', async () => {
  assert.match(
    secureReportRequestKey(),
    /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/
  )
  for (const key of [
    () => 'bad',
    () => {
      throw new Error('SYNTHETIC_SECRET')
    }
  ]) {
    const f = fixture({ key })
    await assert.rejects(f.client.run({ assessmentId: 7 }, yes), code('RANDOM'))
    assert.equal(f.sent.length, 0)
  }
  const f = fixture()
  await f.client.run(
    { assessmentId: 7, token: 'SYNTHETIC_SECRET', requestKey: 'untrusted', note: 'SYNTHETIC_NOTE' },
    yes
  )
  assert.equal('token' in f.sent[0], false)
  assert.notEqual(f.sent[0].requestKey, 'untrusted')
})
for (const outcome of ['resolve', 'reject']) {
  test(`identity reset blocks late ${outcome} and old finally cannot clear new identity flight`, async () => {
    const old = deferred(),
      next = deferred()
    let calls = 0
    const f = fixture({ send: () => (++calls === 1 ? old.promise : next.promise) })
    const original = f.client.run({ assessmentId: 7 }, yes)
    const rejected = assert.rejects(original, code('IDENTITY'))
    await Promise.resolve()
    await Promise.resolve()
    await Promise.resolve()
    f.scope.reset()
    f.setContext('SYNTHETIC_USER_2_TENANT_2')
    const current = f.client.run({ assessmentId: 7 }, yes)
    await Promise.resolve()
    await Promise.resolve()
    await Promise.resolve()
    old[outcome](outcome === 'resolve' ? { id: 42 } : new Error('SYNTHETIC_SECRET'))
    await rejected
    assert.equal(f.client.run({ assessmentId: 7 }, yes), current)
    next.resolve({ id: 43 })
    assert.equal((await current).value.id, 43)
  })
}
test('context changes without explicit hook and dispose also clear pending intent', async () => {
  const f = fixture({
    limit: 1,
    send: async () => {
      throw new Error('SYNTHETIC_SECRET')
    }
  })
  await assert.rejects(f.client.run({ assessmentId: 7 }, yes), code('UNCERTAIN'))
  f.setContext('SYNTHETIC_NEW_TENANT')
  assert.equal(f.client.hasUncertain(7), false)
  await assert.rejects(f.client.run({ assessmentId: 8 }, yes), code('UNCERTAIN'))
  f.client.dispose()
  assert.equal(f.client.hasUncertain(8), false)
  await assert.rejects(f.client.run({ assessmentId: 8 }, yes), code('IDENTITY'))
})
test('identity changes during confirmation send nothing', async () => {
  const f = fixture()
  await assert.rejects(
    f.client.run({ assessmentId: 7 }, async () => {
      f.scope.reset()
      return true
    }),
    code('IDENTITY')
  )
  assert.equal(f.sent.length, 0)
})
test('optional 404 suppression is strictly scoped and never hides auth/server/other route errors', () => {
  const input = {
    response: { status: 404 },
    config: {
      url: '/rehab/report/generation-contract',
      method: 'GET',
      optionalReportContractProbe: true
    }
  }
  assert.equal(isOptionalReportContract404(input), true)
  for (const status of [401, 403, 500])
    assert.equal(isOptionalReportContract404({ ...input, response: { status } }), false)
  for (const change of [
    { method: 'POST' },
    { url: '/rehab/report/generate' },
    { optionalReportContractProbe: false }
  ]) {
    assert.equal(
      isOptionalReportContract404({ ...input, config: { ...input.config, ...change } }),
      false
    )
  }
})
test('three real Vue entries share intent flow, busy state and permission-guarded explicit reset', () => {
  for (const path of [
    'assessment/index.vue',
    'assessment/detail/index.vue',
    'patient/detail/index.vue'
  ]) {
    const source = readFileSync(new URL(`../src/views/rehab/${path}`, import.meta.url), 'utf8')
    assert.match(source, /useReportGeneration\(\)/)
    assert.match(source, /:loading="isGeneratingReport\(/)
    assert.match(source, /v-if="hasUncertainReport\(/)
    assert.match(source, /@click="resetPendingReport\(/)
    assert.doesNotMatch(source, /await generateRehabReport\(/)
    assert.match(
      source,
      /v-hasPermi="\['rehab:assessment:generate-report'\]" :disabled="isGeneratingReport/
    )
  }
  const hook = readFileSync(
    new URL('../src/hooks/web/useReportGeneration.ts', import.meta.url),
    'utf8'
  )
  assert.match(hook, /刷新页面/)
  assert.match(hook, /旧报告不删除/)
  assert.match(hook, /await message.confirm[\s\S]*forgetUncertain/)
  assert.match(hook, /!active \|\| \(stamp && !reportGenerationIntents.isCurrent\(stamp\)\)/)
})
test('auth boundaries clear intents but normal token refresh retains them; no new persistence/logging', () => {
  const read = (path) => readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8')
  const auth = read('utils/auth.ts')
  assert.match(
    auth,
    /invalidateSession = \(\) => \{\s*authSessionScope.reset\(\)\s*invalidateReportIntents\(\)/
  )
  assert.match(auth, /if \(!options\?\.refresh\) invalidateSession\(\)/)
  assert.match(auth, /removeToken = \(\) => \{\s*invalidateSession\(\)/)
  assert.match(read('store/modules/user.ts'), /resetState\(\) \{\s*invalidateSession\(\)/)
  assert.match(read('config/axios/service.ts'), /setToken\([^\n]+refresh: true/)
  const intent = read('utils/reportIntent.ts')
  assert.doesNotMatch(intent, /localStorage|sessionStorage|indexedDB|console\.|Math\.random/)
  assert.doesNotMatch(read('api/rehab/report/index.ts'), /getAccessToken|getRefreshToken/)
})
