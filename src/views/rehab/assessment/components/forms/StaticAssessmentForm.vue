<template>
  <el-card shadow="never" class="assessment-form-shell">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">静态评估表单</span>
        <el-tag size="small" type="success">完整录入</el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-12px"
      title="依据静态体态评估量表录入：基础信息、后面观、侧面观、正面观。"
    />

    <el-card shadow="never" class="mb-12px">
      <template #header>基础信息区</template>
      <el-form :model="localData.basic_info" label-width="95px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="姓名">
              <el-input v-model="localData.basic_info.name" placeholder="姓名" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="评估日期">
              <el-date-picker
                v-model="localData.basic_info.assessment_date"
                value-format="YYYY-MM-DD"
                type="date"
                class="!w-full"
                placeholder="请选择评估日期"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="评估人">
              <el-input v-model="localData.basic_info.assessor" placeholder="评估人" clearable />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="身高(cm)">
              <el-input-number v-model="localData.basic_info.height_cm" :min="0" :max="260" class="!w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="体重(kg)">
              <el-input-number v-model="localData.basic_info.weight_kg" :min="0" :max="500" class="!w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="年龄">
              <el-input-number v-model="localData.basic_info.age" :min="0" :max="120" class="!w-full" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="性别">
              <el-radio-group v-model="localData.basic_info.gender">
                <el-radio
                  v-for="option in BASIC_GENDER_OPTIONS"
                  :key="option.value"
                  :label="option.value"
                >
                  {{ option.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mb-12px">
      <template #header>后面观（Posterior View）</template>
      <div class="matrix-header">
        <div>检查项目</div>
        <div>左侧</div>
        <div>右侧</div>
      </div>
      <div
        v-for="field in POSTERIOR_BILATERAL_FIELDS"
        :key="`posterior-${field.key}`"
        class="matrix-row"
      >
        <div class="matrix-label">
          {{ field.label }}
        </div>
        <el-select
          v-model="localData.posterior_view.left[field.key]"
          clearable
          class="!w-full"
          placeholder="左侧"
        >
          <el-option
            v-for="option in field.options"
            :key="`${field.key}-left-${option.value}`"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="localData.posterior_view.right[field.key]"
          clearable
          class="!w-full"
          placeholder="右侧"
        >
          <el-option
            v-for="option in field.options"
            :key="`${field.key}-right-${option.value}`"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>

      <el-divider content-position="left">中轴/整体观察</el-divider>

      <el-row :gutter="12">
        <el-col
          v-for="field in POSTERIOR_MIDLINE_FIELDS"
          :key="`posterior-midline-${field.key}`"
          :span="12"
          class="mb-10px"
        >
          <div class="field-label">{{ field.label }}</div>
          <el-select
            v-model="localData.posterior_view.midline[field.key]"
            clearable
            class="!w-full"
            :placeholder="field.label"
          >
            <el-option
              v-for="option in field.options"
              :key="`${field.key}-midline-${option.value}`"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-col>
      </el-row>

      <div class="mb-10px">
        <div class="field-label">脊柱排列备注</div>
        <el-input
          v-model="localData.notes.spine_alignment_note"
          type="textarea"
          :rows="2"
          placeholder="可填写脊柱排列图示观察备注"
        />
      </div>
    </el-card>

    <el-card shadow="never" class="mb-12px">
      <template #header>侧面观（Lateral View）</template>
      <div class="matrix-header">
        <div>检查项目</div>
        <div>左侧</div>
        <div>右侧</div>
      </div>
      <div
        v-for="field in LATERAL_BILATERAL_FIELDS"
        :key="`lateral-${field.key}`"
        class="matrix-row"
      >
        <div class="matrix-label">
          {{ field.label }}
        </div>
        <el-select
          v-model="localData.lateral_view.left[field.key]"
          clearable
          class="!w-full"
          placeholder="左侧"
        >
          <el-option
            v-for="option in field.options"
            :key="`${field.key}-lateral-left-${option.value}`"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="localData.lateral_view.right[field.key]"
          clearable
          class="!w-full"
          placeholder="右侧"
        >
          <el-option
            v-for="option in field.options"
            :key="`${field.key}-lateral-right-${option.value}`"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
    </el-card>

    <el-card shadow="never" class="mb-12px">
      <template #header>正面观（Anterior View）</template>
      <el-row :gutter="12">
        <el-col
          v-for="field in ANTERIOR_MIDLINE_FIELDS"
          :key="`anterior-midline-${field.key}`"
          :span="12"
          class="mb-10px"
        >
          <div class="field-label">{{ field.label }}</div>
          <el-select
            v-model="localData.anterior_view.midline[field.key]"
            clearable
            class="!w-full"
            :placeholder="field.label"
          >
            <el-option
              v-for="option in field.options"
              :key="`${field.key}-anterior-midline-${option.value}`"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-col>
      </el-row>

      <el-divider content-position="left">左右对称项目</el-divider>

      <div class="matrix-header">
        <div>检查项目</div>
        <div>左侧</div>
        <div>右侧</div>
      </div>
      <div
        v-for="field in ANTERIOR_BILATERAL_FIELDS"
        :key="`anterior-${field.key}`"
        class="matrix-row"
      >
        <div class="matrix-label">
          {{ field.label }}
          <span v-if="field.description" class="field-todo">（TODO）</span>
        </div>
        <el-select
          v-model="localData.anterior_view.left[field.key]"
          clearable
          class="!w-full"
          placeholder="左侧"
        >
          <el-option
            v-for="option in field.options"
            :key="`${field.key}-anterior-left-${option.value}`"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="localData.anterior_view.right[field.key]"
          clearable
          class="!w-full"
          placeholder="右侧"
        >
          <el-option
            v-for="option in field.options"
            :key="`${field.key}-anterior-right-${option.value}`"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>备注</template>
      <el-input
        v-model="localData.notes.general_note"
        type="textarea"
        :rows="3"
        placeholder="填写本次静态评估补充备注"
      />
    </el-card>
  </el-card>
</template>

<script lang="ts" setup>
import { ElMessage } from 'element-plus'
import { reactive, watch } from 'vue'
import {
  ANTERIOR_BILATERAL_FIELDS,
  ANTERIOR_MIDLINE_FIELDS,
  BASIC_GENDER_OPTIONS,
  buildDefaultStaticAssessmentFormData,
  LATERAL_BILATERAL_FIELDS,
  mergeStaticAssessmentFormData,
  POSTERIOR_BILATERAL_FIELDS,
  POSTERIOR_MIDLINE_FIELDS,
  type StaticAssessmentFormData
} from '@/views/rehab/assessment/config/staticAssessmentConfig'

interface StaticAssessmentBaseInfoSnapshot {
  name?: string
  height_cm?: number | null
  weight_kg?: number | null
  assessment_date?: string
  age?: number | null
  gender?: string
  assessor?: string
}

const props = defineProps<{
  modelValue?: Record<string, any>
  assessmentBaseInfo?: StaticAssessmentBaseInfoSnapshot
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', value: Record<string, any>): void
}>()

const deepClone = (value: any) => JSON.parse(JSON.stringify(value))

const localData = reactive<StaticAssessmentFormData>(buildDefaultStaticAssessmentFormData())

const setReactiveObject = (target: Record<string, any>, value: Record<string, any>) => {
  Object.keys(target).forEach((key) => delete target[key])
  Object.assign(target, value)
}

const applyBaseInfoSnapshot = () => {
  const snapshot = props.assessmentBaseInfo || {}
  const basicInfo = localData.basic_info
  if (!basicInfo.name && snapshot.name) {
    basicInfo.name = snapshot.name
  }
  if ((basicInfo.height_cm === null || basicInfo.height_cm === undefined) && snapshot.height_cm != null) {
    basicInfo.height_cm = snapshot.height_cm
  }
  if ((basicInfo.weight_kg === null || basicInfo.weight_kg === undefined) && snapshot.weight_kg != null) {
    basicInfo.weight_kg = snapshot.weight_kg
  }
  if (!basicInfo.assessment_date && snapshot.assessment_date) {
    basicInfo.assessment_date = snapshot.assessment_date
  }
  if ((basicInfo.age === null || basicInfo.age === undefined) && snapshot.age != null) {
    basicInfo.age = snapshot.age
  }
  if (!basicInfo.gender && snapshot.gender) {
    basicInfo.gender = snapshot.gender
  }
  if (!basicInfo.assessor && snapshot.assessor) {
    basicInfo.assessor = snapshot.assessor
  }
}

const resetLocalData = (value?: Record<string, any>) => {
  const merged = mergeStaticAssessmentFormData(value)
  setReactiveObject(localData as unknown as Record<string, any>, merged as unknown as Record<string, any>)
  applyBaseInfoSnapshot()
}

const getFormData = () => {
  const payload = deepClone(localData)
  // static_summary 为后端自动生成字段，不由录入层直接维护
  delete payload.static_summary
  return payload
}

watch(
  () => props.modelValue,
  (value) => {
    resetLocalData(value || {})
  },
  { immediate: true, deep: true }
)

watch(
  () => props.assessmentBaseInfo,
  () => {
    applyBaseInfoSnapshot()
  },
  { immediate: true, deep: true }
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
  if (!localData.basic_info.assessment_date) {
    ElMessage.warning('建议填写静态评估日期，以便后续汇总与报告追溯')
  }
  return true
}

const reset = () => {
  resetLocalData(buildDefaultStaticAssessmentFormData() as unknown as Record<string, any>)
  emit('update:modelValue', getFormData())
  emit('change', getFormData())
}

defineExpose({ validate, getFormData, reset })
</script>

<style scoped>
.assessment-form-shell {
  border: 1px solid var(--el-border-color-light);
}

.matrix-header {
  display: grid;
  grid-template-columns: 1.1fr 1fr 1fr;
  gap: 12px;
  padding: 8px 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  margin-bottom: 8px;
}

.matrix-row {
  display: grid;
  grid-template-columns: 1.1fr 1fr 1fr;
  gap: 12px;
  margin-bottom: 10px;
  align-items: center;
}

.matrix-label {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.field-label {
  font-size: 13px;
  margin-bottom: 6px;
  color: var(--el-text-color-primary);
}

.field-todo {
  color: var(--el-color-warning);
  font-size: 12px;
}
</style>
