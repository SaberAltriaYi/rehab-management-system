// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Synthetic identities only.
import test from 'node:test'
import assert from 'node:assert/strict'
import { inspect } from 'node:util'
import axios, { AxiosError } from 'axios'
import {
  createAuthRefreshCoordinator,
  waitForAuthRefresh
} from '../src/config/axios/refreshCoordinator.ts'
import { createAuthSessionScope } from '../src/utils/authSession.ts'
import { openAuthService, deferred, flush, reply } from './helpers/auth-service-fixture.mjs'

const rejects = (code) => (error) =>
  error.code === code && !inspect(error).includes('SYNTHETIC_SECRET')
function coordinator(options = {}) {
  const scope = createAuthSessionScope(),
    response = deferred(),
    applied = []
  let fires,
    timers = 0,
    clears = 0,
    context = 'SYNTHETIC_A',
    signal
  const client = createAuthRefreshCoordinator({
    context: () => context,
    subscribe: scope.subscribe,
    schedule: (callback, timeout) => {
      assert.equal(timeout, 30000)
      fires = callback
      timers++
      return () => {
        clears++
      }
    },
    refresh: async (value) => {
      signal = value
      return response.promise
    },
    apply: (value) => applied.push(value),
    ...options
  })
  return {
    client,
    scope,
    response,
    applied,
    change: () => {
      context = 'SYNTHETIC_B'
      scope.reset()
    },
    context: () => context,
    expire: () => fires(),
    state: () => ({ timers, clears, aborted: signal?.aborted })
  }
}

test('coordinator shares one settlement and clears its timer', async () => {
  const f = coordinator(),
    a = f.client.run(f.context()),
    b = f.client.run(f.context())
  assert.equal(a, b)
  await flush()
  f.response.resolve('SYNTHETIC_TOKENS')
  await a
  assert.deepEqual(f.applied, ['SYNTHETIC_TOKENS'])
  assert.equal(f.state().clears, 1)
  f.client.dispose()
})
test('coordinator failure rejects all waiters without retaining raw diagnostics or retry storms', async () => {
  const f = coordinator(),
    a = f.client.run(f.context()),
    b = f.client.run(f.context())
  const results = Promise.allSettled([a, b])
  await flush()
  f.response.reject(new Error('SYNTHETIC_SECRET'))
  for (const item of await results) {
    assert.equal(item.status, 'rejected')
    assert.ok(rejects('FAILED')(item.reason))
  }
  await assert.rejects(f.client.run(f.context()), rejects('FAILED'))
  assert.equal(f.state().timers, 1)
  f.client.dispose()
})
test('deadline aborts shared work, settles every waiter and discards late success', async () => {
  const f = coordinator(),
    a = f.client.run(f.context()),
    checked = assert.rejects(a, rejects('TIMEOUT'))
  await flush()
  f.expire()
  await checked
  assert.equal(f.state().aborted, true)
  f.response.resolve('SYNTHETIC_LATE')
  await flush()
  assert.deepEqual(f.applied, [])
  assert.equal(f.state().clears, 1)
  f.client.dispose()
})
test('identity reset immediately rejects old flight and late callbacks do not clear a newer flight', async () => {
  const first = deferred(),
    second = deferred()
  let calls = 0
  const f = coordinator({ refresh: () => (++calls === 1 ? first.promise : second.promise) })
  const a = f.client.run(f.context()),
    checked = assert.rejects(a, rejects('CONTEXT'))
  await flush()
  f.change()
  await checked
  const b = f.client.run(f.context())
  await flush()
  first.resolve('SYNTHETIC_OLD')
  await flush()
  assert.equal(f.client.run(f.context()), b)
  assert.deepEqual(f.applied, [])
  second.resolve('SYNTHETIC_NEW')
  await b
  assert.deepEqual(f.applied, ['SYNTHETIC_NEW'])
  f.client.dispose()
})
test('one caller cancellation leaves shared refresh available for other callers', async () => {
  const f = coordinator(),
    abort = new AbortController(),
    shared = f.client.run(f.context())
  const canceled = waitForAuthRefresh(shared, abort.signal),
    checked = assert.rejects(canceled, (error) => axios.isCancel(error))
  abort.abort()
  await checked
  f.response.resolve('SYNTHETIC_OK')
  await shared
  assert.deepEqual(f.applied, ['SYNTHETIC_OK'])
  f.client.dispose()
})

