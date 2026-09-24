import CryptoJS from 'crypto-js'

export function encryptCaptcha(value: string, secretKey: string): string {
  const key = CryptoJS.enc.Utf8.parse(secretKey)
  return CryptoJS.AES.encrypt(value, key, {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7,
  }).toString()
}
