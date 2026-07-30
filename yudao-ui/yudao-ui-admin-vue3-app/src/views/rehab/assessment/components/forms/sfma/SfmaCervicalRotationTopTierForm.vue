<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">颈椎旋转 Top Tier（左/右精简初筛）</span>
        <el-tag :type="allCompleted ? 'success' : 'warning'" size="small">
          {{ allCompleted ? '左右已评估' : '待评估' }}
        </el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-10px"
      title="Top Tier 仅保留分类、疼痛、简要备注与分解建议；详细观察请在旋转 Breakout 中记录。"
    />

    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>左侧旋转（L）</template>
          <el-alert
            v-if="painConflictLeft"
            :closable="false"
            type="warning"
            class="mb-10px"
            title="左侧分类与疼痛标记存在冲突，建议复核。"
          />
          <el-form :model="model.left" label-width="128px">
            <el-form-item label="分类(FN/FP/DN/DP)" required>
              <el-radio-group v-model="model.left.classification" @change="handleClassificationChange('left')">
                <el-radio v-for="option in SFMA_CLASSIFICATION_OPTIONS" :key="option.value" :label="option.value">
                  {{ option.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="疼痛标记">
              <el-switch v-model="model.left.pain_present" @change="emitChange" />
            </el-form-item>
            <el-form-item label="疼痛VAS">
              <el-input-number
                v-model="model.left.pain_vas"
                :min="0"
                :max="10"
                :step="0.5"
                :disabled="!model.left.pain_present"
                class="!w-full"
                @change="emitChange"
              />
            </el-form-item>
            <el-form-item label="Top Tier 备注">
              <el-input
                v-model="model.left.top_tier_note"
                type="textarea"
                :rows="2"
                placeholder="一句话记录左侧初筛观察"
                @input="emitChange"
              />
            </el-form-item>
            <el-form-item label="建议进入 Breakout">
              <el-switch v-model="model.left.needs_breakout_suggestion" disabled />
            </el-form-item>
            <el-form-item label="建议原因">
              <el-input v-model="model.left.breakout_reason_text" type="textarea" :rows="2" disabled />
            </el-form-item>
          </el-form>
          <div v-if="model.left.needs_breakout_suggestion" class="flex items-center gap-8px mt-8px">
            <el-button type="primary" @click="emitAction('left', 'enter')">左侧进入分解</el-button>
            <el-button @click="emitAction('left', 'skip')">左侧暂不分解</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header>右侧旋转（R）</template>
          <el-alert
            v-if="painConflictRight"
            :closable="false"
            type="warning"
            class="mb-10px"
            title="右侧分类与疼痛标记存在冲突，建议复核。"
          />
          <el-form :model="model.right" label-width="128px">
            <el-form-item label="分类(FN/FP/DN/DP)" required>
              <el-radio-group v-model="model.right.classification" @change="handleClassificationChange('right')">
                <el-radio v-for="option in SFMA_CLASSIFICATION_OPTIONS" :key="option.value" :label="option.value">
                  {{ option.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="疼痛标记">
              <el-switch v-model="model.right.pain_present" @change="emitChange" />
            </el-form-item>
            <el-form-item label="疼痛VAS">
              <el-input-number
                v-model="model.right.pain_vas"
                :min="0"
                :max="10"
                :step="0.5"
                :disabled="!model.right.pain_present"
                class="!w-full"
                @change="emitChange"
              />
            </el-form-item>
            <el-form-item label="Top Tier 备注">
              <el-input
                v-model="model.right.top_tier_note"
                type="textarea"
                :rows="2"
                placeholder="一句话记录右侧初筛观察"
                @input="emitChange"
              />
            </el-form-item>
            <el-form-item label="建议进入 Breakout">
              <el-switch v-model="model.right.needs_breakout_suggestion" disabled />
            </el-form-item>
            <el-form-item label="建议原因">
              <el-input v-model="model.right.breakout_reason_text" type="textarea" :rows="2" disabled />
            </el-form-item>
          </el-form>
          <div v-if="model.right.needs_breakout_suggestion" class="flex items-center gap-8px mt-8px">
            <el-button type="primary" @click="emitAction('right', 'enter')">右侧进入分解</el-button>
            <el-button @click="emitAction('right', 'skip')">右侧暂不分解</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </el-card>
</template>

<script lang="ts" setup>
import { computed, reactive, watch } from 'vue'
import {
  applyCervicalRotationTopTierRules,
  buildDefaultCervicalRotationTopTier,
  SFMA_CLASSIFICATION_OPTIONS,
  SfmaCervicalRotationTopTier
} from '@/views/rehab/assessment/config/sfmaConfig'

type RotationSide = 'left' | 'right'

const props = defineProps<{
  modelValue?: SfmaCervicalRotationTopTier
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: SfmaCervicalRotationTopTier): void
  (e: 'change', value: SfmaCervicalRotationTopTier): void
  (e: 'breakout-action', value: { side: RotationSide; action: 'enter' | 'skip' }): void
}>()

const deepClone = (value: any) => JSON.parse(JSON.stringify(value || {}))
const model = reactive<SfmaCervicalRotationTopTier>(buildDefaultCervicalRotationTopTier())
const syncFromProps = (value?: SfmaCervicalRotationTopTier) => {
  const payload = applyCervicalRotationTopTierRules(value || buildDefaultCervicalRotationTopTier())
  model.left = payload.left
  model.right = payload.right
}
const emitModel = () => {
  const payload = applyCervicalRotationTopTierRules(deepClone(model))
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

const sidePainConflict = (side: RotationSide) => {
  const row = model[side]
  if (!row.classification) {
    return false
  }
  if ((row.classification === 'FP' || row.classification === 'DP') && !row.pain_present) {
    return true
  }
  if ((row.classification === 'FN' || row.classification === 'DN') && row.pain_present) {
    return true
  }
  return false
}

const painConflictLeft = computed(() => sidePainConflict('left'))
const painConflictRight = computed(() => sidePainConflict('right'))
const allCompleted = computed(() => !!model.left.classification && !!model.right.classification)

const emitChange = () => {
  emitModel()
}

const handleClassificationChange = (side: RotationSide) => {
  const next = deepClone(model)
  if (next[side].classification === 'FP' || next[side].classification === 'DP') {
    next[side].pain_present = true
  } else if (!next[side].pain_present) {
    next[side].pain_vas = null
  }
  const payload = applyCervicalRotationTopTierRules(next)
  model.left = payload.left
  model.right = payload.right
  emitModel()
}

const emitAction = (side: RotationSide, action: 'enter' | 'skip') => {
  emit('breakout-action', { side, action })
}

const validate = async () => !!model.left.classification && !!model.right.classification
const getFormData = () => deepClone(model)
const reset = () => {
  const payload = buildDefaultCervicalRotationTopTier()
  model.left = payload.left
  model.right = payload.right
  emitModel()
}

defineExpose({ validate, getFormData, reset })
</script>
