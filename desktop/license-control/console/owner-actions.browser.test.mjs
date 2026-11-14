// Synthetic Chrome verification of Owner action selection and feedback.
// Never logs in to the real Owner or writes production tenant/license state.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { existsSync, mkdtempSync, rmSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const chrome = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const uuidPending = 'b2f45bfa-2ee1-4d1a-99a0-a429fb8d27d1'
const uuidApproved = '77146a60-1d21-41b1-8687-4bf0e2892ed1'
const uuidRevoked = '1d5649cf-6a1f-4615-b91a-773748412c00'
const installUuid = 'cdd39eac-12a9-4f8d-854b-225991827fd9'
const appIdPending = '9e195782-73a4-402e-b72a-2d78316e4557'
const appIdApproved = 'd61517fa-1a32-4663-a617-92febd79d7cb'
const appIdRevoked = '711dfd35-5ac6-46d7-8734-752504b7a66e'
const files = Object.freeze({
  '/owner/console/app.mjs': ['app.mjs', 'text/javascript'],
  '/owner/console/policy.mjs': ['policy.mjs', 'text/javascript'],
  '/owner/console/styles.css': ['styles.css', 'text/css']
})

async function runChromeUntilDriver(url, profile) {
  // Unlike --dump-dom, DevTools waits for the asynchronous synthetic
  // event handlers to finish and prevents timing-based false failures.
  const child = spawn(chrome, [
    '--headless=new', '--no-sandbox', '--disable-gpu',
    '--disable-software-rasterizer', '--disable-dev-shm-usage',
    '--disable-extensions', '--no-first-run', '--disable-background-networking',
    '--remote-debugging-port=0', '--remote-allow-origins=http://localhost',
    '--user-data-dir=' + profile, url
  ], { stdio: 'ignore' })
  let socket
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
  try {
    const portFile = join(profile, 'DevToolsActivePort')
    let port
    for (let i = 0; i < 80; i++) {
      if (existsSync(portFile)) {
        port = Number(readFileSync(portFile, 'utf8').split('\n')[0])
        break
      }
      await sleep(100)
    }
    assert.ok(Number.isSafeInteger(port) && port > 0, 'Synthetic Chrome CDP startup failed')
    let page
    for (let i = 0; i < 50; i++) {
      const tabs = await fetch('http://127.0.0.1:' + port + '/json/list').then(r => r.json())
      page = tabs.find(tab => tab.type === 'page' && tab.url.startsWith(url))
      if (page) break
      await sleep(100)
    }
    assert.ok(page?.webSocketDebuggerUrl, 'Synthetic owner page CDP target missing')
    socket = new WebSocket(page.webSocketDebuggerUrl)
    await new Promise((resolve, reject) => {
      socket.addEventListener('open', resolve, { once: true })
      socket.addEventListener('error', reject, { once: true })
    })
    let commandId = 0
    const pending = new Map()
    socket.addEventListener('message', event => {
      const msg = JSON.parse(event.data)
      const entry = pending.get(msg.id)
      if (!entry) return
      pending.delete(msg.id)
      entry(msg)
    })
    async function evaluate(expression) {
      const id = ++commandId
      const answer = new Promise(resolve => pending.set(id, resolve))
      socket.send(JSON.stringify({ id, method: 'Runtime.evaluate',
        params: { expression, returnByValue: true } }))
      const msg = await answer
      if (msg.error || msg.result?.exceptionDetails) throw Error('CDP evaluation error')
      return msg.result?.result?.value
    }
    let lastPhase = ''
    let lastFeedback = ''
    for (let i = 0; i < 180; i++) {
      const state = await evaluate('document.body?.getAttribute("data-owner-ui-test")')
      if (state) return JSON.parse(state)
      lastPhase = await evaluate('document.documentElement.getAttribute("data-synthetic-phase")')
      lastFeedback = await evaluate('document.getElementById("feedback")?.textContent?.slice(0,150)')
      await sleep(100)
    }
    throw Error('Synthetic Owner driver timed out; phase=' + lastPhase + '; feedback=' + lastFeedback)
  } finally {
    socket?.close()
    child.kill('SIGTERM')
    await sleep(350)
    if (!child.killed) child.kill('SIGKILL')
  }
}

