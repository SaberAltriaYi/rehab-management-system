<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">Breakout 建议列表</span>
        <el-tag size="small" :type="disabled ? 'info' : 'success'">{{ disabled ? '待 Top Tier 完成' : '可处理' }}</el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-10px"
      title="Top Tier 完成后，系统会统一建议需要分解评估的项目。治疗师可选择进入分解或暂不分解。"
      description="建议顺序遵循教材保守路径：DN 优先分解，FP 次之，DP 最后且需疼痛管理优先。"
    />

    <el-empty v-if="!displayRows.length" description="暂未生成 Breakout 建议（完成 Top Tier 后自动生成）" />

    <el-table v-else :data="displayRows" border size="small">
      <el-table-column label="模式项" min-width="200">
        <template #default="scope">
          {{ scope.row.test_name_zh }}
        </template>
      </el-table-column>
      <el-table-column label="Top Tier" width="110">
        <template #default="scope">
          <el-tag :type="classificationTagType(scope.row.classification)">{{ scope.row.classification || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="建议顺序" width="120">
        <template #default="scope">
          <el-tag size="small" :type="stageTagType(scope.row.recommendation_stage)">
            {{ stageLabel(scope.row.recommendation_stage) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="建议原因" min-width="260">
        <template #default="scope">
          {{ scope.row.recommendation_reason || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="处理方式" min-width="260">
        <template #default="scope">
            <el-radio-group
              v-model="scope.row.recommendation_status"
              :disabled="disabled"
              @change="handleStatusChange(scope.row, scope.row.recommendation_status)"
            >
            <el-radio label="suggested">建议中</el-radio>
            <el-radio label="accepted">进入分解</el-radio>
            <el-radio label="skipped">暂不分解</el-radio>
          </el-radio-group>
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="240">
        <template #default="scope">
          <el-input
            v-model="scope.row.recommendation_note"
            type="textarea"
            :rows="2"
            :disabled="disabled"
            @input="emitChange"
          />
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { SfmaBreakoutRecommendation } from '@/views/rehab/assessment/config/sfmaConfig'

const props = withDefaults(
  defineProps<{
    modelValue?: SfmaBreakoutRecommendation[]
    disabled?: boolean
    excludeTestCodes?: string[]
  }>(),
  {
    modelValue: () => [],
    disabled: false,
    excludeTestCodes: () => []
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: SfmaBreakoutRecommendation[]): void
  (e: 'change', value: SfmaBreakoutRecommendation[]): void
}>()

const deepClone = (value: any) => JSON.parse(JSON.stringify(value || []))

const rows = ref<SfmaBreakoutRecommendation[]>([])
const displayRows = computed(() => {
  const excluded = new Set(props.excludeTestCodes || [])
  return rows.value.filter((item) => !excluded.has(item.test_code))
})
watch(
  () => props.modelValue,
  (value) => {
    rows.value = deepClone(value || [])
  },
  { immediate: true, deep: true }
)

const emitChange = () => {
  const payload = deepClone(rows.value || [])
  emit('update:modelValue', payload)
  emit('change', payload)
}

const handleStatusChange = (row: SfmaBreakoutRecommendation, status: SfmaBreakoutRecommendation['recommendation_status']) => {
  if (!row) return
  if (status === 'accepted') {
    const blocked = hasUnresolvedHigherPriority(row)
    if (blocked) {
      ElMessage.warning('请先处理更高优先级的 Breakout 建议（DN 优先，FP 次之，DP 最后）。')
      row.recommendation_status = 'suggested'
      emitChange()
      return
    }
  }
  row.recommendation_status = status
  if (status === 'accepted' && !row.recommendation_note) {
    row.recommendation_note = '已进入 Breakout 分解评估。'
  }
  if (status === 'skipped' && !row.recommendation_note) {
    row.recommendation_note = '本次暂不分解，后续可补录。'
  }
  emitChange()
}

const stageRank = (stage?: string) => {
  if (stage === 'dn_first') {
    return 1
  }
  if (stage === 'fp_second') {
    return 2
  }
  if (stage === 'dp_last') {
    return 3
  }
  return 9
}

const hasUnresolvedHigherPriority = (current: SfmaBreakoutRecommendation) => {
  const currentRank = stageRank(current.recommendation_stage)
  if (currentRank <= 1) {
    return false
  }
  return rows.value.some((row) => {
    const rank = stageRank(row.recommendation_stage)
    if (rank >= currentRank) {
      return false
    }
    return row.recommendation_status !== 'accepted' && row.recommendation_status !== 'skipped'
  })
}

const classificationTagType = (classification: string) => {
  if (classification === 'FP' || classification === 'DP') {
    return 'danger'
  }
  if (classification === 'DN') {
    return 'warning'
  }
  if (classification === 'FN') {
    return 'success'
  }
  return 'info'
}

const stageLabel = (stage?: string) => {
  if (stage === 'dn_first') {
    return 'DN优先'
  }
  if (stage === 'fp_second') {
    return 'FP次序'
  }
  if (stage === 'dp_last') {
    return 'DP最后'
  }
  return '待定'
}

const stageTagType = (stage?: string) => {
  if (stage === 'dn_first') {
    return 'success'
  }
  if (stage === 'fp_second') {
    return 'warning'
  }
  if (stage === 'dp_last') {
    return 'danger'
  }
  return 'info'
}

const validate = async () => true
const getFormData = () => deepClone(rows.value)

defineExpose({ validate, getFormData })
</script>
