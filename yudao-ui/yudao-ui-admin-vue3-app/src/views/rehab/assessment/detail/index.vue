<template>
  <ContentWrap>
    <div class="mb-12px flex items-center justify-between">
      <div class="text-16px font-bold">评估详情</div>
      <div>
        <el-button @click="goBack">返回</el-button>
        <el-button type="primary" v-hasPermi="['rehab:assessment:update']" @click="handleEdit">
          编辑
        </el-button>
        <el-button
          v-if="AI_ENABLED"
          type="primary"
          v-hasPermi="['rehab:ai:generate']"
          @click="handleGenerateAiInterpretation"
        >
          AI 解读
        </el-button>
        <el-button type="primary" v-hasPermi="['rehab:plan:create']" @click="handleCreatePlan">
          创建计划
        </el-button>
        <el-button type="primary" v-hasPermi="['rehab:assessment:generate-report']" @click="handleGenerateReport">
          生成报告
        </el-button>
      </div>
    </div>

    <el-skeleton :loading="loading" animated>
      <template #default>
        <el-row :gutter="12" class="mb-12px">
          <el-col :span="16">
            <el-card shadow="never">
              <template #header>评估基本信息</template>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="assessment_no">{{ detail?.assessment?.assessmentNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="评估类型">{{ detail?.assessment?.assessmentType || '-' }}</el-descriptions-item>
                <el-descriptions-item label="评估日期">{{ detail?.assessment?.assessmentDate || '-' }}</el-descriptions-item>
                <el-descriptions-item label="评估人">{{ detail?.assessment?.assessorName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="状态">{{ detail?.assessment?.status || '-' }}</el-descriptions-item>
                <el-descriptions-item label="数据状态">{{ detail?.assessment?.rawInputStatus || '-' }}</el-descriptions-item>
                <el-descriptions-item label="质量等级">{{ detail?.assessment?.qualityGrade || '-' }}</el-descriptions-item>
                <el-descriptions-item label="置信等级">{{ detail?.assessment?.confidenceGrade || '-' }}</el-descriptions-item>
                <el-descriptions-item label="本次重点">{{ detail?.assessment?.chiefFocus || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="mb-12px">
              <template #header>患者</template>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="患者编号">{{ detail?.patient?.patientNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="姓名">{{ detail?.patient?.name || '-' }}</el-descriptions-item>
                <el-descriptions-item label="当前阶段">{{ detail?.patient?.currentStage || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
            <el-card shadow="never">
              <template #header>Episode</template>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="episode_no">{{ detail?.episode?.episodeNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="阶段">{{ detail?.episode?.currentStage || '-' }}</el-descriptions-item>
                <el-descriptions-item label="状态">{{ detail?.episode?.status || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
        </el-row>

        <el-card v-if="AI_ENABLED" shadow="never" class="mb-12px">
          <template #header>
            <div class="flex items-center justify-between">
              <span>AI 解读（治疗师）</span>
              <el-button type="primary" link v-hasPermi="['rehab:ai:generate']" @click="handleGenerateAiInterpretation">
                重生成
              </el-button>
            </div>
          </template>
          <el-descriptions :column="2" border class="mb-10px">
            <el-descriptions-item label="审核状态">{{ aiInterpretation?.reviewStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="安全状态">{{ aiInterpretation?.safetyStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="输出时间">{{ aiInterpretation?.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="输出ID">{{ aiInterpretation?.id || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="module-json">{{ aiInterpretation?.renderedText || '暂无 AI 解读，请点击“AI 解读”生成。' }}</div>
        </el-card>

        <el-card v-if="hasStaticModule" shadow="never" class="mb-12px">
          <template #header>静态评估结果汇总</template>
          <el-row :gutter="12">
            <el-col :span="8">
              <el-card shadow="never" class="summary-card">
                <template #header>后面观汇总</template>
                <div class="summary-text">{{ staticSummary.posterior.summaryText }}</div>
                <div class="summary-meta">
                  非正常项：{{ staticSummary.posterior.abnormalCount }}，正常项：{{ staticSummary.posterior.normalCount }}，未填项：{{ staticSummary.posterior.missingCount }}
                </div>
                <ul class="summary-list">
                  <li v-for="item in staticSummary.posterior.abnormalItems" :key="item.fieldPath + item.value">
                    {{ item.fieldLabel }}：{{ item.value }}
                  </li>
                </ul>
              </el-card>
            </el-col>
            <el-col :span="8">
              <el-card shadow="never" class="summary-card">
                <template #header>侧面观汇总</template>
                <div class="summary-text">{{ staticSummary.lateral.summaryText }}</div>
                <div class="summary-meta">
                  非正常项：{{ staticSummary.lateral.abnormalCount }}，正常项：{{ staticSummary.lateral.normalCount }}，未填项：{{ staticSummary.lateral.missingCount }}
                </div>
                <ul class="summary-list">
                  <li v-for="item in staticSummary.lateral.abnormalItems" :key="item.fieldPath + item.value">
                    {{ item.fieldLabel }}：{{ item.value }}
                  </li>
                </ul>
              </el-card>
            </el-col>
            <el-col :span="8">
              <el-card shadow="never" class="summary-card">
                <template #header>正面观汇总</template>
                <div class="summary-text">{{ staticSummary.anterior.summaryText }}</div>
                <div class="summary-meta">
                  非正常项：{{ staticSummary.anterior.abnormalCount }}，正常项：{{ staticSummary.anterior.normalCount }}，未填项：{{ staticSummary.anterior.missingCount }}
                </div>
                <ul class="summary-list">
                  <li v-for="item in staticSummary.anterior.abnormalItems" :key="item.fieldPath + item.value">
                    {{ item.fieldLabel }}：{{ item.value }}
                  </li>
                </ul>
              </el-card>
            </el-col>
          </el-row>
          <el-card shadow="never" class="mt-12px summary-card">
            <template #header>总体静态汇总</template>
            <div class="summary-text">{{ staticSummary.overall.summaryText }}</div>
            <div class="summary-meta">关键发现：{{ staticSummary.overall.keyFindings.length }} 项</div>
            <el-alert
              v-if="staticSummary.overall.needsManualReview"
              type="warning"
              :closable="false"
              title="存在中轴/左右冗余记录冲突，需人工复核。"
              class="mb-8px"
            />
            <ul class="summary-list">
              <li v-for="(item, index) in staticSummary.overall.keyFindings" :key="`${item}-${index}`">{{ item }}</li>
            </ul>
          </el-card>
        </el-card>

        <NasmCesSummaryCard v-if="hasNasmModule" :module-data-json="nasmModuleDataJson" />
        <SfmaSummaryCard v-if="hasSfmaModule" :module-data-json="sfmaModuleDataJson" />

        <el-card shadow="never" class="mb-12px">
          <template #header>模块数据概览</template>
          <el-table :data="detail?.moduleDataList || []" stripe>
            <el-table-column label="模块" prop="moduleType" min-width="120" />
            <el-table-column label="状态" prop="moduleStatus" min-width="120" />
            <el-table-column label="来源" prop="sourceType" min-width="100" />
            <el-table-column label="版本" prop="version" min-width="90" />
            <el-table-column label="更新时间" prop="updateTime" min-width="170" />
            <el-table-column label="数据" min-width="420">
              <template #default="scope">
                <pre class="module-json">{{ scope.row.dataJson || '-' }}</pre>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" class="mb-12px">
          <template #header>
            <div class="flex items-center justify-between">
              <span>附件列表</span>
              <div class="flex items-center gap-8px">
                <el-select
                  v-model="attachmentModuleType"
                  placeholder="选择所属模块"
                  class="!w-150px"
                >
                  <el-option
                    v-for="item in attachmentModuleOptions"
                    :key="item"
                    :label="item"
                    :value="item"
                  />
                </el-select>
                <el-upload
                  :show-file-list="false"
                  :http-request="handleAttachmentUpload"
                  :disabled="uploadingAttachment || !attachmentModuleType"
                >
                  <el-button
                    type="primary"
                    :loading="uploadingAttachment"
                    v-hasPermi="['rehab:assessment:update']"
                  >
                    上传附件
                  </el-button>
                </el-upload>
              </div>
            </div>
          </template>
          <el-table :data="detail?.attachments || []" stripe>
            <el-table-column label="文件名" prop="fileName" min-width="220" />
            <el-table-column label="模块" prop="moduleType" min-width="120" />
            <el-table-column label="大小" min-width="100">
              <template #default="scope">{{ formatFileSize(scope.row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="解析状态" prop="parseStatus" min-width="120" />
            <el-table-column label="上传时间" prop="createTime" min-width="170" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="scope">
                <el-button
                  type="primary"
                  link
                  v-hasPermi="['rehab:assessment:detail']"
                  @click="handleAttachmentDownload(scope.row)"
                >
                  下载
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never">
          <template #header>操作日志</template>
          <el-table :data="detail?.operationLogs || []" stripe>
            <el-table-column label="时间" prop="createTime" min-width="170" />
            <el-table-column label="操作" prop="operationType" min-width="160" />
            <el-table-column label="操作人" prop="operatorName" min-width="120" />
            <el-table-column label="备注" prop="remark" min-width="260" />
          </el-table>
        </el-card>
      </template>
    </el-skeleton>
  </ContentWrap>
</template>

<script lang="ts" setup>
import {
  generateAssessmentInterpretation,
  getRehabAiOutputPage,
  RehabAiGenerateRespVO
} from '@/api/rehab/ai'
import {
  downloadRehabAssessmentAttachment,
  getRehabAssessment,
  uploadRehabAssessmentAttachment
} from '@/api/rehab/assessment'
import { generateRehabReport } from '@/api/rehab/report'
import NasmCesSummaryCard from '@/views/rehab/assessment/components/NasmCesSummaryCard.vue'
import SfmaSummaryCard from '@/views/rehab/assessment/components/SfmaSummaryCard.vue'

defineOptions({ name: 'RehabAssessmentDetail' })

const AI_ENABLED = import.meta.env.VITE_REHAB_AI_ENABLED === 'true'
const message = useMessage()
const route = useRoute()
const { push } = useRouter()

const loading = ref(false)
const detail = ref<any>()
const aiInterpretation = ref<any>()
const attachmentModuleType = ref('')
const uploadingAttachment = ref(false)
const id = computed(() => Number(route.params.id))
const attachmentModuleOptions = computed(() => {
  const moduleTypes = (detail.value?.moduleDataList || [])
    .map((item: any) => item.moduleType)
    .filter(Boolean)
  return [...new Set<string>(moduleTypes)]
})
const hasStaticModule = computed(() =>
  Boolean((detail.value?.moduleDataList || []).find((item: any) => item.moduleType === 'static'))
)
const hasNasmModule = computed(() =>
  Boolean((detail.value?.moduleDataList || []).find((item: any) => item.moduleType === 'nasm'))
)
const hasSfmaModule = computed(() =>
  Boolean((detail.value?.moduleDataList || []).find((item: any) => item.moduleType === 'sfma'))
)
const nasmModuleDataJson = computed(() => {
  const moduleList = detail.value?.moduleDataList || []
  const nasmModule = moduleList.find((item: any) => item.moduleType === 'nasm')
  return nasmModule?.dataJson || null
})
const sfmaModuleDataJson = computed(() => {
  const moduleList = detail.value?.moduleDataList || []
  const sfmaModule = moduleList.find((item: any) => item.moduleType === 'sfma')
  return sfmaModule?.dataJson || null
})

type SummaryAbnormalItem = {
  fieldPath: string
  fieldLabel: string
  value: string
}

const emptyViewSummary = {
  summaryText: '该视角暂未录入完整评估结果',
  abnormalCount: 0,
  normalCount: 0,
  missingCount: 0,
  abnormalItems: [] as SummaryAbnormalItem[]
}

const staticSummary = computed(() => {
  const moduleList = detail.value?.moduleDataList || []
  const staticModule = moduleList.find((item: any) => item.moduleType === 'static')
  if (!staticModule?.dataJson) {
    return {
      posterior: { ...emptyViewSummary },
      lateral: { ...emptyViewSummary },
      anterior: { ...emptyViewSummary },
      overall: {
        summaryText: '静态评估显示当前录入信息有限，证据不足，需结合人工复核。',
        keyFindings: [] as string[],
        needsManualReview: true
      }
    }
  }
  const payload = parseJsonObject(staticModule.dataJson)
  const summary = payload?.static_summary || {}
  return {
    posterior: normalizeViewSummary(summary?.posterior_view_summary),
    lateral: normalizeViewSummary(summary?.lateral_view_summary),
    anterior: normalizeViewSummary(summary?.anterior_view_summary),
    overall: normalizeOverallSummary(summary?.overall_summary)
  }
})

const parseJsonObject = (raw: any) => {
  if (!raw) {
    return {}
  }
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw)
    } catch {
      return {}
    }
  }
  if (typeof raw === 'object') {
    return raw
  }
  return {}
}

const normalizeViewSummary = (raw: any) => {
  const abnormalItems = Array.isArray(raw?.abnormal_items)
    ? raw.abnormal_items.map((item: any) => ({
        fieldPath: item?.field_path || '',
        fieldLabel: item?.field_label || '未命名项目',
        value: item?.value || '-'
      }))
    : []
  return {
    summaryText: raw?.summary_text || emptyViewSummary.summaryText,
    abnormalCount: abnormalItems.length,
    normalCount: Number(raw?.normal_count || 0),
    missingCount: Number(raw?.missing_count || 0),
    abnormalItems
  }
}

const normalizeOverallSummary = (raw: any) => {
  return {
    summaryText: raw?.summary_text || '静态评估显示当前录入信息有限，证据不足，需结合人工复核。',
    keyFindings: Array.isArray(raw?.key_findings) ? raw.key_findings : [],
    needsManualReview: Boolean(raw?.needs_manual_review)
  }
}

const load = async () => {
  loading.value = true
  try {
    detail.value = await getRehabAssessment(id.value)
    if (!attachmentModuleOptions.value.includes(attachmentModuleType.value)) {
      attachmentModuleType.value = attachmentModuleOptions.value[0] || ''
    }
    if (AI_ENABLED) {
      await loadAiInterpretation()
    }
  } finally {
    loading.value = false
  }
}

const loadAiInterpretation = async () => {
  if (!detail.value?.assessment?.patientId) {
    aiInterpretation.value = undefined
    return
  }
  const data = await getRehabAiOutputPage({
    pageNo: 1,
    pageSize: 50,
    patientId: detail.value.assessment.patientId,
    outputType: 'therapist_summary',
    targetObjectType: 'assessment'
  })
  const rows = data.list || []
  aiInterpretation.value = rows.find((item: any) => Number(item.targetObjectId) === id.value) || undefined
}

const goBack = () => {
  if (detail.value?.assessment?.patientId) {
    push(`/rehab/patient/detail/${detail.value.assessment.patientId}`)
    return
  }
  push('/rehab/assessment')
}

const handleGenerateReport = async () => {
  await message.confirm('确认基于当前评估生成报告吗？')
  await generateRehabReport({ assessmentId: id.value })
  message.success('报告生成成功')
}

const handleEdit = () => {
  push(`/rehab/assessment/edit/${id.value}`)
}

const handleAttachmentUpload = async (options: any) => {
  if (!attachmentModuleType.value) {
    message.warning('请先选择附件所属模块')
    return
  }
  const formData = new FormData()
  formData.append('assessmentId', String(id.value))
  formData.append('moduleType', attachmentModuleType.value)
  formData.append('file', options.file)
  uploadingAttachment.value = true
  try {
    await uploadRehabAssessmentAttachment(formData)
    message.success('附件上传成功')
    await load()
  } finally {
    uploadingAttachment.value = false
  }
}

const handleAttachmentDownload = async (row: any) => {
  const data = await downloadRehabAssessmentAttachment(row.id)
  const blob = data instanceof Blob ? data : new Blob([data])
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = row.fileName || 'assessment-attachment'
  anchor.click()
  URL.revokeObjectURL(url)
}

const formatFileSize = (value?: number) => {
  const bytes = Number(value || 0)
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const handleCreatePlan = () => {
  if (!detail.value?.assessment) {
    return
  }
  const assessment = detail.value.assessment
  push(
    `/rehab/plan/create?patientId=${assessment.patientId}&episodeId=${assessment.episodeId}&sourceAssessmentId=${assessment.id}`
  )
}

const handleGenerateAiInterpretation = async () => {
  if (!AI_ENABLED) {
    return
  }
  await message.confirm('确认生成该评估的 AI 解读吗？')
  const resp: RehabAiGenerateRespVO = await generateAssessmentInterpretation({
    assessmentId: id.value,
    asyncMode: false
  })
  message.success(resp.fallbackUsed ? 'AI 解读已降级生成' : 'AI 解读生成成功')
  await loadAiInterpretation()
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.module-json {
  max-height: 120px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f6f8fa;
  border-radius: 4px;
  padding: 6px;
}

.summary-card {
  min-height: 240px;
}

.summary-text {
  font-size: 14px;
  color: var(--el-text-color-primary);
  line-height: 22px;
  margin-bottom: 8px;
}

.summary-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.summary-list {
  margin: 0;
  padding-left: 18px;
  max-height: 180px;
  overflow: auto;
}
</style>