test('broken timer cleanup and apply failure still settle and redact every waiter', async () => {
  const f = coordinator({
    schedule: () => () => {
      throw new Error('SYNTHETIC_SECRET')
    },
    apply: () => {
      throw new Error('SYNTHETIC_SECRET')
    }
  })
  const work = f.client.run(f.context()),
    checked = assert.rejects(work, rejects('FAILED'))
  f.response.resolve('SYNTHETIC_VALUE')
  await checked
  f.client.dispose()
})

test('actual Axios shared deadline rejects all waiters, aborts refresh and never replays', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const gate = deferred(),
    f = await openAuthService({
      refresh: async (config) => {
        await gate.promise
        return reply(config, {
          code: 200,
          data: { accessToken: 'SYNTHETIC_NEW_ACCESS', refreshToken: 'SYNTHETIC_NEW_REFRESH' }
        })
      }
    })
  try {
    const work = Promise.allSettled([f.service.get('/rehab/a'), f.service.get('/rehab/b')])
    await flush()
    assert.equal(f.refreshes.length, 1)
    t.mock.timers.tick(30000)
    await flush()
    assert.ok((await work).every((item) => item.status === 'rejected'))
    assert.equal(f.refreshes[0].signal.aborted, true)
    assert.equal(f.requests.length, 2)
    gate.resolve()
    await flush()
    assert.equal(f.auth.getAccessToken(), 'SYNTHETIC_OLD_ACCESS')
  } finally {
    f.close()
    t.mock.timers.reset()
  }
})

test('actual individual caller abort does not cancel another caller refresh or replay canceled body', async () => {
  const gate = deferred(),
    f = await openAuthService({
      refresh: async (config) => {
        await gate.promise
        return reply(config, {
          code: 200,
          data: { accessToken: 'SYNTHETIC_NEW_ACCESS', refreshToken: 'SYNTHETIC_NEW_REFRESH' }
        })
      }
    })
  const abort = new AbortController()
  try {
    const canceled = f.service.get('/rehab/canceled', { signal: abort.signal })
    const checked = assert.rejects(canceled, (error) => axios.isCancel(error))
    const survivor = f.service.get('/rehab/survivor')
    await flush()
    abort.abort()
    await checked
    assert.equal(f.refreshes[0].signal.aborted, false)
    gate.resolve()
    await survivor
    assert.equal(f.requests.filter((item) => item.url.endsWith('/canceled')).length, 1)
    assert.equal(f.requests.filter((item) => item.url.endsWith('/survivor')).length, 2)
    assert.equal(f.notices.length, 0)
  } finally {
    f.close()
  }
})

