<template>
  <ContentWrap>
    <div class="motion-hero">
      <div class="motion-heading">
        <p class="motion-eyebrow">CLINICAL WORKSPACE / 动作评估</p>
        <h2>智能动作评估</h2>
        <p>管理采集、运动学处理和审核流程。所有结果均需治疗师确认，不代表自动诊断。</p>
      </div>
      <div class="motion-toolbar">
        <el-button :loading="loading" aria-label="刷新动作评估列表" @click="load">
          <Icon icon="ep:refresh" class="mr-4px" />刷新
        </el-button>
        <el-button
          type="primary"
          v-hasPermi="['rehab:motion:create']"
          @click="formRef.open({ patientId: query.patientId })"
        >
          <Icon icon="ep:plus" class="mr-4px" />新建评估
        </el-button>
      </div>
    </div>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="OpenCap 提供运动学数据，规则引擎提供质控与计算；AI 仅辅助解释，最终由治疗师复核和签署。"
      class="motion-notice"
    />
    <div class="motion-workflow-strip" role="region" aria-label="动作评估快捷工作流">
      <button
        class="workflow-card workflow-primary"
        type="button"
        @click="formRef.open({ patientId: query.patientId })"
      >
        <span class="workflow-icon"><Icon icon="ep:plus" /></span>
        <span><strong>新建一次评估</strong><small>患者 → 协议 → OpenCap → 审核</small></span>
        <Icon icon="ep:arrow-right" class="workflow-arrow" />
      </button>
      <div class="workflow-card"
        ><span class="workflow-icon review"><Icon icon="ep:document-checked" /></span
        ><span
          ><strong>{{ pageMetrics.pendingReview }} 项待审核</strong
          ><small>先看质控和评分证据，再决定是否签署</small></span
        ></div
      >
      <div class="workflow-card"
        ><span class="workflow-icon process"><Icon icon="ep:loading" /></span
        ><span
          ><strong>{{ processingCount }} 项处理中</strong
          ><small>任务会在页面关闭后继续执行</small></span
        ></div
      >
      <div class="workflow-card workflow-note"
        ><span
          ><strong>OpenCap + 规则 + 治疗师</strong
          ><small>AI 只解释结构化结果，不直接改分或签发</small></span
        ></div
      >
    </div>
    <div
      class="motion-overview"
      role="region"
      aria-label="当前页动作评估工作概况"
      :aria-busy="loading"
    >
      <div
        v-for="item in overviewItems"
        :key="item.label"
        class="motion-overview-card"
        :class="item.tone"
      >
        <div class="motion-overview-label">
          <Icon :icon="item.icon" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </div>
        <strong>{{ item.value }}</strong>
      </div>
    </div>
    <p class="motion-page-note"
      >以上数量仅统计当前页 {{ list.length }} 条可见记录，不代表全部患者或全部评估。</p
    >
    <el-form :model="query" inline class="motion-filter">
      <el-form-item label="患者 ID">
        <el-input-number
          v-model="query.patientId"
          :min="1"
          controls-position="right"
          clearable
          aria-label="按患者编号筛选评估"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部状态" class="motion-select">
          <el-option v-for="(v, k) in STATE_LABELS" :key="k" :label="v" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.visitType" clearable placeholder="全部类型" class="motion-select">
          <el-option v-for="v in VISIT_OPTIONS" :key="v.value" :label="v.label" :value="v.value" />
        </el-select>
      </el-form-item>
      <el-form-item class="motion-filter-actions">
        <el-button type="primary" :loading="loading" @click="search">
          <Icon icon="ep:search" class="mr-4px" />查询
        </el-button>
        <el-button @click="reset">重置</el-button>
        <el-button v-if="query.patientId" @click="trendVisible = true">
          <Icon icon="ep:trend-charts" class="mr-4px" />患者趋势
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <div class="motion-section-heading">
      <div>
        <h3>评估记录</h3>
        <p>符合筛选条件共 {{ total }} 条，按评估编号排序。</p>
      </div>
    </div>
    <el-alert
      v-if="errorMessage"
      type="error"
      :closable="false"
      show-icon
      class="mb-12px"
      :title="errorMessage"
    >
      <template #default>
        <el-button link type="primary" @click="load">重新加载</el-button>
      </template>
    </el-alert>
    <el-table v-loading="loading" :data="list" stripe row-key="id" class="motion-table">
      <template #empty>
        <el-empty description="当前筛选条件下暂无评估记录">
          <el-button v-if="hasFilters" @click="reset">清除筛选条件</el-button>
        </el-empty>
      </template>
      <el-table-column label="编号" prop="id" width="80" />
      <el-table-column label="患者" min-width="140">
        <template #default="{ row }"
          ><el-button
            link
            type="primary"
            class="patient-link"
            @click="openPatient(row.patientId)"
            >{{ row.patientName || `患者 #${row.patientId}` }}</el-button
          ><span class="muted"> {{ row.patientNo }}</span></template
        >
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ visitLabel(row.visitType) }}</template>
      </el-table-column>
      <el-table-column label="评估体系" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="f in row.protocolFamilies || []" :key="f" size="small" class="mr-4px">{{
            f
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="采集时间" width="160">
        <template #default="{ row }">{{
          row.captureTime ? formatDate(row.captureTime) : '-'
        }}</template>
      </el-table-column>
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <el-tag :type="(STATE_TAG[row.status] as any) || 'info'">{{
            row.statusLabel || row.status
          }}</el-tag>
          <el-tag v-if="row.stale" type="warning" size="small" class="ml-4px">需重算</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="质控" width="90">
        <template #default="{ row }">{{ QC_LABEL[row.sessionQualityStatus] || '-' }}</template>
      </el-table-column>
      <el-table-column label="FMS 总分" width="100">
        <template #default="{ row }">
          <span v-if="row.fmsTotal !== null && row.fmsTotal !== undefined"
            >{{ row.fmsTotal }} / 21</span
          >
          <el-tooltip v-else content="7 项均经治疗师审核后才显示总分"
            ><span class="muted">未完成</span></el-tooltip
          >
        </template>
      </el-table-column>
      <el-table-column label="签署" width="160">
        <template #default="{ row }">{{
          row.signedTime ? formatDate(row.signedTime) : '-'
        }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button
            link
            type="primary"
            v-hasPermi="['rehab:motion:create']"
            :disabled="row.status === 'COMPLETED' || Boolean(row.signedTime)"
            @click="formRef.open({ id: row.id })"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            v-hasPermi="['rehab:motion:create']"
            :disabled="row.status === 'COMPLETED' || Boolean(row.signedTime)"
            @click="remove(row.id)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-if="total > 0"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      :total="total"
      @pagination="load"
    />
  </ContentWrap>

  <MotionWizard ref="formRef" @success="onCreated" />
  <el-drawer v-model="trendVisible" title="患者动作评估趋势" size="min(760px, 100%)">
    <TrendPanel v-if="trendVisible && query.patientId" :patient-id="query.patientId" />
  </el-drawer>
