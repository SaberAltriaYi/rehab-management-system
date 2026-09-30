<template>
  <div>
    <el-empty v-if="!result || !result.analyzedRevision" description="尚未完成分析" />
    <template v-else>
      <el-alert
        v-if="(result.signBlockers || []).length && assessment.status !== 'COMPLETED'"
        type="warning"
        :closable="false"
        show-icon
        class="mb-12px"
      >
        <template #title>签署前需完成：</template>
        <ul class="blockers">
          <li v-for="(b, i) in result.signBlockers" :key="i">{{ b }}</li>
        </ul>
      </el-alert>
      <el-alert
        type="info"
        :closable="false"
        class="mb-12px"
        title="系统分由规则引擎计算；治疗师可确认、修改（需原因）或标记不适用（需原因）。AI 不参与评分。疼痛导致的 0 分不能改为非 0；疼痛记录有误请回到“人工录入”修正后重新计算。"
      />

      <h4>评分审核</h4>
      <el-table :data="result.scores || []" size="small" :row-class-name="rowClass">
        <el-table-column label="项目" min-width="160">
          <template #default="{ row }">
            {{ row.testLabel || testLabel(row.testCode) }} {{ sideLabel(row.side) }}
            <el-tag v-if="row.systemScoreChanged" size="small" type="danger">系统分已变化</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="系统状态" width="140">
          <template #default="{ row }">{{ SYSTEM_STATUS_LABEL[row.systemStatus] || row.systemStatus }}</template>
        </el-table-column>
        <el-table-column label="系统分" width="70">
          <template #default="{ row }">{{ fmt(row.systemScore) }}</template>
        </el-table-column>
        <el-table-column label="参考分" width="70">
          <template #default="{ row }"><span class="muted">{{ fmt(row.provisionalScore) }}</span></template>
        </el-table-column>
        <el-table-column label="当前最终" width="120">
          <template #default="{ row }">
            {{ fmt(row.finalScore) }} <span class="muted">{{ FINAL_STATUS_LABEL[row.finalStatus] || row.finalStatus }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审核" min-width="460">
          <template #default="{ row }">
            <div class="review-row">
              <el-select v-model="form(row).finalStatus" size="small" style="width: 120px" :disabled="!editable">
                <el-option v-for="o in FINAL_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-input-number
                v-if="form(row).finalStatus === 'modified' && scoreRange(row.scoringScheme)"
                v-model="form(row).finalScore"
                size="small"
                :min="scoreRange(row.scoringScheme)!.min"
                :max="scoreRange(row.scoringScheme)!.max"
                :step="scoreRange(row.scoringScheme)!.step"
                :precision="scoreRange(row.scoringScheme)!.step < 1 ? 2 : 0"
                controls-position="right"
                style="width: 110px"
                :disabled="!editable"
              />
              <el-input
                v-if="form(row).finalStatus !== 'confirmed'"
                v-model="form(row).reason"
                size="small"
                maxlength="200"
                placeholder="原因（必填）"
                style="width: 180px"
                :disabled="!editable"
              />
              <el-button
                size="small"
                type="primary"
                v-hasPermi="['rehab:motion:review']"
                :disabled="!editable || row.systemStatus === 'pending_pain'"
                @click="saveScore(row)"
              >
                保存
              </el-button>
            </div>
            <div v-if="row.systemStatus === 'pending_pain'" class="err">疼痛信息未记录，需补录后重新计算</div>
          </template>
        </el-table-column>
      </el-table>

      <h4>
        AI 报告草稿
        <el-button
          size="small"
          class="ml-8px"
          v-hasPermi="['rehab:motion:review']"
          :disabled="!editable"
          :loading="regenerating"
          @click="regenerate"
        >
          重新生成
        </el-button>
      </h4>
      <div class="muted mb-8px">
        AI 只解释已计算的指标与评分，每条结论须引用证据编号；不诊断、不改分、不签署。AI 关闭或失败时自动生成模板草稿（不含 AI 内容）。
        {{ assessment.aiAllowed ? '' : '该评估未授权使用 AI，仅生成模板草稿。' }}
      </div>
      <el-empty v-if="!drafts.length" description="暂无草稿（分析完成后自动生成）" :image-size="60" />
      <el-card v-for="d in drafts" :key="d.id" shadow="never" class="mb-8px">
        <template #header>
          <div class="draft-head">
            <span>
              #{{ d.id }}
              <el-tag size="small" :type="draftTag(d.status)">{{ DRAFT_LABEL[d.status] || d.status }}</el-tag>
              <span class="muted">
                {{ d.provider || 'template' }} {{ d.model || '' }} · 提示词 {{ d.promptVersion || '-' }} · 安全校验 {{ d.safetyStatus || '-' }}
                {{ d.analysisRevision !== result.analyzedRevision ? ' · 基于旧分析' : '' }}
              </span>
            </span>
            <span v-if="isOpen(d)">
              <el-button size="small" v-hasPermi="['rehab:motion:review']" :disabled="!editable" @click="startEdit(d)">编辑</el-button>
              <el-button size="small" type="success" v-hasPermi="['rehab:motion:review']" :disabled="!editable" @click="reviewDraft(d, 'accept')">
                接受
              </el-button>
              <el-button size="small" type="danger" v-hasPermi="['rehab:motion:review']" :disabled="!editable" @click="reviewDraft(d, 'reject')">
                驳回
              </el-button>
            </span>
          </div>
        </template>
        <div v-if="d.fallbackReason" class="muted mb-8px">模板草稿原因：{{ d.fallbackReason }}</div>
        <div v-if="d.validationMessage" class="err mb-8px">校验：{{ d.validationMessage }}</div>
        <el-input v-if="editingId === d.id" v-model="editText" type="textarea" :rows="12" maxlength="20000" show-word-limit />
        <pre v-else class="draft">{{ d.editedText || d.renderedText }}</pre>
        <div class="muted">引用证据：{{ refs(d).join('、') || '无' }}</div>
      </el-card>

      <el-divider />
      <div class="sign">
        <template v-if="assessment.status === 'COMPLETED'">
          <el-tag type="success">已签署</el-tag>
          <span class="muted">签署后结果只读；如需修改请发起修订（原报告保留为历史版本）。</span>
          <el-button type="warning" v-hasPermi="['rehab:motion:sign']" @click="amend">发起修订</el-button>
        </template>
        <template v-else>
          <el-button
            type="primary"
            v-hasPermi="['rehab:motion:sign']"
            :disabled="(result.signBlockers || []).length > 0"
            @click="sign"
          >
            治疗师签署
          </el-button>
          <span class="muted">签署将生成治疗师版与患者版报告（含 PDF），并记录签署人和时间。</span>
        </template>
      </div>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'
import { MotionApi, MotionAssessmentVO, newIdempotencyKey } from '@/api/rehab/motion'
import { FINAL_STATUS_LABEL, FINAL_STATUS_OPTIONS, SYSTEM_STATUS_LABEL, scoreRange, sideLabel, testLabel } from '../constants'

const props = defineProps<{ assessment: MotionAssessmentVO; result: any }>()
const emit = defineEmits(['changed'])
const message = useMessage()

const DRAFT_LABEL: Record<string, string> = {
  generated: 'AI 草稿待审',
  fallback: '模板草稿待审',
  accepted: '已接受',
  rejected: '已驳回',
  stale: '已过期'
}
const draftTag = (s: string): any => (s === 'accepted' ? 'success' : s === 'rejected' || s === 'stale' ? 'info' : 'warning')
const editable = computed(() => props.assessment.status === 'PENDING_REVIEW' && !props.result?.stale)
const drafts = computed<any[]>(() => props.result?.aiDrafts || [])
const forms = reactive<Record<number, { finalStatus: string; finalScore?: number; reason: string }>>({})
const editingId = ref<number>()
const editText = ref('')
const regenerating = ref(false)

const fmt = (v: any) => (v === null || v === undefined ? '—' : Number(v).toString())
const rowClass = ({ row }: any) => (row.finalStatus === 'pending' || row.finalStatus === 'needs_recheck' ? 'pending-row' : '')
const form = (row: any) => {
  if (!forms[row.id]) {
    const def = row.finalStatus && row.finalStatus !== 'pending' && row.finalStatus !== 'needs_recheck' ? row.finalStatus : row.systemScore !== null && row.systemScore !== undefined ? 'confirmed' : row.scoringScheme === 'NASM_EVIDENCE' ? 'confirmed' : 'modified'
    forms[row.id] = {
      finalStatus: def,
      finalScore: row.finalScore ?? row.systemScore ?? row.provisionalScore ?? undefined,
      reason: row.changeReason || ''
    }
  }
  return forms[row.id]
}
const isOpen = (d: any) => (d.status === 'generated' || d.status === 'fallback') && d.analysisRevision === props.result.analyzedRevision
const refs = (d: any): string[] => {
  try {
    return JSON.parse(d.evidenceRefsJson || '[]')
  } catch {
    return []
  }
}

const saveScore = async (row: any) => {
  const f = form(row)
  if (f.finalStatus !== 'confirmed' && !f.reason.trim()) return message.warning('修改或不适用必须填写原因')
  const needsValue = f.finalStatus === 'modified' && !!scoreRange(row.scoringScheme)
  if (needsValue && (f.finalScore === undefined || f.finalScore === null)) return message.warning('请填写最终分')
  await MotionApi.reviewScore({
    scoreId: row.id,
    finalStatus: f.finalStatus,
    finalScore: needsValue ? f.finalScore : null,
    reason: f.reason.trim() || undefined
  })
  delete forms[row.id]
  message.success('已保存')
  emit('changed')
}

const startEdit = (d: any) => {
  editingId.value = d.id
  editText.value = d.editedText || d.renderedText || ''
}
const reviewDraft = async (d: any, action: 'accept' | 'reject') => {
  await MotionApi.reviewAi({ draftId: d.id, action, editedText: action === 'accept' && editingId.value === d.id ? editText.value : undefined })
  editingId.value = undefined
  message.success(action === 'accept' ? '已接受草稿' : '已驳回')
  emit('changed')
}
const regenerate = async () => {
  regenerating.value = true
  try {
    await MotionApi.regenerateAi(props.assessment.id, newIdempotencyKey('ai'))
    message.success('已提交重新生成任务，可在“处理任务”查看进度')
    emit('changed')
  } finally {
    regenerating.value = false
  }
}
const sign = async () => {
  await message.confirm('确认以本人身份签署该评估？签署后结果只读，并生成正式报告。')
  await MotionApi.sign(props.assessment.id)
  message.success('已签署，正在生成 PDF')
  emit('changed')
}
const amend = async () => {
  const { value } = await ElMessageBox.prompt('请输入修订原因（写入审计留痕）', '发起修订', {
    inputValidator: (v: string) => (!!v && v.trim().length > 0) || '原因必填',
    inputPlaceholder: '例如：补录清除测试结果'
  })
  await MotionApi.amend(props.assessment.id, value.trim())
  message.success('已进入修订，状态回到待治疗师审核')
  emit('changed')
}

watch(
  () => props.result?.analyzedRevision,
  () => Object.keys(forms).forEach((k) => delete forms[Number(k)])
)
</script>

<style scoped>
h4 {
  margin: 16px 0 8px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.err {
  color: var(--el-color-danger);
  font-size: 12px;
}
.blockers {
  margin: 4px 0 0;
  padding-left: 18px;
}
.review-row {
  display: flex;
  gap: 6px;
  align-items: center;
}
.draft-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.draft {
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 13px;
  margin: 0 0 8px;
  max-height: 420px;
  overflow: auto;
}
.sign {
  display: flex;
  gap: 12px;
  align-items: center;
}
:deep(.pending-row) {
  background: var(--el-color-warning-light-9);
}
</style>
