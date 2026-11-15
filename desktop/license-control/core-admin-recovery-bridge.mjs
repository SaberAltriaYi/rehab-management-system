// Owner -> Core restricted local helper. No SQL, Docker commands or
// passwords in argv, env, URLs, disk, audit records or process diagnostics.
import { createHash, timingSafeEqual } from 'node:crypto'
import { createReadStream, lstatSync } from 'node:fs'
import { spawn } from 'node:child_process'
import { resolve, isAbsolute } from 'node:path'
import { createInterface } from 'node:readline'

function limitedText(text) {
  return typeof text === 'string' && text.length === 43 && /^[A-Za-z0-9_-]{43}$/.test(text)
}

async function verifyBinary(executable, sha256) {
  if (!isAbsolute(executable) || !/^[0-9a-f]{64}$/.test(sha256)) throw Error('RECOVERY_HELPER_UNAVAILABLE')
  const file = lstatSync(executable)
  if (!file.isFile() || (file.mode & 0o022) || !(file.mode & 0o100)) {
    throw Error('RECOVERY_HELPER_UNAVAILABLE')
  }
  // Absolute executable path is provided only from the private Owner config,
  // never a browser request. Only use after hash pinning.
  const chunks = []
  let bytes = 0
  for await (const chunk of createReadStream(executable)) {
    bytes += chunk.length
    if (bytes > 40_000_000) throw Error('RECOVERY_HELPER_UNAVAILABLE')
    chunks.push(chunk)
  }
  const actual = createHash('sha256').update(Buffer.concat(chunks)).digest()
  const expected = Buffer.from(sha256, 'hex')
  if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) {
    throw Error('RECOVERY_HELPER_UNAVAILABLE')
  }
}

export class LocalCoreRecoveryBridge {
  #running = false
  constructor({ executable, sha256, deadlineMs = 80_000 } = {}) {
    this.executable = executable
    this.sha256 = sha256
    this.deadlineMs = Math.max(10_000, Math.min(100_000, deadlineMs))
  }
  get configured() {
    return Boolean(this.executable && this.sha256)
  }
  async recover({ newPassword, authorize }) {
    if (this.#running) throw Error('RECOVERY_ALREADY_RUNNING')
    if (typeof authorize !== 'function' ||
      typeof newPassword !== 'string' || newPassword.length < 12 || newPassword.length > 16) {
      throw Error('INVALID_RECOVERY_INPUT')
    }
    if (!this.configured) throw Error('RECOVERY_HELPER_UNAVAILABLE')
    this.#running = true
    try {
      await verifyBinary(this.executable, this.sha256)
      return await this.#execute(newPassword, authorize)
    } finally {
      this.#running = false
    }
  }
  async #execute(newPassword, authorize) {
    const child = spawn(this.executable, [], {
      cwd: '/', env: { PATH: '/usr/bin:/bin:/usr/sbin:/sbin' },
      stdio: ['pipe', 'pipe', 'ignore'], shell: false, windowsHide: true
    })
    let phase = 'challenge'
    let closed = false
    let lines = 0
    const completion = new Promise((resolvePromise, rejectPromise) => {
      const timeout = setTimeout(() => child.kill('SIGTERM'), this.deadlineMs)
      let done = false
      const finish = (error, value) => {
        if (done) return
        done = true
        clearTimeout(timeout)
        if (error) rejectPromise(error)
        else resolvePromise(value)
      }
      child.once('error', () => finish(Error('RECOVERY_HELPER_UNAVAILABLE')))
      child.once('close', code => {
        closed = true
        if (phase === 'success' && code === 0) finish(null, { status: 'RECOVERED' })
        else finish(Error('RECOVERY_NOT_CONFIRMED'))
      })
      const reader = createInterface({ input: child.stdout, crlfDelay: Infinity })
      reader.on('line', async line => {
        try {
          if (++lines > 2 || line.length > 512) throw Error('RECOVERY_HELPER_PROTOCOL')
          const data = JSON.parse(line)
          if (phase === 'challenge') {
            if (data.type !== 'challenge' || !limitedText(data.challenge)) {
              throw Error('RECOVERY_HELPER_PROTOCOL')
            }
            phase = 'authorization'
            // Called by Owner after its session and TOTP step-up checks.
            const authorizationCode = await authorize(data.challenge)
            if (!limitedText(authorizationCode) || closed) throw Error('RECOVERY_HELPER_PROTOCOL')
            phase = 'result'
            child.stdin.end(JSON.stringify({ authorizationCode, newPassword }) + '\n')
          } else if (phase === 'result' && data.type === 'result' && data.success === true) {
            phase = 'success'
          } else throw Error('RECOVERY_HELPER_PROTOCOL')
        } catch {
          child.kill('SIGTERM')
          finish(Error('RECOVERY_NOT_CONFIRMED'))
        }
      })
    })
    return await completion
  }
}
