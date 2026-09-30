/**
 * OpenCap 数据自动筛选 + 逐个上传队列（纯逻辑，无 Vue 依赖，可在 Node 中单测）。
 *
 * 设计要点：
 * - 规则来自后端 GET /rehab/motion/upload-policy（与服务端 MotionFileRules 同源），前端不自行放宽；
 * - pickle/pkl（反序列化风险）、vtp 几何、OutputMedia 叠加视频、图片、日志与系统文件自动跳过并给出原因；
 * - 未取得视频授权时视频一律跳过；超过单文件上限的文件跳过（不切片、不压缩、不绕过服务端限制）；
 * - 串行上传（concurrency=1），网络错误/超时/5xx/429 指数退避重试，业务错误（4xx/校验失败）不重试；
 * - 服务端已存在的相对路径跳过（断点续传）；服务端仍会再次做路径、大小、内容嗅探校验。
 */

export interface PolicyRule {
  kind: string
  pattern: string
  maxBytes: number
  requiredForAnalysis?: boolean
}

export interface UploadPolicy {
  rules: PolicyRule[]
  maxPathLength: number
  videoRequiresConsent: boolean
  concurrency: number
  maxRetries: number
  skipNote?: string
}

export interface FileLike {
  name: string
  size: number
  webkitRelativePath?: string
}

export type ItemStatus = 'queued' | 'skipped' | 'uploading' | 'done' | 'failed' | 'cancelled'

export interface QueueItem<F extends FileLike = FileLike> {
  file: F
  /** 规范化后提交给服务端的相对路径（以 OpenCapData_xxx/ 开头或会话内相对路径） */
  relativePath: string
  kind?: string
  required?: boolean
  status: ItemStatus
  reason?: string
  attempts: number
  error?: string
}

export interface PlanOptions {
  videoConsent: boolean
  /** 服务端已存在的相对路径，用于断点续传 */
  existing?: Iterable<string>
}

const VIDEO_KIND = 'video'

