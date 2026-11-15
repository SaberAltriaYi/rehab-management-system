import { invoke } from '@tauri-apps/api/core'
import { open, save } from '@tauri-apps/plugin-dialog'
import './styles.css'
import {
  EMPTY_OVERVIEW,
  type LauncherOverview,
  type LauncherSettings,
  deletionConfirmationMatches,
  formatBackupTime,
  importConfirmationMatches,
  isValidAdminCredentialInput,
  isValidTransferPassword,
  licenseLabel,
  licenseEnrollmentReady,
  licenseOnboardingStep,
  licenseFirstRunGuidance,
  licenseAllowsBusiness,
  licenseExpiryNotice,
  licenseNeedsOnlineRenewal,
  serviceBadgeClass
} from './model'

const appElement = document.querySelector<HTMLElement>('#app')
if (!appElement) throw new Error('启动器根节点不存在')
const app: HTMLElement = appElement

let overview: LauncherOverview = structuredClone(EMPTY_OVERVIEW)
let settings: LauncherSettings = {
  bindAddress: '127.0.0.1',
  httpPort: 8080,
  httpsPort: 8443,
  lanEnabled: false
}
let busy = false
let logText = ''
let selectedImportPath = ''
let licenseError = ''
// Null means auto-expand for a not-yet-licensed user; clicks override across 5s refreshes.
let licenseOnboardingPreference: boolean | null = null

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

