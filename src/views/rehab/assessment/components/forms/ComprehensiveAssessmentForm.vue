<template>
  <el-card shadow="never" class="assessment-form-shell">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">综合评估</span>
        <el-tag size="small" type="success">{{ selectedDefinitions.length }} 个模块</el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-12px"
      title="按本次需要组合评估模块；各模块数据将随综合评估一起保存并用于报告汇总。"
    />

    <el-form :model="localData" label-width="120px">
      <el-form-item label="评估模块">
        <el-checkbox-group v-model="localData.selectedModules">
          <el-checkbox
            v-for="item in COMPREHENSIVE_MODULE_OPTIONS"
            :key="item.value"
            :label="item.value"
          >
            {{ item.label }}
          </el-checkbox>
        </el-checkbox-group>
      </el-form-item>
    </el-form>

    <el-empty v-if="!selectedDefinitions.length" description="请至少选择一个评估模块" />
    <el-tabs v-else v-model="activeModule" type="border-card" class="module-tabs">
      <el-tab-pane
        v-for="item in selectedDefinitions"
        :key="item.typeCode"
        :label="item.label"
        :name="item.typeCode"
        lazy
      >
        <component
          :is="item.component"
          :ref="(instance) => setModuleRef(item.typeCode, instance)"
          :model-value="localData.modules[item.typeCode]"
          :assessment-base-info="assessmentBaseInfo"
          @update:model-value="(value) => updateModuleData(item.typeCode, value)"
        />
      </el-tab-pane>
    </el-tabs>

    <el-form :model="localData" label-width="120px" class="mt-16px">
      <el-divider content-position="left">综合结论</el-divider>
      <el-form-item label="核心问题">
        <el-input
          v-model="localData.summary.chiefProblem"
          placeholder="本次最需要解决的问题"
        />
      </el-form-item>
      <el-form-item label="综合结论">
        <el-input v-model="localData.summary.conclusion" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="干预优先级">
        <el-input
          v-model="localData.summary.priority"
          type="textarea"
          :rows="2"
          placeholder="按先后顺序记录主要目标"
        />
      </el-form-item>
      <el-form-item label="总体建议">
        <el-input v-model="localData.summary.recommendation" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script lang="ts" setup>
import type { Component } from 'vue'
import type { AssessmentFormExpose } from '../../config/assessmentFormRegistry'
import StaticAssessmentForm from './StaticAssessmentForm.vue'
import BodyCompositionForm from './BodyCompositionForm.vue'
import NasmCesForm from './NasmCesForm.vue'
import SfmaForm from './SfmaForm.vue'
import FmsForm from './FmsForm.vue'
import YbtForm from './YbtForm.vue'
import OpenCapForm from './OpenCapForm.vue'
import ObservationOnlyForm from './ObservationOnlyForm.vue'
import { buildDefaultStaticAssessmentFormData } from '../../config/staticAssessmentConfig'
import { buildDefaultNasmCesFormData } from '../../config/nasmCesConfig'
import { buildDefaultSfmaFormData } from '../../config/sfmaConfig'
import {
  buildDefaultBodyCompositionFormData,
  buildDefaultComprehensiveFormData,
  buildDefaultFmsFormData,
  buildDefaultObservationFormData,
  buildDefaultOpenCapFormData,
  buildDefaultYbtFormData,
  COMPREHENSIVE_MODULE_OPTIONS,
  mergeStructuredAssessmentData
} from '../../config/structuredAssessmentConfig'

interface ModuleDefinition {
  typeCode: string
  label: string
  component: Component
  buildDefault: () => Record<string, any>
}

const props = defineProps<{
  modelValue?: Record<string, any>
  assessmentBaseInfo?: Record<string, any>
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', value: Record<string, any>): void
}>()