test('actual Axios interceptor chain refreshes once for concurrent 401s and replays each once', async () => {
  const gate = deferred(),
    f = await openAuthService({
      refresh: async (config) => {
        await gate.promise
        return reply(config, {
          code: 200,
          data: { accessToken: 'SYNTHETIC_NEW_ACCESS', refreshToken: 'SYNTHETIC_NEW_REFRESH' }
        })
      }
    })
  try {
    const work = Promise.all([
      f.service.get('/rehab/a'),
      f.service.get('/rehab/b'),
      f.service.get('/rehab/c')
    ])
    await flush()
    assert.equal(f.refreshes.length, 1)
    assert.equal(f.requests.length, 3)
    gate.resolve()
    const results = await work
    assert.equal(results.length, 3)
    assert.equal(f.requests.length, 6)
    assert.equal(f.notices.length, 0)
    assert.equal(f.refreshes[0].timeout, 30000)
    assert.ok(f.refreshes[0].signal)
    assert.equal(new URLSearchParams(f.refreshes[0].data).get('refreshToken'), 'SYNTHETIC_REFRESH')
    assert.equal(f.refreshes[0].url.includes('SYNTHETIC_'), false)
    assert.equal(f.refreshes[0].headers.Authorization, undefined)
    assert.equal(f.refreshes[0].headers['visit-tenant-id'], undefined)
  } finally {
    f.close()
  }
})
test('missing refresh credential fails promptly for all callers without a sticky queue', async () => {
  const f = await openAuthService()
  f.cache.delete('REFRESH_TOKEN')
  try {
    const results = await Promise.allSettled([f.service.get('/rehab/a'), f.service.get('/rehab/b')])
    assert.ok(results.every((item) => item.status === 'rejected'))
    assert.equal(f.requests.length, 2)
    assert.equal(f.refreshes.length, 0)
    await assert.rejects(f.service.get('/rehab/c'), rejects('FAILED'))
    f.auth.setToken({ accessToken: 'SYNTHETIC_OLD_ACCESS', refreshToken: 'SYNTHETIC_REFRESH' })
    await f.service.get('/rehab/d')
    assert.equal(f.refreshes.length, 1)
  } finally {
    f.close()
  }
})
for (const mode of ['network', 'business', 'malformed']) {
  test(`actual ${mode} refresh failure rejects all waiting requests and never replays old bodies`, async () => {
    const gate = deferred(),
      f = await openAuthService({
        refresh: async (config) => {
          await gate.promise
          if (mode === 'network') throw new AxiosError('SYNTHETIC_SECRET', 'ERR_NETWORK', config)
          return reply(
            config,
            mode === 'business' ? { code: 401, msg: 'SYNTHETIC_SECRET' } : { code: 200, data: {} }
          )
        }
      })
    try {
      const work = Promise.allSettled([
        f.service.post('/rehab/a', { note: 'SYNTHETIC_SECRET' }),
        f.service.get('/rehab/b')
      ])
      await flush()
      gate.resolve()
      const results = await work
      for (const item of results) {
        assert.equal(item.status, 'rejected')
        assert.equal(inspect(item.reason).includes('SYNTHETIC_SECRET'), false)
      }
      assert.equal(f.requests.length, 2)
      assert.equal(f.refreshes.length, 1)
      await assert.rejects(f.service.get('/rehab/c'), rejects('FAILED'))
      assert.equal(f.refreshes.length, 1)
    } finally {
      f.close()
    }
  })
}
test('a replay that still gets 401 stops and blocks another refresh storm', async () => {
  const f = await openAuthService({ resource: (config) => reply(config, { code: 401 }) })
  try {
    await assert.rejects(f.service.get('/rehab/a'), rejects('FAILED'))
    assert.equal(f.requests.length, 2)
    assert.equal(f.refreshes.length, 1)
    await assert.rejects(f.service.get('/rehab/b'), rejects('FAILED'))
    assert.equal(f.requests.length, 3)
    assert.equal(f.refreshes.length, 1)
  } finally {
    f.close()
  }
})
test('late old-token 401 replays with current token, not a second refresh', async () => {
  const slow = deferred(),
    f = await openAuthService({
      resource: async (config) => {
        if (config.url.endsWith('/slow') && !config.authReplayAttempt) {
          await slow.promise
          return reply(config, { code: 401 })
        }
        return reply(config, {
          code: config.headers.Authorization === 'Bearer SYNTHETIC_NEW_ACCESS' ? 200 : 401
        })
      }
    })
  try {
    const delayed = f.service.get('/rehab/slow')
    await f.service.get('/rehab/fast')
    slow.resolve()
    await delayed
    assert.equal(f.refreshes.length, 1)
    assert.equal(f.requests.length, 4)
  } finally {
    f.close()
  }
})
for (const boundary of ['logout', 'login', 'tenant', 'visitTenant', 'storage']) {
  test(`actual ${boundary} boundary rejects old refresh without credential overwrite, replay or new-identity logout`, async () => {
    const gate = deferred(),
      f = await openAuthService({
        refresh: async (config) => {
          await gate.promise
          return reply(config, {
            code: 200,
            data: { accessToken: 'SYNTHETIC_STALE', refreshToken: 'SYNTHETIC_STALE' }
          })
        }
      })
    try {
      const work = f.service.get('/rehab/a'),
        checked = assert.rejects(work, rejects('CONTEXT'))
      await flush()
      if (boundary === 'logout') f.auth.removeToken()
      if (boundary === 'login')
        f.auth.setToken({ accessToken: 'SYNTHETIC_OTHER', refreshToken: 'SYNTHETIC_OTHER' })
      if (boundary === 'tenant') f.auth.setTenantId(72)
      if (boundary === 'visitTenant') f.auth.setVisitTenantId(72)
      if (boundary === 'storage') {
        f.cache.set('ACCESS_TOKEN', 'SYNTHETIC_OTHER')
        f.storageEvent('ACCESS_TOKEN')
      }
      const expected = f.auth.getAccessToken()
      await checked
      gate.resolve()
      await flush()
      assert.equal(f.auth.getAccessToken(), expected)
      assert.equal(f.requests.length, 1)
      assert.equal(f.notices.length, 0)
    } finally {
      f.close()
    }
  })
}
test('old successful response and old transport failure cannot reach a changed identity', async () => {
  for (const fails of [false, true]) {
    const gate = deferred(),
      f = await openAuthService({
        resource: async (config) => {
          await gate.promise
          if (fails) throw new AxiosError('SYNTHETIC_SECRET', 'ERR_NETWORK', config)
          return reply(config, { code: 200, data: 'SYNTHETIC_SECRET' })
        }
      })
    try {
      const work = f.service.get('/rehab/a'),
        checked = assert.rejects(work, rejects('CONTEXT'))
      await flush()
      f.auth.setTenantId(72)
      gate.resolve()
      await checked
      assert.equal(f.notices.length, 0)
    } finally {
      f.close()
    }
  }
})
test('login page 401 rejects without undefined success, token refresh, or a dialog', async () => {
  const f = await openAuthService({
    location: 'https://synthetic.invalid/login',
    resource: (config) => reply(config, { code: 401 })
  })
  try {
    await assert.rejects(f.service.post('/system/auth/login', {}), rejects('FAILED'))
    assert.equal(f.requests[0].headers.Authorization, undefined)
    assert.equal(f.refreshes.length, 0)
    assert.equal(f.notices.length, 0)
  } finally {
    f.close()
  }
})
test('late login-dialog confirmation cannot clear a new identity credentials or router', async () => {
  const f = await openAuthService({ resource: (config) => reply(config, { code: 401 }) })
  f.cache.delete('REFRESH_TOKEN')
  try {
    await assert.rejects(f.service.get('/rehab/a'))
    f.auth.setToken({ accessToken: 'SYNTHETIC_OTHER', refreshToken: 'SYNTHETIC_OTHER' })
    f.dialog.resolve()
    await flush()
    assert.equal(f.auth.getAccessToken(), 'SYNTHETIC_OTHER')
    assert.equal(f.statistics().resets, 0)
    assert.equal(f.statistics().reloads, 0)
  } finally {
    f.close()
  }
})

