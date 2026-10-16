<template>
  <ContentWrap v-loading="loading">
    <div class="head" v-if="a">
      <div>
        <p class="eyebrow">MOTION ASSESSMENT / 评估详情</p>
        <div class="title">
          动作评估 #{{ a.id }} · {{ a.patientName || '患者 ' + a.patientId }}
          <el-tag :type="(STATE_TAG[a.status] as any) || 'info'" class="ml-8px">{{
            a.statusLabel || a.status
          }}</el-tag>
          <el-tag v-if="a.stale" type="warning" class="ml-4px">输入已修改，需重新计算</el-tag>
        </div>
        <div class="sub">
          {{ visitLabel(a.visitType) }} · {{ (a.protocolFamilies || []).join(' / ') }} · 相机
          {{ a.cameraCount }} 台 · 模型 {{ a.modelName || 'LaiUhlrich2022' }} · 视频授权
          {{ a.videoConsent ? '是' : '否' }} · AI
          {{ a.aiAllowed ? '允许' : '关闭' }}
        </div>
        <div class="sub" v-if="a.engineVersion">
          引擎 {{ a.engineVersion }} · 规则 {{ a.ruleVersion }} · 结果格式
          {{ a.resultSchemaVersion }} · 分析版本 {{ a.analyzedRevision }} / 输入版本
          {{ a.inputRevision }}
        </div>
      </div>
      <div class="head-actions">
        <el-button @click="back"><Icon icon="ep:arrow-left" class="mr-4px" />返回列表</el-button>
        <el-button v-if="a" @click="openPatient"
          ><Icon icon="ep:user" class="mr-4px" />患者档案</el-button
        >
        <el-button :loading="loading" @click="reload"
          ><Icon icon="ep:refresh" class="mr-4px" />刷新</el-button
        >
        <el-button type="primary" plain @click="goNext">{{ nextAction.label }}</el-button>
      </div>
    </div>
    <el-alert
      v-if="a && (a.status === 'FAILED' || a.status === 'CANCELLED')"
      :type="a.status === 'FAILED' ? 'error' : 'info'"
      :closable="false"
      show-icon
      class="assessment-notice"
      :title="
        a.status === 'FAILED'
          ? '本次处理失败，请检查任务信息与输入数据；不要将未通过的计算视为有效结果。'
          : '本次处理已取消，当前状态不代表评估完成。'
      "
    />
    <el-alert
      v-else-if="a?.stale"
      type="warning"
      :closable="false"
      show-icon
      class="assessment-notice"
      title="输入已更改，展示结果可能不是最新版本。请重新分析并经治疗师复核后使用。"
    />
    <el-alert
      v-else-if="a && !a.signedTime"
      type="info"
      :closable="false"
      show-icon
      class="assessment-notice"
      title="当前评估尚未签署；系统分析与 AI 草稿不能替代治疗师的人工确认。"
    />
    <p v-if="a" class="steps-swipe-hint">流程共有六个阶段，窄屏可向右滑动查看后续步骤。</p>
    <div
      v-if="a"
      class="steps-scroll"
      aria-label="评估流程进度（可水平滚动）"
      role="region"
      tabindex="0"
    >
      <el-steps :active="stepIndex" finish-status="success" simple class="assessment-steps">
        <el-step title="待上传" />
        <el-step :title="a.dataSource === 'opencap' ? 'OpenCap处理' : '上传'" />
        <el-step title="解析/规则计算" />
        <el-step title="AI 草稿" />
        <el-step title="治疗师审核" />
        <el-step title="已签署" />
      </el-steps>
    </div>
    <div v-if="a" class="detail-summary" role="region" aria-label="评估状态摘要">
      <div class="detail-summary-card"
        ><span class="summary-kicker">当前状态</span><strong>{{ a.statusLabel || a.status }}</strong
        ><small>{{ nextAction.hint }}</small></div
      >
      <div class="detail-summary-card"
        ><span class="summary-kicker">数据质量</span
        ><strong :class="qualityTone">{{ qualityLabel }}</strong
        ><small>{{ result?.sessionQuality?.issues?.length || 0 }} 项质控记录</small></div
      >
      <div class="detail-summary-card"
        ><span class="summary-kicker">OpenSim / 动力学</span><strong>{{ dynamicsLabel }}</strong
        ><small>{{ dynamicsHint }}</small></div
      >
      <div class="detail-summary-card"
        ><span class="summary-kicker">报告签发</span
        ><strong>{{ a.signedTime ? '已签署' : '待治疗师签署' }}</strong
        ><small>{{
          a.signedTime ? formatDate(a.signedTime) : 'AI 草稿不能代替正式报告'
        }}</small></div
      >
    </div>
    <el-empty v-else-if="!loading" :description="errorMessage || '评估记录不存在或无权查看'">
      <el-button type="primary" @click="back">返回列表</el-button>
      <el-button @click="reload">重试加载</el-button>
    </el-empty>
  </ContentWrap>

  <ContentWrap v-if="a">
    <el-tabs v-model="tab" class="assessment-tabs">
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
import { formatDate } from '@/utils/formatTime'
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
const errorMessage = ref('')
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
  () =>
    (result.value?.scores || []).filter(
      (s: any) => !['confirmed', 'modified', 'not_applicable'].includes(s.finalStatus)
    ).length
)
const qualityLabel = computed(
  () =>
    (({ pass: '通过', warn: '需关注', fail: '未通过' }) as Record<string, string>)[
      result.value?.sessionQuality?.status
    ] || '待检查'
)
const qualityTone = computed(
  () =>
    (
      ({ pass: 'tone-success', warn: 'tone-warning', fail: 'tone-danger' }) as Record<
        string,
        string
      >
    )[result.value?.sessionQuality?.status] || ''
)
const dynamicsLabel = computed(() =>
  result.value?.opensim_dynamics?.status === 'pass'
    ? '已完成'
    : result.value?.opensim_dynamics?.status === 'blocked'
      ? '等待载荷'
      : result.value?.opensim?.status === 'pass'
        ? '模型已校验'
        : '未请求'
)
const dynamicsHint = computed(() =>
  result.value?.opensim_dynamics?.reason === 'EXTERNAL_LOADS_MISSING'
    ? '缺少外部载荷，不生成力矩或肌肉力'
    : '仅作为辅助分析证据，不进入正式评分'
)
const nextAction = computed(() => {
  if (!a.value) return { label: '查看审核', hint: '等待评估记录加载', tab: 'review' }
  if (a.value.status === 'WAITING_UPLOAD')
    return { label: '开始上传', hint: '先上传 OpenCap 导出文件或读取会话 Trial', tab: 'upload' }
  if (
    [
      'UPLOADING',
      'OPENCAP_PROCESSING',
      'DOWNLOADING',
      'PARSING',
      'RULES',
      'AI_GENERATING'
    ].includes(a.value.status)
  )
    return { label: '查看任务', hint: '异步任务正在运行，关闭页面后仍会继续', tab: 'tasks' }
  if (a.value.status === 'PENDING_REVIEW')
    return { label: '进入审核', hint: '系统建议分和 AI 草稿都需要治疗师确认', tab: 'review' }
  return {
    label: '查看报告',
    hint: a.value.signedTime ? '正式报告已经签署，可查看和导出' : '报告仍需治疗师审核后签署',
    tab: a.value.signedTime ? 'reports' : 'review'
  }
})

