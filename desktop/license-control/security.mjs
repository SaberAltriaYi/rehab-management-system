// Builtin-only password MFA and encrypted signing-key vault.
import {
  createCipheriv, createDecipheriv, createHmac, randomBytes,
  scrypt as deriveAsync, timingSafeEqual
} from 'node:crypto'
import { promisify } from 'node:util'

const derive = promisify(deriveAsync)
const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
const MIN_PASSWORD_LENGTH = 16

function invalid(message) { throw new Error(message) }

export class SecretVault {
  constructor(masterKey) {
    if (!Buffer.isBuffer(masterKey) || masterKey.length !== 32) {
      invalid('Owner vault requires a 32-byte separately provisioned encryption key')
    }
    this.masterKey = Buffer.from(masterKey)
  }

  encrypt(plain, aad) {
    if (typeof plain !== 'string' || typeof aad !== 'string' || !aad) invalid('Invalid vault context')
    const iv = randomBytes(12)
    const cipher = createCipheriv('aes-256-gcm', this.masterKey, iv)
    cipher.setAAD(Buffer.from(aad, 'utf8'))
    const ciphertext = Buffer.concat([cipher.update(plain, 'utf8'), cipher.final()])
    return ['v1', iv.toString('base64url'), ciphertext.toString('base64url'),
      cipher.getAuthTag().toString('base64url')].join('.')
  }

  decrypt(serialized, aad) {
    if (typeof serialized !== 'string' || typeof aad !== 'string') invalid('Invalid vault context')
    const parts = serialized.split('.')
    if (parts.length !== 4 || parts[0] !== 'v1') invalid('Unsupported encrypted vault record')
    const iv = Buffer.from(parts[1], 'base64url')
    const tag = Buffer.from(parts[3], 'base64url')
    if (iv.length !== 12 || tag.length !== 16) invalid('Corrupt vault record')
    const decipher = createDecipheriv('aes-256-gcm', this.masterKey, iv)
    decipher.setAAD(Buffer.from(aad, 'utf8'))
    decipher.setAuthTag(tag)
    return Buffer.concat([
      decipher.update(Buffer.from(parts[2], 'base64url')), decipher.final()
    ]).toString('utf8')
  }
}

export async function hashOwnerPassword(password) {
  if (typeof password !== 'string' || password.length < MIN_PASSWORD_LENGTH ||
    password.length > 256) invalid('Owner password must be 16–256 characters')
  const salt = randomBytes(16)
  const digest = await derive(password, salt, 64, { N: 32768, r: 8, p: 1, maxmem: 64 * 1024 * 1024 })
  return ['scrypt-v1', salt.toString('base64url'), digest.toString('base64url')].join('.')
}

export async function checkOwnerPassword(password, stored) {
  if (typeof password !== 'string' || typeof stored !== 'string') return false
  const parts = stored.split('.')
  if (parts.length !== 3 || parts[0] !== 'scrypt-v1') return false
  const salt = Buffer.from(parts[1], 'base64url')
  const expected = Buffer.from(parts[2], 'base64url')
  if (salt.length !== 16 || expected.length !== 64 || password.length > 256) return false
  const digest = await derive(password, salt, 64, { N: 32768, r: 8, p: 1, maxmem: 64 * 1024 * 1024 })
  return timingSafeEqual(digest, expected)
}

export function generateTotpSecret() {
  const bytes = randomBytes(20)
  let bits = 0
  let buffer = 0
  let out = ''
  for (const byte of bytes) {
    buffer = (buffer << 8) | byte
    bits += 8
    while (bits >= 5) {
      bits -= 5
      out += BASE32[(buffer >>> bits) & 31]
    }
  }
  if (bits) out += BASE32[(buffer << (5 - bits)) & 31]
  return out
}

function decodeBase32(value) {
  if (typeof value !== 'string' || !/^[A-Z2-7]{32}$/.test(value)) invalid('Invalid TOTP seed')
  let acc = 0
  let bits = 0
  const bytes = []
  for (const char of value) {
    acc = (acc << 5) | BASE32.indexOf(char)
    bits += 5
    if (bits >= 8) {
      bits -= 8
      bytes.push((acc >>> bits) & 255)
    }
  }
  return Buffer.from(bytes)
}

export function totpAt(secret, step) {
  if (!Number.isSafeInteger(step) || step < 0) invalid('Invalid TOTP step')
  const counter = Buffer.alloc(8)
  counter.writeBigUInt64BE(BigInt(step))
  const mac = createHmac('sha1', decodeBase32(secret)).update(counter).digest()
  const offset = mac[19] & 0x0f
  const number = (mac.readUInt32BE(offset) & 0x7fffffff) % 1_000_000
  return String(number).padStart(6, '0')
}

export function verifyTotp(secret, code, nowMs, lastUsedStep) {
  if (typeof code !== 'string' || !/^\d{6}$/.test(code) ||
    !Number.isSafeInteger(nowMs) || nowMs < 0 ||
    !Number.isSafeInteger(lastUsedStep) || lastUsedStep < -1) return null
  const step = Math.floor(nowMs / 30_000)
  const supplied = Buffer.from(code, 'ascii')
  for (let i = -1; i <= 1; i++) {
    if (step + i <= lastUsedStep || step + i < 0) continue
    const expected = Buffer.from(totpAt(secret, step + i), 'ascii')
    if (timingSafeEqual(supplied, expected)) return step + i
  }
  return null
}