</template>

<script lang="ts" setup>
import { MotionApi, MotionAssessmentVO } from '@/api/rehab/motion'
import { formatDate } from '@/utils/formatTime'
import MotionWizard from './MotionWizard.vue'
import TrendPanel from './components/TrendPanel.vue'
import { summarizeMotionPage } from './motionMetrics'
import { QC_LABEL, STATE_TAG, VISIT_OPTIONS, visitLabel } from './constants'

defineOptions({ name: 'RehabMotion' })

const STATE_LABELS: Record<string, string> = {
  WAITING_UPLOAD: '待上传',
  UPLOADING: '上传中',
  OPENCAP_PROCESSING: 'OpenCap处理中',
  DOWNLOADING: '下载结果',
  PARSING: '数据解析',
  RULES: '规则计算',
  AI_GENERATING: 'AI报告生成',
  PENDING_REVIEW: '待治疗师审核',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消'
}

const message = useMessage()
const route = useRoute()
const { push } = useRouter()
const formRef = ref()
const loading = ref(false)
const list = ref<MotionAssessmentVO[]>([])
const total = ref(0)
const trendVisible = ref(false)
const errorMessage = ref('')
const initialPatientId = Number(route.query.patientId)
const query = reactive<{
  pageNo: number
  pageSize: number
  patientId?: number
  status?: string
  visitType?: string
}>({
  pageNo: 1,
  pageSize: 10,
  patientId:
    Number.isSafeInteger(initialPatientId) && initialPatientId > 0 ? initialPatientId : undefined
})
const hasFilters = computed(() => Boolean(query.patientId || query.status || query.visitType))
const pageMetrics = computed(() => summarizeMotionPage(list.value))
const processingCount = computed(
  () =>
    list.value.filter((item) =>
      [
        'UPLOADING',
        'OPENCAP_PROCESSING',
        'DOWNLOADING',
        'PARSING',
        'RULES',
        'AI_GENERATING'
      ].includes(item.status)
    ).length
)
const overviewItems = computed(() => [
  {
    label: '待上传',
    value: pageMetrics.value.waitingUpload,
    icon: 'ep:upload-filled',
    tone: 'neutral'
  },
  {
    label: '待治疗师审核',
    value: pageMetrics.value.pendingReview,
    icon: 'ep:document-checked',
    tone: 'review'
  },
  {
    label: '已完成',
    value: pageMetrics.value.completed,
    icon: 'ep:circle-check-filled',
    tone: 'complete'
  },
  {
    label: '需关注',
    value: pageMetrics.value.needsAttention,
    icon: 'ep:warning-filled',
    tone: 'attention'
  }
])

