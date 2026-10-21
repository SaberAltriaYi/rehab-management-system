// Real Chrome layout smoke on an isolated, temporary, owner-free TLS listener.
// Does not launch an owner DB, sign a license or connect to a clinic system.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { spawn, execFileSync } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync, statSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createOwnerControlServer } from '../http.mjs'

const chrome = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
function runChrome(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(chrome, args, { stdio: ['ignore', 'pipe', 'pipe'] })
    let stdout = ''
    let stderr = ''
    const timer = setTimeout(() => { child.kill('SIGKILL') }, 20_000)
    child.stdout.on('data', bytes => {
      stdout += bytes.toString()
      if (stdout.length > 1024 * 1024) child.kill('SIGKILL')
    })
    child.stderr.on('data', bytes => {
      stderr += bytes.toString()
      if (stderr.length > 128 * 1024) child.kill('SIGKILL')
    })
    child.on('error', reject)
    child.on('close', code => {
      clearTimeout(timer)
      if (code !== 0) reject(Error('Chrome synthetic smoke exited ' + code +
        ': ' + stderr.slice(-300)))
      else resolve(stdout)
    })
  })
}

// Chrome may be unable to start headless inside ShunCode's macOS PTY policy.
// The optional real-browser gate belongs to L5 on a dedicated browser runner.
// Never mark this as PASS just because Java/Node component tests succeeded.
test('Chrome renders owner login and responsive console over synthetic TLS without owner credentials',
  { skip: !existsSync(chrome) || process.env.REHAB_L4_CHROME_SMOKE !== '1' }, async () => {
    const dir = mkdtempSync(join(tmpdir(), 'rehab-l4-browser-'))
    const certPath = join(dir, 'cert.pem')
    const keyPath = join(dir, 'key.pem')
    let server
    try {
      execFileSync('openssl', [
        'req', '-x509', '-nodes', '-newkey', 'rsa:2048', '-days', '1',
        '-keyout', keyPath, '-out', certPath,
        '-subj', '/CN=127.0.0.1', '-addext', 'subjectAltName=IP:127.0.0.1'
      ], { stdio: 'ignore', timeout: 10_000 })
      server = createOwnerControlServer({}, {
        tlsOptions: { key: readFileSync(keyPath), cert: readFileSync(certPath) }
      })
      await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
      const url = 'https://127.0.0.1:' + server.address().port + '/owner/console'
      const common = [
        '--headless=new', '--no-sandbox', '--disable-gpu',
        '--disable-software-rasterizer', '--disable-dev-shm-usage',
        '--disable-extensions',
        '--disable-background-networking', '--no-first-run',
        // Synthetic one-day test-only CA; never used for the real owner URL.
        '--ignore-certificate-errors',
        '--user-data-dir=' + join(dir, 'profile'),
        '--virtual-time-budget=1000'
      ]
      const dom = await runChrome([...common, '--dump-dom', '--window-size=1280,900', url])
      assert.match(dom, /软件所有者双因素登录|所有者双因素登录/)
      assert.match(dom, /id="auth-panel"/)
      assert.match(dom, /id="login-form"/)
      assert.match(dom, /id="owner-content" hidden/)
      assert.doesNotMatch(dom, /患者病历(内容|姓名)|真实患者记录/)
      const screenshot = join(dir, 'owner-mobile.png')
      await runChrome([...common, '--window-size=390,844',
        '--screenshot=' + screenshot, url])
      assert.ok(statSync(screenshot).size > 10000,
        'Mobile login should be painted by a real Chromium renderer')
    } finally {
      if (server) await new Promise(resolve => server.close(resolve))
      rmSync(dir, { recursive: true, force: true })
    }
  }
)
