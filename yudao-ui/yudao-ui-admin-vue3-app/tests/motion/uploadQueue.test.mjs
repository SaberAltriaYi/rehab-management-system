// 运行：node --experimental-strip-types --test tests/motion/uploadQueue.test.mjs
// 使用真实 OpenCap 导出目录的文件清单（仅路径与大小，会话 ID 已替换）和服务端策略快照。
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  candidatePaths,
  planUploads,
  requeueFailed,
  runQueue,
  summarize,
  defaultIsRetryable
} from '../../src/views/rehab/motion/uploadQueue.ts'

const here = dirname(fileURLToPath(import.meta.url))
const policy = JSON.parse(
  readFileSync(join(here, '../../../../yudao-module-rehab/src/test/resources/motion/upload-policy.json'), 'utf8')
)
const listing = readFileSync(join(here, 'opencap-listing.txt'), 'utf8')
  .split('\n')
  .filter(Boolean)
  .map((line) => {
    const i = line.indexOf(' ')
    const path = line.substring(i + 1)
    return { name: path.substring(path.lastIndexOf('/') + 1), size: Number(line.substring(0, i)), webkitRelativePath: path }
  })

test('real OpenCap export: with video consent keeps 65 analysis files, skips 116 with reasons', () => {
  assert.equal(listing.length, 181)
  const items = planUploads(listing, policy, { videoConsent: true })
  const s = summarize(items)
  assert.equal(s.queued, 65)
  assert.equal(s.skipped, 116)
  assert.ok(s.hasMetadata && s.hasRequiredMot)
  const kinds = {}
  for (const it of items.filter((x) => x.status === 'queued')) kinds[it.kind] = (kinds[it.kind] || 0) + 1
  assert.deepEqual(kinds, { metadata: 1, mot: 7, trc: 8, osim: 1, video: 48 })
  assert.equal(s.skippedByReason['模型几何网格，分析不需要'], 81)
  assert.equal(s.skippedByReason['pickle 文件存在反序列化风险，禁止上传'], 28)
  assert.equal(s.skippedByReason['系统文件'], 3)
  assert.equal(s.skippedByReason['图片，分析不需要'], 3)
  assert.equal(s.skippedByReason['日志/文本，分析不需要'], 1)
  // 结构化数据先传：第一个是 sessionMetadata.yaml，其后是 mot
  assert.equal(items[0].kind, 'metadata')
  assert.equal(items[1].kind, 'mot')
  assert.ok(items.every((x) => x.status !== 'queued' || x.relativePath.startsWith('OpenCapData_')))
})

test('without video consent all videos are skipped and never uploaded', () => {
  const items = planUploads(listing, policy, { videoConsent: false })
  const s = summarize(items)
  assert.equal(s.queued, 17)
  assert.equal(s.skippedByReason['未取得患者视频授权，视频不上传'], 48)
  assert.ok(items.filter((x) => x.kind === 'video').every((x) => x.status === 'skipped'))
})

test('oversize, empty, traversal, duplicate and already-uploaded files are skipped', () => {
  const big = { name: 'a.mov', size: 17 * 1024 * 1024, webkitRelativePath: 'S/Videos/Cam0/InputMedia/t/a.mov' }
  const empty = { name: 'x.mot', size: 0, webkitRelativePath: 'S/OpenSimData/Kinematics/x.mot' }
  const evil = { name: 'p.mot', size: 10, webkitRelativePath: 'S/OpenSimData/Kinematics/../../../etc/p.mot' }
  const pkl = { name: 'trial.pkl', size: 10, webkitRelativePath: 'S/Videos/Cam0/OutputPkl/trial.pkl' }
  const out = { name: 'o.mp4', size: 10, webkitRelativePath: 'S/Videos/Cam0/OutputMedia/t/o.mp4' }
  const ok1 = { name: 'y.mot', size: 10, webkitRelativePath: 'S/OpenSimData/Kinematics/y.mot' }
  const dup = { name: 'y.mot', size: 10, webkitRelativePath: 'S/OpenSimData/Kinematics/y.mot' }
  const done = { name: 'z.mot', size: 10, webkitRelativePath: 'S/OpenSimData/Kinematics/z.mot' }
  const items = planUploads([big, empty, evil, pkl, out, ok1, dup, done], policy, {
    videoConsent: true,
    existing: ['OpenSimData/Kinematics/z.mot']
  })
  const by = (f) => items.find((x) => x.file === f)
  assert.match(by(big).reason, /超过单文件上限 16\.0 MB/)
  assert.equal(by(empty).reason, '空文件')
  assert.equal(by(evil).status, 'skipped')
  assert.equal(by(pkl).status, 'skipped')
  assert.equal(by(out).reason, 'OpenCap 叠加渲染视频，分析不需要')
  assert.equal(by(ok1).status, 'queued')
  assert.equal(by(ok1).relativePath, 'OpenSimData/Kinematics/y.mot')
  assert.equal(by(dup).reason, '重复文件')
  assert.equal(by(done).reason, '服务端已存在（续传跳过）')
})

