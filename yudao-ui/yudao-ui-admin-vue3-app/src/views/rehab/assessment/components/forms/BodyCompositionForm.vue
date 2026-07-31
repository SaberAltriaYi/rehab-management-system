<template>
  <el-card shadow="never" class="assessment-form-shell">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">身体成分评估</span>
        <el-tag size="small" type="success">结构化录入</el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-12px"
      title="记录同一测量条件下的身体成分指标；BMI 与腰臀比会自动计算。"
    />

    <el-form :model="localData" label-width="120px">
      <el-divider content-position="left">基础测量</el-divider>
      <el-row :gutter="12">
        <el-col v-for="field in measurementFields" :key="field.key" :xs="24" :sm="12" :lg="8">
          <el-form-item :label="field.label">
            <el-input-number
              v-model="localData.measurements[field.key]"
              :min="field.min"
              :max="field.max"
              :precision="field.precision"
              :disabled="field.readonly"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">测量条件与生长指标</el-divider>
      <el-row :gutter="12">
        <el-col :xs="24" :sm="12" :lg="8">
          <el-form-item label="测量设备">
            <el-input v-model="localData.measurementMeta.device" placeholder="型号或设备名称" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12" :lg="8">
          <el-form-item label="测量时间">
            <el-date-picker
              v-model="localData.measurementMeta.measuredAt"
              value-format="YYYY-MM-DD HH:mm:ss"
              type="datetime"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12" :lg="8">
          <el-form-item label="数据质量">
            <el-select v-model="localData.measurementMeta.quality" class="!w-full">
              <el-option label="良好" value="good" />
              <el-option label="可用但有偏差" value="fair" />
              <el-option label="需复测" value="retest" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12" :lg="8">
          <el-form-item label="空腹测量">
            <el-switch v-model="localData.measurementMeta.fasting" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12" :lg="8">
          <el-form-item label="发育阶段">
            <el-select v-model="localData.growth.stage" clearable class="!w-full">
              <el-option label="儿童期" value="child" />
              <el-option label="青春前期" value="prepubertal" />
              <el-option label="青春期" value="pubertal" />
              <el-option label="成年期" value="adult" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col
          v-for="field in growthFields"
          :key="field.key"
          :xs="24"
          :sm="12"
          :lg="8"
        >
          <el-form-item :label="field.label">
            <el-input-number
              v-model="localData.growth[field.key]"
              :min="0"
              :max="100"
              :precision="1"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="风险提示">
        <el-checkbox-group v-model="localData.riskFlags">
          <el-checkbox label="weight_change">近期体重显著变化</el-checkbox>
          <el-checkbox label="low_muscle">肌肉量偏低</el-checkbox>
          <el-checkbox label="high_body_fat">体脂偏高</el-checkbox>
          <el-checkbox label="growth_attention">生长发育需关注</el-checkbox>
          <el-checkbox label="retest_needed">测量条件不稳定，建议复测</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="评估结论">
        <el-input v-model="localData.summary.conclusion" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="建议">
        <el-input v-model="localData.summary.recommendation" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script lang="ts" setup>
import {
  buildDefaultBodyCompositionFormData,
  mergeStructuredAssessmentData
} from '../../config/structuredAssessmentConfig'

const props = defineProps<{
  modelValue?: Record<string, any>
  assessmentBaseInfo?: Record<string, any>
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', value: Record<string, any>): void
}>()

const measurementFields = [
  { key: 'heightCm', label: '身高（cm）', min: 30, max: 250, precision: 1 },
  { key: 'weightKg', label: '体重（kg）', min: 2, max: 350, precision: 1 },
  { key: 'bmi', label: 'BMI', min: 0, max: 100, precision: 1, readonly: true },
  { key: 'bodyFatPercent', label: '体脂率（%）', min: 0, max: 80, precision: 1 },
  { key: 'skeletalMuscleKg', label: '骨骼肌（kg）', min: 0, max: 150, precision: 1 },
  { key: 'bodyWaterPercent', label: '体水分（%）', min: 0, max: 100, precision: 1 },
  { key: 'visceralFatLevel', label: '内脏脂肪等级', min: 0, max: 60, precision: 0 },
  { key: 'waistCm', label: '腰围（cm）', min: 20, max: 250, precision: 1 },
  { key: 'hipCm', label: '臀围（cm）', min: 20, max: 250, precision: 1 },
  { key: 'waistHipRatio', label: '腰臀比', min: 0, max: 3, precision: 2, readonly: true }
]

const growthFields = [
  { key: 'heightPercentile', label: '身高百分位' },
  { key: 'weightPercentile', label: '体重百分位' },
  { key: 'bmiPercentile', label: 'BMI 百分位' }
]

const localData = reactive<Record<string, any>>(buildDefaultBodyCompositionFormData())

const applyBaseInfo = () => {
  const baseInfo = props.assessmentBaseInfo || {}
  if (localData.measurements.heightCm == null && baseInfo.height_cm != null) {
    localData.measurements.heightCm = baseInfo.height_cm
  }
  if (localData.measurements.weightKg == null && baseInfo.weight_kg != null) {
    localData.measurements.weightKg = baseInfo.weight_kg
  }
}

const syncDerivedValues = () => {
  const { heightCm, weightKg, waistCm, hipCm } = localData.measurements
  const bmi = heightCm > 0 && weightKg > 0 ? weightKg / Math.pow(heightCm / 100, 2) : undefined
  const waistHipRatio = waistCm > 0 && hipCm > 0 ? waistCm / hipCm : undefined
  localData.measurements.bmi = bmi == null ? undefined : Number(bmi.toFixed(1))
  localData.measurements.waistHipRatio =
    waistHipRatio == null ? undefined : Number(waistHipRatio.toFixed(2))
}

const resetLocalData = (value?: Record<string, any>) => {
  const next = mergeStructuredAssessmentData(buildDefaultBodyCompositionFormData(), value)
  if (JSON.stringify(localData) === JSON.stringify(next)) {
    applyBaseInfo()
    syncDerivedValues()
    return
  }
  Object.keys(localData).forEach((key) => delete localData[key])
  Object.assign(localData, next)
  applyBaseInfo()
  syncDerivedValues()
}

const getFormData = () => JSON.parse(JSON.stringify(localData))

watch(
  () => props.modelValue,
  (value) => resetLocalData(value),
  { immediate: true, deep: true }
)

watch(
  () => props.assessmentBaseInfo,
  () => applyBaseInfo(),
  { deep: true }
)

watch(
  localData,
  () => {
    syncDerivedValues()
    const payload = getFormData()
    emit('update:modelValue', payload)
    emit('change', payload)
  },
  { deep: true }
)

const validate = async () => {
  if (!localData.measurements.heightCm || !localData.measurements.weightKg) {
    ElMessage.warning('建议填写身高和体重，以生成 BMI')
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
</style>
