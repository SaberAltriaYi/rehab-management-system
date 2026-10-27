// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
const messages = {
  CONTEXT: '登录或机构上下文已变化，已停止旧请求',
  FAILED: '登录刷新未完成，请重新登录',
  TIMEOUT: '登录刷新超时，请检查连接并重新登录',
  CANCELED: '请求已取消'
}
export class AuthRefreshError extends Error {
  code: keyof typeof messages
  __CANCEL__?: true
  constructor(code: keyof typeof messages) {
    super(messages[code])
    this.name = 'AuthRefreshError'
    this.code = code
    if (code === 'CANCELED') this.__CANCEL__ = true
  }
}
type Options<T> = {
  context: () => string
  subscribe: (listener: () => void) => () => void
  refresh: (signal: AbortSignal) => Promise<T>
  apply: (value: T) => void
  timeoutMs?: number
  schedule?: (callback: () => void, ms: number) => () => void
}
type Flight = {
  context: string
  promise: Promise<void>
  resolve: () => void
  reject: (error: AuthRefreshError) => void
  abort: AbortController
  cancelTimer: () => void
}

/** Shared settlement, not callbacks that replay failed requests. No raw error survives. */
export function createAuthRefreshCoordinator<T>(options: Options<T>) {
  let flight: Flight | undefined,
    failedContext: string | undefined,
    disposed = false
  const timeout = options.timeoutMs ?? 30000
  if (!Number.isFinite(timeout) || timeout <= 0 || timeout > 120000)
    throw new AuthRefreshError('FAILED')
  const schedule =
    options.schedule ??
    ((callback, ms) => {
      const timer = setTimeout(callback, ms)
      return () => clearTimeout(timer)
    })
  const current = (context: string) => {
    try {
      return !disposed && !!context && options.context() === context
    } catch {
      return false
    }
  }
  function finish(entry: Flight, failure?: keyof typeof messages) {
    if (flight !== entry) return
    flight = undefined
    try {
      entry.cancelTimer()
    } catch {
      /* settlement must not depend on timer cleanup */
    }
    if (failure) {
      if (failure !== 'CONTEXT' && current(entry.context)) failedContext = entry.context
      entry.abort.abort()
      entry.reject(new AuthRefreshError(failure))
    } else entry.resolve()
  }
  const unsubscribe = options.subscribe(() => {
    if (flight) finish(flight, 'CONTEXT')
    failedContext = undefined
  })
  function run(context: string): Promise<void> {
    if (!current(context)) return Promise.reject(new AuthRefreshError('CONTEXT'))
    if (flight && flight.context !== context) finish(flight, 'CONTEXT')
    if (failedContext === context) return Promise.reject(new AuthRefreshError('FAILED'))
    if (flight) return flight.promise
    let resolve!: () => void, reject!: (error: AuthRefreshError) => void
    const promise = new Promise<void>((a, b) => {
      resolve = a
      reject = b
    })
    const entry: Flight = {
      context,
      promise,
      resolve,
      reject,
      abort: new AbortController(),
      cancelTimer: () => {}
    }
    flight = entry
    try {
      entry.cancelTimer = schedule(() => finish(entry, 'TIMEOUT'), timeout)
    } catch {
      finish(entry, 'FAILED')
    }
    Promise.resolve()
      .then(() => {
        if (flight !== entry || !current(context)) throw new AuthRefreshError('CONTEXT')
        return options.refresh(entry.abort.signal)
      })
      .then(
        (value) => {
          if (flight !== entry) return
          if (!current(context)) return finish(entry, 'CONTEXT')
          try {
            options.apply(value)
            finish(entry)
          } catch {
            finish(entry, 'FAILED')
          }
        },
        () => {
          finish(entry, current(context) ? 'FAILED' : 'CONTEXT')
        }
      )
    return promise
  }
  return {
    run,
    block: (context: string) => {
      if (!current(context)) return
      failedContext = context
      if (flight?.context === context) finish(flight, 'FAILED')
    },
    dispose: () => {
      disposed = true
      if (flight) finish(flight, 'CONTEXT')
      unsubscribe()
    }
  }
}

/** Individual cancellation must not abort the shared refresh for other requests. */
type AbortLike = Pick<AbortSignal, 'aborted'> &
  Partial<Pick<AbortSignal, 'addEventListener' | 'removeEventListener'>>
export function waitForAuthRefresh(shared: Promise<void>, signal?: AbortLike): Promise<void> {
  return new Promise((resolve, reject) => {
    let done = false
    const finish = (error?: unknown) => {
      if (done) return
      done = true
      signal?.removeEventListener?.('abort', cancel)
      if (error) reject(error)
      else resolve()
    }
    const cancel = () => finish(new AuthRefreshError('CANCELED'))
    signal?.addEventListener?.('abort', cancel, { once: true })
    if (signal?.aborted) cancel()
    shared.then(
      () => finish(),
      (error) => finish(error)
    )
  })
}
