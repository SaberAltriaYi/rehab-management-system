import { describe, expect, it } from 'vitest'
import {
  deletionConfirmationMatches,
  formatBackupTime,
  importConfirmationMatches,
  isValidAdminCredentialInput,
  isValidTransferPassword,
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
})