test('normal refresh preserves identity generation and report intents, while login and storage boundaries reset both', async () => {
  const f = await openAuthService()
  try {
    const epoch = f.scope.version,
      clears = f.statistics().intentClears
    await f.service.get('/rehab/a')
    assert.equal(f.scope.version, epoch)
    assert.equal(f.statistics().intentClears, clears)
    f.storageEvent('theme')
    assert.equal(f.scope.version, epoch)
    f.auth.setToken({ accessToken: 'SYNTHETIC_NEW_ACCESS', refreshToken: 'SYNTHETIC_NEW_REFRESH' })
    assert.equal(f.scope.version, epoch + 1)
    assert.equal(f.statistics().intentClears, clears + 1)
    f.storageEvent(null)
    assert.equal(f.scope.version, epoch + 2)
  } finally {
    f.close()
  }
})

test('current-identity login confirmation clears credentials through the original login path', async () => {
  const f = await openAuthService()
  f.cache.delete('REFRESH_TOKEN')
  try {
    await assert.rejects(f.service.get('/rehab/a'), rejects('FAILED'))
    f.dialog.resolve()
    await flush()
    assert.equal(f.auth.getAccessToken(), undefined)
    assert.equal(f.statistics().resets, 1)
    assert.equal(f.statistics().cacheDeletes, 1)
    assert.equal(f.statistics().reloads, 1)
  } finally {
    f.close()
  }
})

