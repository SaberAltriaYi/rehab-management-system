<template>
  <SfmaBreakoutBaseForm
    v-model="model"
    title="Deep Squat Breakout"
    description="垂臂下蹲分解，记录下肢排列、LPHC 控制与深蹲模式线索。"
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
