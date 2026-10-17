const configuredOrigin = (import.meta.env.VITE_REHAB_API_ORIGIN || '').trim()

export const API_ORIGIN = configuredOrigin.replace(/\/+$/, '')
export const CAPTCHA_ENABLED = (import.meta.env.VITE_REHAB_CAPTCHA_ENABLED || 'true').toLowerCase() !== 'false'
// Enabled only for the short-lived SMS + clinic invitation flow. Patient sessions
// never persist a refresh token and the server still enforces the binding gate.
export const PATIENT_LOGIN_ENABLED = true
