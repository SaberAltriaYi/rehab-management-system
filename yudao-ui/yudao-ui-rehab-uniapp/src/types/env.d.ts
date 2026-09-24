/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_REHAB_API_ORIGIN?: string
  readonly VITE_REHAB_CAPTCHA_ENABLED?: string
  readonly VITE_REHAB_PATIENT_LOGIN_ENABLED?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