function render(): void {
  const enrollmentReady = licenseEnrollmentReady(overview.license)
  const onboardingStep = licenseOnboardingStep(overview.license)
  const licenseOnboardingOpen = licenseOnboardingPreference ?? (onboardingStep > 0 && onboardingStep < 5)
  const onboardingSteps = [
    ['获取可信安装包', '由软件所有者提供正式签名版本，预置可信发行公钥及可访问的 HTTPS Owner 授权地址。'],
    ['登记这台设备', '填写工作室名称，在本机生成独立安装身份与私钥。私钥不得复制给软件所有者。'],
    ['提交授权申请', '在线提交后记录申请编号；提交不等于已批准。'],
    ['Owner 审核与领证', '所有者独立登录并完成 MFA、机构与套餐审核；批准后点击“在线获取或续签”。'],
    ['验证后进入系统', '验证本机签名租约和 Core 服务端授权均有效，再启动业务并使用管理员账户登录。']
  ] as const
  const serviceCards = overview.services
    .map(
      (service) => `
        <article class="service-card">
          <div>
            <span class="service-name">${escapeHtml(service.label)}</span>
            <p>${escapeHtml(service.detail)}</p>
          </div>
          <span class="${serviceBadgeClass(service.state)}">${escapeHtml(service.state)}</span>
        </article>
      `
    )
    .join('')

  app.innerHTML = `
    <section class="shell">
      <header class="hero">
        <div>
          <p class="eyebrow">运动康复评估与业务管理系统</p>
          <h1>康复管理系统启动器</h1>
          <p class="subtitle">本机安全运行 · Docker Compose 服务管理</p>
        </div>
        <span class="version">V${escapeHtml(overview.appVersion)}</span>
      </header>

      <section class="docker-panel">
        <div>
          <span class="${serviceBadgeClass(overview.dockerState)}">Docker</span>
          <strong>${escapeHtml(overview.dockerDetail)}</strong>
        </div>
        ${overview.operation ? `<p class="operation">${escapeHtml(overview.operation)}</p>` : ''}
      </section>

      ${overview.lastError || licenseError ? `<aside class="error"><strong>最近错误</strong><p>${escapeHtml(overview.lastError || licenseError)}</p><small>请检查所有者审批、可信公钥、网络或签名许可；勿发送安装私钥。</small></aside>` : ''}
      ${overview.firstLoginPassword ? `<aside class="first-login"><strong>首次登录：租户“工作室内部”，账号 admin</strong><code>${escapeHtml(overview.firstLoginPassword)}</code><p>请先保存临时密码并登录后立即修改。此密码不会写入日志或诊断信息。</p><button data-action="ack-password" class="secondary">我已保存，停止显示</button></aside>` : ''}

      <section class="license-panel">
        <header>
          <strong>工作室授权与激活</strong>
          <span class="license-state">${overview.license ? escapeHtml(licenseLabel(overview.license.status)) : '尚未检测'}</span>
        </header>
        <p>${escapeHtml(overview.license?.detail ?? '读取安装许可中')}</p>
        ${licenseExpiryNotice(overview.license) ?
          `<p class="warning-text">${escapeHtml(licenseExpiryNotice(overview.license) ?? '')}</p>` : ''}
        <p class="license-hint">${overview.releaseRequiresLicense
          ? '正式强制授权构建：业务服务还必须验证相同签名许可证；未激活不得启动。'
          : '当前为开发/兼容构建，未启用正式业务授权强制门禁；本机校验通过不代表生产服务已获准使用。'}</p>
        ${overview.license && !enrollmentReady
          ? '<p class="warning-text">当前安装包未配置完整可信授权渠道，不能登记或提交真实机构申请；请由软件所有者提供包含可信公钥和HTTPS授权端点的正式签名发行包。不要导入未知第三方公钥。</p>'
          : ''}
        <div class="license-next-action" role="status" aria-live="polite">
          <strong>${onboardingStep ? `当前步骤 ${onboardingStep}/5：${escapeHtml(onboardingSteps[onboardingStep - 1]?.[0] ?? '核查授权状态')}` : '正在读取授权状态'}</strong>
          <p>${escapeHtml(licenseFirstRunGuidance(overview.license))}</p>
        </div>
        <button type="button" class="license-help-toggle secondary" data-action="license-help"
          aria-expanded="${licenseOnboardingOpen}" aria-controls="license-onboarding-steps">
          ${licenseOnboardingOpen ? '收起首次授权步骤' : '查看首次授权步骤'}
        </button>
        ${licenseOnboardingOpen ? `<section class="license-onboarding" id="license-onboarding-steps">
          <ol class="license-step-list">
            ${onboardingSteps.map((step, index) => {
              const stepNumber = index + 1
              const state = stepNumber < onboardingStep ? 'done' : stepNumber === onboardingStep ? 'current' : 'pending'
              return `<li class="license-step--${state}" ${state === 'current' ? 'aria-current="step"' : ''}>
                <strong>${escapeHtml(step[0])}</strong><span>${escapeHtml(step[1])}</span>
              </li>`
            }).join('')}
          </ol>
          <p>尚未获得发行包时，请联系软件所有者；不要提供本机私钥、Owner密码或TOTP。</p>
        </section>` : ''}
        <dl class="license-fields">
          <div><dt>工作室</dt><dd>${escapeHtml(overview.license?.organization ?? '未登记')}</dd></div>
          <div><dt>租户编号</dt><dd>${escapeHtml(overview.license?.tenantId ?? '未生成')}</dd></div>
          <div><dt>本机实例</dt><dd>${escapeHtml(overview.license?.installationId ?? '未生成')}</dd></div>
          <div><dt>申请编号</dt><dd>${escapeHtml(overview.license?.applicationId ?? '未提交')}</dd></div>
          <div><dt>本次签名租约截至（最长72小时）</dt><dd>${escapeHtml(formatBackupTime(overview.license?.expiry ?? null))}</dd></div>
          <div><dt>工作室合同到期</dt><dd>${escapeHtml(formatBackupTime(overview.license?.entitlementExpiry ?? null))}</dd></div>
          <div><dt>员工席位</dt><dd>${overview.license?.status === 'ACTIVE' || overview.license?.status === 'GRACE'
            ? overview.license.seatLimit === null ? '按许可：无限' : String(overview.license.seatLimit)
            : '未授权'}</dd></div>
        </dl>
        <div class="actions license-actions">
          <button data-action="license-begin" title="${escapeHtml(enrollmentReady ? '生成本机安装身份' : licenseFirstRunGuidance(overview.license))}" ${busy || !!overview.license?.tenantId || !enrollmentReady ? 'disabled' : ''}>首次登记安装身份</button>
          <button data-action="license-submit" ${busy || !enrollmentReady || !overview.license?.tenantId || !overview.license?.installationId || !!overview.license?.applicationId || !!overview.license?.pendingReplacement ? 'disabled' : ''}>在线提交授权申请</button>
          <button data-action="license-renew" ${busy || !enrollmentReady || !overview.license?.tenantId || !overview.license?.installationId || !overview.license.applicationId ? 'disabled' : ''}>在线获取或续签</button>
          <button data-action="license-check" ${busy || !enrollmentReady || !overview.license?.tenantId || !overview.license?.installationId || !overview.license.applicationId ? 'disabled' : ''}>刷新撤销状态</button>
          <button data-action="license-import" ${busy || !overview.license?.publisherConfigured || !overview.license?.tenantId || !overview.license?.installationId ? 'disabled' : ''}>导入离线许可证</button>
          <button data-action="license-replace" ${busy || !!overview.license?.tenantId || !enrollmentReady ? 'disabled' : ''}>新设备换机申请</button>
        </div>
        <p class="license-hint">离线许可证必须由软件所有者签发，最长有效 72 小时。换机需要所有者人工确认，复制文件或自行重建私钥无法转移授权。</p>
      </section>

      <section class="services">${serviceCards}</section>

      <section class="details">
        <dl>
          <div><dt>访问地址</dt><dd>${escapeHtml(overview.accessUrl)}</dd></div>
          <div><dt>数据目录</dt><dd>${escapeHtml(overview.dataDirectory)}</dd></div>
          <div><dt>最近备份</dt><dd>${escapeHtml(formatBackupTime(overview.lastBackupAt))}</dd></div>
        </dl>
      </section>

      <section class="actions primary-actions">
        <button data-action="start" ${busy ? 'disabled' : ''}>启动服务</button>
        <button data-action="stop" class="secondary" ${busy ? 'disabled' : ''}>停止服务</button>
        <button data-action="restart" class="secondary" ${busy ? 'disabled' : ''}>重启服务</button>
        <button data-action="open" class="accent" ${busy || !overview.ready || (overview.releaseRequiresLicense && !licenseAllowsBusiness(overview.license)) ? 'disabled' : ''}>打开康复管理系统</button>
        <button data-action="open-admin" class="accent" ${busy || !overview.ready || (overview.releaseRequiresLicense && !licenseAllowsBusiness(overview.license)) ? 'disabled' : ''}>进入后台管理</button>
      </section>

      <section class="actions utility-actions">
        <button data-action="backup" ${busy || !overview.ready ? 'disabled' : ''}>创建备份</button>
        <button data-action="export-data" ${busy || !overview.ready || overview.releaseRequiresLicense ? 'disabled' : ''}>一键导出整店数据</button>
        <button data-action="import-data" ${busy || !overview.ready || overview.releaseRequiresLicense ? 'disabled' : ''}>一键导入整店数据</button>
        <button data-action="admin-settings" ${busy || !overview.ready || overview.releaseRequiresLicense ? 'disabled' : ''}>管理员账号设置</button>
        <button data-action="admin-recovery" class="secondary" ${busy || !overview.ready ? 'disabled' : ''}>通过 Owner MFA 找回管理员密码</button>
        <button data-action="logs">查看日志</button>
        <button data-action="directory">打开数据目录</button>
        <button data-action="diagnostics">复制诊断信息</button>
        <button data-action="settings">端口设置</button>
      </section>

      <details class="danger-zone">
        <summary>危险操作</summary>
        <p>卸载程序默认保留数据。只有输入“删除所有本地数据”并再次确认，才会删除固定的 Docker 数据卷和应用数据。</p>
        <input id="delete-confirmation" autocomplete="off" placeholder="输入：删除所有本地数据" />
        <button data-action="delete" class="danger" ${overview.releaseRequiresLicense ? 'disabled' : ''}>删除所有本地数据</button>
      </details>

      ${logText ? `<section class="logs"><header><strong>脱敏日志</strong><button data-action="close-logs">关闭</button></header><pre>${escapeHtml(logText)}</pre></section>` : ''}
    </section>

    <dialog id="license-begin-dialog">
      <form method="dialog" id="license-begin-form">
        <h2>登记工作室安装身份</h2>
        <p>将创建不可随意重置的本机 Ed25519 密钥和安装编号。不上传患者信息，提交后需软件所有者审批。</p>
        <label>工作室名称<input name="organization" maxlength="120" minlength="2" autocomplete="organization" required /></label>
        <menu><button value="cancel" class="secondary">取消</button><button value="default" data-action="license-confirm-begin">生成安装身份</button></menu>
      </form>
    </dialog>

    <dialog id="license-replace-dialog">
      <form method="dialog" id="license-replace-form">
        <h2>在新设备上申请换机</h2>
        <p>必须填写已获批准的原工作室和旧实例 UUID。新设备生成独立密钥后，请将请求材料安全交给所有者审核；本步骤不会自动激活。</p>
        <label>原工作室租户 UUID<input name="tenantId" required placeholder="xxxxxxxx-xxxx-4xxx-8xxx-xxxxxxxxxxxx" /></label>
        <label>原安装实例 UUID<input name="previousInstallationId" required /></label>
        <label>工作室名称<input name="organization" maxlength="120" minlength="2" required /></label>
        <menu><button value="cancel" class="secondary">取消</button><button value="default" data-action="license-confirm-replace">生成待审批请求</button></menu>
      </form>
    </dialog>

    <dialog id="settings-dialog">
      <form method="dialog" id="settings-form">
        <h2>本机端口设置</h2>
        <p>默认仅绑定 127.0.0.1。局域网访问第一版不自动开启。</p>
        <label>绑定地址<input name="bindAddress" value="${escapeHtml(settings.bindAddress)}" readonly /></label>
        <label>HTTP 跳转端口<input name="httpPort" type="number" min="1024" max="65535" value="${settings.httpPort}" /></label>
        <label>HTTPS 端口<input name="httpsPort" type="number" min="1024" max="65535" value="${settings.httpsPort}" /></label>
        <menu>
          <button value="cancel" class="secondary">取消</button>
          <button value="default" data-action="save-settings">保存</button>
        </menu>
      </form>
    </dialog>

    <dialog id="export-dialog">
      <form method="dialog" id="export-form">
        <h2>导出整店数据</h2>
        <p>导出完整数据库、系统用户、权限和附件。迁移包不包含本机运行密码、TLS 私钥、端口、日志或备份。</p>
        <label>迁移包密码（12–128 位）<input name="password" type="password" minlength="12" maxlength="128" autocomplete="new-password" /></label>
        <label>再次输入密码<input name="passwordConfirm" type="password" minlength="12" maxlength="128" autocomplete="new-password" /></label>
        <menu>
          <button value="cancel" class="secondary">取消</button>
          <button value="default" data-action="confirm-export">选择保存位置并导出</button>
        </menu>
      </form>
    </dialog>

    <dialog id="import-dialog">
      <form method="dialog" id="import-form">
        <h2>覆盖导入整店数据</h2>
        <p class="warning-text">此操作会先自动创建目标设备本机备份，再用迁移包全量替换数据库和附件。目标设备的运行密码、TLS 与端口配置保持不变。</p>
        <label>已选择迁移包<input name="source" value="${escapeHtml(selectedImportPath)}" readonly /></label>
        <label>迁移包密码<input name="password" type="password" minlength="12" maxlength="128" autocomplete="off" /></label>
        <label>确认文字<input name="confirmation" autocomplete="off" placeholder="输入：覆盖导入全部数据" /></label>
        <menu>
          <button value="cancel" class="secondary">取消</button>
          <button value="default" class="danger" data-action="confirm-import">自动备份并覆盖导入</button>
        </menu>
      </form>
    </dialog>

    <dialog id="admin-dialog">
      <form method="dialog" id="admin-form">
        <h2>内置超级管理员设置</h2>
        <p>修改租户 1、用户 ID 1 的内置超级管理员。保存后旧登录令牌立即失效；当前密码不会显示或写入日志。</p>
        <label>新管理员账号<input name="username" minlength="4" maxlength="30" autocomplete="username" placeholder="例如 studio_admin" /></label>
        <label>新密码（12–16 位）<input name="password" type="password" minlength="12" maxlength="16" autocomplete="new-password" /></label>
        <label>再次输入密码<input name="passwordConfirm" type="password" minlength="12" maxlength="16" autocomplete="new-password" /></label>
        <menu>
          <button value="cancel" class="secondary">取消</button>
          <button value="default" data-action="save-admin">保存管理员账号</button>
        </menu>
      </form>
    </dialog>
    <dialog id="admin-recovery-dialog">
      <form method="dialog" id="admin-recovery-form">
        <h2>Owner MFA · 恢复内置 Core admin 密码</h2>
        <p class="warning-text">仅适用于本机内置租户 1、用户 ID 1 的 admin 密码，不更改账号、租户或许可证。执行前自动创建加密备份，成功后撤销旧登录会话。需独立 Owner MFA 审批，不能跳过。</p>
        <label>设备挑战码（复制到 Owner 控制台）
          <input name="deviceChallenge" type="text" readonly autocomplete="off" />
        </label>
        <p>在浏览器中打开 <code>https://127.0.0.1:9444/owner/console</code>，登录独立 Owner 账号，完成 TOTP 再认证并生成 120 秒一次性授权码。</p>
        <label>Owner 一次性恢复授权码
          <input name="authorizationCode" minlength="43" maxlength="43" pattern="[A-Za-z0-9_-]{43}" autocomplete="off" required />
        </label>
        <label>新 Core admin 密码（12–16 位）
          <input name="newPassword" type="password" minlength="12" maxlength="16" autocomplete="new-password" required />
        </label>
        <label>再次输入新密码
          <input name="confirmPassword" type="password" minlength="12" maxlength="16" autocomplete="new-password" required />
        </label>
        <menu>
          <button value="cancel" class="secondary">取消恢复</button>
          <button data-action="confirm-admin-recovery" value="default">验证 Owner MFA 并重设密码</button>
        </menu>
      </form>
    </dialog>
  `
  bindActions()
}

