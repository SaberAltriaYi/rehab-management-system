<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">多节段伸展 Top Tier（精简初筛）</span>
        <el-tag :type="model.classification ? 'success' : 'warning'" size="small">
          {{ model.classification ? '已评估' : '待评估' }}
        </el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-10px"
      title="Top Tier 仅保留分类、疼痛、简要备注与分解建议；详细观察请在 MSE Breakout 中记录。"
    />

    <el-form :model="model" label-width="136px">
      <el-form-item label="分类(FN/FP/DN/DP)" required>
        <el-radio-group v-model="model.classification" @change="handleClassificationChange">
          <el-radio v-for="option in SFMA_CLASSIFICATION_OPTIONS" :key="option.value" :label="option.value">
            {{ option.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="疼痛标记">
            <el-switch v-model="model.pain_present" @change="emitModel" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="疼痛VAS">
            <el-input-number
              v-model="model.pain_vas"
              :min="0"
              :max="10"
              :step="0.5"
              :disabled="!model.pain_present"
              class="!w-full"
              @change="emitModel"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="复核优先级">
            <el-tag :type="model.review_priority === 'high' ? 'danger' : model.review_priority === 'normal' ? 'warning' : 'info'">
              {{ model.review_priority || 'normal' }}
            </el-tag>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Top Tier 备注">
        <el-input v-model="model.clinician_note" type="textarea" :rows="2" placeholder="一句话记录初筛观察" @input="emitModel" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="10">
          <el-form-item label="建议进入 Breakout">
            <el-switch v-model="model.needs_breakout_suggestion" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="14">
          <el-form-item label="Breakout 目标">
            <el-input :model-value="model.needs_breakout_suggestion ? 'mse_breakout' : ''" disabled />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="建议原因">
        <el-input v-model="model.breakout_reason_text" type="textarea" :rows="2" disabled />
      </el-form-item>
    </el-form>

    <div v-if="model.needs_breakout_suggestion" class="flex items-center gap-8px mt-8px">
      <el-button type="primary" @click="emit('breakout-action', 'enter')">进入分解评估</el-button>
      <el-button @click="emit('breakout-action', 'skip')">暂不分解</el-button>
    </div>
  </el-card>
</template>

<script lang="ts" setup>
import { reactive, watch } from 'vue'
import {
  buildDefaultSfmaTopTierRecord,
  SFMA_CLASSIFICATION_OPTIONS,
  SFMA_TOP_TIER_DEFINITIONS,
  SfmaTopTierRecord
} from '@/views/rehab/assessment/config/sfmaConfig'

const TEST_CODE = 'multi_segmental_extension'
const TEST_NAME = '多节段伸展（MSE）'

const props = defineProps<{
  modelValue?: SfmaTopTierRecord
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: SfmaTopTierRecord): void
  (e: 'change', value: SfmaTopTierRecord): void
  (e: 'breakout-action', value: 'enter' | 'skip'): void
}>()

const deepClone = (value: any) => JSON.parse(JSON.stringify(value || {}))
const normalizePainPresent = (value: unknown, fallback = false) => {
  if (value === undefined || value === null || value === '') return fallback
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value !== 0
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    if (['true', '1', 'yes', 'y', 'on'].includes(normalized)) return true
    if (['false', '0', 'no', 'n', 'off', ''].includes(normalized)) return false
  }
  return Boolean(value)
}
const buildDefault = () => {
  const definition = SFMA_TOP_TIER_DEFINITIONS.find((item) => item.test_code === TEST_CODE)
  return buildDefaultSfmaTopTierRecord(definition || SFMA_TOP_TIER_DEFINITIONS[0])
}
const applyRules = (input?: Partial<SfmaTopTierRecord>): SfmaTopTierRecord => {
  const row = { ...buildDefault(), ...(input || {}), test_code: TEST_CODE, test_name_zh: TEST_NAME }
  if (row.classification === 'FP') {
    return {
      ...row,
      pain_present: normalizePainPresent((input as any)?.pain_present, true),
      needs_breakout_suggestion: true,
      breakout_reason_text: '多节段伸展为疼痛性功能模式，建议谨慎进入 MSE 分解评估。',
      review_priority: 'high',
      caution_text: '优先疼痛管理/谨慎继续分解'
    }
  }
  if (row.classification === 'DN') {
    return {
      ...row,
      pain_present: normalizePainPresent((input as any)?.pain_present, false),
      needs_breakout_suggestion: true,
      breakout_reason_text: '多节段伸展存在功能异常，建议进入 MSE 分解评估。',
      review_priority: row.review_priority === 'high' ? 'high' : 'normal'
    }
  }
  if (row.classification === 'DP') {
    return {
      ...row,
      pain_present: normalizePainPresent((input as any)?.pain_present, true),
      needs_breakout_suggestion: true,
      breakout_reason_text: '多节段伸展存在功能异常并伴疼痛，建议优先人工复核并谨慎进入 MSE 分解评估。',
      review_priority: 'high',
      caution_text: '优先疼痛管理/谨慎继续分解'
    }
  }
  if (row.classification === 'FN') {
    return {
      ...row,
      pain_present: normalizePainPresent((input as any)?.pain_present, false),
      needs_breakout_suggestion: false,
      breakout_reason_text: '',
      review_priority: 'low',
      caution_text: row.caution_text === '优先疼痛管理/谨慎继续分解' ? '' : row.caution_text
    }
  }
  return row
}

const model = reactive<SfmaTopTierRecord>(buildDefault())
const syncFromProps = (value?: SfmaTopTierRecord) => {
  Object.assign(model, applyRules(value || buildDefault()))
}
const emitModel = () => {
  const payload = applyRules(deepClone(model))
  Object.assign(model, payload)
  emit('update:modelValue', payload)
  emit('change', payload)
}

watch(
  () => props.modelValue,
  (value) => syncFromProps(value),
  { immediate: true }
)

const handleClassificationChange = () => {
  if (model.classification === 'FP' || model.classification === 'DP') {
    model.pain_present = true
  } else if (!model.pain_present) {
    model.pain_vas = null
  }
  emitModel()
}

const validate = async () => !!model.classification
const getFormData = () => deepClone(model)
const reset = () => {
  Object.assign(model, buildDefault())
  emitModel()
}

defineExpose({ validate, getFormData, reset })
</script>
