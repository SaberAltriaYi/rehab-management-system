<template>
  <el-alert
    type="info"
    :closable="false"
    show-icon
    title="原生运动学工作区：患者与评估由主系统绑定。提交和接收仅传输结构化证据，不上传原片、不覆盖原评分或已签报告。"
  />
  <div class="workspace-actions">
    <el-button v-hasPermi="['rehab:motion:workspace']" :loading="busy" @click="launch"
      >获取工作区访问入口</el-button
    >
    <el-link v-if="entry" :href="entry" target="_blank" rel="noopener noreferrer" type="primary"
      >进入独立运动评估页面（60秒内使用一次）</el-link
    >
    <el-button v-hasPermi="['rehab:motion:sync']" :loading="busy" @click="receive"
      >接收已提交的评估结果</el-button
    >
    <el-button @click="refresh">刷新同步历史</el-button>
  </div>
  <p
    >流程：进入工作区 → 导入原件 → 核对坐标/模型/关键帧 → 采纳或拒绝候选结果 → 完成附件复核 →
    确认数据 → 提交主系统 → 回此处接收 →
    治疗师复核。工作区会话10分钟失效，失效后请重新从本页进入。</p
  >
  <el-table
    :data="history"
    @row-click="select"
    empty-text="尚无接收结果；请先在工作区提交并返回接收"
  >
    <el-table-column prop="source_revision" label="来源修订" width="100" />
    <el-table-column prop="core_revision" label="主系统输入修订" width="150" />
    <el-table-column prop="received_at" label="接收时间" />
    <el-table-column prop="review_status" label="临床复核状态"
      ><template #default="{ row }">{{
        row.review_status === 'reviewed' ? '已复核' : '待临床复核'
      }}</template></el-table-column
    >
    <el-table-column prop="sha256" label="快照 SHA-256" show-overflow-tooltip />
  </el-table>
  <div v-if="snapshot">
    <el-alert
      v-if="chosen?.core_revision !== assessment.inputRevision"
      type="warning"
      :closable="false"
      title="此历史版本与当前主系统输入修订不同，仅可追溯，不能直接复核为当前结果。"
    />
    <p v-if="chosen?.review_status === 'reviewed'">复核结论：{{ chosen.review_note }}</p>
    <h4>来源结果（工程证据，不自动成为现场评分）</h4>
    <el-table :data="snapshot.observations || []">
      <el-table-column prop="module" label="板块" />
      <el-table-column prop="label" label="项目" min-width="200" />
      <el-table-column prop="value" label="已确认值" />
      <el-table-column prop="unit" label="单位" />
      <el-table-column prop="source_id" label="来源" />
    </el-table>
    <el-collapse
      ><el-collapse-item title="来源哈希、试次可用性和同步依据">
        <pre>{{ JSON.stringify(snapshot, null, 2) }}</pre>
      </el-collapse-item></el-collapse
    >
    <el-input
      v-if="chosen?.review_status === 'pending'"
      v-model="reviewNote"
      type="textarea"
      :maxlength="1000"
      placeholder="填写来源、适用范围及临床复核结论；不自动改写原评分"
    />
    <el-button
      v-if="chosen?.review_status === 'pending'"
      v-hasPermi="['rehab:motion:review']"
      @click="review"
      :disabled="chosen?.core_revision !== assessment.inputRevision"
      >确认此版本复核</el-button
    >
  </div>
</template>
<script setup lang="ts">
import { MotionApi, MotionAssessmentVO } from '@/api/rehab/motion'
import { checkPermi } from '@/utils/permission'
const props = defineProps<{ assessment: MotionAssessmentVO }>()
const message = useMessage(),
  busy = ref(false),
  entry = ref(''),
  history = ref<any[]>([]),
  snapshot = ref<any>()
const chosen = ref<any>(),
  reviewNote = ref('')
async function refresh() {
  if (checkPermi(['rehab:motion:workspace'])) {
    const id = props.assessment.id
    const rows = await MotionApi.workspaceHistory(id)
    if (id !== props.assessment.id) return
    history.value = rows
    if (history.value.length) select(history.value[0])
  }
}
function select(row: any) {
  chosen.value = row
  reviewNote.value = ''
  try {
    snapshot.value = JSON.parse(row.snapshot_json)
  } catch {
    snapshot.value = null
    message.error('来源快照格式不正确')
  }
}
async function review() {
  if (!chosen.value || !reviewNote.value.trim()) {
    message.error('请填写复核结论')
    return
  }
  const id = props.assessment.id,
    inboxId = chosen.value.id,
    note = reviewNote.value
  await message.confirm('确认此版本工程证据已完成临床复核？此操作不会自动填写现场评分或签发报告。')
  if (id !== props.assessment.id) return
  await MotionApi.workspaceReview(id, inboxId, note)
  await refresh()
  message.success('此版本已复核，原评分与报告保持不变')
}
async function launch() {
  const id = props.assessment.id
  busy.value = true
  try {
    const url = (await MotionApi.workspaceLaunch(id)).url
    if (id !== props.assessment.id) return
    entry.value = url
    setTimeout(() => {
      if (entry.value === url) entry.value = ''
    }, 60000)
  } finally {
    busy.value = false
  }
}
async function receive() {
  const id = props.assessment.id
  await message.confirm('接收已明确提交的结果快照；不会覆盖原评分与签署报告。确认接收？')
  if (id !== props.assessment.id) return
  busy.value = true
  try {
    const r = await MotionApi.workspaceReceive(id)
    if (id !== props.assessment.id) return
    message.success(
      r.state === 'already_received'
        ? '该修订已接收，无需重复导入'
        : '已接收，请核对来源和临床可用性'
    )
    await refresh()
  } finally {
    busy.value = false
  }
}
onMounted(refresh)
watch(
  () => props.assessment.id,
  () => {
    entry.value = ''
    history.value = []
    snapshot.value = null
    chosen.value = null
    reviewNote.value = ''
    refresh()
  }
)
</script>
<style scoped>
.workspace-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin: 16px 0;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