test('anonymous flag clears bearer/visit headers and a 401 cannot acquire authenticated retry', async () => {
  const f = await openAuthService({ resource: (config) => reply(config, { code: 401 }) })
  try {
    f.auth.setVisitTenantId(72)
    await assert.rejects(
      f.service.get('/synthetic/public', {
        headers: { isToken: false, Authorization: 'SYNTHETIC_STALE', 'visit-tenant-id': '999' }
      }),
      rejects('FAILED')
    )
    assert.equal(f.requests[0].headers.Authorization, undefined)
    assert.equal(f.requests[0].headers['visit-tenant-id'], undefined)
    assert.equal(f.refreshes.length, 0)
    assert.equal(f.requests.length, 1)
  } finally {
    f.close()
  }
})

test('Owner portal keeps its fixed tenant on original and refresh requests without visiting-tenant credentials', async () => {
  const f = await openAuthService({
    environment: { VITE_PORTAL_MODE: 'owner', VITE_OWNER_TENANT_ID: '99' }
  })
  try {
    await f.service.get('/system/synthetic')
    assert.equal(String(f.requests[0].headers['tenant-id']), '99')
    assert.equal(String(f.refreshes[0].headers['tenant-id']), '99')
    assert.equal(f.refreshes[0].headers.Authorization, undefined)
    assert.equal(f.refreshes[0].headers['visit-tenant-id'], undefined)
  } finally {
    f.close()
  }
})

test('transport HTTP 401 retains existing rejection semantics, never an automatic authenticated replay', async () => {
  const f = await openAuthService({
    resource: (config) => {
      throw new AxiosError(
        'SYNTHETIC_SECRET',
        'ERR_BAD_REQUEST',
        config,
        undefined,
        reply(config, 'SYNTHETIC_SECRET', 401)
      )
    }
  })
  try {
    await assert.rejects(
      f.service.get('/rehab/a'),
      (error) => error.response?.status === 401 && !inspect(error).includes('SYNTHETIC_SECRET')
    )
    assert.equal(f.requests.length, 1)
    assert.equal(f.refreshes.length, 0)
  } finally {
    f.close()
  }
})
test('encrypted POST replay is not encrypted twice, and downloads/form payloads remain compatible', async () => {
  const f = await openAuthService()
  try {
    await f.service.post('/rehab/a', { zero: 0 }, { headers: { isEncrypt: true } })
    assert.equal(f.statistics().encryptions, 1)
    assert.equal(f.requests[0].data, f.requests[1].data)
    await f.service.post(
      '/rehab/form',
      { zero: 0, text: 'a&b' },
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
    )
    assert.equal(f.requests.at(-1).data, 'zero=0&text=a%26b')
  } finally {
    f.close()
  }
  const binary = await openAuthService({
    resource: (config) => reply(config, new Blob(['SYNTHETIC_BINARY'], { type: 'application/pdf' }))
  })
  try {
    const blob = await binary.service.get('/rehab/file', { responseType: 'blob' })
    assert.equal(await blob.text(), 'SYNTHETIC_BINARY')
    assert.equal(binary.refreshes.length, 0)
  } finally {
    binary.close()
  }
})
