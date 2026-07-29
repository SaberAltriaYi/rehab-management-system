<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">{{ title }}</span>
        <el-tag size="small" :type="statusTagType(localData.status)">{{ statusLabel(localData.status) }}</el-tag>
      </div>
    </template>

    <el-alert :closable="false" type="info" class="mb-12px" :title="description" />

    <el-form :model="localData" label-width="132px">
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="分解状态">
            <el-select v-model="localData.status" class="!w-full">
              <el-option label="未开始" value="not_started" />
              <el-option label="进行中" value="in_progress" />
              <el-option label="已完成" value="completed" />
              <el-option label="暂不分解" value="skipped" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="疼痛出现">
            <el-switch v-model="localData.pain_present" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="疼痛VAS">
            <el-input-number v-model="localData.pain_vas" :min="0" :max="10" :step="0.5" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row v-if="showSlsTime" :gutter="12">
        <el-col :span="8">
          <el-form-item label="SLS时间(秒)">
            <el-input-number v-model="localData.sls_time_sec" :min="0" :step="1" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="关键观察">
        <el-input v-model="localData.findings" type="textarea" :rows="2" placeholder="记录分解评估关键发现" />
      </el-form-item>
      <el-form-item label="关键ROM/量化">
        <el-input
          v-model="localData.rom_key_values"
          type="textarea"
          :rows="2"
          placeholder="例如：ROM 45°、左右差 10°"
        />
      </el-form-item>
      <el-form-item label="活动度限制线索">
        <el-input
          v-model="localData.mobility_restriction_signs"
          type="textarea"
          :rows="2"
          placeholder="记录关节/组织活动度受限线索"
        />
      </el-form-item>
      <el-form-item label="控制障碍线索">
        <el-input
          v-model="localData.motor_control_signs"
          type="textarea"
          :rows="2"
          placeholder="记录稳定性或运动控制障碍线索"
        />
      </el-form-item>
      <el-form-item label="左右差线索">
        <el-input
          v-model="localData.asymmetry_signs"
          type="textarea"
          :rows="2"
          placeholder="记录左右差异或单侧代偿线索"
        />
      </el-form-item>

      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="评估方法">
            <el-input v-model="localData.method" placeholder="method 占位" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="量表/刻度">
            <el-input v-model="localData.scale" placeholder="scale 占位" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="来源ID">
            <el-input v-model="localData.source_id" placeholder="source_id 占位" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="记录日期">
            <el-date-picker v-model="localData.date" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="治疗师备注">
            <el-input v-model="localData.clinician_note" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">疼痛停止/谨慎推进</el-divider>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="疼痛中止">
            <el-switch v-model="localData.stop_due_to_pain" />
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="中止原因">
            <el-input v-model="localData.stop_reason" placeholder="如触发请记录原因，便于后续人工复核" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </el-card>
</template>

<script lang="ts" setup>
import { reactive, watch } from 'vue'
import { buildDefaultSfmaBreakoutRecord, SfmaBreakoutRecord } from '@/views/rehab/assessment/config/sfmaConfig'

const props = withDefaults(
  defineProps<{
    title: string
    description: string
    modelValue?: SfmaBreakoutRecord
    showSlsTime?: boolean
  }>(),
  {
    modelValue: () => buildDefaultSfmaBreakoutRecord(),
    showSlsTime: false
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: SfmaBreakoutRecord): void
  (e: 'change', value: SfmaBreakoutRecord): void
}>()

const localData = reactive<SfmaBreakoutRecord>(buildDefaultSfmaBreakoutRecord())

const deepClone = (value: any) => JSON.parse(JSON.stringify(value || {}))

const resetLocalData = (value?: SfmaBreakoutRecord) => {
  const next = deepClone(value || buildDefaultSfmaBreakoutRecord())
  Object.keys(localData).forEach((key) => delete (localData as any)[key])
  Object.assign(localData, next)
}

watch(
  () => props.modelValue,
  (value) => resetLocalData(value),
  { immediate: true, deep: true }
)

watch(
  localData,
  (value) => {
    const payload = deepClone(value)
    emit('update:modelValue', payload)
    emit('change', payload)
  },
  { deep: true }
)

const statusLabel = (status: SfmaBreakoutRecord['status']) => {
  switch (status) {
    case 'in_progress':
    case 'partial':
      return '部分完成'
    case 'completed':
      return '已完成'
    case 'skipped':
      return '暂不分解'
    case 'stopped_due_to_pain':
      return '已完成'
    default:
      return '未开始'
  }
}

const statusTagType = (status: SfmaBreakoutRecord['status']) => {
  switch (status) {
    case 'completed':
      return 'success'
    case 'in_progress':
    case 'partial':
      return 'warning'
    case 'stopped_due_to_pain':
      return 'success'
    case 'skipped':
      return 'info'
    default:
      return 'info'
  }
}

const validate = async () => true
const getFormData = () => deepClone(localData)
const reset = () => resetLocalData(buildDefaultSfmaBreakoutRecord())

defineExpose({ validate, getFormData, reset })
</script>
