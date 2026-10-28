#!/usr/bin/env node
// Independently operated owner-only service, not part of the studio launcher.
// No default admin, no default signing key, no remotely callable bootstrap.
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { stdin, stdout } from 'node:process'
import { OwnerLicenseStore } from './store.mjs'
import { SecretVault } from './security.mjs'
import { OwnerLicensingService } from './service.mjs'
import { createOwnerControlServer } from './http.mjs'

function requiredEnv(name) {
  const value = process.env[name]
  if (!value) throw new Error(`Required operator-supplied environment variable missing: ${name}`)
  return value
}

function setup() {
  const encoded = requiredEnv('REHAB_OWNER_VAULT_KEY_B64')
  if (!/^[A-Za-z0-9_-]{43}$/.test(encoded)) {
    throw new Error('Vault key must be 32 cryptographically random bytes encoded as base64url')
  }
  const key = Buffer.from(encoded, 'base64url')
  if (key.length !== 32 || key.toString('base64url') !== encoded) {
    throw new Error('Invalid owner vault encryption key')
  }
  const file = resolve(requiredEnv('REHAB_OWNER_DB_PATH'))
  const store = new OwnerLicenseStore(file)
  return { store, service: new OwnerLicensingService(store, new SecretVault(key)) }
}

function readHidden(label) {
  if (!stdin.isTTY || typeof stdin.setRawMode !== 'function') {
    throw new Error('Bootstrap requires an interactive local terminal; password must never be passed as CLI argument')
  }
  return new Promise((resolveInput, reject) => {
    let input = ''
    stdout.write(label)
    stdin.setRawMode(true)
    stdin.resume()
    stdin.setEncoding('utf8')
    const cleanup = () => {
      stdin.removeListener('data', onKey)
      stdin.setRawMode(false)
      stdin.pause()
      stdout.write('\n')
    }
    const onKey = chunk => {
      for (const char of chunk) {
        if (char === '\u0003') {
          cleanup()
          return reject(new Error('Bootstrap cancelled'))
        }
        if (char === '\r' || char === '\n') {
          cleanup()
          return resolveInput(input)
        }
        if (char === '\u007f' || char === '\b') {
          input = input.slice(0, -1)
        } else if (char >= ' ' && input.length < 256) {
          input += char
        }
      }
    }
    stdin.on('data', onKey)
  })
}

async function main() {
  const mode = process.argv[2]
  if (!['bootstrap', 'serve'].includes(mode) || process.argv.length !== 3) {
    throw new Error('Usage: node desktop/license-control/cli.mjs bootstrap|serve')
  }
  const { store, service } = setup()
  if (mode === 'bootstrap') {
    if (service.hasOwner) throw new Error('Owner already provisioned; refusing re-bootstrap')
    const username = requiredEnv('REHAB_OWNER_USERNAME')
    const password = await readHidden('Choose owner password (min 16 characters, hidden): ')
    const again = await readHidden('Confirm owner password (hidden): ')
    if (password !== again) throw new Error('Password confirmation did not match')
    const { mfaSecret } = await service.provisionOwner({
      username, password, nowMs: Date.now()
    })
    // This is the ONLY intentional exposure of the MFA enrollment secret.
    // The owner must immediately add it to a TOTP authenticator and store
    // recovery credentials securely. It is never printed during server mode.
    stdout.write('OWNER ACCOUNT CREATED. TOTP enrollment secret (SHOW ONCE):\n')
    stdout.write(mfaSecret + '\n')
    stdout.write('Provision the TOTP authenticator before starting the server.\n')
    store.close()
    return
  }
  if (!service.hasOwner) {
    store.close()
    throw new Error('Owner MFA is not provisioned; run bootstrap locally first')
  }
  const cert = readFileSync(requiredEnv('REHAB_OWNER_TLS_CERT'))
  const key = readFileSync(requiredEnv('REHAB_OWNER_TLS_KEY'))
  const port = Number(process.env.REHAB_OWNER_PORT ?? '9444')
  if (!Number.isSafeInteger(port) || port < 1024 || port > 65535) {
    store.close()
    throw new Error('Owner port must be in 1024..65535')
  }
  const server = createOwnerControlServer(service, {
    tlsOptions: { cert, key, minVersion: 'TLSv1.2' },
    // Explicit HTTPS loopback default for the isolated same-origin L4 owner
    // console. Reverse-proxy deployments must set their own exact HTTPS origin.
    permittedBrowserOrigin: process.env.REHAB_OWNER_BROWSER_ORIGIN
      ?.split(',').map(origin => origin.trim()).filter(Boolean)
      .filter((origin, index, origins) => origins.indexOf(origin) === index)
      || `https://127.0.0.1:${port}`
  })
  server.requestTimeout = 10_000
  server.headersTimeout = 15_000
  server.listen(port, '127.0.0.1', () => {
    stdout.write('Owner licensing TLS control service bound to loopback only; port=' + port + '\n')
  })
  const shutdown = () => server.close(() => {
    store.close()
    process.exitCode = 0
  })
  process.once('SIGINT', shutdown)
  process.once('SIGTERM', shutdown)
}

main().catch(error => {
  // Never print stack traces, environment values, passphrases or private keys.
  process.stderr.write('Owner licensing startup failed: ' + error.message + '\n')
  process.exitCode = 1
})
