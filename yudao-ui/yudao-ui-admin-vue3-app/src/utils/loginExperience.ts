/**
 * A single-studio deployment can hide tenant selection, but MUST still send
 * the resolved tenant ID to Yudao. Misconfigured environments fall back to
 * the regular editable tenant field, never to an anonymous/default tenant.
 */
export function getFixedLoginTenantName(
  tenantEnabled: string | undefined,
  fixedTenantEnabled: string | undefined,
  configuredTenantName: string | undefined
): string | null {
  if (tenantEnabled !== 'true' || fixedTenantEnabled !== 'true') return null
  return configuredTenantName?.trim() || null
}

/**
 * Fixed studio configuration wins over persisted user input. When tenant mode
 * is disabled, ignore old tenant cache rather than silently reusing it.
 */
export function getLoginTenantName(
  tenantEnabled: string | undefined,
  fixedTenantEnabled: string | undefined,
  configuredTenantName: string | undefined,
  rememberedTenantName: string | undefined
): string {
  if (tenantEnabled !== 'true') return ''
  return (
    getFixedLoginTenantName(tenantEnabled, fixedTenantEnabled, configuredTenantName) ||
    rememberedTenantName?.trim() ||
    configuredTenantName?.trim() ||
    ''
  )
}

/** Internal employees are created through Yudao user management. */
export function allowSelfRegistration(flag: string | undefined): boolean {
  // Closed by default; legacy public registration requires explicit opt-in.
  return flag === 'true'
}