const moduleDefinitions: ModuleDefinition[] = [
  {
    typeCode: 'static_assessment',
    label: '静态评估',
    component: StaticAssessmentForm,
    buildDefault: buildDefaultStaticAssessmentFormData
  },
  {
    typeCode: 'body_composition',
    label: '身体成分',
    component: BodyCompositionForm,
    buildDefault: buildDefaultBodyCompositionFormData
  },
  {
    typeCode: 'nasm_ces',
    label: 'NASM-CES',
    component: NasmCesForm,
    buildDefault: buildDefaultNasmCesFormData
  },
  {
    typeCode: 'sfma',
    label: 'SFMA',
    component: SfmaForm,
    buildDefault: buildDefaultSfmaFormData
  },
  {
    typeCode: 'fms',
    label: 'FMS',
    component: FmsForm,
    buildDefault: buildDefaultFmsFormData
  },
  {
    typeCode: 'ybt',
    label: 'YBT',
    component: YbtForm,
    buildDefault: buildDefaultYbtFormData
  },
  {
    typeCode: 'opencap',
    label: 'OpenCap / OpenSim',
    component: OpenCapForm,
    buildDefault: buildDefaultOpenCapFormData
  },
  {
    typeCode: 'observation_only',
    label: '人工观察记录',
    component: ObservationOnlyForm,
    buildDefault: buildDefaultObservationFormData
  }
]

const localData = reactive<Record<string, any>>(buildDefaultComprehensiveFormData())
const moduleRefs = new Map<string, AssessmentFormExpose>()
const activeModule = ref('')

const selectedDefinitions = computed(() =>
  moduleDefinitions.filter((item) => localData.selectedModules.includes(item.typeCode))
)

const ensureSelectedModules = () => {
  selectedDefinitions.value.forEach((definition) => {
    if (!localData.modules[definition.typeCode]) {
      localData.modules[definition.typeCode] = definition.buildDefault()
    }
  })
  if (!localData.selectedModules.includes(activeModule.value)) {
    activeModule.value = selectedDefinitions.value[0]?.typeCode || ''
  }
}

const setModuleRef = (typeCode: string, instance: unknown) => {
  if (instance) {
    moduleRefs.set(typeCode, instance as AssessmentFormExpose)
  } else {
    moduleRefs.delete(typeCode)
  }
}

const updateModuleData = (typeCode: string, value: Record<string, any>) => {
  localData.modules[typeCode] = value
}

const resetLocalData = (value?: Record<string, any>) => {
  const next = mergeStructuredAssessmentData(buildDefaultComprehensiveFormData(), value)
  if (JSON.stringify(localData) === JSON.stringify(next)) {
    ensureSelectedModules()
    return
  }
  Object.keys(localData).forEach((key) => delete localData[key])
  Object.assign(localData, next)
  ensureSelectedModules()
}

const getFormData = () => JSON.parse(JSON.stringify(localData))

watch(
  () => props.modelValue,
  (value) => resetLocalData(value),
  { immediate: true, deep: true }
)

watch(
  () => localData.selectedModules,
  () => ensureSelectedModules(),
  { deep: true }
)

watch(
  localData,
  () => {
    const payload = getFormData()
    emit('update:modelValue', payload)
    emit('change', payload)
  },
  { deep: true }
)

const validate = async () => {
  if (!selectedDefinitions.value.length) {
    ElMessage.warning('综合评估请至少选择一个模块')
    return false
  }
  for (const definition of selectedDefinitions.value) {
    const childForm = moduleRefs.get(definition.typeCode)
    if (childForm?.validate && !(await childForm.validate())) {
      activeModule.value = definition.typeCode
      return false
    }
  }
  return true
}

const reset = () => {
  resetLocalData()
  emit('update:modelValue', getFormData())
  emit('change', getFormData())
}

defineExpose({ validate, getFormData, reset })
</script>

<style scoped>
.assessment-form-shell {
  border: 1px solid var(--el-border-color-light);
}

.module-tabs {
  border-radius: 6px;
}
</style>
