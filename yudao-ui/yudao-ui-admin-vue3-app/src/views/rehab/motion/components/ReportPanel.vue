<template>
  <div>
    <div class="toolbar">
      <el-button @click="load">刷新</el-button>
      <span class="muted">报告在治疗师签署后生成（治疗师版 / 患者版，含 JSON 与 PDF）；修订后旧版本保留为历史。</span>
    </div>
    <el-empty v-if="!reports.length" description="暂无报告（签署后生成）" :image-size="60" />
    <el-table v-else :data="reports" size="small" class="mt-8px">
      <el-table-column label="#" prop="id" width="70" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ row.reportType === 'patient' ? '患者版' : '治疗师版' }}</template>
      </el-table-column>
      <el-table-column label="版本" prop="versionNo" width="70" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'signed' ? 'success' : 'info'">{{ row.status === 'signed' ? '有效' : '已被替代' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签署人" prop="signerName" width="110" />
      <el-table-column label="签署时间" width="160">
        <template #default="{ row }">{{ row.signedTime ? formatDate(row.signedTime) : '' }}</template>
      </el-table-column>
      <el-table-column label="规则 / 提示词" min-width="160">
        <template #default="{ row }">{{ row.ruleVersion }} / {{ row.promptVersion || '无 AI' }}</template>
      </el-table-column>
      <el-table-column label="PDF" width="90">
        <template #default="{ row }">{{ PDF_LABEL[row.pdfStatus] || row.pdfStatus || '-' }}</template>
      </el-table-column>
      <el-table-column label="SHA-256" min-width="130" show-overflow-tooltip>
        <template #default="{ row }"><code>{{ row.contentSha256 }}</code></template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="view(row)">查看</el-button>
          <el-button link type="primary" v-hasPermi="['rehab:motion:export']" @click="exportJson(row)">JSON</el-button>
          <el-button link type="primary" v-hasPermi="['rehab:motion:export']" :disabled="row.pdfStatus !== 'ready'" @click="pdf(row)">
            PDF
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="visible" :title="content?.title || '报告'" size="60%">
      <template v-if="content">
        <div class="muted mb-8px">
          {{ content.assessment_ref }} · v{{ content.version_no }} · 引擎 {{ content.engine_version }} · 规则 {{ content.rule_version }}
          · 分析修订 {{ content.analysis_revision }}{{ content.ai_model ? ' · AI ' + content.ai_model + ' / ' + content.prompt_version : '' }}
        </div>
        <section v-for="(s, i) in content.sections || []" :key="i" class="sec">
          <h4>{{ s.title }}</h4>
          <p v-for="(l, j) in s.lines || []" :key="j">{{ l }}</p>
        </section>
        <div class="muted">签署：{{ content.signer_name }} {{ content.signed_time }}</div>
      </template>
    </el-drawer>
  </div>
</template>

<script lang="ts" setup>
import { MotionApi, MotionAssessmentVO } from '@/api/rehab/motion'
import download from '@/utils/download'
import { formatDate } from '@/utils/formatTime'

const props = defineProps<{ assessment: MotionAssessmentVO; result: any }>()
defineEmits(['changed'])
const PDF_LABEL: Record<string, string> = { pending: '生成中', ready: '可下载', failed: '失败' }
const reports = ref<any[]>([])
const visible = ref(false)
const content = ref<any>()
let timer: ReturnType<typeof setTimeout> | undefined

const load = async () => {
  reports.value = (await MotionApi.reports(props.assessment.id)) || []
  if (timer) clearTimeout(timer)
  if (reports.value.some((r) => r.pdfStatus === 'pending')) timer = setTimeout(load, 4000)
}
const view = async (row: any) => {
  const r: any = await MotionApi.report(row.id)
  content.value = r?.content
  visible.value = true
}
const exportJson = async (row: any) => {
  const r: any = await MotionApi.report(row.id)
  const blob = new Blob([JSON.stringify(r?.content ?? {}, null, 2)], { type: 'application/json' })
  download.json(blob, `motion-report-${props.assessment.id}-${row.reportType}-v${row.versionNo}.json`)
}
const pdf = async (row: any) => {
  const blob: any = await MotionApi.reportPdf(row.id)
  download.pdf(blob, `motion-report-${props.assessment.id}-${row.reportType}-v${row.versionNo}.pdf`)
}

watch(() => props.result?.reports?.length, load)
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
.sec h4 {
  margin: 14px 0 6px;
}
.sec p {
  margin: 2px 0;
  font-size: 13px;
  white-space: pre-wrap;
}
</style>
