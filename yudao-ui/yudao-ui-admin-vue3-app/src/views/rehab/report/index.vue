<template>
  <ContentWrap>
    <div class="report-hero">
      <div>
        <p class="report-eyebrow">CLINICAL DOCUMENTS / 报告工作台</p>
        <h2>康复评估报告</h2>
        <p>按患者整理报告、追溯版本和复核记录。报告状态不等同于临床签署。</p>
      </div>
      <div class="report-hero-actions">
        <el-button :loading="engineReportLoading" type="primary" plain @click="openEngineReport">
          <Icon icon="ep:data-analysis" class="mr-4px" />引擎联调报告
        </el-button>
        <el-button :loading="loading" aria-label="刷新报告患者列表" @click="getList">
          <Icon icon="ep:refresh" class="mr-4px" />刷新列表
        </el-button>
      </div>
    </div>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="report-filter -mb-15px"
      label-width="90px"
    >
      <el-form-item label="患者" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          placeholder="姓名/患者编号"
          clearable
          class="report-filter-control"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="报告类型" prop="reportType">
        <el-select v-model="queryParams.reportType" clearable class="report-filter-control">
          <el-option label="综合评估" value="comprehensive" />
          <el-option label="随访评估" value="followup" />
          <el-option label="结案评估" value="discharge" />
        </el-select>
      </el-form-item>
      <el-form-item label="报告状态" prop="reportStatus">
        <el-select v-model="queryParams.reportStatus" clearable class="report-filter-control">
          <el-option label="草稿" value="draft" />
          <el-option label="已复核" value="reviewed" />
          <el-option label="已审批" value="approved" />
          <el-option label="已导出" value="exported" />
          <el-option label="已锁版" value="locked" />
        </el-select>
      </el-form-item>
      <el-form-item label="生成方式" prop="generationMode">
        <el-select v-model="queryParams.generationMode" clearable class="report-filter-control">
          <el-option label="系统生成" value="auto" />
          <el-option v-if="AI_ENABLED" label="AI 辅助" value="ai_assisted" />
          <el-option label="人工调整" value="manual_adjusted" />
        </el-select>
      </el-form-item>
      <el-form-item label="生成时间" prop="createTime">
        <el-date-picker
          v-model="queryParams.createTime"
          value-format="YYYY-MM-DD HH:mm:ss"
          type="datetimerange"
          start-placeholder="开始"
          end-placeholder="结束"
          class="report-filter-control report-date-editor"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery"><Icon icon="ep:search" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <div class="report-overview" role="region" aria-label="当前页报告工作概况">
      <div class="report-metric">
        <span>当前页患者</span>
        <strong>{{ patientList.length }}</strong>
      </div>
      <div class="report-metric">
        <span>当前页评估记录</span>
        <strong>{{ currentPageAssessmentCount }}</strong>
      </div>
      <div class="report-metric">
        <span>当前页报告</span>
        <strong>{{ currentPageReportCount }}</strong>
      </div>
    </div>
    <p class="report-metric-note">以上三项只统计当前页；共有 {{ total }} 位患者符合筛选条件。</p>
    <el-alert
      title="报告中心按患者归档。点击患者后，可在该患者名下选择具体评估报告。"
      type="info"
      :closable="false"
      class="mb-16px"
      show-icon
    />
    <el-alert
      title="报告质量提醒：现行 V4.1 自动报告以原始数据整理为主，并非青少年 B 版治疗师正式报告。正式交付前须核查原始来源、疼痛与红旗、冲突和缺失项，完成临床推理、复评 KPI 及人工审核。"
      type="warning"
      :closable="false"
      class="mb-16px"
      show-icon
    />
    <el-table
      v-loading="loading"
      :data="patientList"
      stripe
      class="patient-table"
      row-key="patientId"
      @row-click="handlePatientClick"
    >
      <template #empty>
        <el-empty description="当前筛选条件下暂无报告归档记录">
          <el-button @click="resetQuery">清除筛选条件</el-button>
        </el-empty>
      </template>
      <el-table-column label="患者编号" prop="patientNo" min-width="160" />
      <el-table-column label="患者姓名" prop="patientName" min-width="150" />
      <el-table-column label="评估数量" prop="assessmentCount" min-width="110" align="center" />
      <el-table-column label="报告数量" prop="reportCount" min-width="110" align="center">
        <template #default="scope">
          <el-tag type="primary">{{ scope.row.reportCount }} 份</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="最近报告时间"
        prop="latestReportTime"
        min-width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" width="160" align="center">
        <template #default="scope">
          <el-button type="primary" link @click.stop="openPatientReports(scope.row)">
            选择评估报告
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-if="total > 0"
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <Dialog
    v-model="patientReportsVisible"
    :title="
      selectedPatient
        ? `${selectedPatient.patientName}（${selectedPatient.patientNo}）的评估报告`
        : '患者评估报告'
    "
    width="min(1180px, 96%)"
  >
    <p class="report-dialog-note">此处显示所选患者的报告版本与流转状态。预览、审批和锁版操作须遵循授权及人工审核流程。</p>
    <el-table v-loading="reportLoading" :data="reportList" stripe class="report-desktop-table">
      <template #empty>
        <el-empty description="该患者暂无符合条件的报告" />
      </template>
      <el-table-column label="评估编号" prop="assessmentNo" min-width="150" />
      <el-table-column label="报告编号" prop="reportNo" min-width="170" />
      <el-table-column label="疗程编号" prop="episodeNo" min-width="150" />
      <el-table-column label="版本" prop="reportVersion" width="80" align="center" />
      <el-table-column label="状态" prop="reportStatus" min-width="100">
        <template #default="scope">
          <el-tag :type="reportStatusType(scope.row.reportStatus)">
            {{ reportStatusLabel(scope.row.reportStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生成方式" prop="generationMode" min-width="110">
        <template #default="scope">{{ generationModeLabel(scope.row.generationMode) }}</template>
      </el-table-column>
      <el-table-column
        label="更新时间"
        prop="updateTime"
        min-width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" fixed="right" min-width="500">
        <template #default="scope">
          <div class="report-actions">
            <el-button
              v-if="scope.row.reportStatus === 'draft'"
              type="primary"
              link
              v-hasPermi="['rehab:report:review']"
              @click="handleReview(scope.row)"
              >复核</el-button
            >
            <el-button
              v-if="scope.row.reportStatus === 'reviewed'"
              type="success"
              link
              v-hasPermi="['rehab:report:approve']"
              @click="handleApprove(scope.row)"
              >审批</el-button
            >
            <el-button
              v-if="scope.row.reportStatus === 'approved' || scope.row.reportStatus === 'exported'"
              type="warning"
              link
              v-hasPermi="['rehab:report:lock']"
              @click="handleLock(scope.row)"
              >锁版</el-button
            >
            <el-button
              v-if="scope.row.reportStatus === 'locked'"
              type="warning"
              link
              v-hasPermi="['rehab:report:unlock']"
              @click="handleUnlock(scope.row)"
              >解锁</el-button
            >
            <el-button
              type="primary"
              link
              v-hasPermi="['rehab:report:preview']"
              @click="handlePreview(scope.row)"
            >
              预览
            </el-button>
            <el-button
              type="primary"
              link
              v-hasPermi="['rehab:report:export']"
              @click="handleExportDocx(scope.row)"
            >
              导出 DOCX
            </el-button>
            <el-button
              type="primary"
              link
              v-hasPermi="['rehab:report:export']"
              @click="handleExportPdf(scope.row)"
            >
              导出 PDF
            </el-button>
            <el-button type="primary" link @click="openAssessment(scope.row)">查看评估</el-button>
            <el-button
              v-if="AI_ENABLED"
              type="primary"
              link
              v-hasPermi="['rehab:ai:generate']"
              @click="handleAiSummary(scope.row)"
              >AI 摘要</el-button
            >
            <el-button type="primary" link @click="handleVersion(scope.row)">版本</el-button>
            <el-button type="primary" link @click="handleAudit(scope.row)">审计</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div v-loading="reportLoading" class="report-mobile-list" role="region" aria-label="该患者的评估报告">
      <el-empty v-if="!reportLoading && reportList.length === 0" description="该患者暂无符合条件的报告" />
      <article v-for="row in reportList" :key="row.id" class="report-mobile-card">
        <div class="report-mobile-heading">
          <strong>{{ row.reportNo || '报告 #' + row.id }}</strong>
          <el-tag :type="reportStatusType(row.reportStatus)">{{ reportStatusLabel(row.reportStatus) }}</el-tag>
        </div>
        <dl class="report-mobile-metadata">
          <div><dt>评估</dt><dd>{{ row.assessmentNo || row.assessmentId || '未填写' }}</dd></div>
          <div><dt>疗程</dt><dd>{{ row.episodeNo || row.episodeId || '未填写' }}</dd></div>
          <div><dt>版本</dt><dd>V{{ row.reportVersion || 1 }}</dd></div>
          <div><dt>生成方式</dt><dd>{{ generationModeLabel(row.generationMode) }}</dd></div>
        </dl>
        <div class="report-mobile-actions" aria-label="报告操作">
          <el-button
            v-if="row.reportStatus === 'draft'"
            link type="primary" v-hasPermi="['rehab:report:review']" @click="handleReview(row)"
          >复核</el-button>
          <el-button
            v-if="row.reportStatus === 'reviewed'"
            link type="success" v-hasPermi="['rehab:report:approve']" @click="handleApprove(row)"
          >审批</el-button>
          <el-button
            v-if="row.reportStatus === 'approved' || row.reportStatus === 'exported'"
            link type="warning" v-hasPermi="['rehab:report:lock']" @click="handleLock(row)"
          >锁版</el-button>
          <el-button
            v-if="row.reportStatus === 'locked'"
            link type="warning" v-hasPermi="['rehab:report:unlock']" @click="handleUnlock(row)"
          >解锁</el-button>
          <el-button link type="primary" v-hasPermi="['rehab:report:preview']" @click="handlePreview(row)">预览</el-button>
          <el-button link type="primary" v-hasPermi="['rehab:report:export']" @click="handleExportDocx(row)">DOCX</el-button>
          <el-button link type="primary" v-hasPermi="['rehab:report:export']" @click="handleExportPdf(row)">PDF</el-button>
          <el-button link type="primary" @click="openAssessment(row)">查看评估</el-button>
          <el-button v-if="AI_ENABLED" link type="primary" v-hasPermi="['rehab:ai:generate']" @click="handleAiSummary(row)">AI 摘要</el-button>
          <el-button link type="primary" @click="handleVersion(row)">版本</el-button>
          <el-button link type="primary" @click="handleAudit(row)">审计</el-button>
        </div>
      </article>
    </div>

    <Pagination
      v-if="reportTotal > reportQuery.pageSize"
      class="report-pagination"
      :total="reportTotal"
      v-model:page="reportQuery.pageNo"
      v-model:limit="reportQuery.pageSize"
      @pagination="getPatientReports"
    />
  </Dialog>

  <Dialog v-model="previewVisible" title="报告预览（已过滤不安全 HTML）" width="min(1000px, 96%)">
    <div class="preview-wrap" v-html="previewHtml"></div>
  </Dialog>

  <Dialog v-model="versionVisible" title="版本历史" width="60%">
    <el-table :data="versionList" size="small">
      <el-table-column label="版本" prop="versionNo" width="80" />
      <el-table-column label="状态" prop="reportStatus" width="120" />
      <el-table-column label="模式" prop="generationMode" width="120" />
      <el-table-column label="变更说明" prop="changeSummary" min-width="200" />
      <el-table-column label="时间" prop="createTime" min-width="170" :formatter="dateFormatter" />
    </el-table>
  </Dialog>

  <Dialog v-model="auditVisible" title="报告审计日志" width="65%">
    <el-table :data="auditList" size="small">
      <el-table-column label="操作" prop="operationType" width="160" />
      <el-table-column label="操作人" prop="operatorName" width="140" />
      <el-table-column label="结果" prop="resultStatus" width="90" />
      <el-table-column label="备注" prop="remark" min-width="220" />
      <el-table-column label="时间" prop="createTime" min-width="170" :formatter="dateFormatter" />
    </el-table>
  </Dialog>

  <Dialog v-model="engineReportVisible" title="运动评估引擎联调报告" width="min(1100px, 96%)">
    <el-alert
      title="该结果来自运动评估引擎 /v1/research/rehearsal 接口，是合成资料的连通自检，不是患者临床报告。"
      type="info"
      :closable="false"
      class="mb-12px"
    />
    <pre v-loading="engineReportLoading" class="engine-report-json">{{ engineReportText || '暂无联调结果' }}</pre>
  </Dialog>

  <Dialog v-if="AI_ENABLED" v-model="aiSummaryVisible" title="AI 报告摘要" width="60%">
    <el-alert
      v-if="aiFallback"
      type="warning"
      :closable="false"
      title="本次 AI 输出已降级为保守文案，请结合人工复核。"
      class="mb-12px"
    />
    <div class="preview-wrap">{{ aiSummaryText || '暂无可展示的 AI 摘要。' }}</div>
  </Dialog>
</template>

<script lang="ts" setup>
import DOMPurify from 'dompurify'
import download from '@/utils/download'
import { dateFormatter } from '@/utils/formatTime'
import { generateReportSummary, RehabAiGenerateRespVO } from '@/api/rehab/ai'
import {
  approveRehabReport,
  exportRehabReportDocx,
  exportRehabReportPdf,
  getRehabEngineRehearsalReport,
  getRehabReportAuditLogs,
  getRehabReportPage,
  getRehabReportPatientPage,
  getRehabReportVersionPage,
  lockRehabReport,
  previewRehabReport,
  reviewRehabReport,
  unlockRehabReport,
  RehabReportPageReqVO,
  RehabReportPatientRespVO
} from '@/api/rehab/report'

defineOptions({ name: 'RehabReport' })

const AI_ENABLED = import.meta.env.VITE_REHAB_AI_ENABLED === 'true'
const { push } = useRouter()
const loading = ref(false)
const total = ref(0)
const patientList = ref<RehabReportPatientRespVO[]>([])
const currentPageAssessmentCount = computed(() =>
  patientList.value.reduce((total, patient) => total + Number(patient.assessmentCount ?? 0), 0)
)
const currentPageReportCount = computed(() =>
  patientList.value.reduce((total, patient) => total + Number(patient.reportCount ?? 0), 0)
)
const queryFormRef = ref()

const patientReportsVisible = ref(false)
const selectedPatient = ref<RehabReportPatientRespVO>()
const reportLoading = ref(false)
const reportTotal = ref(0)
const reportList = ref<any[]>([])
const reportQuery = reactive({ pageNo: 1, pageSize: 10 })

const previewVisible = ref(false)
const previewHtml = ref('')
const versionVisible = ref(false)
const versionList = ref<any[]>([])
const auditVisible = ref(false)
const auditList = ref<any[]>([])
const aiSummaryVisible = ref(false)
const aiSummaryText = ref('')
const aiFallback = ref(false)
const engineReportVisible = ref(false)
const engineReportLoading = ref(false)
const engineReportText = ref('')

const queryParams = reactive<RehabReportPageReqVO>({
  pageNo: 1,
  pageSize: 10,
  keyword: undefined,
  reportType: undefined,
  reportStatus: undefined,
  generationMode: undefined,
  createTime: []
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getRehabReportPatientPage(queryParams)
    patientList.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const openEngineReport = async () => {
  engineReportVisible.value = true
  engineReportLoading.value = true
  try {
    const data = await getRehabEngineRehearsalReport()
    engineReportText.value = JSON.stringify(data, null, 2)
  } finally {
    engineReportLoading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const getPatientReports = async () => {
  if (!selectedPatient.value) return
  reportLoading.value = true
  try {
    const data = await getRehabReportPage({
      ...queryParams,
      pageNo: reportQuery.pageNo,
      pageSize: reportQuery.pageSize,
      patientId: selectedPatient.value.patientId,
      keyword: undefined
    })
    reportList.value = data.list || []
    reportTotal.value = data.total || 0
  } finally {
    reportLoading.value = false
  }
}

const openPatientReports = async (patient: RehabReportPatientRespVO) => {
  selectedPatient.value = patient
  reportQuery.pageNo = 1
  patientReportsVisible.value = true
  await getPatientReports()
}

const handlePatientClick = (patient: RehabReportPatientRespVO) => openPatientReports(patient)

const refreshReportData = async () => {
  await Promise.all([getList(), getPatientReports()])
}

const handlePreview = async (row: any) => {
  const data = await previewRehabReport(row.id)
  // Defense-in-depth: reports may include imported narrative text. Never
  // insert server-provided HTML into v-html before sanitizing it.
  previewHtml.value = DOMPurify.sanitize(
    typeof data?.html === 'string' ? data.html : '<p>暂无可预览内容</p>',
    {
      USE_PROFILES: { html: true },
      FORBID_TAGS: ['style', 'iframe', 'object', 'embed', 'form', 'meta', 'link'],
      FORBID_ATTR: ['style', 'srcset']
    }
  )
  previewVisible.value = true
}

const handleExportDocx = async (row: any) => {
  const data = await exportRehabReportDocx(row.id)
  download.word(data, `康复管理系统-V1.0-${row.reportNo || '评估报告'}.docx`)
}

const handleExportPdf = async (row: any) => {
  const data = await exportRehabReportPdf(row.id)
  download.pdf(data, `康复管理系统-V1.0-${row.reportNo || '评估报告'}.pdf`)
}

const handleReview = async (row: any) => {
  await reviewRehabReport({ id: row.id })
  await refreshReportData()
}

const handleApprove = async (row: any) => {
  await approveRehabReport({ id: row.id })
  await refreshReportData()
}

const handleLock = async (row: any) => {
  await lockRehabReport({ id: row.id, reason: '治疗师确认当前版本可锁版' })
  await refreshReportData()
}

const handleUnlock = async (row: any) => {
  await unlockRehabReport({ id: row.id, reason: '管理员人工复核后解锁' })
  await refreshReportData()
}

const handleVersion = async (row: any) => {
  const data = await getRehabReportVersionPage({ reportId: row.id, pageNo: 1, pageSize: 20 })
  versionList.value = data.list || []
  versionVisible.value = true
}

const handleAudit = async (row: any) => {
  auditList.value = await getRehabReportAuditLogs(row.id)
  auditVisible.value = true
}

const handleAiSummary = async (row: any) => {
  if (!AI_ENABLED) return
  const resp: RehabAiGenerateRespVO = await generateReportSummary({
    reportId: row.id,
    asyncMode: false
  })
  aiSummaryText.value = resp.renderedText || ''
  aiFallback.value = !!resp.fallbackUsed
  aiSummaryVisible.value = true
}

const openAssessment = (row: any) => {
  if (row.assessmentId) push(`/rehab/assessment/detail/${row.assessmentId}`)
}

const reportStatusLabel = (status?: string) =>
  ({
    draft: '草稿',
    reviewed: '已复核',
    approved: '已审批',
    exported: '已导出',
    locked: '已锁版'
  })[status || ''] ||
  status ||
  '-'

const reportStatusType = (status?: string) =>
  (({
    draft: 'info',
    reviewed: 'warning',
    approved: 'success',
    exported: 'primary',
    locked: 'danger'
  })[status || ''] || 'info') as any

const generationModeLabel = (mode?: string) =>
  ({
    auto: '系统生成',
    ai_assisted: 'AI 辅助',
    manual_adjusted: '人工调整'
  })[mode || ''] ||
  mode ||
  '-'

onMounted(getList)
</script>

<style scoped>
.report-hero-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.engine-report-json { max-height: 60vh; overflow: auto; padding: 16px; border-radius: 8px; background: var(--el-fill-color-light); color: var(--el-text-color-primary); white-space: pre-wrap; word-break: break-word; }
.report-filter-control { width: 170px; }
.report-date-editor { width: 300px; }
.report-mobile-list { display: none; }
.report-pagination { max-width: 100%; overflow-x: auto; }
.report-mobile-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  padding: 14px;
  background: var(--el-fill-color-lighter);
}
.report-mobile-card + .report-mobile-card { margin-top: 12px; }
.report-mobile-heading { display: flex; justify-content: space-between; align-items: start; gap: 10px; }
.report-mobile-heading strong { overflow-wrap: anywhere; min-width: 0; }
.report-mobile-metadata { margin: 12px 0; }
.report-mobile-metadata > div { display: flex; gap: 10px; padding: 4px 0; }
.report-mobile-metadata dt { width: 62px; flex-shrink: 0; color: var(--el-text-color-secondary); }
.report-mobile-metadata dd { margin: 0; min-width: 0; overflow-wrap: anywhere; }
.report-mobile-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
  border-top: 1px solid var(--el-border-color-light);
  padding-top: 10px;
}
.report-mobile-actions :deep(.el-button + .el-button) { margin-left: 0; }
.report-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
}
.report-eyebrow {
  margin: 0 0 6px;
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .08em;
}
.report-hero h2 {
  margin: 0 0 6px;
  color: var(--el-text-color-primary);
  font-size: 22px;
  font-weight: 650;
}
.report-hero p:last-child, .report-dialog-note {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.report-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.report-metric {
  padding: 15px 18px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
}
.report-metric span {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.report-metric strong {
  display: block;
  margin-top: 8px;
  font-size: 25px;
  font-variant-numeric: tabular-nums;
}
.report-metric-note {
  margin: 9px 0 18px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
.report-dialog-note { margin-bottom: 14px; }
.patient-table :deep(.el-table__row) {
  cursor: pointer;
}

.report-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 12px;
}
.report-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.preview-wrap {
  max-height: 70vh;
  overflow: auto;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 16px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
@media (max-width: 760px) {
  .report-hero { flex-direction: column; align-items: flex-start; }
  .report-filter :deep(.el-form-item) {
    display: flex;
    align-items: stretch;
    flex-direction: column;
    width: 100%;
    margin-right: 0;
  }
  .report-filter :deep(.el-form-item__label) { width: auto !important; justify-content: flex-start; }
  .report-filter :deep(.el-form-item__content) { min-width: 0; width: 100%; }
  .report-filter-control, .report-date-editor { width: 100% !important; max-width: 100%; min-width: 0; }
  .report-desktop-table { display: none; }
  .report-mobile-list { display: block; }
  .report-overview { grid-template-columns: 1fr; gap: 8px; }
  .report-metric { padding: 12px 14px; }
  .report-metric strong { font-size: 21px; }
  .report-actions { gap: 8px; }
  .preview-wrap { padding: 10px; }
}
</style>
