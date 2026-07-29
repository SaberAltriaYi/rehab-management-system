<template>
  <ContentWrap>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="AI 任务" name="job">
        <el-form :inline="true" :model="jobQuery" class="-mb-15px">
          <el-form-item label="患者ID">
            <el-input-number v-model="jobQuery.patientId" :min="1" controls-position="right" class="!w-140px" />
          </el-form-item>
          <el-form-item label="任务类型">
            <el-select v-model="jobQuery.jobType" clearable class="!w-200px">
              <el-option label="assessment_interpretation" value="assessment_interpretation" />
              <el-option label="report_summary" value="report_summary" />
              <el-option label="risk_explanation" value="risk_explanation" />
              <el-option label="plan_draft_generation" value="plan_draft_generation" />
              <el-option label="followup_message_generation" value="followup_message_generation" />
              <el-option label="progress_summary" value="progress_summary" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="jobQuery.status" clearable class="!w-150px">
              <el-option label="pending" value="pending" />
              <el-option label="success" value="success" />
              <el-option label="fallback_used" value="fallback_used" />
              <el-option label="failed" value="failed" />
              <el-option label="reviewed" value="reviewed" />
              <el-option label="accepted" value="accepted" />
              <el-option label="rejected" value="rejected" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleJobQuery"><Icon icon="ep:search" />搜索</el-button>
            <el-button @click="resetJobQuery"><Icon icon="ep:refresh" />重置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="AI 输出" name="output">
        <el-form :inline="true" :model="outputQuery" class="-mb-15px">
          <el-form-item label="患者ID">
            <el-input-number v-model="outputQuery.patientId" :min="1" controls-position="right" class="!w-140px" />
          </el-form-item>
          <el-form-item label="输出类型">
            <el-select v-model="outputQuery.outputType" clearable class="!w-200px">
              <el-option label="therapist_summary" value="therapist_summary" />
              <el-option label="patient_summary" value="patient_summary" />
              <el-option label="admin_summary" value="admin_summary" />
              <el-option label="risk_explanation" value="risk_explanation" />
              <el-option label="plan_draft" value="plan_draft" />
              <el-option label="followup_message" value="followup_message" />
              <el-option label="progress_summary" value="progress_summary" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标对象">
            <el-select v-model="outputQuery.targetObjectType" clearable class="!w-160px">
              <el-option label="assessment" value="assessment" />
              <el-option label="report" value="report" />
              <el-option label="plan" value="plan" />
              <el-option label="progress" value="progress" />
              <el-option label="patient" value="patient" />
              <el-option label="alert" value="alert" />
              <el-option label="trigger" value="trigger" />
            </el-select>
          </el-form-item>
          <el-form-item label="审核">
            <el-select v-model="outputQuery.reviewStatus" clearable class="!w-150px">
              <el-option label="pending" value="pending" />
              <el-option label="accepted" value="accepted" />
              <el-option label="edited" value="edited" />
              <el-option label="rejected" value="rejected" />
            </el-select>
          </el-form-item>
          <el-form-item label="安全">
            <el-select v-model="outputQuery.safetyStatus" clearable class="!w-150px">
              <el-option label="passed" value="passed" />
              <el-option label="downgraded" value="downgraded" />
              <el-option label="blocked" value="blocked" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleOutputQuery"><Icon icon="ep:search" />搜索</el-button>
            <el-button @click="resetOutputQuery"><Icon icon="ep:refresh" />重置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <ContentWrap v-if="activeTab === 'job'">
    <el-table v-loading="jobLoading" :data="jobList" stripe>
      <el-table-column label="job_no" prop="jobNo" min-width="170" />
      <el-table-column label="患者" min-width="170">
        <template #default="scope">{{ scope.row.patientName || '-' }} ({{ scope.row.patientId || '-' }})</template>
      </el-table-column>
      <el-table-column label="任务类型" prop="jobType" min-width="190" />
      <el-table-column label="状态" prop="status" min-width="120" />
      <el-table-column label="fallback" min-width="90">
        <template #default="scope">{{ scope.row.fallbackUsed ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="模型" prop="modelName" min-width="140" />
      <el-table-column label="触发人" prop="triggeredByName" min-width="120" />
      <el-table-column label="时间" prop="createTime" min-width="170" />
      <el-table-column label="操作" min-width="120" fixed="right">
        <template #default="scope">
          <el-button type="primary" link @click="openJobDetail(scope.row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="jobTotal"
      v-model:page="jobQuery.pageNo"
      v-model:limit="jobQuery.pageSize"
      @pagination="getJobList"
    />
  </ContentWrap>

  <ContentWrap v-else>
    <el-table v-loading="outputLoading" :data="outputList" stripe>
      <el-table-column label="ID" prop="id" width="90" />
      <el-table-column label="患者" min-width="170">
        <template #default="scope">{{ scope.row.patientName || '-' }} ({{ scope.row.patientId || '-' }})</template>
      </el-table-column>
      <el-table-column label="输出类型" prop="outputType" min-width="160" />
      <el-table-column label="目标对象" min-width="160">
        <template #default="scope">{{ scope.row.targetObjectType }}#{{ scope.row.targetObjectId || '-' }}</template>
      </el-table-column>
      <el-table-column label="审核" prop="reviewStatus" min-width="100" />
      <el-table-column label="安全" prop="safetyStatus" min-width="110" />
      <el-table-column label="患者可见" min-width="100">
        <template #default="scope">{{ scope.row.patientVisible ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="时间" prop="createTime" min-width="170" />
      <el-table-column label="操作" min-width="340" fixed="right">
        <template #default="scope">
          <el-button type="primary" link @click="openOutputDetail(scope.row)">详情</el-button>
          <el-button type="success" link v-hasPermi="['rehab:ai:accept']" @click="handleAccept(scope.row, false)">采纳</el-button>
          <el-button
            type="success"
            link
            v-hasPermi="['rehab:ai:accept']"
            @click="handleAccept(scope.row, true)"
          >
            采纳并患者可见
          </el-button>
          <el-button type="warning" link v-hasPermi="['rehab:ai:edit']" @click="openEdit(scope.row)">编辑</el-button>
          <el-button type="danger" link v-hasPermi="['rehab:ai:reject']" @click="handleReject(scope.row)">驳回</el-button>
          <el-button type="primary" link v-hasPermi="['rehab:ai:regenerate']" @click="handleRegenerate(scope.row)">
            重生成
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="outputTotal"
      v-model:page="outputQuery.pageNo"
      v-model:limit="outputQuery.pageSize"
      @pagination="getOutputList"
    />
  </ContentWrap>

  <Dialog v-model="jobDialogVisible" title="AI 任务详情" width="65%">
    <el-descriptions :column="2" border v-if="currentJob">
      <el-descriptions-item label="job_no">{{ currentJob.jobNo }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ currentJob.status }}</el-descriptions-item>
      <el-descriptions-item label="任务类型">{{ currentJob.jobType }}</el-descriptions-item>
      <el-descriptions-item label="模型">{{ currentJob.modelName }}</el-descriptions-item>
      <el-descriptions-item label="prompt">{{ currentJob.promptName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="fallback">{{ currentJob.fallbackUsed ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="输入 hash">{{ currentJob.inputHash || '-' }}</el-descriptions-item>
      <el-descriptions-item label="输出 hash">{{ currentJob.outputHash || '-' }}</el-descriptions-item>
    </el-descriptions>
    <el-divider />
    <div class="detail-label">Token 使用</div>
    <pre class="json-pre">{{ prettyJson(currentJob?.tokenUsageJson) }}</pre>
  </Dialog>

  <Dialog v-model="outputDialogVisible" title="AI 输出详情" width="70%">
    <el-descriptions :column="2" border v-if="currentOutput">
      <el-descriptions-item label="输出类型">{{ currentOutput.outputType }}</el-descriptions-item>
      <el-descriptions-item label="目标对象">{{ currentOutput.targetObjectType }}#{{ currentOutput.targetObjectId }}</el-descriptions-item>
      <el-descriptions-item label="审核状态">{{ currentOutput.reviewStatus }}</el-descriptions-item>
      <el-descriptions-item label="安全状态">{{ currentOutput.safetyStatus }}</el-descriptions-item>
      <el-descriptions-item label="患者可见">{{ currentOutput.patientVisible ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="审核人">{{ currentOutput.reviewedByName || '-' }}</el-descriptions-item>
    </el-descriptions>
    <el-divider />
    <div class="detail-label">渲染文本</div>
    <div class="rendered-text">{{ currentOutput?.renderedText || '-' }}</div>
    <el-divider />
    <div class="detail-label">evidence_refs</div>
    <pre class="json-pre">{{ prettyJson(currentOutput?.evidenceRefsJson) }}</pre>
    <div class="detail-label">content_json</div>
    <pre class="json-pre">{{ prettyJson(currentOutput?.contentJson) }}</pre>
  </Dialog>

  <Dialog v-model="editDialog.visible" title="编辑 AI 输出" width="700px">
    <el-form label-width="110px">
      <el-form-item label="编辑文本">
        <el-input v-model="editDialog.editedText" type="textarea" :rows="8" />
      </el-form-item>
      <el-form-item label="患者可见">
        <el-switch v-model="editDialog.patientVisible" />
      </el-form-item>
      <el-form-item label="审核备注">
        <el-input v-model="editDialog.reviewNote" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="editDialog.loading" @click="handleEditSubmit">保存</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import {
  acceptRehabAiOutput,
  editRehabAiOutput,
  getRehabAiJob,
  getRehabAiJobPage,
  getRehabAiOutput,
  getRehabAiOutputPage,
  regenerateRehabAiOutput,
  rejectRehabAiOutput,
  RehabAiJobPageReqVO,
  RehabAiOutputPageReqVO
} from '@/api/rehab/ai'

defineOptions({ name: 'RehabAiCenter' })

const message = useMessage()
const activeTab = ref<'job' | 'output'>('job')

const jobLoading = ref(false)
const outputLoading = ref(false)

const jobList = ref<any[]>([])
const outputList = ref<any[]>([])
const jobTotal = ref(0)
const outputTotal = ref(0)

const jobQuery = reactive<RehabAiJobPageReqVO>({
  pageNo: 1,
  pageSize: 10,
  patientId: undefined,
  jobType: undefined,
  status: undefined,
  triggeredByUserId: undefined
})

const outputQuery = reactive<RehabAiOutputPageReqVO>({
  pageNo: 1,
  pageSize: 10,
  patientId: undefined,
  outputType: undefined,
  targetObjectType: undefined,
  reviewStatus: undefined,
  safetyStatus: undefined
})

const currentJob = ref<any>()
const currentOutput = ref<any>()
const jobDialogVisible = ref(false)
const outputDialogVisible = ref(false)

const editDialog = reactive({
  visible: false,
  loading: false,
  outputId: undefined as number | undefined,
  editedText: '',
  patientVisible: false,
  reviewNote: ''
})

const getJobList = async () => {
  jobLoading.value = true
  try {
    const data = await getRehabAiJobPage(jobQuery)
    jobList.value = data.list || []
    jobTotal.value = data.total || 0
  } finally {
    jobLoading.value = false
  }
}

const getOutputList = async () => {
  outputLoading.value = true
  try {
    const data = await getRehabAiOutputPage(outputQuery)
    outputList.value = data.list || []
    outputTotal.value = data.total || 0
  } finally {
    outputLoading.value = false
  }
}

const handleJobQuery = () => {
  jobQuery.pageNo = 1
  getJobList()
}

const resetJobQuery = () => {
  jobQuery.pageNo = 1
  jobQuery.pageSize = 10
  jobQuery.patientId = undefined
  jobQuery.jobType = undefined
  jobQuery.status = undefined
  jobQuery.triggeredByUserId = undefined
  getJobList()
}

const handleOutputQuery = () => {
  outputQuery.pageNo = 1
  getOutputList()
}

const resetOutputQuery = () => {
  outputQuery.pageNo = 1
  outputQuery.pageSize = 10
  outputQuery.patientId = undefined
  outputQuery.outputType = undefined
  outputQuery.targetObjectType = undefined
  outputQuery.reviewStatus = undefined
  outputQuery.safetyStatus = undefined
  getOutputList()
}

const openJobDetail = async (row: any) => {
  currentJob.value = await getRehabAiJob(row.id)
  jobDialogVisible.value = true
}

const openOutputDetail = async (row: any) => {
  currentOutput.value = await getRehabAiOutput(row.id)
  outputDialogVisible.value = true
}

const handleAccept = async (row: any, patientVisible: boolean) => {
  await acceptRehabAiOutput({ outputId: row.id, patientVisible })
  message.success(patientVisible ? '已采纳并设置患者可见' : '已采纳')
  await getOutputList()
}

const openEdit = (row: any) => {
  editDialog.outputId = row.id
  editDialog.editedText = row.renderedText || ''
  editDialog.patientVisible = !!row.patientVisible
  editDialog.reviewNote = ''
  editDialog.visible = true
}

const handleEditSubmit = async () => {
  if (!editDialog.outputId) return
  editDialog.loading = true
  try {
    await editRehabAiOutput({
      outputId: editDialog.outputId,
      editedText: editDialog.editedText,
      patientVisible: editDialog.patientVisible,
      reviewNote: editDialog.reviewNote
    })
    message.success('编辑成功')
    editDialog.visible = false
    await getOutputList()
  } finally {
    editDialog.loading = false
  }
}

const handleReject = async (row: any) => {
  await rejectRehabAiOutput({ outputId: row.id, reviewNote: '人工驳回' })
  message.success('已驳回')
  await getOutputList()
}

const handleRegenerate = async (row: any) => {
  const resp = await regenerateRehabAiOutput({ outputId: row.id, asyncMode: false })
  message.success(`已发起重生成，jobId=${resp.jobId}`)
  await Promise.all([getJobList(), getOutputList()])
}

const prettyJson = (value?: string) => {
  if (!value) return '-'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

watch(
  () => activeTab.value,
  (tab) => {
    if (tab === 'job') {
      getJobList()
    } else {
      getOutputList()
    }
  }
)

onMounted(() => {
  getJobList()
})
</script>

<style scoped>
.json-pre {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 260px;
  overflow: auto;
}

.detail-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #334155;
}

.rendered-text {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
