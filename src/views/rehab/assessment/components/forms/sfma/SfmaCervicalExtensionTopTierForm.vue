<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">颈椎伸展 Top Tier（精简初筛）</span>
        <el-tag :type="model.classification ? 'success' : 'warning'" size="small">
          {{ model.classification ? '已评估' : '待评估' }}
        </el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-10px"
      title="Top Tier 仅保留分类、疼痛、简要备注与分解建议；详细观察请在 Breakout 中记录。"
    />

    <el-alert
      v-if="painConflict"
      :closable="false"
      type="warning"
      class="mb-10px"
      title="当前分类与疼痛标记存在冲突，建议复核后再保存。"
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
            <el-switch v-model="model.pain_present" @change="emitChange" />
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
              @change="emitChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="复核优先级">
            <el-tag
              :type="
                model.review_priority === 'high'
                  ? 'danger'
                  : model.review_priority === 'medium'
                    ? 'warning'
                    : 'info'
              "
            >
              {{ model.review_priority }}
            </el-tag>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="Top Tier 备注">
        <el-input
          v-model="model.top_tier_note"
          type="textarea"
          :rows="2"
          placeholder="一句话记录初筛观察"
          @input="emitChange"
        />
      </el-form-item>

      <el-row :gutter="12">
        <el-col :span="10">
          <el-form-item label="建议进入 Breakout">
            <el-switch v-model="model.needs_breakout_suggestion" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="14">
          <el-form-item label="Breakout 目标">
            <el-input v-model="model.breakout_target" disabled />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="建议原因">
        <el-input v-model="model.breakout_reason_text" type="textarea" :rows="2" disabled />
      </el-form-item>
    </el-form>

    <div v-if="model.needs_breakout_suggestion" class="flex items-center gap-8px mt-8px">
      <el-button type="primary" @click="emitAction('enter')">进入分解评估</el-button>
      <el-button @click="emitAction('skip')">暂不分解</el-button>
    </div>
  </el-card>
</template>

<script lang="ts" setup>
import { computed, reactive, watch } from 'vue'
import {
  applyCervicalExtensionTopTierRules,
  buildDefaultCervicalExtensionTopTier,
  SFMA_CLASSIFICATION_OPTIONS,
  SfmaCervicalExtensionTopTier
} from '@/views/rehab/assessment/config/sfmaConfig'

const props = defineProps<{
  modelValue?: SfmaCervicalExtensionTopTier
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: SfmaCervicalExtensionTopTier): void
  (e: 'change', value: SfmaCervicalExtensionTopTier): void
  (e: 'breakout-action', value: 'enter' | 'skip'): void
}>()

const deepClone = (value: any) => JSON.parse(JSON.stringify(value || {}))
const model = reactive<SfmaCervicalExtensionTopTier>(buildDefaultCervicalExtensionTopTier())
const syncFromProps = (value?: SfmaCervicalExtensionTopTier) => {
  Object.assign(model, applyCervicalExtensionTopTierRules(value || buildDefaultCervicalExtensionTopTier()))
}
const emitModel = () => {
  const payload = applyCervicalExtensionTopTierRules(deepClone(model))
  Object.assign(model, payload)
  emit('update:modelValue', payload)
  emit('change', payload)
}
watch(
  () => props.modelValue,
  (value) => syncFromProps(value),
  { immediate: true }
)

const painConflict = computed(() => {
  if (!model.classification) {
    return false
  }
  if ((model.classification === 'FP' || model.classification === 'DP') && !model.pain_present) {
    return true
  }
  if ((model.classification === 'FN' || model.classification === 'DN') && model.pain_present) {
    return true
  }
  return false
})

const emitChange = () => {
  emitModel()
}

const handleClassificationChange = () => {
  const next = deepClone(model)
  if (next.classification === 'FP' || next.classification === 'DP') {
    next.pain_present = true
  } else if (!next.pain_present) {
    next.pain_vas = null
  }
  Object.assign(model, applyCervicalExtensionTopTierRules(next))
  emitModel()
}

const emitAction = (action: 'enter' | 'skip') => {
  emit('breakout-action', action)
}

const validate = async () => !!model.classification
const getFormData = () => deepClone(model)
const reset = () => {
  Object.assign(model, buildDefaultCervicalExtensionTopTier())
  emitModel()
}

defineExpose({ validate, getFormData, reset })
</script>
