import { invoke } from '@tauri-apps/api/core'

type RecoveryStatus = { servicesHealthy: boolean }

const root = document.querySelector<HTMLElement>('#app')
if (!root) throw new Error('恢复工具页面缺失')

root.innerHTML = `
  <style>
    :root { font-family: system-ui, -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif; color: #20343a; background: #f4f6f7; font-size: 15px; }
    * { box-sizing: border-box; }
    body { margin: 0; }
    .page { max-width: 620px; margin: 0 auto; padding: 28px 28px 45px; }
    h1 { font-size: 25px; margin: 0 0 8px; }
    h2 { font-size: 17px; margin: 22px 0 10px; }
    p { line-height: 1.6; }
    .subtle { color: #657782; font-size: 13px; }
    .card { background: white; border: 1px solid #e2e8eb; border-radius: 14px; padding: 18px; margin: 16px 0; }
    label { display: block; margin: 12px 0 5px; font-weight: 600; font-size: 13px; }
    input { display: block; width: 100%; min-height: 39px; padding: 9px; border: 1px solid #afbdc5; border-radius: 7px; background: white; color: #182f35; }
    input[readonly] { background: #f2f5f6; }
    button { border: 0; padding: 11px 17px; background: #14655b; color: white; font-weight: 600; border-radius: 7px; cursor: pointer; }
    button:disabled { opacity: .45; cursor: not-allowed; }
    button.secondary { background: #ebf2f0; color: #14594f; border: 1px solid #b6d2c9; }
    a { color: #096b6a; overflow-wrap: anywhere; }
    .actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 16px; }
    #message { white-space: pre-wrap; min-height: 24px; font-size: 13px; color: #395767; }
    .warning { color: #7d4c15; }
    .success { color: #176047 !important; }
    .error { color: #a72828 !important; }
  </style>
  <div class="page">
    <h1>Core admin 受控恢复</h1>
    <p class="subtle">独立维护程序 · 仅限当前 Mac 单工作室 · Owner MFA 审批</p>
    <div class="card">
      <p class="warning">本程序不是康复临床应用。只能恢复现有租户 1、用户 ID 1 的内置 admin 密码；不会创建账号、授予角色、修改许可证或安装数据库迁移。</p>
      <p id="health" role="status">正在确认本机服务状态…</p>
      <button class="secondary" id="refresh" type="button">重新检查健康状态</button>
    </div>
    <div class="card">
      <h2>步骤 1 · 生成设备挑战码</h2>
      <p class="subtle">服务必须全部健康。每次挑战码在当前程序内存中保存 10 分钟，不保存到磁盘。</p>
      <button id="begin" type="button" disabled>生成新的挑战码</button>
      <label for="challenge">本机设备挑战码（复制到 Owner 控制台）</label>
      <input id="challenge" readonly autocomplete="off" />
      <h2>步骤 2 · Owner 单独完成 MFA 审批</h2>
      <p>请在浏览器手动打开 <a href="https://127.0.0.1:9444/owner/console" target="_blank" rel="noopener noreferrer">https://127.0.0.1:9444/owner/console</a>。正常登录 Owner、输入最新 TOTP 二次验证、填写设备挑战码，领取 120 秒的一次性授权码。</p>
      <h2>步骤 3 · 在本机输入新密码</h2>
      <label for="approval">Owner 一次性授权码</label>
      <input id="approval" maxlength="43" minlength="43" autocomplete="off" spellcheck="false" />
      <label for="password">新 Core admin 密码（12–16 位）</label>
      <input id="password" type="password" minlength="12" maxlength="16" autocomplete="new-password" />
      <label for="confirmation">确认新密码</label>
      <input id="confirmation" type="password" minlength="12" maxlength="16" autocomplete="new-password" />
      <p class="subtle">新密码仅发送给本机启动器进行 bcrypt 更新；不会发送给 Owner、远端服务、聊天或日志。恢复程序先验证一次性 Owner 授权，再创建加密备份、仅修改已有 admin 密码并撤销旧会话。</p>
      <div class="actions">
        <button id="apply" type="button">验证授权并恢复 admin 密码</button>
      </div>
      <p class="subtle" id="action-guide" role="status" aria-live="polite">点击按钮将检查所有前置条件；缺少条件时会显示具体提示，不会修改正式账号。</p>
      <p id="message" role="alert" aria-live="polite"></p>
    </div>
  </div>
`

