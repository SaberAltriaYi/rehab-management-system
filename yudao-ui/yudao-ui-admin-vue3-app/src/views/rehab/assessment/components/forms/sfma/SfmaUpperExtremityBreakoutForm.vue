<template>
  <SfmaBreakoutBaseForm
    v-model="model"
    :title="title"
    description="分解 UE1/UE2 模式，记录肩胛-胸椎-肩关节线索与左右差。"
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

const title = computed(() => `上肢模式 Breakout（${props.side === 'left' ? '左' : '右'}）`)
</script>
