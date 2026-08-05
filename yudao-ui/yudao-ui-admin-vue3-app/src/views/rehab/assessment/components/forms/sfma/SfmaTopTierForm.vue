<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">SFMA Top Tier 初筛</span>
        <el-tag :type="isComplete ? 'success' : 'warning'" size="small">
          {{ isComplete ? '已完成' : '待完成' }}
        </el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-12px"
      title="先完成 Top Tier（FN/FP/DN/DP），系统再统一给出 Breakout 建议。"
      description="FP/DP 为疼痛性模式，将自动标记高优先级并提示谨慎继续分解。"
    />

    <section v-for="group in groupedDefinitions" :key="group.groupKey" class="top-tier-group">
      <div class="top-tier-group__title">{{ group.label }}</div>
      <el-table :data="group.items" border size="small">
        <el-table-column label="测试项" min-width="180">
          <template #default="scope">
            <div>{{ scope.row.test_name_zh }}</div>
            <div class="text-12px text-[var(--el-text-color-secondary)]"
              >code: {{ scope.row.test_code }}</div
            >
          </template>
        </el-table-column>

        <el-table-column label="分类(FN/FP/DN/DP)" min-width="240">
          <template #default="scope">
            <el-radio-group
              :model-value="getRow(scope.row.test_code).classification"
              @change="(value) => handleClassificationChange(scope.row.test_code, value as any)"
            >
              <el-radio
                v-for="option in SFMA_CLASSIFICATION_OPTIONS"
                :key="option.value"
                :label="option.value"
              >
                {{ option.label }}
              </el-radio>
            </el-radio-group>
          </template>
        </el-table-column>

        <el-table-column label="疼痛" width="95">
          <template #default="scope">
            <el-switch
              :model-value="getRow(scope.row.test_code).pain_present"
              @change="(value) => handleRowFieldChange(scope.row.test_code, 'pain_present', value)"
            />
          </template>
        </el-table-column>

        <el-table-column label="疼痛VAS" width="120">
          <template #default="scope">
            <el-input-number
              :model-value="getRow(scope.row.test_code).pain_vas"
              :min="0"
              :max="10"
              :step="0.5"
              class="!w-full"
              @change="(value) => handleRowFieldChange(scope.row.test_code, 'pain_vas', value)"
            />
          </template>
        </el-table-column>

        <el-table-column label="关键ROM" width="140">
          <template #default="scope">
            <el-input
              :model-value="getRow(scope.row.test_code).rom_key_value"
              placeholder="可空"
              @input="(value) => handleRowFieldChange(scope.row.test_code, 'rom_key_value', value)"
            />
          </template>
        </el-table-column>

        <el-table-column label="观察要点" min-width="220">
          <template #default="scope">
            <el-input
              :model-value="getRow(scope.row.test_code).key_observation_note"
              type="textarea"
              :rows="2"
              placeholder="关键观察"
              @input="
                (value) => handleRowFieldChange(scope.row.test_code, 'key_observation_note', value)
              "
            />
          </template>
        </el-table-column>

        <el-table-column label="质量备注" min-width="220">
          <template #default="scope">
            <el-input
              :model-value="getRow(scope.row.test_code).movement_quality_note"
              type="textarea"
              :rows="2"
              placeholder="动作质量备注"
              @input="
                (value) => handleRowFieldChange(scope.row.test_code, 'movement_quality_note', value)
              "
            />
          </template>
        </el-table-column>

        <el-table-column label="优先级" width="96">
          <template #default="scope">
            <el-tag
              :type="
                getRow(scope.row.test_code).review_priority === 'high'
                  ? 'danger'
                  : getRow(scope.row.test_code).review_priority === 'normal'
                    ? 'warning'
                    : 'info'
              "
            >
              {{ getRow(scope.row.test_code).review_priority || 'normal' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </el-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import {
  SFMA_CLASSIFICATION_OPTIONS,
  SFMA_GROUP_LABELS,
  SFMA_TOP_TIER_DEFINITIONS,
  SfmaTopTierRecord
} from '@/views/rehab/assessment/config/sfmaConfig'

const props = defineProps<{
  modelValue: Record<string, SfmaTopTierRecord>
  excludeTestCodes?: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, SfmaTopTierRecord>): void
  (e: 'change', value: Record<string, SfmaTopTierRecord>): void
}>()

const deepClone = (value: any) => JSON.parse(JSON.stringify(value || {}))

const model = computed<Record<string, SfmaTopTierRecord>>({
  get: () => props.modelValue || {},
  set: (value) => {
    const payload = deepClone(value || {})
    emit('update:modelValue', payload)
    emit('change', payload)
  }
})

const groupedDefinitions = computed(() => {
  const excluded = new Set(props.excludeTestCodes || [])
  const grouped = new Map<string, any[]>()
  SFMA_TOP_TIER_DEFINITIONS.forEach((item) => {
    if (excluded.has(item.test_code)) {
      return
    }
    if (!grouped.has(item.group)) {
      grouped.set(item.group, [])
    }
    grouped.get(item.group)!.push(item)
  })
  return Array.from(grouped.entries()).map(([groupKey, items]) => ({
    groupKey,
    label: SFMA_GROUP_LABELS[groupKey as keyof typeof SFMA_GROUP_LABELS] || groupKey,
    items
  }))
})

const isComplete = computed(() => {
  const excluded = new Set(props.excludeTestCodes || [])
  return SFMA_TOP_TIER_DEFINITIONS.filter((item) => !excluded.has(item.test_code)).every((item) => {
    const row = model.value[item.test_code]
    return !!row?.classification
  })
})

const patchTopTierRow = (
  testCode: string,
  patch: Partial<SfmaTopTierRecord>,
  options: { emitWhenMissing?: boolean } = {}
) => {
  const payload = deepClone(model.value)
  const nextRow = {
    ...(payload[testCode] || {}),
    ...(patch || {})
  }
  if (!nextRow.test_code && !options.emitWhenMissing) {
    return
  }
  payload[testCode] = nextRow
  model.value = payload
}

const handleRowFieldChange = (testCode: string, field: keyof SfmaTopTierRecord, value: any) => {
  patchTopTierRow(testCode, { [field]: value } as Partial<SfmaTopTierRecord>, {
    emitWhenMissing: true
  })
}

const handleClassificationChange = (
  testCode: string,
  classification: SfmaTopTierRecord['classification']
) => {
  const payload = deepClone(model.value)
  const row = payload[testCode] || ({} as SfmaTopTierRecord)
  row.classification = classification || ''
  if (!row) {
    return
  }
  if (row.classification === 'FP' || row.classification === 'DP') {
    row.pain_present = true
    row.review_priority = 'high'
    row.needs_breakout_suggestion = true
    row.breakout_reason_text = '疼痛性功能模式（FP/DP），建议优先疼痛管理并谨慎继续 Breakout。'
    row.caution_text = '优先疼痛管理/谨慎继续分解'
  } else if (row.classification === 'DN') {
    row.review_priority = row.review_priority === 'high' ? 'high' : 'normal'
    row.needs_breakout_suggestion = true
    row.breakout_reason_text = '存在非疼痛性功能障碍（DN），建议进入 Breakout 分解。'
    if (!row.caution_text || row.caution_text === '优先疼痛管理/谨慎继续分解') {
      row.caution_text = ''
    }
  } else if (row.classification === 'FN') {
    row.needs_breakout_suggestion = false
    row.breakout_reason_text = ''
    row.review_priority = 'low'
    if (!row.pain_present) {
      row.pain_vas = null
    }
    if (row.caution_text === '优先疼痛管理/谨慎继续分解') {
      row.caution_text = ''
    }
  } else {
    row.review_priority = 'normal'
    row.needs_breakout_suggestion = false
    row.breakout_reason_text = ''
  }
  model.value = payload
}

const getRow = (testCode: string) => model.value[testCode] || ({} as SfmaTopTierRecord)

const validate = async () => true
const getFormData = () => deepClone(model.value)
const reset = () => {
  // reset 由上层统一处理
}

defineExpose({ validate, getFormData, reset })
</script>

<style scoped>
.top-tier-group + .top-tier-group {
  margin-top: 16px;
}

.top-tier-group__title {
  margin-bottom: 12px;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
}
</style>
