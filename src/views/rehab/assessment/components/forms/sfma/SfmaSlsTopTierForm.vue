<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">单腿站立 Top Tier（左/右精简初筛）</span>
        <el-tag :type="allCompleted ? 'success' : 'warning'" size="small">
          {{ allCompleted ? '左右已评估' : '待评估' }}
        </el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-10px"
      title="Top Tier 仅保留分类、疼痛、简要备注与分解建议；详细观察请在 SLS Breakout 中记录。"
    />

    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>单腿站立（左）</template>
          <el-form :model="model.left" label-width="128px">
            <el-form-item label="分类(FN/FP/DN/DP)" required>
              <el-radio-group v-model="model.left.classification" @change="handleClassificationChange('left')">
                <el-radio v-for="option in SFMA_CLASSIFICATION_OPTIONS" :key="option.value" :label="option.value">
                  {{ option.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="疼痛标记">
              <el-switch v-model="model.left.pain_present" @change="emitModel" />
            </el-form-item>
            <el-form-item label="疼痛VAS">
              <el-input-number
                v-model="model.left.pain_vas"
                :min="0"
                :max="10"
                :step="0.5"
                :disabled="!model.left.pain_present"
                class="!w-full"
                @change="emitModel"
              />
            </el-form-item>
            <el-form-item label="Top Tier 备注">
              <el-input v-model="model.left.clinician_note" type="textarea" :rows="2" @input="emitModel" />
            </el-form-item>
            <el-form-item label="建议原因">
              <el-input v-model="model.left.breakout_reason_text" type="textarea" :rows="2" disabled />
            </el-form-item>
          </el-form>
          <div v-if="model.left.needs_breakout_suggestion" class="flex items-center gap-8px mt-8px">
            <el-button type="primary" @click="emit('breakout-action', { side: 'left', action: 'enter' })">左侧进入分解</el-button>
            <el-button @click="emit('breakout-action', { side: 'left', action: 'skip' })">左侧暂不分解</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>单腿站立（右）</template>
          <el-form :model="model.right" label-width="128px">
            <el-form-item label="分类(FN/FP/DN/DP)" required>
              <el-radio-group v-model="model.right.classification" @change="handleClassificationChange('right')">
                <el-radio v-for="option in SFMA_CLASSIFICATION_OPTIONS" :key="option.value" :label="option.value">
                  {{ option.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="疼痛标记">
              <el-switch v-model="model.right.pain_present" @change="emitModel" />
            </el-form-item>
            <el-form-item label="疼痛VAS">
              <el-input-number
                v-model="model.right.pain_vas"
                :min="0"
                :max="10"
                :step="0.5"
                :disabled="!model.right.pain_present"
                class="!w-full"
                @change="emitModel"
              />
            </el-form-item>
            <el-form-item label="Top Tier 备注">
              <el-input v-model="model.right.clinician_note" type="textarea" :rows="2" @input="emitModel" />
            </el-form-item>
            <el-form-item label="建议原因">
              <el-input v-model="model.right.breakout_reason_text" type="textarea" :rows="2" disabled />
            </el-form-item>
          </el-form>
          <div v-if="model.right.needs_breakout_suggestion" class="flex items-center gap-8px mt-8px">
            <el-button type="primary" @click="emit('breakout-action', { side: 'right', action: 'enter' })">右侧进入分解</el-button>
            <el-button @click="emit('breakout-action', { side: 'right', action: 'skip' })">右侧暂不分解</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </el-card>
</template>

<script lang="ts" setup>
import { computed, reactive, watch } from 'vue'
import {
  buildDefaultSfmaTopTierRecord,
  SFMA_CLASSIFICATION_OPTIONS,
  SFMA_TOP_TIER_DEFINITIONS,
  SfmaTopTierRecord
} from '@/views/rehab/assessment/config/sfmaConfig'

type SlsSide = 'left' | 'right'

const props = defineProps<{
  modelValue?: { left: SfmaTopTierRecord; right: SfmaTopTierRecord }
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: { left: SfmaTopTierRecord; right: SfmaTopTierRecord }): void
  (e: 'change', value: { left: SfmaTopTierRecord; right: SfmaTopTierRecord }): void
  (e: 'breakout-action', value: { side: SlsSide; action: 'enter' | 'skip' }): void
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
const buildDefaultByCode = (
  code: 'single_leg_stance_left' | 'single_leg_stance_right'
): SfmaTopTierRecord => {
  const definition = SFMA_TOP_TIER_DEFINITIONS.find((item) => item.test_code === code)
  return buildDefaultSfmaTopTierRecord(definition || SFMA_TOP_TIER_DEFINITIONS[0])
}
const applyRulesForCode = (
  rowInput: Partial<SfmaTopTierRecord> | undefined,
  code: 'single_leg_stance_left' | 'single_leg_stance_right'
): SfmaTopTierRecord => {
  const actionZh = code === 'single_leg_stance_left' ? '单腿站立（左）' : '单腿站立（右）'
  const row: SfmaTopTierRecord = {
    ...buildDefaultByCode(code),
    ...(rowInput || {}),
    test_code: code,
    test_name_zh: actionZh
  }
  if (row.classification === 'FP') {
    return {
      ...row,
      pain_present: normalizePainPresent((rowInput as any)?.pain_present, true),
      needs_breakout_suggestion: true,
      breakout_reason_text: `${actionZh}为疼痛性功能模式，建议谨慎进入 SLS 分解评估。`,
      review_priority: 'high',
      caution_text: '优先疼痛管理/谨慎继续分解'
    }
  }
  if (row.classification === 'DN') {
    return {
      ...row,
      pain_present: normalizePainPresent((rowInput as any)?.pain_present, false),
      needs_breakout_suggestion: true,
      breakout_reason_text: `${actionZh}存在功能异常，建议进入 SLS 分解评估。`,
      review_priority: row.review_priority === 'high' ? 'high' : 'normal'
    }
  }
  if (row.classification === 'DP') {
    return {
      ...row,
      pain_present: normalizePainPresent((rowInput as any)?.pain_present, true),
      needs_breakout_suggestion: true,
      breakout_reason_text: `${actionZh}存在功能异常并伴疼痛，建议优先人工复核并谨慎进入 SLS 分解评估。`,
      review_priority: 'high',
      caution_text: '优先疼痛管理/谨慎继续分解'
    }
  }
  if (row.classification === 'FN') {
    return {
      ...row,
      pain_present: normalizePainPresent((rowInput as any)?.pain_present, false),
      needs_breakout_suggestion: false,
      breakout_reason_text: '',
      review_priority: 'low',
      caution_text: row.caution_text === '优先疼痛管理/谨慎继续分解' ? '' : row.caution_text
    }
  }
  return row
}

const applyRules = (
  input?: { left?: Partial<SfmaTopTierRecord>; right?: Partial<SfmaTopTierRecord> }
): { left: SfmaTopTierRecord; right: SfmaTopTierRecord } => ({
  left: applyRulesForCode(input?.left, 'single_leg_stance_left'),
  right: applyRulesForCode(input?.right, 'single_leg_stance_right')
})

const model = reactive<{ left: SfmaTopTierRecord; right: SfmaTopTierRecord }>(applyRules())
const syncFromProps = (value?: { left: SfmaTopTierRecord; right: SfmaTopTierRecord }) => {
  const payload = applyRules(value)
  model.left = payload.left
  model.right = payload.right
}
const emitModel = () => {
  const payload = applyRules(deepClone(model))
  model.left = payload.left
  model.right = payload.right
  emit('update:modelValue', payload)
  emit('change', payload)
}

watch(
  () => props.modelValue,
  (value) => syncFromProps(value),
  { immediate: true }
)

const allCompleted = computed(() => !!model.left.classification && !!model.right.classification)
const handleClassificationChange = (side: SlsSide) => {
  if (model[side].classification === 'FP' || model[side].classification === 'DP') {
    model[side].pain_present = true
  } else if (!model[side].pain_present) {
    model[side].pain_vas = null
  }
  emitModel()
}

const validate = async () => !!model.left.classification && !!model.right.classification
const getFormData = () => deepClone(model)
const reset = () => {
  const payload = applyRules()
  model.left = payload.left
  model.right = payload.right
  emitModel()
}

defineExpose({ validate, getFormData, reset })
</script>
