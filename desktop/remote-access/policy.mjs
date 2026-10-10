/**
 * L6 private-tailnet deployment preflight. This tool never opens a listener,
 * invokes VPN, or sends patient data anywhere. Real SSO MFA, device posture,
 * ACL and revocation MUST be configured/verified in the network provider.
 */
import { isIP } from 'node:net'

const FORBIDDEN = /(?:funnel|0\.0\.0\.0|\*:443|--accept-routes|insecure-skip-verify|--advertise-exit-node)/i
export function validateL6Policy(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) {
    return { ready: false, blockers: ['REMOTE_POLICY_MISSING'] }
  }
  const blockers = []
  if (input.mode !== 'tailscale-private-serve' || input.publicInternet !== false) {
    blockers.push('PUBLIC_GATEWAY_PROHIBITED')
  }
  if (input.organizationMfaEnforced !== true ||
      input.requireDeviceApproval !== true || input.requireDevicePosture !== true ||
      input.revokeLostDevices !== true || input.explicitStaffAcl !== true) {
    blockers.push('SSO_MFA_POSTURE_ACL_REVOCATION_MISSING')
  }
  if (input.patientDataToOwnerControlPlane !== false) blockers.push('PATIENT_TO_OWNER_DATA_LEAKAGE')
  if (input.requireJavaTenantRbac !== true || input.requireSignedStudioLicense !== true) {
    blockers.push('BACKEND_IDENTITY_AND_LICENSE_MISSING')
  }
  if (input.auditGateways !== true || input.shortBrowserSessions !== true) {
    blockers.push('GATEWAY_AUDIT_OR_SESSION_CONTROL_MISSING')
  }
  if (input.bindAddress !== '127.0.0.1' || input.localHttpsPort !== 8443) {
    blockers.push('UNEXPECTED_LOCAL_BIND')
  }
  if (!Array.isArray(input.allowedRemoteGroups) || input.allowedRemoteGroups.length < 1 ||
      input.allowedRemoteGroups.some(g => !/^group:[a-z0-9_-]{2,40}$/.test(g))) {
    blockers.push('STAFF_GROUPS_MISSING')
  }
  if (input.ownerGatewaySharedWithPatient !== false || input.exportDatabasePort !== false ||
      input.exportRedisPort !== false || input.allowUnauthenticatedFallback !== false) {
    blockers.push('UNSAFE_AUTHORIZATION_BYPASS')
  }
  return { ready: blockers.length === 0, blockers }
}
export function isTailnetIPv4(ip) {
  if (typeof ip !== 'string' || isIP(ip) !== 4) return false
  const [a, b] = ip.split('.').map(Number)
  return a === 100 && b >= 64 && b <= 127
}
export function validateTailnetStatus(snapshot, policy) {
  const errors = [...validateL6Policy(policy).blockers]
  if (snapshot?.BackendState !== 'Running' || snapshot?.Self?.Online !== true) {
    errors.push('TAILNET_CLIENT_NOT_RUNNING')
  }
  if (!Array.isArray(snapshot?.TailscaleIPs) ||
      !snapshot.TailscaleIPs.some(isTailnetIPv4)) errors.push('NO_TAILNET_DEVICE_ADDRESS')
  if (snapshot?.Health && Object.keys(snapshot.Health).length > 0) {
    errors.push('TAILNET_CLIENT_UNHEALTHY')
  }
  return { ready: errors.length === 0, blockers: [...new Set(errors)] }
}
export function validateServeSnapshot(snapshot) {
  if (!snapshot || typeof snapshot !== 'object' || Array.isArray(snapshot)) {
    return { safe: false, blockers: ['SERVE_STATUS_NOT_VERIFIED'] }
  }
  const text = JSON.stringify(snapshot)
  const blockers = []
  if (FORBIDDEN.test(text) || /\b(?:mysql|redis|3306|6379|48080)\b/i.test(text)) {
    blockers.push('DIRECT_SERVICE_OR_FUNNEL_EXPOSURE')
  }
  if (text.includes('https+insecure://') &&
      !text.includes('https+insecure://127.0.0.1:8443')) {
    blockers.push('UNEXPECTED_TLS_BYPASS_TARGET')
  }
  return { safe: blockers.length === 0, blockers }
}
export function validatePublicUrl(value) {
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:' || !url.hostname.endsWith('.ts.net') ||
        url.username || url.password || url.search || url.hash || url.port ||
        url.pathname !== '/') return false
    const labels = url.hostname.split('.')
    return labels.length >= 4 && labels.every(label => /^[a-z0-9-]{1,63}$/.test(label))
  } catch { return false }
}
