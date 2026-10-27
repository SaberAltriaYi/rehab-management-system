// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
// Run the actual composable in a Vue effect scope, with synthetic API/dialog ports.
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'
import * as vue from 'vue'
import * as intents from '../src/utils/reportIntent.ts'

const deferred = () => {
  let resolve, reject
  const promise = new Promise((a, b) => {
    resolve = a
    reject = b
  })
  return { promise, resolve, reject }
}
const flush = async () => {
  for (let n = 0; n < 8; n++) await Promise.resolve()
}
let fixtureNumber = 0
async function open(options = {}) {
  const scope = intents.createReportIntentScope(),
    messages = [],
    sent = []
  let sequence = 0,
    confirm = options.confirm ?? (async () => {}),
    context = 'SYNTHETIC_USER_1'
  const coordinator = intents.createReportIntentCoordinator({
    scope,
    context: () => context,
    probe: async () => 'ready',
    key: () => `00000000-0000-4000-8000-${String(++sequence).padStart(12, '0')}`,
    send: async (payload) => {
      sent.push(payload)
      return options.send ? options.send(payload) : { id: 42 }
    }
  })
  const message = Object.fromEntries(
    ['success', 'warning', 'info'].map((level) => [level, (text) => messages.push({ level, text })])
  )
  message.confirm = (text) => {
    messages.push({ level: 'confirm', text })
    return confirm(text)
  }
  const key = `__report_ui_fixture_${++fixtureNumber}`
  globalThis[key] = {
    ...vue,
    ...intents,
    reportIntentScope: scope,
    reportGenerationIntents: coordinator,
    useMessage: () => message
  }
  const source = readFileSync(
    new URL('../src/hooks/web/useReportGeneration.ts', import.meta.url),
    'utf8'
  )
  const output = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext }
  }).outputText
  // Only replace the four known dependency imports, not production control flow.
  let count = 0
  const code = output.replace(
    /import\s+\{([^}]+)\}\s+from\s+['"]([^'"]+)['"];?/g,
    (_, names, path) => {
      assert.ok(
        ['vue', './useMessage', '@/api/rehab/report', '@/utils/reportIntent'].includes(path)
      )
      count++
      return `const {${names}} = globalThis[${JSON.stringify(key)}];`
    }
  )
  assert.equal(count, 4)
  let module
  try {
    module = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
  } finally {
    delete globalThis[key]
  }
  const effect = vue.effectScope(),
    ui = effect.run(() => module.useReportGeneration())
  return {
    ui,
    messages,
    sent,
    scope,
    stop: () => {
      effect.stop()
      coordinator.dispose()
    },
    unmount: () => effect.stop(),
    changeIdentity: () => {
      context = 'SYNTHETIC_USER_2'
      scope.reset()
    },
    setConfirm: (value) => {
      confirm = value
    }
  }
}

test('real Vue composable sets busy before dialog, prevents double dialog, then releases busy', async () => {
  const dialog = deferred(),
    response = deferred()
  const f = await open({ confirm: () => dialog.promise, send: () => response.promise })
  try {
    const work = f.ui.generateReport(7)
    assert.equal(f.ui.isGeneratingReport(7), true)
    assert.equal(await f.ui.generateReport(7), false)
    await flush()
    assert.equal(f.messages.filter((m) => m.level === 'confirm').length, 1)
    assert.equal(f.sent.length, 0)
    dialog.resolve()
    await flush()
    assert.equal(f.sent.length, 1)
    response.resolve({ id: 42 })
    assert.equal(await work, true)
    assert.equal(f.ui.isGeneratingReport(7), false)
    assert.equal(f.messages.filter((m) => m.level === 'success').length, 1)
  } finally {
    f.stop()
  }
})

test('real Vue uncertainty UI retains key on cancelled reset, and resets only after confirmed dialog', async () => {
  const f = await open({
    send: async () => {
      throw new Error('SYNTHETIC_SECRET')
    }
  })
  try {
    assert.equal(await f.ui.generateReport(7), false)
    assert.equal(f.ui.hasUncertainReport(7), true)
    f.setConfirm(async () => {
      throw 'cancel'
    })
    await f.ui.resetPendingReport(7)
    assert.equal(f.ui.hasUncertainReport(7), true)
    f.setConfirm(async () => {})
    assert.equal(await f.ui.generateReport(7), false)
    assert.equal(f.sent[0].requestKey, f.sent[1].requestKey)
    await f.ui.resetPendingReport(7)
    assert.equal(f.ui.hasUncertainReport(7), false)
    assert.equal(await f.ui.generateReport(7), false)
    assert.notEqual(f.sent[1].requestKey, f.sent[2].requestKey)
    assert.equal(JSON.stringify(f.messages).includes('SYNTHETIC_SECRET'), false)
  } finally {
    f.stop()
  }
})

for (const boundary of ['changeIdentity', 'unmount']) {
  test(`real Vue ${boundary} suppresses late success and downstream refresh signal`, async () => {
    const response = deferred(),
      f = await open({ send: () => response.promise })
    try {
      const work = f.ui.generateReport(7)
      await flush()
      f[boundary]()
      const before = f.messages.length
      response.resolve({ id: 42 })
      assert.equal(await work, false)
      assert.equal(f.messages.length, before)
    } finally {
      f.stop()
    }
  })
}

test('real Vue identity switch suppresses late errors and does not clear newer busy state', async () => {
  const old = deferred(),
    current = deferred()
  let calls = 0
  const f = await open({ send: () => (++calls === 1 ? old.promise : current.promise) })
  try {
    const first = f.ui.generateReport(7)
    await flush()
    f.changeIdentity()
    const second = f.ui.generateReport(7)
    await flush()
    const before = f.messages.length
    old.reject(new Error('SYNTHETIC_SECRET'))
    assert.equal(await first, false)
    assert.equal(f.messages.length, before)
    assert.equal(f.ui.isGeneratingReport(7), true)
    current.resolve({ id: 43 })
    assert.equal(await second, true)
    assert.equal(f.ui.isGeneratingReport(7), false)
  } finally {
    f.stop()
  }
})
