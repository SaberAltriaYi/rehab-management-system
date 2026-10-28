// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. No real identities or network.
import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import ts from 'typescript'
import axios from 'axios'
import qs from 'qs'
import * as privacy from '../../src/config/axios/privacy.ts'
import * as recovery from '../../src/config/axios/refreshCoordinator.ts'
import { createAuthSessionScope } from '../../src/utils/authSession.ts'

export const deferred = () => {
  let resolve, reject
  const promise = new Promise((a, b) => {
    resolve = a
    reject = b
  })
  return { promise, resolve, reject }
}
export const flush = async () => {
  for (let n = 0; n < 24; n++) await Promise.resolve()
}
export const reply = (config, data, status = 200) => ({
  config,
  data,
  status,
  statusText: 'SYNTHETIC',
  headers: {},
  request: { responseType: config.responseType }
})
let counter = 0
async function load(path, dependencies, browser, environment = {}) {
  const key = `__synthetic_auth_fixture_${++counter}`
  globalThis[key] = { dependencies, browser }
  const source = readFileSync(new URL(`../../src/${path}`, import.meta.url), 'utf8')
  const output = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext }
  }).outputText
  const code = output
    .replace(/import\s+([\s\S]*?)\s+from\s+['"]([^'"]+)['"];?/g, (_, clause, specifier) => {
      assert.ok(Object.hasOwn(dependencies, specifier), `unexpected dependency: ${specifier}`)
      const port = `globalThis[${JSON.stringify(key)}].dependencies[${JSON.stringify(specifier)}]`
      return clause.trim().startsWith('{')
        ? `const ${clause} = ${port};`
        : `const ${clause} = ${port}.default;`
    })
    .replace(
      /import\.meta\.env/g,
      JSON.stringify({ VITE_APP_TENANT_ENABLE: 'true', VITE_PORTAL_MODE: 'system', ...environment })
    )
  try {
    return await import(
      `data:text/javascript;base64,${Buffer.from(
        `const window = globalThis[${JSON.stringify(key)}].browser;
const useI18n = () => ({ t: value => value });
${code}`
      ).toString('base64')}`
    )
  } finally {
    delete globalThis[key]
  }
}

export async function openAuthService(options = {}) {
  const scope = createAuthSessionScope(),
    cache = new Map(),
    requests = [],
    refreshes = [],
    notices = [],
    events = new Map()
  const dialog = deferred()
  let location = options.location ?? 'https://synthetic.invalid/rehab',
    reloads = 0,
    resets = 0,
    cacheDeletes = 0,
    encryptions = 0,
    intentClears = 0
  const browser = {
    addEventListener: (name, callback) => {
      events.set(name, callback)
    },
    location: {
      get href() {
        return location
      },
      set href(value) {
        location = value
        reloads++
      }
    }
  }
  const CACHE_KEY = {
    TenantId: 'tenantId',
    VisitTenantId: 'visitTenantId',
    USER: 'user',
    LoginForm: 'loginForm'
  }
  const cacheModule = {
    CACHE_KEY,
    useCache: () => ({
      wsCache: {
        get: (key) => cache.get(key),
        set: (key, value) => cache.set(key, value),
        delete: (key) => cache.delete(key)
      }
    }),
    deleteUserCache: () => {
      cacheDeletes++
      cache.delete('user')
      cache.delete('visitTenantId')
    }
  }
  const auth = await load(
    'utils/auth.ts',
    {
      '@/hooks/web/useCache': cacheModule,
      '@/utils/reportIntent': {
        invalidateReportIntents: () => {
          intentClears++
        }
      },
      '@/utils/authSession': { authSessionScope: scope }
    },
    browser,
    options.environment
  )
  auth.setTenantId(71)
  auth.setToken({ accessToken: 'SYNTHETIC_OLD_ACCESS', refreshToken: 'SYNTHETIC_REFRESH' })
  const resourceAdapter = async (config) => {
    requests.push(config)
    return options.resource
      ? options.resource(config, requests.length)
      : reply(config, {
          code: config.headers.Authorization === 'Bearer SYNTHETIC_NEW_ACCESS' ? 200 : 401,
          data: { accepted: true }
        })
  }
  const refreshHttp = axios.create({
    adapter: async (config) => {
      refreshes.push(config)
      return options.refresh
        ? options.refresh(config, refreshes.length)
        : reply(config, {
            code: 200,
            data: { accessToken: 'SYNTHETIC_NEW_ACCESS', refreshToken: 'SYNTHETIC_NEW_REFRESH' }
          })
    }
  })
  const facade = {
    default: {
      create: (config) => axios.create({ ...config, adapter: resourceAdapter }),
      post: refreshHttp.post.bind(refreshHttp)
    }
  }
  const element = {
    ElMessage: { error: (value) => notices.push({ kind: 'error', value }) },
    ElNotification: { error: (value) => notices.push({ kind: 'notification', value }) },
    ElMessageBox: {
      confirm: () => {
        notices.push({ kind: 'login' })
        return dialog.promise
      }
    }
  }
  const module = await load(
    'config/axios/service.ts',
    {
      axios: facade,
      'element-plus': element,
      qs: { default: qs },
      '@/config/axios/config': {
        config: {
          base_url: 'https://synthetic.invalid/admin-api',
          result_code: 200,
          request_timeout: 30000
        }
      },
      '@/utils/auth': auth,
      './errorCode': { default: { default: '操作未完成' } },
      '@/router': {
        resetRouter: () => {
          resets++
        }
      },
      '@/hooks/web/useCache': cacheModule,
      '@/utils/encrypt': {
        ApiEncrypt: {
          getEncryptHeader: () => 'X-Api-Encrypt',
          encryptRequest: (value) => {
            encryptions++
            return `SYNTHETIC_ENCRYPTED_${JSON.stringify(value)}`
          },
          decryptResponse: (value) => JSON.parse(value)
        }
      },
      './privacy': privacy,
      '@/utils/authSession': { authSessionScope: scope },
      './refreshCoordinator': recovery
    },
    browser,
    options.environment
  )
  return {
    ...module,
    auth,
    scope,
    cache,
    requests,
    refreshes,
    notices,
    dialog,
    statistics: () => ({ reloads, resets, cacheDeletes, encryptions, intentClears }),
    storageEvent: (key) => events.get('storage')?.({ key }),
    close: () => {
      scope.reset()
    }
  }
}
