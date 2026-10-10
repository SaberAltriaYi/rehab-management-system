import { describe, expect, it } from 'vitest'
import {
  deletionConfirmationMatches,
  formatBackupTime,
  importConfirmationMatches,
  isValidAdminCredentialInput,
  isValidTransferPassword,
  licenseAllowsBusiness,
  licenseExpiryNotice,
  licenseLabel,
  redactDiagnosticText,
  serviceBadgeClass
} from './model'

describe('launcher view model', () => {
  it('redacts credentials and mobile numbers from diagnostics', () => {
    const value = 'password=abc123 token:xyz mobile=13800138000'
    const redacted = redactDiagnosticText(value)
    expect(redacted).not.toContain('abc123')
    expect(redacted).not.toContain('xyz')
    expect(redacted).not.toContain('13800138000')
  })

  it('requires the exact destructive confirmation', () => {
    expect(deletionConfirmationMatches('删除所有本地数据')).toBe(true)
    expect(deletionConfirmationMatches('删除数据')).toBe(false)
  })

  it('validates transfer import confirmation and passwords', () => {
    expect(importConfirmationMatches('覆盖导入全部数据')).toBe(true)
    expect(importConfirmationMatches('覆盖导入数据')).toBe(false)
    expect(isValidTransferPassword('correct-horse')).toBe(true)
    expect(isValidTransferPassword('short')).toBe(false)
  })

  it('validates administrator username and 12 to 16 character password', () => {
    expect(isValidAdminCredentialInput('studio_admin', 'SafePassword12')).toBe(true)
    expect(isValidAdminCredentialInput('bad name', 'SafePassword12')).toBe(false)
    expect(isValidAdminCredentialInput('studio_admin', 'too-short')).toBe(false)
    expect(isValidAdminCredentialInput('studio_admin', 'ThisPasswordIsTooLong')).toBe(false)
  })

  it('formats empty backup state and service classes', () => {
    expect(formatBackupTime(null)).toBe('尚未创建')
    expect(serviceBadgeClass('healthy')).toBe('status status--healthy')
  })

  it('renders license statuses and never treats pending/revoked/expired as authorized', () => {
    expect(licenseLabel('UNLICENSED')).toBe('尚未授权')
    expect(licenseLabel('PENDING')).toBe('等待审批')
    expect(licenseLabel('GRACE')).toBe('离线有效期内')
    expect(licenseLabel('REVOKED')).toBe('许可证已撤销')
    expect(licenseAllowsBusiness(null)).toBe(false)
    for (const status of ['UNLICENSED', 'PENDING', 'EXPIRED', 'SUSPENDED', 'REVOKED', 'INVALID'] as const) {
      expect(licenseAllowsBusiness({
        status, detail: '', tenantId: null, installationId: null,
        organization: null, applicationId: null, expiry: null, seatLimit: null,
        modules: [], pendingReplacement: false, publisherConfigured: false,
        localVerificationOnly: true
      })).toBe(false)
    }
    for (const status of ['ACTIVE', 'GRACE'] as const) {
      expect(licenseAllowsBusiness({
        status, detail: '', tenantId: null, installationId: null,
        organization: null, applicationId: null, expiry: null, seatLimit: null,
        modules: [], pendingReplacement: false, publisherConfigured: true,
        localVerificationOnly: true
      })).toBe(true)
    }
  })

  it('warns seven days before expiry and ignores pending approval', () => {
    const nowMs = Date.parse('2026-10-10T05:00:00.000Z')
    const license = {
      status: 'ACTIVE' as const, detail: '', tenantId: null,
      installationId: null, organization: null, applicationId: null,
      expiry: new Date(nowMs + 6 * 60 * 60 * 1000).toISOString(),
      seatLimit: 6, modules: ['rehab'], pendingReplacement: false,
      publisherConfigured: true, localVerificationOnly: true
    }
    expect(licenseExpiryNotice(license, nowMs)).toContain('6 小时')
    expect(licenseExpiryNotice({ ...license, status: 'PENDING' }, nowMs)).toBe(null)
    expect(licenseExpiryNotice({
      ...license, expiry: new Date(nowMs + 8 * 86_400_000).toISOString()
    }, nowMs)).toBe(null)
  })
})
