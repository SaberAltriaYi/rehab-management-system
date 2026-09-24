const configuredOrigin = (import.meta.env.VITE_REHAB_API_ORIGIN || '').trim()

export const API_ORIGIN = configuredOrigin.replace(/\/+$/, '')
export const CAPTCHA_ENABLED = (import.meta.env.VITE_REHAB_CAPTCHA_ENABLED || 'true').toLowerCase() !== 'false'
// Deliberately opt-in: the current patient endpoint uses phone + patient number as the only login proof.
export const PATIENT_LOGIN_ENABLED = (import.meta.env.VITE_REHAB_PATIENT_LOGIN_ENABLED || 'false').toLowerCase() === 'true'
