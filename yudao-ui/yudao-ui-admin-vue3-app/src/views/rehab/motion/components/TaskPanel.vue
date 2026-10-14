<template>
  <div>
    <div class="toolbar">
      <el-button
        type="primary"
        v-hasPermi="['rehab:motion:process']"
        :disabled="locked || hasActive"
        :loading="submitting"
        @click="process"
      >
        {{ assessment.analyzedRevision ? '重新计算' : '开始处理' }}
      </el-button>
      <el-button @click="load">刷新</el-button>
      <span class="muted">
        处理为异步任务：{{ assessment.dataSource === 'opencap' ? 'OpenCap 处理中 → 下载结果 → ' : '' }}数据解析 → 规则计算 →
        AI 报告生成 → 待治疗师审核。可随时取消；失败后可从失败步骤重试。同一次点击重复提交不会产生重复任务。
      </span>
    </div>
    <el-table :data="tasks" size="small" class="mt-8px">
      <el-table-column label="#" prop="id" width="70" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ TYPE_LABEL[row.taskType] || row.taskType }}</template>
      </el-table-column>
      <el-table-column label="状态" width="140">
        <template #default="{ row }">
          <el-tag size="small" :type="(STATE_TAG[row.state] as any) || 'info'">{{ row.stateLabel || row.state }}</el-tag>
          <el-tag v-if="row.cancelRequested && !isTerminal(row.state)" size="small" type="warning">取消中</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="180">
        <template #default="{ row }"><el-progress :percentage="row.progress || 0" :stroke-width="8" /></template>
      </el-table-column>
      <el-table-column label="步骤说明" prop="stepMessage" min-width="160" show-overflow-tooltip />
      <el-table-column label="尝试" width="80">
        <template #default="{ row }">{{ row.attempts }}/{{ row.maxAttempts }}</template>
      </el-table-column>
      <el-table-column label="错误" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.errorCode" class="err">{{ row.errorCode }}：{{ row.errorMessage }}</span>
          <span v-if="row.failedState" class="muted">（失败于 {{ row.failedState }}）</span>
        </template>
      </el-table-column>
      <el-table-column label="下次执行" width="150">
        <template #default="{ row }">{{ !isTerminal(row.state) && row.nextRunTime ? formatDate(row.nextRunTime) : '' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button
            v-if="!isTerminal(row.state)"
            link
            type="warning"
            v-hasPermi="['rehab:motion:process']"
            @click="cancel(row)"
          >
            取消
          </el-button>
          <el-button
            v-if="row.state === 'FAILED' || row.state === 'CANCELLED'"
            link
            type="primary"
            v-hasPermi="['rehab:motion:process']"
            :disabled="locked || hasActive"
            @click="retry(row)"
          >
            重试
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script lang="ts" setup>
import { MotionApi, MotionAssessmentVO, MotionTaskVO, newIdempotencyKey } from '@/api/rehab/motion'
import { formatDate } from '@/utils/formatTime'
import { STATE_TAG } from '../constants'

const props = defineProps<{ assessment: MotionAssessmentVO }>()
const emit = defineEmits(['changed'])
const message = useMessage()

const TYPE_LABEL: Record<string, string> = { PIPELINE: '分析', RESCORE: '重新计算', AI: 'AI 草稿', PDF: 'PDF' }
const TERMINAL = ['COMPLETED', 'FAILED', 'CANCELLED', 'PENDING_REVIEW']
const isTerminal = (s: string) => TERMINAL.includes(s)

const tasks = ref<MotionTaskVO[]>([])
const submitting = ref(false)
const locked = computed(() => props.assessment.status === 'COMPLETED')
const hasActive = computed(() => tasks.value.some((t) => !isTerminal(t.state) && t.taskType !== 'PDF'))
let timer: ReturnType<typeof setTimeout> | undefined
let pendingKey: string | undefined

const load = async () => {
  const before = tasks.value.filter((t) => !isTerminal(t.state)).map((t) => t.id)
  tasks.value = (await MotionApi.listTasks(props.assessment.id)) || []
  const finished = before.some((id) => {
    const t = tasks.value.find((x) => x.id === id)
    return t && isTerminal(t.state)
  })
  if (finished) emit('changed')
  schedule()
}
const schedule = () => {
  if (timer) clearTimeout(timer)
  if (tasks.value.some((t) => !isTerminal(t.state))) timer = setTimeout(load, 3000)
}

const process = async () => {
  submitting.value = true
  // 同一次操作的网络重试复用同一幂等键
  pendingKey = pendingKey || newIdempotencyKey('proc')
  try {
    await MotionApi.process(props.assessment.id, pendingKey)
    pendingKey = undefined
    message.success('已提交处理任务')
    await load()
    emit('changed')
  } finally {
    submitting.value = false
  }
}
const cancel = async (t: MotionTaskVO) => {
  await message.confirm('确认取消该任务？已完成的步骤结果会保留。')
  await MotionApi.cancelTask(t.id)
  await load()
  emit('changed')
}
const retry = async (t: MotionTaskVO) => {
  await MotionApi.retryTask(t.id, newIdempotencyKey('retry'))
  message.success('已重新排队')
  await load()
  emit('changed')
}

onMounted(load)
onBeforeUnmount(() => timer && clearTimeout(timer))
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.err {
  color: var(--el-color-danger);
}
</style>