const browserDriver = `
document.documentElement.setAttribute('data-synthetic-phase', 'loaded')
const timer = ms => new Promise(resolve => setTimeout(resolve, ms))
const ready = async condition => {
  for (let i = 0; i < 75; i++) {
    if (condition()) return true
    await timer(45)
  }
  return false
}
const $ = id => document.getElementById(id)
const result = {}
try {
  document.documentElement.setAttribute('data-synthetic-phase', 'login')
  const login = $('login-form')
  login.elements.namedItem('username').value = 'synthetic-owner'
  login.elements.namedItem('password').value = 'synthetic-not-a-real-secret'
  login.elements.namedItem('totp').value = '123456'
  login.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  document.documentElement.setAttribute('data-synthetic-phase', 'await-applications')
  if (!await ready(() => $('applications').children.length === 3)) throw Error('synthetic table unavailable')
  document.documentElement.setAttribute('data-synthetic-phase', 'loaded-applications')
  if (!await ready(() => $('feedback').textContent.includes('申请与独立授权审计已刷新'))) {
    throw Error('initial Owner refresh did not finish')
  }
  document.documentElement.setAttribute('data-synthetic-phase', 'ready-for-actions')
  await timer(150)
  result.initialApproveEnabled = !$('approve').disabled
  result.initialTenantPolicyEnabled = !$('update-policy').disabled
  $('approve').click()
  document.documentElement.setAttribute('data-synthetic-phase', 'clicked-approve')
  if (!await ready(() => $('feedback').textContent.includes('不能只填写审批表单'))) throw Error('missing approval hint')
  result.approvalHint = $('selection-status').textContent.includes('请在上方申请列表')
  $('update-policy').click()
  if (!await ready(() => $('feedback').textContent.includes('合法的小写 UUID'))) throw Error('missing tenant hint')
  result.tenantHint = true
  const rows = [...$('applications').children]
  result.submittedTimeColumn = [...document.querySelectorAll('#applications')].length === 1 &&
    document.querySelector('th:nth-child(2)')?.textContent.includes('提交时间')
  result.newestFirst = rows[0].textContent.includes('模拟已批准机构') &&
    rows[1].textContent.includes('模拟待审核机构')
  result.beijingTimestamp = rows[1].children[1].textContent.includes('2026-10-21 13:07:50')
  result.missingTimestampExplained = rows[2].children[1].textContent.includes('日期未记录')
  result.pendingCount = $('application-count').textContent.includes('待审核 1 条 / 共 3 条')
  const pending = rows.find(row => row.textContent.includes('模拟待审核机构'))
  const approved = rows.find(row => row.textContent.includes('模拟已批准机构'))
  const revoked = rows.find(row => row.textContent.includes('模拟已撤销机构'))
  const clickNamed = (row, label) => [...row.querySelectorAll('button')].find(b => b.textContent === label).click()
  result.pendingDeleteAvailable = !![...pending.querySelectorAll('button')]
    .find(button => button.textContent === '删除申请')
  result.approvedDeleteAbsent = ![...approved.querySelectorAll('button')]
    .some(button => button.textContent === '删除申请')
  clickNamed(pending, '审核')
  result.pendingSelected = $('selection-status').textContent.includes('已选中')
  clickNamed(approved, '维护')
  if (!await ready(() => $('tenant-status').textContent.includes('状态：ACTIVE'))) throw Error('maintenance status not loaded')
  result.maintainLoaded = $('tenant-action-guide').textContent.includes('已启用')
  result.provisionPanelVisible = !$('approved-provision-panel').hidden
  result.provisionButtonEnabled = !$('provision-approved').disabled
  if (!result.provisionPanelVisible) throw Error('approved retry panel hidden')
  if (!await ready(() => $('owner-content').getAttribute('aria-busy') === 'false')) throw Error('maintenance callback busy')
  clickNamed(revoked, '维护')
  if (!await ready(() => $('tenant-status').textContent.includes('状态：REVOKED'))) throw Error('revoked tenant not loaded')
  result.revokedPolicyNotice = $('tenant-policy-result').textContent.includes('REVOKED')
  result.revokedProvisionNotice = $('approved-provision-result').textContent.includes('不能开通')
  if (!await ready(() => $('owner-content').getAttribute('aria-busy') === 'false')) throw Error('revoked maintenance callback busy')
  $('update-policy').click()
  if (!await ready(() => $('tenant-policy-result').textContent.includes('无法执行'))) throw Error('revoked policy click silent')
  result.revokedPolicyClickFeedback = $('tenant-policy-result').textContent.includes('ACTIVE')
  if (!await ready(() => $('owner-content').getAttribute('aria-busy') === 'false')) throw Error('revoked policy click busy')
  $('provision-approved').click()
  if (!await ready(() => $('approved-provision-result').textContent.includes('无法执行'))) throw Error('revoked provisioning click silent')
  result.revokedProvisionClickFeedback = $('approved-provision-result').textContent.includes('许可证')
} catch (e) {
  result.error = String(e.message || e)
}
document.body.setAttribute('data-owner-ui-test', JSON.stringify(result))
`

