let challenge = null
let revealTimer = null
const $ = id => document.getElementById(id)
function feedback(message, error = false) {
  const element = $('feedback')
  element.textContent = message
  element.classList.toggle('error', error)
}
async function jsonApi(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    credentials: 'omit',
    cache: 'no-store',
    headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}) }
  })
  const body = await response.json()
  if (!response.ok) throw new Error('恢复操作拒绝：' + String(body.error || response.status))
  return body
}
function clearReveal() {
  clearInterval(revealTimer)
  $('secret').textContent = ''
  $('otpauth-link').textContent = ''
  $('otpauth-link').removeAttribute('href')
  $('result').hidden = true
  $('recovery-form').reset()
  $('recovery-form').querySelector('button').disabled = true
}
async function loadChallenge() {
  try {
    const result = await jsonApi('/owner/v1/mfa/recovery/challenge')
    challenge = result.challenge
  } catch (error) {
    feedback('恢复页面只能从所有者服务本机打开。' + error.message, true)
  }
}
$('recovery-form').addEventListener('submit', event => {
  event.preventDefault()
  void (async () => {
    try {
      if (!challenge) throw Error('恢复挑战尚未准备好，请刷新页面')
      const data = Object.fromEntries(new FormData($('recovery-form')))
      const result = await jsonApi('/owner/v1/mfa/recover', {
        method: 'POST',
        body: JSON.stringify({ challenge, ...data })
      })
      challenge = null
      $('secret').textContent = result.secret
      $('otpauth-link').textContent = result.otpauthUrl
      $('otpauth-link').href = result.otpauthUrl
      $('result').hidden = false
      $('recovery-form').querySelector('button').disabled = true
      let remaining = 60
      $('countdown').textContent = String(remaining)
      revealTimer = setInterval(() => {
        remaining -= 1
        $('countdown').textContent = String(remaining)
        if (remaining <= 0) {
          clearReveal()
          feedback('本次密钥已清除。请重新打开恢复页生成新的配置。')
        }
      }, 1000)
      feedback('新的 TOTP 配置已生成；旧会话和旧验证码已失效。')
    } catch (error) {
      feedback(error.message, true)
    }
  })()
})
$('copy-secret').addEventListener('click', () => {
  void navigator.clipboard.writeText($('secret').textContent).then(() => feedback('密钥已复制到本机剪贴板，请立即导入并清理剪贴板。'))
})
$('copy-uri').addEventListener('click', () => {
  void navigator.clipboard.writeText($('otpauth-link').textContent).then(() => feedback('配置链接已复制到本机剪贴板。'))
})
void loadChallenge()
