<template>
  <el-card shadow="never" class="assessment-form-shell">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">人工观察记录</span>
        <el-tag size="small" type="success">临床记录</el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-12px"
      title="适用于无需量表的现场观察；请避免仅写结论，优先记录可复核的动作表现。"
    />

    <el-form :model="localData" label-width="120px">
      <el-divider content-position="left">观察条件</el-divider>
      <el-row :gutter="12">
        <el-col :xs="24" :sm="12">
          <el-form-item label="活动 / 动作">
            <el-input v-model="localData.context.activity" placeholder="例如：步行、深蹲、上肢推举" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="环境">
            <el-input v-model="localData.context.environment" placeholder="例如：治疗区、训练场" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="负荷条件">
            <el-input v-model="localData.context.loadCondition" placeholder="自重或具体负荷" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="鞋具 / 辅具">
            <el-input v-model="localData.context.footwear" placeholder="赤足、运动鞋或辅助器具" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">疼痛与症状</el-divider>
      <el-row :gutter="12">
        <el-col :xs="24" :sm="8">
          <el-form-item label="疼痛评分">
            <el-input-number
              v-model="localData.pain.score"
              :min="0"
              :max="10"
              :step="0.5"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="16">
          <el-form-item label="诱发条件">
            <el-input v-model="localData.pain.trigger" placeholder="动作阶段、持续时间或负荷阈值" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="疼痛部位">
        <el-select
          v-model="localData.pain.bodyRegions"
          multiple
          filterable
          allow-create
          default-first-option
          class="!w-full"
          placeholder="可选择或直接输入部位"
        >
          <el-option v-for="region in bodyRegionOptions" :key="region" :label="region" :value="region" />
        </el-select>
      </el-form-item>

      <el-divider content-position="left">可复核观察</el-divider>
      <el-row :gutter="12">
        <el-col
          v-for="field in observationFields"
          :key="field.key"
          :xs="24"
          :lg="12"
        >
          <el-form-item :label="field.label">
            <el-input
              v-model="localData.observations[field.key]"
              type="textarea"
              :rows="3"
              :placeholder="field.placeholder"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="红旗提示">
        <el-select
          v-model="localData.redFlags"
          multiple
          filterable
          allow-create
          default-first-option
          class="!w-full"
          placeholder="无红旗可留空"
        >
          <el-option label="静息或夜间痛" value="静息或夜间痛" />
          <el-option label="进行性神经症状" value="进行性神经症状" />
          <el-option label="近期严重创伤" value="近期严重创伤" />
          <el-option label="不明原因发热或体重下降" value="不明原因发热或体重下降" />
          <el-option label="运动中胸痛、晕厥或呼吸异常" value="运动中胸痛、晕厥或呼吸异常" />
        </el-select>
      </el-form-item>

      <el-divider content-position="left">结论与跟进</el-divider>
      <el-form-item label="观察结论">
        <el-input v-model="localData.summary.conclusion" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="处理建议">
        <el-input v-model="localData.summary.recommendation" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="复查安排">
        <el-input v-model="localData.summary.followUp" placeholder="复查时间、条件或需补充的检查" />
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script lang="ts" setup>
import {
  buildDefaultObservationFormData,
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

const bodyRegionOptions = [
  '颈部',
  '肩部',
  '胸椎',
  '腰部',
  '髋部',
  '膝部',
  '踝足',
  '上肢',
  '下肢'
]

const observationFields = [
  { key: 'posture', label: '姿势排列', placeholder: '静态排列、重心与对称性' },
  { key: 'gait', label: '步态表现', placeholder: '支撑期、摆动期、步幅与节律' },
  { key: 'balance', label: '平衡控制', placeholder: '稳定性、摇摆方向和保护反应' },
  { key: 'movementCompensation', label: '动作代偿', placeholder: '发生动作、阶段、方向和频率' },
  { key: 'breathing', label: '呼吸表现', placeholder: '呼吸节律、屏气或辅助呼吸肌参与' }
]

const localData = reactive<Record<string, any>>(buildDefaultObservationFormData())

const resetLocalData = (value?: Record<string, any>) => {
  const next = mergeStructuredAssessmentData(buildDefaultObservationFormData(), value)
  if (JSON.stringify(localData) === JSON.stringify(next)) {
    if (localData.pain.score == null && props.assessmentBaseInfo?.pain_score != null) {
      localData.pain.score = props.assessmentBaseInfo.pain_score
    }
    return
  }
  Object.keys(localData).forEach((key) => delete localData[key])
  Object.assign(localData, next)
  if (localData.pain.score == null && props.assessmentBaseInfo?.pain_score != null) {
    localData.pain.score = props.assessmentBaseInfo.pain_score
  }
}

const getFormData = () => JSON.parse(JSON.stringify(localData))

watch(
  () => props.modelValue,
  (value) => resetLocalData(value),
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
  if (!localData.summary.conclusion && !localData.observations.movementCompensation) {
    ElMessage.warning('建议至少填写动作代偿或观察结论，仍可保存草稿')
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
