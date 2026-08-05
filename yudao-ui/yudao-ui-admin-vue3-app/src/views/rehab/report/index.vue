<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="90px"
    >
      <el-form-item label="患者" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          placeholder="姓名/患者编号"
          clearable
          class="!w-220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="报告类型" prop="reportType">
        <el-select v-model="queryParams.reportType" clearable class="!w-150px">
          <el-option label="综合评估" value="comprehensive" />
          <el-option label="随访评估" value="followup" />
          <el-option label="结案评估" value="discharge" />
        </el-select>
      </el-form-item>
      <el-form-item label="报告状态" prop="reportStatus">
        <el-select v-model="queryParams.reportStatus" clearable class="!w-140px">
          <el-option label="草稿" value="draft" />
          <el-option label="已复核" value="reviewed" />
          <el-option label="已审批" value="approved" />
          <el-option label="已导出" value="exported" />
          <el-option label="已锁版" value="locked" />
        </el-select>
      </el-form-item>
      <el-form-item label="生成方式" prop="generationMode">
        <el-select v-model="queryParams.generationMode" clearable class="!w-150px">
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
          class="!w-300px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery"><Icon icon="ep:search" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-alert
      title="报告中心按患者归档。点击患者后，可在该患者名下选择具体评估报告。"
      type="info"
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
    width="88%"
  >
    <el-table v-loading="reportLoading" :data="reportList" stripe>
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

    <Pagination
      :total="reportTotal"
      v-model:page="reportQuery.pageNo"
      v-model:limit="reportQuery.pageSize"
      @pagination="getPatientReports"
    />
  </Dialog>

  <Dialog v-model="previewVisible" title="报告预览" width="70%">
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
import download from '@/utils/download'
import { dateFormatter } from '@/utils/formatTime'
import { generateReportSummary, RehabAiGenerateRespVO } from '@/api/rehab/ai'
import {
  approveRehabReport,
  exportRehabReportDocx,
  exportRehabReportPdf,
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
  previewHtml.value = data.html || '<p>暂无可预览内容</p>'
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
.patient-table :deep(.el-table__row) {
  cursor: pointer;
}

.report-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 0;
}

.preview-wrap {
  max-height: 70vh;
  overflow: auto;
  border: 1px solid var(--el-border-color);
}
</style>