const load = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await MotionApi.page({ ...query })
    list.value = page?.list || []
    total.value = page?.total || 0
  } catch {
    list.value = []
    total.value = 0
    errorMessage.value = '列表加载失败，请检查连接后重试。'
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  load()
}
const reset = () => {
  query.patientId = undefined
  query.status = undefined
  query.visitType = undefined
  search()
}
const openDetail = (id: number) => push(`/rehab/motion/detail/${id}`)
const openPatient = (id?: number) => id && push(`/rehab/patient/detail/${id}`)
const onCreated = (id: number) => {
  load()
  if (id) openDetail(id)
}
const remove = async (id: number) => {
  await message.delConfirm('删除后已上传的数据与计算结果将一并删除，确认删除？')
  await MotionApi.remove(id)
  message.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.motion-hero,
.motion-section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.motion-hero {
  margin-bottom: 18px;
}
.motion-heading h2,
.motion-section-heading h3 {
  margin: 2px 0 6px;
  font-size: 22px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}
.motion-heading p:last-child,
.motion-section-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.motion-eyebrow {
  margin: 0;
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.motion-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}
.motion-toolbar :deep(.el-button + .el-button) {
  margin-left: 0;
}
.motion-notice {
  margin-bottom: 16px;
}
.motion-workflow-strip {
  display: grid;
  grid-template-columns: 1.25fr 1fr 1fr 1.15fr;
  gap: 10px;
  margin-bottom: 16px;
}
.workflow-card {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 72px;
  padding: 13px 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  text-align: left;
}
button.workflow-card {
  cursor: pointer;
  font: inherit;
}
button.workflow-card:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.workflow-card strong,
.workflow-card small {
  display: block;
}
.workflow-card strong {
  font-size: 13px;
  line-height: 1.35;
}
.workflow-card small {
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1.35;
}
.workflow-icon {
  display: flex;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.workflow-icon.review {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}
.workflow-icon.process {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}
.workflow-arrow {
  margin-left: auto;
  color: var(--el-color-primary);
}
.workflow-note {
  background: linear-gradient(135deg, var(--el-fill-color-lighter), var(--el-bg-color));
}
.patient-link {
  padding: 0;
  vertical-align: baseline;
}
.motion-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.motion-overview-card {
  padding: 15px 17px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-fill-color-lighter);
}
.motion-overview-label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.motion-overview-card strong {
  display: block;
  margin-top: 8px;
  color: var(--el-text-color-primary);
  font-size: 26px;
  font-variant-numeric: tabular-nums;
}
.motion-overview-card.review {
  border-color: var(--el-color-primary-light-7);
  background: var(--el-color-primary-light-9);
}
.motion-overview-card.complete {
  border-color: var(--el-color-success-light-7);
  background: var(--el-color-success-light-9);
}
.motion-overview-card.attention {
  border-color: var(--el-color-warning-light-7);
  background: var(--el-color-warning-light-9);
}
.motion-page-note {
  margin: 9px 0 18px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
.motion-filter {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  column-gap: 8px;
}
.motion-select {
  width: 164px;
}
.motion-filter-actions :deep(.el-form-item__content) {
  flex-wrap: wrap;
  row-gap: 8px;
}
.motion-section-heading {
  margin-bottom: 14px;
}
.motion-section-heading h3 {
  font-size: 17px;
}
.motion-table {
  width: 100%;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
@media (max-width: 900px) {
  .motion-workflow-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .motion-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .motion-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
@media (max-width: 520px) {
  .motion-workflow-strip {
    grid-template-columns: 1fr;
  }
  .motion-toolbar {
    width: 100%;
  }
  .motion-toolbar :deep(.el-button) {
    flex: 1;
  }
  .motion-overview {
    gap: 8px;
  }
  .motion-overview-card {
    padding: 12px;
  }
  .motion-overview-card strong {
    font-size: 22px;
  }
  .motion-filter :deep(.el-form-item) {
    width: 100%;
    margin-right: 0;
  }
  .motion-filter :deep(.el-form-item__content) {
    flex: 1;
  }
  .motion-select {
    width: 100%;
  }
}
</style>
