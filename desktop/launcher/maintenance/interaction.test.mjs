import assert from 'node:assert/strict'
import test from 'node:test'
import vm from 'node:vm'
import { build } from 'esbuild'

async function renderRecoveryUI(initialHealthy) {
  const controls = new Map()
  const calls = []
  const root = {
    set innerHTML(html) {
      for (const [, id] of html.matchAll(/\bid="([^"]+)"/g)) {
        controls.set(id, {
          disabled: false,
          value: '',
          textContent: '',
          className: '',
          listeners: {},
          addEventListener(event, listener) { this.listeners[event] = listener }
        })
      }
    }
  }
  const context = {
    document: {
      querySelector(selector) { return selector === '#app' ? root : null },
      getElementById(id) { return controls.get(id) ?? null }
    },
    window: { confirm: () => true },
    __invoke: async (name, args) => {
      calls.push({ name, args })
      if (name === 'recovery_maintenance_status') {
        return { servicesHealthy: initialHealthy }
      }
      if (name === 'begin_admin_recovery') return 'A'.repeat(43)
      if (name === 'recovery_maintenance_apply') return undefined
      throw Error('unexpected command')
    }
  }
  const compiled = await build({
    entryPoints: ['maintenance/main.ts'],
    bundle: true,
    write: false,
    format: 'iife',
    platform: 'browser',
    plugins: [{
      name: 'fake-only-tauri',
      setup(b) {
        b.onResolve({ filter: /^@tauri-apps\/api\/core$/ },
          () => ({ path: 'mocked-tauri', namespace: 'mock' }))
        b.onLoad({ filter: /.*/, namespace: 'mock' },
          () => ({ contents: 'export const invoke = globalThis.__invoke', loader: 'js' }))
      }
    }]
  })
  vm.runInNewContext(compiled.outputFiles[0].text, context)
  // Allow the initially invoked status check to settle.
  await new Promise(resolve => setImmediate(resolve))
  return {
    controls, calls,
    button(id) { return controls.get(id) },
    async click(id) { await controls.get(id).listeners.click() }
  }
}

test('recovery button always responds with a useful message while unhealthy', async () => {
  const app = await renderRecoveryUI(false)
  assert.equal(app.button('apply').disabled, false)
  await app.click('apply')
  assert.match(app.button('message').textContent, /健康检查未通过/)
  assert.equal(app.calls.filter(c => c.name === 'recovery_maintenance_apply').length, 0)
})

test('missing device challenge shows a clear message rather than a disabled button', async () => {
  const app = await renderRecoveryUI(true)
  assert.equal(app.button('apply').disabled, false)
  await app.click('apply')
  assert.match(app.button('message').textContent, /请先在本维护工具点击“生成新的挑战码”/)
  assert.equal(app.calls.filter(c => c.name === 'recovery_maintenance_apply').length, 0)
})

test('full synthetic UI flow invokes only the approved native command and clears secrets', async () => {
  const app = await renderRecoveryUI(true)
  await app.click('begin')
  assert.equal(app.button('challenge').value.length, 43)
  assert.equal(app.button('apply').disabled, false)
  await app.click('apply')
  assert.match(app.button('message').textContent, /43 位 Owner 一次性授权码/)
  app.button('approval').value = 'B'.repeat(43)
  app.button('password').value = 'synthetic1234'
  app.button('confirmation').value = 'synthetic1234'
  await app.click('apply')
  assert.equal(app.calls.filter(c => c.name === 'recovery_maintenance_apply').length, 1)
  assert.equal(app.button('password').value, '')
  assert.equal(app.button('confirmation').value, '')
  assert.equal(app.button('approval').value, '')
  assert.equal(app.button('challenge').value, '')
  assert.match(app.button('message').textContent, /恢复操作返回成功/)
})
