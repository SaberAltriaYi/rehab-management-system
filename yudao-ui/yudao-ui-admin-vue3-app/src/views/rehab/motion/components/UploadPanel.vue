<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="mb-12px">
      <template #title>
        选择 OpenCap 导出的整个文件夹（OpenCapData_xxx），系统按服务端策略自动筛选并逐个上传：只上传
        sessionMetadata.yaml、Kinematics/*.mot、MarkerData/*.trc、Model/*.osim{{ assessment.videoConsent ? ' 与 InputMedia 原始视频' : '' }}；
        pickle/pkl、vtp 几何、OutputMedia、图片、日志自动跳过{{ assessment.videoConsent ? '' : '；未取得视频授权，视频一律不上传' }}。
        单文件上限：{{ limitsText }}。
      </template>
    </el-alert>

    <div class="toolbar" v-hasPermi="['rehab:motion:upload']">
      <el-button type="primary" :disabled="locked || running" @click="folderInput?.click()">选择 OpenCap 文件夹</el-button>
      <el-button :disabled="locked || running" @click="fileInput?.click()">选择文件</el-button>
      <el-button type="success" :disabled="locked || running || summary.queued === 0" @click="start">
        开始逐个上传（{{ summary.queued }} 个，{{ formatBytes(summary.bytesQueued) }}）
      </el-button>
      <el-button v-if="running" type="warning" @click="stopRequested = true">停止</el-button>
      <el-button v-if="!running && summary.failed > 0" @click="retryFailed">重试失败项（{{ summary.failed }}）</el-button>
      <input ref="folderInput" type="file" webkitdirectory multiple class="hidden" @change="onPick" />
      <input ref="fileInput" type="file" multiple class="hidden" @change="onPick" />
    </div>

    <template v-if="items.length">
      <div class="summary">
        共 {{ summary.total }} 个文件：待上传 {{ summary.queued }}，已完成 {{ summary.done }}，失败 {{ summary.failed }}，跳过
        {{ summary.skipped }}
        <el-progress v-if="running || summary.done" :percentage="progress" :stroke-width="10" class="mt-8px" />
        <el-alert
          v-if="!summary.hasRequiredMot && !hasServerKind('mot')"
          type="warning"
          :closable="false"
          title="未发现 OpenSimData/Kinematics/*.mot，无法进行运动学分析"
          class="mt-8px"
        />
      </div>
      <el-collapse class="mt-8px">
        <el-collapse-item :title="`跳过原因（${summary.skipped}）`" name="skip">
          <el-table :data="skipRows" size="small">
            <el-table-column prop="reason" label="原因" />
            <el-table-column prop="count" label="文件数" width="100" />
          </el-table>
        </el-collapse-item>
      </el-collapse>
      <el-radio-group v-model="filter" size="small" class="mt-8px">
        <el-radio-button value="active">待上传/失败</el-radio-button>
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="skipped">已跳过</el-radio-button>
      </el-radio-group>
      <el-table :data="visibleItems" size="small" max-height="360" class="mt-8px">
        <el-table-column label="相对路径" prop="relativePath" min-width="320" show-overflow-tooltip />
        <el-table-column label="类型" prop="kind" width="80" />
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ formatBytes(row.file.size) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="(statusTag[row.status] as any)">{{ statusText[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="尝试" prop="attempts" width="60" />
        <el-table-column label="说明" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.error || row.reason || '' }}</template>
        </el-table-column>
      </el-table>
      <div v-if="filteredCount > visibleItems.length" class="muted">仅显示前 {{ visibleItems.length }} 条</div>
    </template>

    <el-divider content-position="left">服务端已保存的文件（{{ serverFiles.length }}）</el-divider>
    <el-table :data="serverFiles" size="small" max-height="360">
      <el-table-column label="相对路径" prop="relativePath" min-width="320" show-overflow-tooltip />
      <el-table-column label="类型" prop="fileKind" width="80" />
      <el-table-column label="Trial" prop="trialName" width="160" />
      <el-table-column label="相机" prop="cameraKey" width="80" />
      <el-table-column label="大小" width="90">
        <template #default="{ row }">{{ formatBytes(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" v-hasPermi="['rehab:motion:export']" @click="downloadFile(row)">下载</el-button>
          <el-button link type="danger" v-hasPermi="['rehab:motion:upload']" :disabled="locked" @click="removeFile(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <template v-if="assessment.dataSource === 'opencap'">
      <el-divider content-position="left">OpenCap 会话</el-divider>
      <div class="toolbar">
        <span>会话 ID：{{ assessment.opencapSessionId || '未填写' }}</span>
        <el-button
          v-hasPermi="['rehab:motion:process']"
          :disabled="!assessment.opencapSessionId || locked"
          :loading="opencapLoading"
          @click="loadOpencapTrials"
        >
          读取会话 Trial 列表
        </el-button>
      </div>
      <el-table v-if="opencapTrials.length" :data="opencapTrials" size="small">
        <el-table-column v-for="k in opencapColumns" :key="k" :prop="k" :label="k" show-overflow-tooltip />
      </el-table>
      <div class="muted">读取到的 Trial 名称请在“Trial 映射”中与测试项目对应；处理任务会通过引擎从 OpenCap 下载结果。</div>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { MotionApi, MotionAssessmentVO, MotionFileVO } from '@/api/rehab/motion'
import download from '@/utils/download'
import {
  formatBytes,
  planUploads,
  QueueItem,
  requeueFailed,
  runQueue,
  summarize,
  UploadPolicy
} from '../uploadQueue'

const props = defineProps<{ assessment: MotionAssessmentVO }>()
const emit = defineEmits(['changed'])
const message = useMessage()

const policy = ref<UploadPolicy>()
const serverFiles = ref<MotionFileVO[]>([])
const items = ref<QueueItem<File>[]>([])
const running = ref(false)
const stopRequested = ref(false)
const filter = ref<'active' | 'all' | 'skipped'>('active')
const folderInput = ref<HTMLInputElement>()
const fileInput = ref<HTMLInputElement>()
const opencapTrials = ref<any[]>([])
const opencapLoading = ref(false)
const tick = ref(0)

const locked = computed(() => props.assessment.status === 'COMPLETED')
const summary = computed(() => {
  void tick.value
  return summarize(items.value as QueueItem[])
})
const progress = computed(() => {
  const s = summary.value
  const n = s.done + s.failed + s.queued
  return n ? Math.round((100 * s.done) / n) : 0
})
const skipRows = computed(() =>
  Object.entries(summary.value.skippedByReason).map(([reason, count]) => ({ reason, count }))
)
const filtered = computed(() => {
  void tick.value
  return items.value.filter((i) =>
    filter.value === 'all'
      ? true
      : filter.value === 'skipped'
        ? i.status === 'skipped'
        : i.status !== 'skipped' && i.status !== 'done'
  )
})
const filteredCount = computed(() => filtered.value.length)
const visibleItems = computed(() => filtered.value.slice(0, 300))
const opencapColumns = computed(() => (opencapTrials.value[0] ? Object.keys(opencapTrials.value[0]).slice(0, 6) : []))
const limitsText = computed(() =>
  (policy.value?.rules || []).map((r) => `${r.kind} ${formatBytes(r.maxBytes)}`).join('，') || '加载中'
)
const statusText: Record<string, string> = {
  queued: '待上传',
  uploading: '上传中',
  done: '已完成',
  failed: '失败',
  skipped: '跳过',
  cancelled: '已停止'
}
const statusTag: Record<string, string> = {
  queued: 'info',
  uploading: 'warning',
  done: 'success',
  failed: 'danger',
  skipped: 'info',
  cancelled: 'info'
}

const hasServerKind = (k: string) => serverFiles.value.some((f) => f.fileKind === k)

const loadServer = async () => {
  serverFiles.value = (await MotionApi.listFiles(props.assessment.id)) || []
}

const onPick = (e: Event) => {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!policy.value || !files.length) return
  items.value = planUploads(files, policy.value, {
    videoConsent: !!props.assessment.videoConsent,
    existing: serverFiles.value.map((f) => f.relativePath)
  })
  filter.value = 'active'
  tick.value++
}

const start = async () => {
  if (!policy.value) return
  running.value = true
  stopRequested.value = false
  try {
    const s = await runQueue(items.value, (it) => MotionApi.uploadFile(props.assessment.id, it.relativePath, it.file), {
      maxRetries: policy.value.maxRetries ?? 3,
      onChange: () => tick.value++,
      shouldStop: () => stopRequested.value
    })
    if (s.failed) message.warning(`上传结束：成功 ${s.done}，失败 ${s.failed}（可重试失败项）`)
    else message.success(`上传完成：${s.done} 个文件`)
  } finally {
    running.value = false
    await loadServer()
    emit('changed')
  }
}

const retryFailed = () => {
  requeueFailed(items.value as QueueItem[])
  tick.value++
  start()
}

const removeFile = async (row: MotionFileVO) => {
  await message.delConfirm(`删除 ${row.relativePath}？删除后需重新计算。`)
  await MotionApi.deleteFile(row.id)
  await loadServer()
  emit('changed')
}

const downloadFile = async (row: MotionFileVO) => {
  const blob: any = await MotionApi.downloadFile(row.id)
  const name = row.relativePath.substring(row.relativePath.lastIndexOf('/') + 1)
  download.json(blob, name)
}

const loadOpencapTrials = async () => {
  opencapLoading.value = true
  try {
    opencapTrials.value = (await MotionApi.opencapTrials(props.assessment.id, props.assessment.opencapSessionId!)) || []
  } finally {
    opencapLoading.value = false
  }
}

onMounted(async () => {
  policy.value = await MotionApi.uploadPolicy()
  await loadServer()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.hidden {
  display: none;
}
.summary {
  margin-top: 12px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 4px;
}
</style>