async function invokeOperation<T>(name: string, args?: Record<string, unknown>): Promise<T> {
  busy = true
  render()
  try {
    return await invoke<T>(name, args)
  } finally {
    busy = false
  }
}

async function refresh(): Promise<void> {
  try {
    overview = await invoke<LauncherOverview>('get_overview')
    settings = await invoke<LauncherSettings>('get_settings')
  } catch (error) {
    overview = {
      ...overview,
      dockerState: 'error',
      dockerDetail: '启动器后端不可用',
      lastError: String(error)
    }
  }
  render()
}

async function runAndRefresh(command: string, args?: Record<string, unknown>): Promise<void> {
  const operationLabels: Record<string, string> = {
    start_services: '正在校验 Docker、拉取固定镜像并启动服务…',
    stop_services: '正在停止服务，业务数据会保留…',
    restart_services: '正在重启服务并等待健康检查…',
    create_backup: '正在导出并加密数据库与附件备份…',
    import_full_transfer: '正在校验迁移包、自动备份并覆盖导入全部数据…',
    update_admin_credentials: '正在安全更新内置超级管理员账号…',
    delete_all_data: '正在删除已确认的本机数据…'
  }
  overview.operation = operationLabels[command] ?? '正在执行操作…'
  try {
    overview = await invokeOperation<LauncherOverview>(command, args)
  } catch (error) {
    overview.lastError = String(error)
  }
  await refresh()
}

