<template>
  <SfmaBreakoutBaseForm
    v-model="model"
    title="颈椎模式 Breakout"
    description="分解颈椎屈曲/伸展/旋转，记录活动度与控制线索（预留上位颈/下位颈扩展位）。"
  />
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import SfmaBreakoutBaseForm from './SfmaBreakoutBaseForm.vue'
import { SfmaBreakoutRecord } from '@/views/rehab/assessment/config/sfmaConfig'

const props = defineProps<{ modelValue: SfmaBreakoutRecord }>()
const emit = defineEmits(['update:modelValue', 'change'])

const model = computed({
  get: () => props.modelValue,
  set: (value: SfmaBreakoutRecord) => {
    emit('update:modelValue', value)
    emit('change', value)
  }
})
</script>
