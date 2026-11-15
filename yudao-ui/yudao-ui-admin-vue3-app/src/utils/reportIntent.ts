// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
// In-memory intents only. No token, clinical body or intent is persisted/logged.
export type ReportGenerationInput = {
  assessmentId: number | string
  reportType?: string
  generationMode?: string
  note?: string
}
export type ReportGenerationPayload = ReportGenerationInput & { requestKey?: string }
export type ReportIntentConfirmation = { retry: boolean; legacy: boolean }
export type ReportIntentResult<T> = { value: T; legacy: boolean; retried: boolean }

const messages = {
  INVALID: '评估编号或生成参数无效，请重新打开评估',
  IDENTITY: '登录或机构上下文已变化，请重新打开评估后操作',
  CHANGED: '上次请求结果尚未确认，不能悄悄更换生成参数；请先核对报告中心',
  LIMIT: '待确认报告请求过多，请先核对报告中心，不会自动丢弃旧请求',
  RANDOM: '安全随机编号不可用，请使用受信任的HTTPS或本机地址',
  CONTRACT: '安全重试契约未就绪或已不可用，请先核对报告中心或完成后台升级',
  PREPARE: '尚未开始生成，无法确认操作；请检查连接和权限后重试',
  BUSY: '原请求仍在执行，请先等待结果，不会重置在途编号',
  UNCERTAIN: '本次结果尚未确认；请在当前页面直接重试。刷新页面后请先核对报告中心，勿连续新建报告'
}
export class ReportIntentError extends Error {
  code: keyof typeof messages
  constructor(code: keyof typeof messages) {
    super(messages[code])
    this.name = 'ReportIntentError'
    this.code = code
  }
}

export function createReportIntentScope() {
  let epoch = 0
  const listeners = new Set<() => void>()
  return {
    get version() {
      return epoch
    },
    reset() {
      epoch++
      listeners.forEach((listener) => {
        try {
          listener()
        } catch {
          /* clear every subscriber */
        }
      })
    },
    subscribe(listener: () => void) {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    }
  }
}
export const reportIntentScope = createReportIntentScope()
export const invalidateReportIntents = () => reportIntentScope.reset()

export async function negotiateReportContract(
  fetcher: () => Promise<unknown>
): Promise<'ready' | 'legacy'> {
  let value: unknown
  try {
    value = await fetcher()
  } catch (error) {
    const e = error as { isAxiosError?: boolean; response?: { status?: number } } | null
    if (e?.isAxiosError === true && e.response?.status === 404) return 'legacy'
    throw new ReportIntentError('PREPARE')
  }
  const response = value as { contract?: unknown; ready?: unknown } | null
  if (response?.contract === 'report-idempotency-v1' && response.ready === true) return 'ready'
  throw new ReportIntentError('CONTRACT')
}

export function secureReportRequestKey(): string {
  if (!globalThis.crypto?.getRandomValues) throw new ReportIntentError('RANDOM')
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 15) | 64
  bytes[8] = (bytes[8] & 63) | 128
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function reportAssessmentId(value: unknown): string {
  const text =
    typeof value === 'number' && Number.isSafeInteger(value)
      ? String(value)
      : typeof value === 'string'
        ? value
        : ''
  if (!/^[1-9][0-9]{0,18}$/.test(text) || (text.length === 19 && text > '9223372036854775807')) {
    throw new ReportIntentError('INVALID')
  }
  return text
}

type Scope = ReturnType<typeof createReportIntentScope>
type Stamp = { epoch: number; context: string }
type Entry<T> = {
  signature: string
  key?: string
  mode?: 'ready' | 'legacy'
  uncertain: boolean
  flight?: Promise<ReportIntentResult<T> | undefined>
}
type Options<T> = {
  context: () => string
  probe: () => Promise<'ready' | 'legacy'>
  send: (payload: ReportGenerationPayload) => Promise<T>
  key?: () => string
  scope?: Scope
  limit?: number
}