const SKIP_RULES: Array<{ test: (p: string, base: string) => boolean; reason: string }> = [
  {
    test: (_p, b) => b.startsWith('._') || b === '.DS_Store' || b === 'Thumbs.db' || b === 'desktop.ini',
    reason: '系统文件'
  },
  {
    test: (_p, b) => /\.(pkl|pickle)$/i.test(b),
    reason: 'pickle 文件存在反序列化风险，禁止上传'
  },
  { test: (_p, b) => /\.vtp$/i.test(b), reason: '模型几何网格，分析不需要' },
  { test: (p) => /(^|\/)OutputMedia\//.test(p), reason: 'OpenCap 叠加渲染视频，分析不需要' },
  { test: (_p, b) => /\.(jpe?g|png|gif|bmp|heic|webp)$/i.test(b), reason: '图片，分析不需要' },
  { test: (_p, b) => /\.(txt|log|csv)$/i.test(b), reason: '日志/文本，分析不需要' }
]

export function formatBytes(n: number): string {
  if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB'
  if (n >= 1024) return (n / 1024).toFixed(1) + ' KB'
  return n + ' B'
}

function basename(p: string): string {
  const i = p.lastIndexOf('/')
  return i >= 0 ? p.substring(i + 1) : p
}

/** 生成候选相对路径：优先从 OpenCapData_ 目录开始；其次去掉所选根目录名；最后原样。 */
export function candidatePaths(raw: string): string[] {
  const p = raw.replace(/\\/g, '/').replace(/^(\.\/)+/, '').replace(/^\/+/, '')
  const out: string[] = []
  const parts = p.split('/')
  const idx = parts.findIndex((s) => s.startsWith('OpenCapData_'))
  if (idx >= 0) out.push(parts.slice(idx).join('/'))
  if (parts.length > 1) out.push(parts.slice(1).join('/'))
  out.push(p)
  return Array.from(new Set(out.filter((x) => x.length > 0)))
}

interface CompiledRule extends PolicyRule {
  re: RegExp
}

function compile(policy: UploadPolicy): CompiledRule[] {
  return policy.rules.map((r) => ({ ...r, re: new RegExp(r.pattern) }))
}

/** 根据策略生成上传计划（纯函数）。 */
export function planUploads<F extends FileLike>(
  files: Iterable<F>,
  policy: UploadPolicy,
  opts: PlanOptions
): QueueItem<F>[] {
  const rules = compile(policy)
  const existing = new Set(opts.existing || [])
  const seen = new Set<string>()
  const items: QueueItem<F>[] = []
  for (const file of files) {
    const raw = file.webkitRelativePath || file.name
    const cands = candidatePaths(raw)
    const display = cands[0]
    const base = basename(display)
    const item: QueueItem<F> = { file, relativePath: display, status: 'skipped', attempts: 0 }
    items.push(item)

    const skip = SKIP_RULES.find((s) => cands.some((c) => s.test(c, base)))
    if (skip) {
      item.reason = skip.reason
      continue
    }
    let matched: CompiledRule | undefined
    for (const c of cands) {
      matched = rules.find((r) => r.re.test(c))
      if (matched) {
        item.relativePath = c
        break
      }
    }
    if (!matched) {
      item.reason = '不在 OpenCap 导出目录结构内（仅接受 sessionMetadata.yaml、Kinematics/*.mot、MarkerData/*.trc、Model/*.osim、InputMedia 视频）'
      continue
    }
    item.kind = matched.kind
    item.required = !!matched.requiredForAnalysis
    if (item.relativePath.length > policy.maxPathLength) {
      item.reason = `路径超过 ${policy.maxPathLength} 字符`
    } else if (file.size <= 0) {
      item.reason = '空文件'
    } else if (file.size > matched.maxBytes) {
      item.reason = `超过单文件上限 ${formatBytes(matched.maxBytes)}（${formatBytes(file.size)}）`
    } else if (matched.kind === VIDEO_KIND && policy.videoRequiresConsent && !opts.videoConsent) {
      item.reason = '未取得患者视频授权，视频不上传'
    } else if (seen.has(item.relativePath)) {
      item.reason = '重复文件'
    } else if (existing.has(item.relativePath)) {
      item.reason = '服务端已存在（续传跳过）'
    } else {
      item.status = 'queued'
      item.reason = undefined
      seen.add(item.relativePath)
    }
  }
  // 结构化数据优先：元数据 → mot → trc → osim → 视频，保证先具备可分析的最小集合
  const order: Record<string, number> = { metadata: 0, mot: 1, trc: 2, osim: 3, video: 4 }
  return items.sort((a, b) => {
    const qa = a.status === 'queued' ? 0 : 1
    const qb = b.status === 'queued' ? 0 : 1
    if (qa !== qb) return qa - qb
    return (order[a.kind || ''] ?? 9) - (order[b.kind || ''] ?? 9) || a.relativePath.localeCompare(b.relativePath)
  })
}

export interface QueueSummary {
  total: number
  queued: number
  done: number
  failed: number
  skipped: number
  bytesQueued: number
  skippedByReason: Record<string, number>
  hasRequiredMot: boolean
  hasMetadata: boolean
}

export function summarize(items: QueueItem[]): QueueSummary {
  const s: QueueSummary = {
    total: items.length,
    queued: 0,
    done: 0,
    failed: 0,
    skipped: 0,
    bytesQueued: 0,
    skippedByReason: {},
    hasRequiredMot: false,
    hasMetadata: false
  }
  for (const it of items) {
    if (it.status === 'skipped') {
      s.skipped++
      const r = it.reason || '其他'
      s.skippedByReason[r] = (s.skippedByReason[r] || 0) + 1
      continue
    }
    if (it.status === 'done') s.done++
    else if (it.status === 'failed') s.failed++
    else if (it.status === 'queued' || it.status === 'uploading') {
      s.queued++
      s.bytesQueued += it.file.size
    }
    if (it.kind === 'mot') s.hasRequiredMot = true
    if (it.kind === 'metadata') s.hasMetadata = true
  }
  return s
}

/** 默认重试判定：网络错误/超时/HTTP 5xx/429 可重试；业务错误（服务端已给出校验结论）不重试。 */
export function defaultIsRetryable(err: any): boolean {
  if (!err || typeof err !== 'object') return false
  if (err.isAxiosError || err.name === 'AxiosError') {
    const status = err.response?.status
    if (!status) return true
    return status >= 500 || status === 429
  }
  return false
}

export function errorText(err: any): string {
  if (typeof err === 'string') return err === 'error' ? '服务端拒绝（见提示）' : err
  if (err?.response?.status) return 'HTTP ' + err.response.status
  if (err?.message) return String(err.message).slice(0, 200)
  return '上传失败'
}

export interface RunOptions<F extends FileLike> {
  maxRetries: number
  /** 第 n 次重试前等待的毫秒数；默认 1s、2s、4s…（上限 15s） */
  backoffMs?: (attempt: number) => number
  sleep?: (ms: number) => Promise<void>
  isRetryable?: (err: any) => boolean
  onChange?: (item: QueueItem<F>) => void
  shouldStop?: () => boolean
}

/** 手动“重试失败项”：失败/已取消的项重新排队并清零尝试次数（被规则跳过的项不受影响）。 */
export function requeueFailed(items: QueueItem[]): number {
  let n = 0
  for (const it of items) {
    if ((it.status === 'failed' || it.status === 'cancelled') && it.kind) {
      it.status = 'queued'
      it.attempts = 0
      it.error = undefined
      n++
    }
  }
  return n
}

const defaultSleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))

/**
 * 串行执行上传。上传函数只负责一次请求；重试、退避、取消由队列处理。
 * 返回时所有 queued 项已变为 done / failed / cancelled。
 */
export async function runQueue<F extends FileLike>(
  items: QueueItem<F>[],
  upload: (item: QueueItem<F>) => Promise<unknown>,
  opts: RunOptions<F>
): Promise<QueueSummary> {
  const sleep = opts.sleep || defaultSleep
  const backoff = opts.backoffMs || ((n: number) => Math.min(15000, 1000 * Math.pow(2, n - 1)))
  const retryable = opts.isRetryable || defaultIsRetryable
  const notify = (it: QueueItem<F>) => opts.onChange && opts.onChange(it)
  for (const item of items) {
    if (item.status !== 'queued') continue
    if (opts.shouldStop && opts.shouldStop()) {
      item.status = 'cancelled'
      notify(item)
      continue
    }
    item.error = undefined
    for (;;) {
      item.status = 'uploading'
      item.attempts++
      notify(item)
      try {
        await upload(item)
        item.status = 'done'
        notify(item)
        break
      } catch (err) {
        item.error = errorText(err)
        const retries = item.attempts - 1
        if (retryable(err) && retries < opts.maxRetries && !(opts.shouldStop && opts.shouldStop())) {
          item.status = 'queued'
          notify(item)
          await sleep(backoff(retries + 1))
          continue
        }
        item.status = 'failed'
        notify(item)
        break
      }
    }
  }
  return summarize(items as QueueItem[])
}