test('Chrome synthetic actions show guidance and load approved tenant without privileged requests',
  { skip: !existsSync(chrome), timeout: 45_000 }, async () => {
    const dir = mkdtempSync(join(tmpdir(), 'rehab-owner-ui-synthetic-'))
    let disallowedMutations = 0
    const server = createServer((req, res) => {
      const path = String(req.url || '')
      if (path === '/owner/console') {
        const source = readFileSync(new URL('./index.html', import.meta.url), 'utf8')
        res.setHeader('content-type', 'text/html; charset=utf-8')
        res.end(source.replace('</body>',
          '<script type="module" src="/synthetic-owner-driver.mjs"></script></body>'))
        return
      }
      if (files[path]) {
        res.setHeader('content-type', files[path][1])
        res.end(readFileSync(new URL('./' + files[path][0], import.meta.url)))
        return
      }
      if (path === '/synthetic-owner-driver.mjs') {
        res.setHeader('content-type', 'text/javascript')
        res.end(browserDriver)
        return
      }
      res.setHeader('content-type', 'application/json')
      if (req.method === 'POST' && path === '/owner/v1/login') {
        res.end(JSON.stringify({ token: 'S'.repeat(43) }))
        return
      }
      if (req.method === 'GET' && path === '/owner/v1/applications') {
        res.end(JSON.stringify({
          adminRecoveryEnabled: true, provisioningConfigured: true, activationEmailConfigured: false,
          applications: [
            { id: appIdPending, tenantId: uuidPending, installationId: installUuid,
              organization: '模拟待审核机构', status: 'PENDING', provisioningStatus: 'NOT_CONFIGURED',
              submittedAt: Date.parse('2026-10-21T05:07:50Z') },
            { id: appIdApproved, tenantId: uuidApproved, installationId: installUuid,
              organization: '模拟已批准机构', status: 'APPROVED', provisioningStatus: 'FAILED',
              submittedAt: Date.parse('2026-10-24T09:54:17Z') },
            { id: appIdRevoked, tenantId: uuidRevoked, installationId: installUuid,
              organization: '模拟已撤销机构', status: 'APPROVED', provisioningStatus: 'FAILED' }
          ]
        }))
        return
      }
      if (req.method === 'GET' && path === '/owner/v1/audit') {
        res.end(JSON.stringify({ audit: [] }))
        return
      }
      if (req.method === 'GET' && path === '/owner/v1/tenants/' + uuidApproved) {
        res.end(JSON.stringify({ tenant: { tenantId: uuidApproved, status: 'ACTIVE',
          seatLimit: null, maxInstallations: 3, revocationEpoch: 0,
          expiresAt: '2027-11-12T00:00:00.000Z', modules: ['rehab', 'motion'] } }))
        return
      }
      if (req.method === 'GET' && path === '/owner/v1/tenants/' + uuidRevoked) {
        res.end(JSON.stringify({ tenant: { tenantId: uuidRevoked, status: 'REVOKED',
          seatLimit: null, maxInstallations: 3, revocationEpoch: 5,
          expiresAt: '2028-01-01T09:54:00.000Z', modules: ['rehab', 'motion'] } }))
        return
      }
      if (req.method !== 'GET') disallowedMutations++
      res.statusCode = 403
      res.end(JSON.stringify({ error: 'SYNTHETIC_MUTATION_FORBIDDEN' }))
    })
    try {
      await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
      const address = 'http://127.0.0.1:' + server.address().port + '/owner/console'
      const state = await runChromeUntilDriver(address, join(dir, 'chrome-profile'))
      assert.equal(state.error, undefined, JSON.stringify(state))
      for (const key of ['initialApproveEnabled', 'initialTenantPolicyEnabled',
        'approvalHint', 'tenantHint', 'pendingSelected', 'maintainLoaded',
        'submittedTimeColumn', 'newestFirst', 'beijingTimestamp',
        'missingTimestampExplained', 'pendingCount',
        'provisionPanelVisible', 'provisionButtonEnabled',
        'pendingDeleteAvailable','approvedDeleteAbsent','revokedPolicyNotice',
        'revokedProvisionNotice','revokedPolicyClickFeedback','revokedProvisionClickFeedback']) {
        assert.equal(state[key], true, key + ': ' + JSON.stringify(state))
      }
      assert.equal(disallowedMutations, 0, 'Synthetic browser must not approve/change a tenant')
    } finally {
      await new Promise(resolve => server.close(resolve))
      rmSync(dir, { recursive: true, force: true })
    }
  })
