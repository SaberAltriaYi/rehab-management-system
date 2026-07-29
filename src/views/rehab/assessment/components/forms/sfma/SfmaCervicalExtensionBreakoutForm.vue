<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">颈椎伸展 Breakout（高级评估）</span>
        <el-tag :type="statusTagType(model.breakout_status)" size="small">
          {{ statusLabel(model.breakout_status) }}
        </el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-10px"
      title="按教材流程记录：仰卧位主动伸展 → 仰卧位被动伸展 → 仰卧位寰枕关节主动伸展（20°）。"
    />

    <el-form :model="model" label-width="168px">
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="Breakout 状态">
            <el-select v-model="model.breakout_status" class="!w-full" @change="emitChange">
              <el-option label="未开始" value="not_started" />
              <el-option label="进行中" value="in_progress" />
              <el-option label="已完成" value="completed" />
              <el-option label="暂不分解" value="skipped" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="Breakout 备注">
            <el-input v-model="model.breakout_note" @input="emitChange" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">步骤1：仰卧位主动伸展</el-divider>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="主动伸展质量">
            <el-radio-group v-model="model.active_cervical_extension_quality" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="受限">受限</el-radio>
              <el-radio label="明显受限">明显受限</el-radio>
              <el-radio label="无法完成">无法完成</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="5">
          <el-form-item label="主动疼痛">
            <el-switch v-model="model.active_cervical_extension_pain" @change="handlePainAutoReview" />
          </el-form-item>
        </el-col>
        <el-col :span="5">
          <el-form-item label="主动ROM(°)">
            <el-input-number
              v-model="model.active_cervical_extension_rom_key"
              :min="0"
              :max="180"
              class="!w-full"
              @change="emitChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="终末感备注">
            <el-input v-model="model.active_cervical_extension_end_feel_note" @input="emitChange" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">步骤2：仰卧位被动伸展</el-divider>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="被动伸展质量">
            <el-radio-group v-model="model.passive_cervical_extension_quality" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="受限">受限</el-radio>
              <el-radio label="明显受限">明显受限</el-radio>
              <el-radio label="未测">未测</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="5">
          <el-form-item label="被动疼痛">
            <el-switch v-model="model.passive_cervical_extension_pain" @change="handlePainAutoReview" />
          </el-form-item>
        </el-col>
        <el-col :span="5">
          <el-form-item label="被动ROM(°)">
            <el-input-number
              v-model="model.passive_cervical_extension_rom_key"
              :min="0"
              :max="180"
              class="!w-full"
              @change="emitChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="主动/被动差异">
            <el-select v-model="model.passive_vs_active_difference" class="!w-full" @change="emitChange">
              <el-option label="被动优于主动" value="被动优于主动" />
              <el-option label="主动与被动接近" value="主动与被动接近" />
              <el-option label="被动也受限" value="被动也受限" />
              <el-option label="未判断" value="未判断" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">步骤3：仰卧位寰枕关节主动伸展（20°）</el-divider>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="上位颈观察">
            <el-radio-group v-model="model.upper_cervical_extension_observation" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="疑似受限">疑似受限</el-radio>
              <el-radio label="疑似代偿">疑似代偿</el-radio>
              <el-radio label="未测">未测</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="上位颈备注">
            <el-input v-model="model.upper_cervical_note" @input="emitChange" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">代偿与相关区域观察</el-divider>
      <el-form-item label="代偿模式">
        <el-checkbox-group v-model="model.compensation_patterns" @change="emitChange">
          <el-checkbox label="胸椎代偿伸展" />
          <el-checkbox label="肩胛上提" />
          <el-checkbox label="肩部后移受限" />
          <el-checkbox label="躯干后仰代偿" />
          <el-checkbox label="下巴前引" />
          <el-checkbox label="其他" />
        </el-checkbox-group>
      </el-form-item>
      <el-form-item v-if="model.compensation_patterns.includes('其他')" label="其他代偿备注">
        <el-input v-model="model.compensation_other_note" @input="emitChange" />
      </el-form-item>
      <el-form-item label="相关区域影响">
        <el-checkbox-group v-model="model.related_region_influence" @change="emitChange">
          <el-checkbox label="胸椎伸展受限影响" />
          <el-checkbox label="肩带紧张影响" />
          <el-checkbox label="软组织长度问题疑似参与" />
          <el-checkbox label="疼痛抑制影响" />
          <el-checkbox label="控制障碍疑似参与" />
          <el-checkbox label="暂不明确" />
        </el-checkbox-group>
      </el-form-item>

      <el-divider content-position="left">初步分解结论</el-divider>
      <el-form-item label="分解方向">
        <el-checkbox-group v-model="model.breakout_preliminary_direction" @change="emitChange">
          <el-checkbox label="更偏活动度限制" />
          <el-checkbox label="更偏疼痛主导" />
          <el-checkbox label="更偏运动控制问题" />
          <el-checkbox label="需进一步颈椎解析" />
          <el-checkbox label="需结合其他模式综合判断" />
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="分解总结">
        <el-input v-model="model.breakout_summary_text" type="textarea" :rows="3" @input="emitChange" />
      </el-form-item>
      <el-form-item label="需人工复核">
        <el-switch v-model="model.needs_manual_review" @change="emitChange" />
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import {
  buildDefaultCervicalExtensionBreakout,
  SfmaCervicalExtensionBreakout
} from '@/views/rehab/assessment/config/sfmaConfig'

const props = defineProps<{
  modelValue?: SfmaCervicalExtensionBreakout
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: SfmaCervicalExtensionBreakout): void
  (e: 'change', value: SfmaCervicalExtensionBreakout): void
}>()

const deepClone = (value: any) => JSON.parse(JSON.stringify(value || {}))

const model = computed<SfmaCervicalExtensionBreakout>({
  get: () => ({
    ...buildDefaultCervicalExtensionBreakout(),
    ...(props.modelValue || {})
  }),
  set: (value) => {
    const payload = {
      ...buildDefaultCervicalExtensionBreakout(),
      ...deepClone(value || {})
    }
    emit('update:modelValue', payload)
    emit('change', payload)
  }
})

const emitChange = () => {
  model.value = deepClone(model.value)
}

const handlePainAutoReview = () => {
  const next = deepClone(model.value)
  if (
    next.active_cervical_extension_pain ||
    next.passive_cervical_extension_pain
  ) {
    next.needs_manual_review = true
  }
  model.value = next
}

const statusLabel = (status: SfmaCervicalExtensionBreakout['breakout_status']) => {
  if (status === 'in_progress' || status === 'partial') {
    return '进行中'
  }
  if (status === 'completed') {
    return '已完成'
  }
  if (status === 'skipped') {
    return '暂不分解'
  }
  if (status === 'stopped_due_to_pain') {
    return '已完成'
  }
  return '未开始'
}

const statusTagType = (status: SfmaCervicalExtensionBreakout['breakout_status']) => {
  if (status === 'completed') {
    return 'success'
  }
  if (status === 'in_progress' || status === 'partial') {
    return 'warning'
  }
  if (status === 'stopped_due_to_pain') {
    return 'success'
  }
  return 'info'
}

const validate = async () => true
const getFormData = () => deepClone(model.value)
const reset = () => {
  model.value = buildDefaultCervicalExtensionBreakout()
}

defineExpose({ validate, getFormData, reset })
</script>
