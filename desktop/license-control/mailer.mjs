// Optional transactional activation notice, deliberately separate from credentials.
// Network use is opt-in: no provider calls until the operator configures all
// three parameters. Owner DB never stores the mail API key or a password.
const ADDRESS = /^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$/

export function activationMailConfig(env = process.env) {
  const apiKey = String(env.REHAB_OWNER_EMAIL_RESEND_KEY || '')
  const from = String(env.REHAB_OWNER_EMAIL_FROM || '')
  const loginUrl = String(env.REHAB_MAIN_PUBLIC_LOGIN_URL || '')
  let publicLogin = false
  try {
    const url = new URL(loginUrl)
    publicLogin = url.protocol === 'https:' && !url.username && !url.password &&
      !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname) &&
      url.search === '' && url.hash === ''
  } catch { /* invalid URLs are not sendable */ }
  return {
    apiKey, from, loginUrl,
    configured: apiKey.length >= 15 && ADDRESS.test(from) && publicLogin
  }
}

export async function sendActivationNotice(app, config, fetchImpl = fetch) {
  if (!config.configured) throw new Error('EMAIL_NOT_CONFIGURED')
  if (app.status !== 'APPROVED' || app.provisioning_status !== 'ACTIVE' ||
    !Number.isSafeInteger(app.main_tenant_id) || app.main_tenant_id <= 0) {
    throw new Error('MAIN_SYSTEM_NOT_ACTIVE')
  }
  if (typeof app.contact_email !== 'string' || !ADDRESS.test(app.contact_email)) {
    throw new Error('CONTACT_EMAIL_MISSING')
  }
  const organization = app.organization.replace(/[\r\n]/g, ' ').slice(0, 120)
  const content = [
    `您的机构「${organization}」已通过审核，主系统已完成开通。`,
    `登录入口：${config.loginUrl}`,
    '请使用主系统机构名称、已单独安全交付的管理员账号及密码登录。',
    '为保护账户安全，本邮件不包含密码、MFA 密钥或患者信息。',
    '若尚未收到账号凭据，请联系平台管理员；本通知本身不能用于重置密码。'
  ].join('\n\n')
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 8000)
  try {
    const response = await fetchImpl('https://api.resend.com/emails', {
      method: 'POST', redirect: 'error', signal: controller.signal,
      headers: {
        Authorization: `Bearer ${config.apiKey}`,
        'Content-Type': 'application/json',
        'Idempotency-Key': `rehab-owner-activation/${app.id}`
      },
      body: JSON.stringify({
        from: config.from, to: [app.contact_email],
        subject: '康复机构主系统开通通知', text: content
      })
    })
    // Accepted by the provider is not the same as delivered to the inbox.
    if (!response.ok) throw new Error('EMAIL_PROVIDER_REJECTED')
    const data = await response.json().catch(() => ({}))
    if (typeof data.id !== 'string' || data.id.length > 120) {
      throw new Error('EMAIL_PROVIDER_INVALID_RESPONSE')
    }
    return { providerId: data.id }
  } finally {
    clearTimeout(timer)
  }
}