const element = <T extends HTMLElement>(id: string): T => {
  const value = document.getElementById(id)
  if (!value) throw new Error('恢复工具控件缺失')
  return value as T
}
const health = element<HTMLParagraphElement>('health')
const beginButton = element<HTMLButtonElement>('begin')
const applyButton = element<HTMLButtonElement>('apply')
const challenge = element<HTMLInputElement>('challenge')
const approval = element<HTMLInputElement>('approval')
const password = element<HTMLInputElement>('password')
const confirmation = element<HTMLInputElement>('confirmation')
const message = element<HTMLParagraphElement>('message')
const actionGuide = element<HTMLParagraphElement>('action-guide')
let working = false
let healthy = false

function report(value: string, style: 'error' | 'success' | '' = '') {
  message.textContent = value
  message.className = style
}

function updateButtons() {
  // Never silently disable the action when prerequisites are missing:
  // all rejected submissions must tell the operator what to correct.
  // Native Rust still enforces healthy services and pending MFA challenge.
  beginButton.disabled = working
  applyButton.disabled = working
  actionGuide.textContent = working
    ? '操作进行中，请勿重复点击。'
    : !healthy
      ? '本机服务状态尚未验证。点击按钮可查看具体前置条件。'
      : !/^[A-Za-z0-9_-]{43}$/.test(challenge.value)
        ? '请先生成此程序的设备挑战码，再使用同一挑战码完成 Owner MFA 审批。'
        : '设备挑战码已生成。请填写对应的一次性授权码和两次新密码。'
}

async function refreshStatus() {
  working = true
  updateButtons()
  try {
    const status = await invoke<RecoveryStatus>('recovery_maintenance_status')
    healthy = status.servicesHealthy === true
    health.textContent = healthy ? '本机 Docker/Core 所需服务正常' : '正式服务未全部健康，禁止恢复'
  } catch {
    healthy = false
    health.textContent = '无法读取本机服务状态，请核查原安装及配置'
  } finally {
    working = false
    updateButtons()
  }
}

element<HTMLButtonElement>('refresh').addEventListener('click', () => void refreshStatus())
beginButton.addEventListener('click', async () => {
  if (!healthy) {
    report('本机服务健康检查尚未通过，请点击“重新检查健康状态”；服务未全部健康时不会生成挑战码。', 'error')
    return
  }
  working = true
  updateButtons()
  report('')
  challenge.value = ''
  approval.value = ''
  try {
    challenge.value = await invoke<string>('begin_admin_recovery')
    report('挑战码已生成。请复制到 Owner 控制台并按步骤完成 MFA 二次核验。')
  } catch {
    report('生成挑战码失败。请检查 Core 四项服务健康、设备归属和本机恢复环境。', 'error')
  } finally {
    working = false
    updateButtons()
  }
})

applyButton.addEventListener('click', async () => {
  if (!healthy) {
    report('无法恢复：本机 Docker/Core 服务健康检查未通过。请先点击“重新检查健康状态”。', 'error')
    return
  }
  if (!/^[A-Za-z0-9_-]{43}$/.test(challenge.value)) {
    report('无法恢复：请先在本维护工具点击“生成新的挑战码”，再使用该挑战码重新进行 Owner MFA 审批。关闭或重启维护工具后，旧挑战码不可继续使用。', 'error')
    return
  }
  if (!/^[A-Za-z0-9_-]{43}$/.test(approval.value.trim())) {
    report('请输入完整的 43 位 Owner 一次性授权码。', 'error')
    return
  }
  if (password.value.length < 12 || password.value.length > 16 ||
      password.value !== confirmation.value) {
    report('新密码须为 12–16 位，且两次输入完全一致。', 'error')
    return
  }
  if (!window.confirm('确认使用当前 Owner MFA 授权恢复正式 Core admin？程序将在更新前创建加密备份，成功后旧登录会话失效。')) return
  working = true
  updateButtons()
  report('正在本机验证 Owner 授权并执行加密备份与账号恢复…')
  try {
    await invoke<void>('recovery_maintenance_apply', {
      authorizationCode: approval.value.trim(),
      newPassword: password.value
    })
    challenge.value = ''
    report('恢复操作返回成功。请使用正常 Core 登录页面验证新密码、旧会话失效及 Owner 审计记录。', 'success')
  } catch {
    report('恢复操作未确认成功。请先核对 Owner 审计和数据库状态，切勿立即重复操作。授权可能已被消耗。', 'error')
  } finally {
    // Keep no credentials or approval in DOM after the request settles.
    approval.value = ''
    password.value = ''
    confirmation.value = ''
    working = false
    updateButtons()
  }
})

void refreshStatus()
