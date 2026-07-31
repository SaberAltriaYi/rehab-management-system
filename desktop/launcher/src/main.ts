import { invoke } from '@tauri-apps/api/core'
import './styles.css'
import {
  EMPTY_OVERVIEW,
  type LauncherOverview,
  type LauncherSettings,
  deletionConfirmationMatches,
  formatBackupTime,
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

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

function render(): void {
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

      ${overview.lastError ? `<aside class="error"><strong>最近错误</strong><p>${escapeHtml(overview.lastError)}</p><small>可查看日志或复制诊断信息后按提示修复。日志目录：${escapeHtml(`${overview.dataDirectory}/logs`)}</small></aside>` : ''}
      ${overview.firstLoginPassword ? `<aside class="first-login"><strong>首次登录：租户“工作室内部”，账号 admin</strong><code>${escapeHtml(overview.firstLoginPassword)}</code><p>请先保存临时密码并登录后立即修改。此密码不会写入日志或诊断信息。</p><button data-action="ack-password" class="secondary">我已保存，停止显示</button></aside>` : ''}

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
        <button data-action="open" class="accent" ${busy || !overview.ready ? 'disabled' : ''}>打开康复管理系统</button>
      </section>

      <section class="actions utility-actions">
        <button data-action="backup" ${busy || !overview.ready ? 'disabled' : ''}>创建备份</button>
        <button data-action="logs">查看日志</button>
        <button data-action="directory">打开数据目录</button>
        <button data-action="diagnostics">复制诊断信息</button>
        <button data-action="settings">端口设置</button>
      </section>

      <details class="danger-zone">
        <summary>危险操作</summary>
        <p>卸载程序默认保留数据。只有输入“删除所有本地数据”并再次确认，才会删除固定的 Docker 数据卷和应用数据。</p>
        <input id="delete-confirmation" autocomplete="off" placeholder="输入：删除所有本地数据" />
        <button data-action="delete" class="danger">删除所有本地数据</button>
      </details>

      ${logText ? `<section class="logs"><header><strong>脱敏日志</strong><button data-action="close-logs">关闭</button></header><pre>${escapeHtml(logText)}</pre></section>` : ''}
    </section>

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

function bindActions(): void {
  document.querySelectorAll<HTMLButtonElement>('[data-action]').forEach((button) => {
    button.addEventListener('click', async (event) => {
      const action = (event.currentTarget as HTMLButtonElement).dataset.action
      if (!action) return
      switch (action) {
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
        case 'backup':
          await runAndRefresh('create_backup')
          break
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