const reload = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    if (!Number.isSafeInteger(id.value) || id.value <= 0) {
      errorMessage.value = '评估编号无效'
      a.value = undefined
      result.value = {}
      return
    }
    a.value = await MotionApi.get(id.value)
    result.value = (await MotionApi.result(id.value)) || {}
    if (tab.value === 'upload' && a.value && a.value.analyzedRevision) {
      tab.value = a.value.status === 'PENDING_REVIEW' ? 'review' : 'result'
    }
  } catch {
    a.value = undefined
    result.value = {}
    errorMessage.value = '评估详情加载失败或没有访问权限，请返回列表或稍后重试。'
  } finally {
    loading.value = false
  }
}
const back = () =>
  a.value ? push(`/rehab/motion?patientId=${a.value.patientId}`) : push('/rehab/motion')
const openPatient = () => a.value?.patientId && push(`/rehab/patient/detail/${a.value.patientId}`)
const goNext = () => {
  tab.value = nextAction.value.tab
}

onMounted(reload)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
}
.eyebrow {
  margin: 0 0 7px;
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.head-actions {
  display: flex;
  flex-wrap: wrap;
  flex-shrink: 0;
  gap: 8px;
}
.head-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.title {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.55;
}
.sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.65;
}
.assessment-notice {
  margin-top: 16px;
}
.detail-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}
.detail-summary-card {
  min-height: 94px;
  padding: 13px 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 11px;
  background: var(--el-fill-color-lighter);
}
.detail-summary-card strong,
.detail-summary-card small,
.summary-kicker {
  display: block;
}
.summary-kicker {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
.detail-summary-card strong {
  margin-top: 8px;
  color: var(--el-text-color-primary);
  font-size: 16px;
}
.detail-summary-card small {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1.45;
}
.detail-summary-card .tone-success {
  color: var(--el-color-success);
}
.detail-summary-card .tone-warning {
  color: var(--el-color-warning);
}
.detail-summary-card .tone-danger {
  color: var(--el-color-danger);
}
.steps-scroll {
  overflow-x: auto;
  margin-top: 16px;
  overscroll-behavior-inline: contain;
}
.steps-scroll:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 3px;
}
.steps-swipe-hint {
  display: none;
  margin: 12px 0 -8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.assessment-steps {
  min-width: 740px;
}
.assessment-tabs {
  min-width: 0;
}
@media (max-width: 850px) {
  .steps-swipe-hint {
    display: block;
  }
  .detail-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .head {
    flex-direction: column;
  }
  .head-actions {
    width: 100%;
  }
}
@media (max-width: 520px) {
  .detail-summary {
    grid-template-columns: 1fr;
  }
  .head-actions :deep(.el-button) {
    flex: 1 1 auto;
  }
  .title {
    font-size: 16px;
  }
}
</style>