test('candidatePaths prefers the OpenCapData_ segment and normalises separators', () => {
  assert.deepEqual(candidatePaths('Downloads\\x\\OpenCapData_a-1\\sessionMetadata.yaml'), [
    'OpenCapData_a-1/sessionMetadata.yaml',
    'x/OpenCapData_a-1/sessionMetadata.yaml',
    'Downloads/x/OpenCapData_a-1/sessionMetadata.yaml'
  ])
})

test('queue uploads strictly one at a time and retries network errors with backoff', async () => {
  const items = planUploads(listing.slice(0, 40), policy, { videoConsent: true }).filter((x) => x.status === 'queued')
  assert.ok(items.length >= 3)
  let active = 0
  let maxActive = 0
  const calls = {}
  const sleeps = []
  const netErr = Object.assign(new Error('Network Error'), { isAxiosError: true })
  const summary = await runQueue(
    items,
    async (it) => {
      active++
      maxActive = Math.max(maxActive, active)
      calls[it.relativePath] = (calls[it.relativePath] || 0) + 1
      await new Promise((r) => setTimeout(r, 1))
      active--
      if (it === items[0] && calls[it.relativePath] < 3) throw netErr
    },
    { maxRetries: 3, sleep: async (ms) => void sleeps.push(ms) }
  )
  assert.equal(maxActive, 1)
  assert.equal(calls[items[0].relativePath], 3)
  assert.deepEqual(sleeps, [1000, 2000])
  assert.equal(summary.done, items.length)
  assert.equal(summary.failed, 0)
})

test('business errors are not retried; retries are capped; failed items can be requeued', async () => {
  const items = planUploads(listing, policy, { videoConsent: false }).filter((x) => x.status === 'queued').slice(0, 3)
  const http500 = { isAxiosError: true, response: { status: 503 } }
  let n0 = 0
  let n1 = 0
  await runQueue(
    items,
    async (it) => {
      if (it === items[0]) {
        n0++
        throw 'error' // 服务端业务校验失败（如内容嗅探不通过）
      }
      if (it === items[1]) {
        n1++
        throw http500
      }
    },
    { maxRetries: 3, sleep: async () => {} }
  )
  assert.equal(n0, 1)
  assert.equal(items[0].status, 'failed')
  assert.equal(n1, 4) // 1 次 + 3 次重试
  assert.equal(items[1].status, 'failed')
  assert.equal(items[1].error, 'HTTP 503')
  assert.equal(items[2].status, 'done')
  assert.equal(requeueFailed(items), 2)
  assert.equal(items[1].attempts, 0)
  assert.equal(defaultIsRetryable({ isAxiosError: true, response: { status: 400 } }), false)
  assert.equal(defaultIsRetryable({ isAxiosError: true, response: { status: 429 } }), true)
})

test('stop request cancels the remaining items', async () => {
  const items = planUploads(listing, policy, { videoConsent: false }).filter((x) => x.status === 'queued').slice(0, 4)
  let stop = false
  let n = 0
  const s = await runQueue(
    items,
    async () => {
      n++
      stop = true
    },
    { maxRetries: 3, shouldStop: () => stop }
  )
  assert.equal(n, 1)
  assert.equal(s.done, 1)
  assert.equal(items.filter((x) => x.status === 'cancelled').length, 3)
})
