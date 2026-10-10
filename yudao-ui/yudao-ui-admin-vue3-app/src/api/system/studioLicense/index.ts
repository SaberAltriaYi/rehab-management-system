import request from '@/config/axios'

/** Display-only snapshot; the Java guard derives it from signed issuer claims. */
export interface StudioLicenseOverview {
  licenseEnforced: boolean
  status: 'ACTIVE' | 'GRACE' | 'NOT_CONFIGURED'
  message?: string
  tenantId?: string
  installationId?: string
  seatLimit?: number | null
  activeStaff?: number
  availableSeats?: number | null
  maxInstallations?: number
  modules?: string[]
  expiresAt?: string
  entitlementExpiresAt?: string
  lastSignedIssueAt?: string
}

export const getStudioLicenseOverview = (): Promise<StudioLicenseOverview> =>
  request.get({ url: '/system/studio-license/overview' })
