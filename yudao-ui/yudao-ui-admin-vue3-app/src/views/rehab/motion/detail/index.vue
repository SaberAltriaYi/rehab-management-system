<template>
  <ContentWrap v-loading="loading">
    <div class="head" v-if="a">
      <div>
        <div class="title">
          动作评估 #{{ a.id }} · {{ a.patientName || '患者 ' + a.patientId }}
          <el-tag :type="(STATE_TAG[a.status] as any) || 'info'" class="ml-8px">{{ a.statusLabel || a.status }}</el-tag>
          <el-tag v-if="a.stale" type="warning" class="ml-4px">输入已修改，需重新计算</el-tag>
        </div>
        <div class="sub">
          {{ visitLabel(a.visitType) }} · {{ (a.protocolFamilies || []).join(' / ') }} · 相机 {{ a.cameraCount }} 台 ·
          模型 {{ a.modelName || 'LaiUhlrich2022' }} · 视频授权 {{ a.videoConsent ? '是' : '否' }} · AI
          {{ a.aiAllowed ? '允许' : '关闭' }}
        </div>
        <div class="sub" v-if="a.engineVersion">
          引擎 {{ a.engineVersion }} · 规则 {{ a.ruleVersion }} · 结果格式 {{ a.resultSchemaVersion }} · 分析版本
          {{ a.analyzedRevision }} / 输入版本 {{ a.inputRevision }}
        </div>
      </div>
      <div>
        <el-button @click="back">返回</el-button>
        <el-button @click="reload">刷新</el-button>
      </div>
    </div>
    <el-steps v-if="a" :active="stepIndex" finish-status="success" simple class="mt-12px">
      <el-step title="待上传" />
      <el-step :title="a.dataSource === 'opencap' ? 'OpenCap处理' : '上传'" />
      <el-step title="解析/规则计算" />
      <el-step title="AI 草稿" />
      <el-step title="治疗师审核" />
      <el-step title="已签署" />
    </el-steps>
  </ContentWrap>

  <ContentWrap v-if="a">
    <el-tabs v-model="tab">
      <el-tab-pane label="数据与上传" name="upload">
        <UploadPanel :assessment="a" @changed="reload" />
      </el-tab-pane>
      <el-tab-pane label="Trial 映射" name="trials">
        <TrialPanel :assessment="a" @changed="reload" />
      </el-tab-pane>
      <el-tab-pane label="处理任务" name="tasks">
        <TaskPanel :assessment="a" @changed="reload" />
      </el-tab-pane>
      <el-tab-pane label="质控与结果" name="result" lazy>
        <ResultPanel :result="result" />
      </el-tab-pane>
      <el-tab-pane label="曲线与分期" name="curves" lazy>
        <CurvePanel :assessment="a" :result="result" />
      </el-tab-pane>
      <el-tab-pane label="人工录入" name="manual" lazy>
        <ManualPanel :assessment="a" :result="result" @changed="reload" />
      </el-tab-pane>
      <el-tab-pane name="review" lazy>
        <template #label>
          审核与签署
          <el-badge v-if="pendingCount > 0" :value="pendingCount" class="ml-4px" />
        </template>
        <ReviewPanel :assessment="a" :result="result" @changed="reload" />
      </el-tab-pane>
      <el-tab-pane label="报告" name="reports" lazy>
        <ReportPanel :assessment="a" :result="result" @changed="reload" />
      </el-tab-pane>
      <el-tab-pane label="对比与趋势" name="compare" lazy>
        <ComparePanel :assessment="a" />
      </el-tab-pane>
      <el-tab-pane label="留痕与审计" name="audit" lazy>
        <AuditPanel :assessment="a" />
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>
</template>

<script lang="ts" setup>
import { MotionApi, MotionAssessmentVO } from '@/api/rehab/motion'
import { STATE_TAG, visitLabel } from '../constants'
import UploadPanel from '../components/UploadPanel.vue'
import TrialPanel from '../components/TrialPanel.vue'
import TaskPanel from '../components/TaskPanel.vue'
import ResultPanel from '../components/ResultPanel.vue'
import CurvePanel from '../components/CurvePanel.vue'
import ManualPanel from '../components/ManualPanel.vue'
import ReviewPanel from '../components/ReviewPanel.vue'
import ReportPanel from '../components/ReportPanel.vue'
import ComparePanel from '../components/ComparePanel.vue'
import AuditPanel from '../components/AuditPanel.vue'

defineOptions({ name: 'RehabMotionDetail' })

const route = useRoute()
const { push } = useRouter()
const id = computed(() => Number(route.params.id))
const loading = ref(false)
const a = ref<MotionAssessmentVO>()
const result = ref<any>({})
const tab = ref('upload')

const stepIndex = computed(() => {
  const s = a.value?.status
  if (!s || s === 'WAITING_UPLOAD') return 0
  if (s === 'UPLOADING' || s === 'OPENCAP_PROCESSING' || s === 'DOWNLOADING') return 1
  if (s === 'PARSING' || s === 'RULES') return 2
  if (s === 'AI_GENERATING') return 3
  if (s === 'PENDING_REVIEW') return 4
  if (s === 'COMPLETED') return 6
  return 1
})
const pendingCount = computed(
  () => (result.value?.scores || []).filter((s: any) => !['confirmed', 'modified', 'not_applicable'].includes(s.finalStatus)).length
)

const reload = async () => {
  loading.value = true
  try {
    a.value = await MotionApi.get(id.value)
    result.value = (await MotionApi.result(id.value)) || {}
    if (tab.value === 'upload' && a.value && a.value.analyzedRevision) {
      tab.value = a.value.status === 'PENDING_REVIEW' ? 'review' : 'result'
    }
  } finally {
    loading.value = false
  }
}
const back = () => (a.value ? push(`/rehab/motion?patientId=${a.value.patientId}`) : push('/rehab/motion'))

onMounted(reload)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