async function runLicenseCommand(command: string, args?: Record<string, unknown>): Promise<void> {
  busy = true
  overview.operation = '正在安全验证工作室授权状态…'
  render()
  try {
    await invoke(command, args)
    licenseError = ''
  } catch (error) {
    licenseError = String(error)
  } finally {
    busy = false
    await refresh()
  }
}

function bindActions(): void {
  document.querySelectorAll<HTMLButtonElement>('[data-action]').forEach((button) => {
    button.addEventListener('click', async (event) => {
      const action = (event.currentTarget as HTMLButtonElement).dataset.action
      if (!action) return
      switch (action) {
        case 'license-help':
          licenseOnboardingPreference = !(licenseOnboardingPreference ?? (licenseOnboardingStep(overview.license) > 0 && licenseOnboardingStep(overview.license) < 5))
          render()
          break
        case 'license-begin':
          document.querySelector<HTMLDialogElement>('#license-begin-dialog')?.showModal()
          break
        case 'license-confirm-begin': {
          event.preventDefault()
          const field = document.querySelector<HTMLInputElement>('#license-begin-form input[name=organization]')
          const organization = field?.value.trim() ?? ''
          if (organization.length < 2 || organization.length > 120) {
            window.alert('工作室名称须为 2–120 个字符')
            return
          }
          if (!window.confirm('确认生成本机安装密钥和不可随意重置的安装编号？')) return
          document.querySelector<HTMLDialogElement>('#license-begin-dialog')?.close()
          await runLicenseCommand('begin_license_application', { organization })
          break
        }
        case 'license-submit':
          await runLicenseCommand('submit_license_application')
          break
        case 'license-renew':
          await runLicenseCommand('renew_license_online')
          break
        case 'license-check':
          await runLicenseCommand('check_license_online')
          break
        case 'license-import': {
          const path = await open({
            multiple: false, directory: false,
            filters: [{ name: '所有者签名离线许可证', extensions: ['rehab-license'] }]
          })
          if (typeof path === 'string') await runLicenseCommand('import_offline_license', { path })
          break
        }
        case 'license-replace':
          document.querySelector<HTMLDialogElement>('#license-replace-dialog')?.showModal()
          break
        case 'license-confirm-replace': {
          event.preventDefault()
          const form = document.querySelector<HTMLFormElement>('#license-replace-form')
          if (!form) return
          const data = new FormData(form)
          const tenantId = String(data.get('tenantId') ?? '').trim()
          const previousInstallationId = String(data.get('previousInstallationId') ?? '').trim()
          const organization = String(data.get('organization') ?? '').trim()
          if (!/^[0-9a-f-]{36}$/.test(tenantId) ||
              !/^[0-9a-f-]{36}$/.test(previousInstallationId) || organization.length < 2) {
            window.alert('请输入有效的工作室和原设备 UUID、工作室名称')
            return
          }
          if (!window.confirm('将在这台新设备上生成独立的安装密钥；审核前无法使用旧许可证。继续？')) return
          document.querySelector<HTMLDialogElement>('#license-replace-dialog')?.close()
          try {
            busy = true
            const request = await invoke<string>('prepare_license_replacement', {
              tenantId, previousInstallationId, organization
            })
            await navigator.clipboard.writeText(request)
            window.alert('换机请求已复制。请通过安全渠道交给软件所有者完成二次审批，不要提供本机私钥。')
          } catch (error) {
            licenseError = String(error)
          } finally {
            busy = false
            await refresh()
          }
          break
        }
        case 'start':
          await runAndRefresh('start_services')
          break
        case 'stop':
          await runAndRefresh('stop_services')
          break
        case 'restart':
          await runAndRefresh('restart_services')
          break
        case 'open':
          await runAndRefresh('open_system')
          break
        case 'open-admin':
          await runAndRefresh('open_admin')
          break
        case 'backup':
          await runAndRefresh('create_backup')
          break
        case 'export-data':
          document.querySelector<HTMLDialogElement>('#export-dialog')?.showModal()
          break
        case 'confirm-export': {
          event.preventDefault()
          const form = document.querySelector<HTMLFormElement>('#export-form')
          if (!form) return
          const data = new FormData(form)
          const password = String(data.get('password') ?? '')
          const passwordConfirm = String(data.get('passwordConfirm') ?? '')
          if (!isValidTransferPassword(password) || password !== passwordConfirm) {
            window.alert('迁移包密码须为 12–128 位，且两次输入必须一致。')
            return
          }
          const date = new Date().toISOString().slice(0, 10).replaceAll('-', '')
          const destination = await save({
            defaultPath: `康复管理系统-整店数据-${date}.rehab-transfer`,
            filters: [{ name: '康复管理系统迁移包', extensions: ['rehab-transfer'] }]
          })
          if (!destination) return
          try {
            overview.operation = '正在导出整店加密迁移包（数据库、账号权限和附件）…'
            const output = await invokeOperation<string>('export_full_transfer', { destination, password })
            window.alert(`整店数据已加密导出到：\n${output}\n\n请将迁移包密码通过其他安全渠道交给分店。`)
          } catch (error) {
            overview.lastError = String(error)
          }
          await refresh()
          break
        }
        case 'import-data': {
          const source = await open({
            multiple: false,
            directory: false,
            filters: [{ name: '康复管理系统迁移包', extensions: ['rehab-transfer'] }]
          })
          if (typeof source !== 'string') return
          selectedImportPath = source
          render()
          document.querySelector<HTMLDialogElement>('#import-dialog')?.showModal()
          break
        }
        case 'confirm-import': {
          event.preventDefault()
          const form = document.querySelector<HTMLFormElement>('#import-form')
          if (!form) return
          const data = new FormData(form)
          const password = String(data.get('password') ?? '')
          const confirmation = String(data.get('confirmation') ?? '')
          if (!isValidTransferPassword(password)) {
            window.alert('迁移包密码须为 12–128 位。')
            return
          }
          if (!importConfirmationMatches(confirmation)) {
            window.alert('确认文字不匹配，未修改任何数据。')
            return
          }
          if (!window.confirm('确认自动备份当前分店数据，并用迁移包全量覆盖数据库和附件？')) return
          try {
            overview.operation = '正在校验迁移包、自动备份并覆盖导入全部数据…'
            overview = await invokeOperation<LauncherOverview>('import_full_transfer', {
              source: selectedImportPath,
              password,
              confirmation
            })
            selectedImportPath = ''
            window.alert('整店数据导入完成。当前设备的运行密码、TLS 和端口配置已保留。')
          } catch (error) {
            overview.lastError = String(error)
          }
          await refresh()
          break
        }
        case 'admin-settings':
          document.querySelector<HTMLDialogElement>('#admin-dialog')?.showModal()
          break
        case 'admin-recovery': {
          try {
            const deviceChallenge = await invoke<string>('begin_admin_recovery')
            const dialog = document.querySelector<HTMLDialogElement>('#admin-recovery-dialog')
            const field = dialog?.querySelector<HTMLInputElement>('input[name="deviceChallenge"]')
            if (!dialog || !field) throw Error('管理员恢复对话框缺失')
            dialog.querySelector<HTMLFormElement>('form')?.reset()
            field.value = deviceChallenge
            dialog.showModal()
          } catch (error) {
            overview.lastError = String(error)
            render()
          }
          break
        }
        case 'confirm-admin-recovery': {
          event.preventDefault()
          const form = document.querySelector<HTMLFormElement>('#admin-recovery-form')
          if (!form) return
          const data = new FormData(form)
          const authorizationCode = String(data.get('authorizationCode') ?? '').trim()
          const newPassword = String(data.get('newPassword') ?? '')
          const confirmPassword = String(data.get('confirmPassword') ?? '')
          if (!/^[A-Za-z0-9_-]{43}$/.test(authorizationCode) ||
              !isValidAdminCredentialInput('admin', newPassword) ||
              newPassword !== confirmPassword) {
            window.alert('请输入完整的 Owner 一次性授权码，并确认两次新密码相同且为 12–16 位。')
            return
          }
          if (!window.confirm('确认以 Owner MFA 授权恢复 Core admin 密码？将先自动备份，成功后旧登录会话失效。')) return
          try {
            overview = await invokeOperation<LauncherOverview>('recover_admin_credentials', {
              authorizationCode, newPassword
            })
            window.alert('Core admin 密码已恢复。请使用租户“工作室内部”、账号 admin 和新密码重新登录。')
          } catch (error) {
            overview.lastError = String(error)
          }
          await refresh()
          break
        }
        case 'save-admin': {
          event.preventDefault()
          const form = document.querySelector<HTMLFormElement>('#admin-form')
          if (!form) return
          const data = new FormData(form)
          const username = String(data.get('username') ?? '').trim()
          const password = String(data.get('password') ?? '')
          const passwordConfirm = String(data.get('passwordConfirm') ?? '')
          if (!isValidAdminCredentialInput(username, password) || password !== passwordConfirm) {
            window.alert('账号须为 4–30 位字母、数字、点、下划线或连字符；密码须为 12–16 位且两次一致。')
            return
          }
          if (!window.confirm('保存后当前管理员登录会话将失效，需要使用新账号密码重新登录。确认继续？')) return
          try {
            overview.operation = '正在安全更新内置超级管理员账号…'
            overview = await invokeOperation<LauncherOverview>('update_admin_credentials', {
              username,
              password
            })
            window.alert('管理员账号密码已更新，旧登录令牌已失效。')
          } catch (error) {
            overview.lastError = String(error)
          }
          await refresh()
          break
        }
        case 'logs':
          logText = await invoke<string>('read_logs')
          render()
          break
        case 'close-logs':
          logText = ''
          render()
          break
        case 'directory':
          await invoke('open_data_directory')
          break
        case 'diagnostics': {
          const diagnostics = await invoke<string>('get_diagnostics')
          await navigator.clipboard.writeText(diagnostics)
          button.textContent = '已复制'
          break
        }
        case 'ack-password':
          await runAndRefresh('acknowledge_initial_password')
          break
        case 'settings':
          document.querySelector<HTMLDialogElement>('#settings-dialog')?.showModal()
          break
        case 'save-settings': {
          event.preventDefault()
          const form = document.querySelector<HTMLFormElement>('#settings-form')
          if (!form) return
          const data = new FormData(form)
          const nextSettings: LauncherSettings = {
            bindAddress: '127.0.0.1',
            httpPort: Number(data.get('httpPort')),
            httpsPort: Number(data.get('httpsPort')),
            lanEnabled: false
          }
          await runAndRefresh('save_settings', { settings: nextSettings })
          document.querySelector<HTMLDialogElement>('#settings-dialog')?.close()
          break
        }
        case 'delete': {
          const input = document.querySelector<HTMLInputElement>('#delete-confirmation')
          const confirmation = input?.value ?? ''
          if (!deletionConfirmationMatches(confirmation)) {
            window.alert('确认文字不匹配，未删除任何数据。')
            return
          }
          if (!window.confirm('此操作不可撤销。确认删除数据库、附件、备份、日志和本机运行配置？')) return
          await runAndRefresh('delete_all_data', { confirmation })
          break
        }
      }
    })
  })
}

render()
void refresh()
window.setInterval(() => {
  if (!busy) void refresh()
}, 5000)

// An online studio can continue using its longer signed contract by obtaining
// a new <=72h lease BEFORE the old one expires. Never rewrite lease claims,
// permit unpinned issuers, or override revocation. Retry at most hourly.
let nextAutomaticRenewalAttempt = 0
async function renewNearExpiry(): Promise<void> {
  const at = Date.now()
  if (busy || at < nextAutomaticRenewalAttempt ||
      !licenseNeedsOnlineRenewal(overview.license, at)) return
  nextAutomaticRenewalAttempt = at + 60 * 60 * 1000
  busy = true
  try {
    await invoke('renew_license_online')
    licenseError = ''
    await refresh()
  } catch {
    // Keep the previously signed grace status. Never imply renewal succeeded
    // or spam a temporary offline Owner service.
  } finally {
    busy = false
  }
}
window.setInterval(() => { void renewNearExpiry() }, 5 * 60 * 1000)