export function createReportIntentCoordinator<T>(options: Options<T>) {
  const scope = options.scope ?? reportIntentScope
  const entries = new Map<string, Entry<T>>()
  let lastContext: string | undefined
  let disposed = false
  const unsubscribe = scope.subscribe(() => {
    entries.clear()
    lastContext = undefined
  })
  const contextValue = () => {
    try {
      return options.context()
    } catch {
      return ''
    }
  }
  const isCurrent = (stamp: Stamp) =>
    !disposed && scope.version === stamp.epoch && contextValue() === stamp.context
  function prepare(): Stamp {
    const context = contextValue()
    if (!context || disposed) throw new ReportIntentError('IDENTITY')
    if (lastContext !== undefined && lastContext !== context) scope.reset()
    lastContext = context
    return { epoch: scope.version, context }
  }
  function run(
    input: ReportGenerationInput,
    confirm: (state: ReportIntentConfirmation) => Promise<boolean>
  ) {
    let stamp: Stamp
    let id: string
    let body: ReportGenerationInput
    try {
      stamp = prepare()
      id = reportAssessmentId(input.assessmentId)
      for (const [value, max] of [
        [input.reportType, 64],
        [input.generationMode, 32],
        [input.note, 1000]
      ] as const) {
        if (value !== undefined && (typeof value !== 'string' || value.length > max))
          throw new ReportIntentError('INVALID')
      }
      body = {
        assessmentId: id,
        reportType: input.reportType,
        generationMode: input.generationMode,
        note: input.note
      }
    } catch (error) {
      return Promise.reject(error)
    }
    const signature = JSON.stringify([
      id,
      body.reportType ?? '',
      body.generationMode ?? '',
      body.note ?? ''
    ])
    let entry = entries.get(id)
    if (entry && entry.signature !== signature)
      return Promise.reject(new ReportIntentError('CHANGED'))
    if (entry?.flight) return entry.flight
    if (!entry) {
      if (entries.size >= (options.limit ?? 64))
        return Promise.reject(new ReportIntentError('LIMIT'))
      entry = { signature, uncertain: false }
      entries.set(id, entry)
    }
    const current = entry
    let sent = false
    const work = Promise.resolve()
      .then(async () => {
        const mode = await options.probe()
        if (!isCurrent(stamp)) throw new ReportIntentError('IDENTITY')
        if (mode !== 'ready' && mode !== 'legacy') throw new ReportIntentError('CONTRACT')
        if (current.uncertain && current.mode === 'ready' && mode !== 'ready')
          throw new ReportIntentError('CONTRACT')
        const retry = current.uncertain
        const legacy = mode === 'legacy' || (retry && current.mode === 'legacy')
        if (!(await confirm({ retry, legacy }))) {
          if (!current.uncertain && entries.get(id) === current) entries.delete(id)
          return undefined
        }
        if (!isCurrent(stamp)) throw new ReportIntentError('IDENTITY')
        if (mode === 'ready' && !current.key) {
          try {
            current.key = (options.key ?? secureReportRequestKey)()
          } catch {
            throw new ReportIntentError('RANDOM')
          }
          if (
            !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
              current.key
            )
          )
            throw new ReportIntentError('RANDOM')
        }
        current.mode = mode
        sent = true
        const value = await options.send({
          ...body,
          ...(mode === 'ready' ? { requestKey: current.key } : {})
        })
        if (!isCurrent(stamp)) throw new ReportIntentError('IDENTITY')
        if (entries.get(id) === current) entries.delete(id)
        return { value, legacy: mode === 'legacy', retried: retry }
      })
      .catch((error) => {
        if (!isCurrent(stamp)) throw new ReportIntentError('IDENTITY')
        if (sent) {
          current.uncertain = true
          throw new ReportIntentError('UNCERTAIN')
        }
        if (!current.uncertain && entries.get(id) === current) entries.delete(id)
        throw error instanceof ReportIntentError ? error : new ReportIntentError('PREPARE')
      })
      .finally(() => {
        current.flight = undefined
      })
    current.flight = work
    return work
  }
  function hasUncertain(assessmentId: number | string) {
    try {
      return (
        !disposed &&
        lastContext === contextValue() &&
        entries.get(reportAssessmentId(assessmentId))?.uncertain === true
      )
    } catch {
      return false
    }
  }
  function forgetUncertain(assessmentId: number | string) {
    prepare()
    const id = reportAssessmentId(assessmentId)
    const entry = entries.get(id)
    if (entry?.flight) throw new ReportIntentError('BUSY')
    if (entry?.uncertain) entries.delete(id)
  }
  return {
    run,
    prepare,
    isCurrent,
    hasUncertain,
    forgetUncertain,
    dispose: () => {
      disposed = true
      unsubscribe()
      entries.clear()
    }
  }
}
