<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px" label-width="90px">
      <el-form-item label="患者关键词" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          placeholder="姓名/患者编号"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="患者ID" prop="patientId">
        <el-input-number v-model="queryParams.patientId" :min="1" controls-position="right" class="!w-140px" />
      </el-form-item>
      <el-form-item label="episode" prop="episodeId">
        <el-input-number v-model="queryParams.episodeId" :min="1" controls-position="right" class="!w-140px" />
      </el-form-item>
      <el-form-item label="评估ID" prop="assessmentId">
        <el-input-number v-model="queryParams.assessmentId" :min="1" controls-position="right" class="!w-140px" />
      </el-form-item>
      <el-form-item label="状态" prop="reportStatus">
        <el-select v-model="queryParams.reportStatus" clearable class="!w-130px">
          <el-option label="draft" value="draft" />
          <el-option label="reviewed" value="reviewed" />
          <el-option label="approved" value="approved" />
          <el-option label="exported" value="exported" />
          <el-option label="locked" value="locked" />
        </el-select>
      </el-form-item>
      <el-form-item label="模式" prop="generationMode">
        <el-select v-model="queryParams.generationMode" clearable class="!w-140px">
          <el-option label="auto" value="auto" />
          <el-option v-if="AI_ENABLED" label="ai_assisted" value="ai_assisted" />
          <el-option label="manual_adjusted" value="manual_adjusted" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间" prop="createTime">
        <el-date-picker
          v-model="queryParams.createTime"
          value-format="YYYY-MM-DD HH:mm:ss"
          type="datetimerange"
          start-placeholder="开始"
          end-placeholder="结束"
          class="!w-260px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="报告编号" prop="reportNo" min-width="160" />
      <el-table-column label="患者" min-width="170">
        <template #default="scope">{{ scope.row.patientName }} ({{ scope.row.patientNo }})</template>
      </el-table-column>
      <el-table-column label="评估编号" prop="assessmentNo" min-width="150" />
      <el-table-column label="疗程编号" prop="episodeNo" min-width="150" />
      <el-table-column label="版本" prop="reportVersion" min-width="80" />
      <el-table-column label="状态" prop="reportStatus" min-width="100" />
      <el-table-column label="模式" prop="generationMode" min-width="120" />
      <el-table-column label="更新时间" prop="updateTime" min-width="170" :formatter="dateFormatter" />
      <el-table-column label="操作" fixed="right" min-width="460">
        <template #default="scope">
          <el-button
            type="primary"
            link
            v-hasPermi="['rehab:report:review']"
            v-if="scope.row.reportStatus === 'draft'"
            @click="handleReview(scope.row)"
          >
            复核
          </el-button>
          <el-button
            type="success"
            link
            v-hasPermi="['rehab:report:approve']"
            v-if="scope.row.reportStatus === 'reviewed'"
            @click="handleApprove(scope.row)"
          >
            审批
          </el-button>
          <el-button
            type="warning"
            link
            v-hasPermi="['rehab:report:lock']"
            v-if="scope.row.reportStatus === 'approved' || scope.row.reportStatus === 'exported'"
            @click="handleLock(scope.row)"
          >
            锁版
          </el-button>
          <el-button
            type="warning"
            link
            v-hasPermi="['rehab:report:unlock']"
            v-if="scope.row.reportStatus === 'locked'"
            @click="handleUnlock(scope.row)"
          >
            解锁
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:report:preview']" @click="handlePreview(scope.row)">
            预览
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:report:export']" @click="handleExportDocx(scope.row)">
            导出 DOCX
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:report:export']" @click="handleExportPdf(scope.row)">
            导出 PDF
          </el-button>
          <el-button type="primary" link @click="openAssessment(scope.row)">查看评估</el-button>
          <el-button
            v-if="AI_ENABLED"
            type="primary"
            link
            v-hasPermi="['rehab:ai:generate']"
            @click="handleAiSummary(scope.row)"
          >
            AI摘要
          </el-button>
          <el-button type="primary" link @click="handleVersion(scope.row)">版本</el-button>
          <el-button type="primary" link @click="handleAudit(scope.row)">审计</el-button>
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
  getRehabReportVersionPage,
  lockRehabReport,
  previewRehabReport,
  reviewRehabReport,
  unlockRehabReport,
  RehabReportPageReqVO
} from '@/api/rehab/report'

defineOptions({ name: 'RehabReport' })

const AI_ENABLED = import.meta.env.VITE_REHAB_AI_ENABLED === 'true'
const { push } = useRouter()
const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const queryFormRef = ref()

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
  patientId: undefined,
  episodeId: undefined,
  assessmentId: undefined,
  keyword: undefined,
  reportStatus: undefined,
  generationMode: undefined,
  createTime: []
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getRehabReportPage(queryParams)
    list.value = data.list || []
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
  await getList()
}

const handleApprove = async (row: any) => {
  await approveRehabReport({ id: row.id })
  await getList()
}

const handleLock = async (row: any) => {
  await lockRehabReport({ id: row.id, reason: '治疗师确认当前版本可锁版' })
  await getList()
}

const handleUnlock = async (row: any) => {
  await unlockRehabReport({ id: row.id, reason: '管理员人工复核后解锁' })
  await getList()
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
  if (!AI_ENABLED) {
    return
  }
  const resp: RehabAiGenerateRespVO = await generateReportSummary({ reportId: row.id, asyncMode: false })
  aiSummaryText.value = resp.renderedText || ''
  aiFallback.value = !!resp.fallbackUsed
  aiSummaryVisible.value = true
}

const openAssessment = (row: any) => {
  if (row.assessmentId) {
    push(`/rehab/assessment/detail/${row.assessmentId}`)
  }
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.preview-wrap {
  max-height: 70vh;
  overflow: auto;
  border: 1px solid #ebeef5;
}
</style>
