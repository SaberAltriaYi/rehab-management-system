const configuredOrigin = (import.meta.env.VITE_REHAB_API_ORIGIN || '').trim()

export const API_ORIGIN = configuredOrigin.replace(/\/+$/, '')
export const CAPTCHA_ENABLED = (import.meta.env.VITE_REHAB_CAPTCHA_ENABLED || 'true').toLowerCase() !== 'false'
// Keep patient login closed until the SMS, invitation, token storage and
// enumeration protections pass the release gates in docs/patient-auth-release-gates.md.
export const PATIENT_LOGIN_ENABLED = (import.meta.env.VITE_REHAB_PATIENT_LOGIN_ENABLED || 'false').toLowerCase() === 'true'
