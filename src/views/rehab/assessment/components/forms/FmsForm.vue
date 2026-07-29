<template>
  <el-card shadow="never" class="assessment-form-shell">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">FMS 功能动作筛查</span>
        <el-tag size="small" type="success">7 项 / 21 分</el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-12px"
      title="双侧动作按较低侧计分；出现疼痛时该项按 0 分计入总分。"
    />

    <div class="score-summary mb-16px">
      <el-statistic title="总分" :value="localData.summary.totalScore" suffix="/ 21" />
      <el-statistic title="左右不对称" :value="localData.summary.asymmetryCount" suffix=" 项" />
      <el-statistic title="未完成" :value="localData.summary.incompleteCount" suffix=" 项" />
      <el-tag :type="riskTagType" size="large">{{ riskLabel }}</el-tag>
    </div>

    <el-table :data="localData.items" border>
      <el-table-column prop="name" label="动作" min-width="150" fixed />
      <el-table-column label="评分（0-3）" min-width="260">
        <template #default="{ row }">
          <div v-if="row.bilateral" class="flex gap-8px">
            <el-input-number
              v-model="row.leftScore"
              :min="0"
              :max="3"
              :step="1"
              controls-position="right"
              class="!w-110px"
            />
            <el-input-number
              v-model="row.rightScore"
              :min="0"
              :max="3"
              :step="1"
              controls-position="right"
              class="!w-110px"
            />
          </div>
          <el-input-number
            v-else
            v-model="row.score"
            :min="0"
            :max="3"
            :step="1"
            controls-position="right"
            class="!w-110px"
          />
          <div v-if="row.bilateral" class="mt-4px text-12px text-gray-500">左 / 右</div>
        </template>
      </el-table-column>
      <el-table-column label="清除测试" min-width="150">
        <template #default="{ row }">
          <el-select v-model="row.clearingTest" class="!w-full">
            <el-option label="未测试" value="not_tested" />
            <el-option label="阴性" value="negative" />
            <el-option label="阳性" value="positive" />
            <el-option label="不适用" value="not_applicable" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="疼痛" width="90" align="center">
        <template #default="{ row }">
          <el-switch v-model="row.pain" />
        </template>
      </el-table-column>
      <el-table-column label="计入分" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="itemScore(row) === 0 ? 'danger' : 'info'">
            {{ itemScore(row) ?? '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="220">
        <template #default="{ row }">
          <el-input v-model="row.note" placeholder="动作质量、代偿或限制" />
        </template>
      </el-table-column>
    </el-table>

    <el-form :model="localData" label-width="110px" class="mt-16px">
      <el-form-item label="综合结论">
        <el-input
          v-model="localData.summary.conclusion"
          type="textarea"
          :rows="3"
          placeholder="记录主要受限模式、左右差和后续处理建议"
        />
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script lang="ts" setup>
import {
  buildDefaultFmsFormData,
  mergeStructuredAssessmentData
} from '../../config/structuredAssessmentConfig'

const props = defineProps<{ modelValue?: Record<string, any> }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', value: Record<string, any>): void
}>()

const localData = reactive<Record<string, any>>(buildDefaultFmsFormData())

const itemScore = (item: Record<string, any>): number | undefined => {
  if (item.pain || item.clearingTest === 'positive') {
    return 0
  }
  if (item.bilateral) {
    if (item.leftScore == null || item.rightScore == null) {
      return undefined
    }
    return Math.min(Number(item.leftScore), Number(item.rightScore))
  }
  return item.score == null ? undefined : Number(item.score)
}

const syncSummary = () => {
  const scores = localData.items.map(itemScore)
  const completedScores = scores.filter((score: number | undefined) => score != null) as number[]
  const asymmetryCount = localData.items.filter(
    (item: Record<string, any>) =>
      item.bilateral &&
      item.leftScore != null &&
      item.rightScore != null &&
      Number(item.leftScore) !== Number(item.rightScore)
  ).length
  const painDetected = localData.items.some(
    (item: Record<string, any>) => item.pain || item.clearingTest === 'positive'
  )
  const totalScore = completedScores.reduce((sum: number, score: number) => sum + score, 0)
  let riskLevel = 'pending'
  if (completedScores.length === localData.items.length) {
    riskLevel = painDetected ? 'pain' : totalScore <= 14 ? 'attention' : 'normal'
  }
  Object.assign(localData.summary, {
    totalScore,
    asymmetryCount,
    painDetected,
    incompleteCount: localData.items.length - completedScores.length,
    riskLevel
  })
}

const riskLabel = computed(() => {
  const labels: Record<string, string> = {
    pending: '待完成',
    pain: '存在疼痛',
    attention: '建议重点关注',
    normal: '常规跟进'
  }
  return labels[localData.summary.riskLevel] || '待完成'
})

const riskTagType = computed(() => {
  const types: Record<string, 'info' | 'danger' | 'warning' | 'success'> = {
    pending: 'info',
    pain: 'danger',
    attention: 'warning',
    normal: 'success'
  }
  return types[localData.summary.riskLevel] || 'info'
})

const resetLocalData = (value?: Record<string, any>) => {
  const next = mergeStructuredAssessmentData(buildDefaultFmsFormData(), value)
  if (JSON.stringify(localData) === JSON.stringify(next)) {
    syncSummary()
    return
  }
  Object.keys(localData).forEach((key) => delete localData[key])
  Object.assign(localData, next)
  syncSummary()
}

const getFormData = () => JSON.parse(JSON.stringify(localData))

watch(
  () => props.modelValue,
  (value) => resetLocalData(value),
  { immediate: true, deep: true }
)

watch(
  localData,
  () => {
    syncSummary()
    const payload = getFormData()
    emit('update:modelValue', payload)
    emit('change', payload)
  },
  { deep: true }
)

const validate = async () => {
  if (localData.summary.incompleteCount > 0) {
    ElMessage.warning(`FMS 尚有 ${localData.summary.incompleteCount} 项未评分，仍可保存草稿`)
  }
  return true
}

const reset = () => {
  resetLocalData()
  emit('update:modelValue', getFormData())
  emit('change', getFormData())
}

defineExpose({ validate, getFormData, reset })
</script>

<style scoped>
.assessment-form-shell {
  border: 1px solid var(--el-border-color-light);
}

.score-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 12px;
  align-items: center;
}

@media (max-width: 768px) {
  .score-summary {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }
}
</style>
