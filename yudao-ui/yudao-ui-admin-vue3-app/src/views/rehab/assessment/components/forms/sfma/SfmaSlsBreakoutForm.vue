<template>
  <SfmaBreakoutBaseForm
    v-model="model"
    :title="title"
    description="SLS 分解，记录足踝-髋-核心稳定链条，保留关键站立时间。"
    show-sls-time
  />
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import SfmaBreakoutBaseForm from './SfmaBreakoutBaseForm.vue'
import { SfmaBreakoutRecord } from '@/views/rehab/assessment/config/sfmaConfig'

const props = withDefaults(
  defineProps<{
    modelValue: SfmaBreakoutRecord
    side?: 'left' | 'right'
  }>(),
  {
    side: 'left'
  }
)
const emit = defineEmits(['update:modelValue', 'change'])

const model = computed({
  get: () => props.modelValue,
  set: (value: SfmaBreakoutRecord) => {
    emit('update:modelValue', value)
    emit('change', value)
  }
})

const title = computed(() => `SLS Breakout（${props.side === 'left' ? '左' : '右'}）`)
</script>
