const configuredOrigin = (import.meta.env.VITE_REHAB_API_ORIGIN || '').trim()

export const API_ORIGIN = configuredOrigin.replace(/\/+$/, '')
export const CAPTCHA_ENABLED = (import.meta.env.VITE_REHAB_CAPTCHA_ENABLED || 'true').toLowerCase() !== 'false'
// Hard-disabled until clinic-verified invitations, a second phone proof and
// platform-appropriate credential storage are delivered. No env flag may open it.
export const PATIENT_LOGIN_ENABLED = false
