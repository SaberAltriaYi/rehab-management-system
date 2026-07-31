<template>
  <el-card shadow="never" class="assessment-form-shell">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">OpenCap / OpenSim 运动学记录</span>
        <el-button type="primary" plain size="small" @click="addTrial">新增 Trial</el-button>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-12px"
      title="本表单仅保存处理结果和文件链接，不在系统内自动上传视频或运行云端分析。"
    />

    <el-form :model="localData" label-width="120px">
      <el-divider content-position="left">采集会话</el-divider>
      <el-row :gutter="12">
        <el-col :xs="24" :sm="12">
          <el-form-item label="会话编号">
            <el-input v-model="localData.session.sessionId" placeholder="OpenCap / OpenSim session ID" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="采集时间">
            <el-date-picker
              v-model="localData.session.capturedAt"
              value-format="YYYY-MM-DD HH:mm:ss"
              type="datetime"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="采集设备">
            <el-input v-model="localData.session.captureDevice" placeholder="例如：双机位 iPhone" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="处理版本">
            <el-input v-model="localData.session.processingVersion" placeholder="例如：OpenCap v2 / OpenSim 4.x" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-empty v-if="!localData.trials.length" description="尚未添加 Trial" />
      <el-card
        v-for="(trial, trialIndex) in localData.trials"
        :key="trialIndex"
        shadow="never"
        class="trial-card"
      >
        <template #header>
          <div class="flex items-center justify-between">
            <span class="font-bold">Trial {{ trialIndex + 1 }}：{{ trial.name || '未命名' }}</span>
            <el-button type="danger" link @click="removeTrial(trialIndex)">删除</el-button>
          </div>
        </template>

        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="Trial 名称">
              <el-input v-model="trial.name" placeholder="例如：深蹲 01" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="动作">
              <el-input v-model="trial.movement" placeholder="例如：Squat / Gait" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="8">
            <el-form-item label="质量">
              <el-select v-model="trial.quality" class="!w-full">
                <el-option label="良好" value="good" />
                <el-option label="可用" value="fair" />
                <el-option label="需复采" value="recollect" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="数据链接">
              <el-input v-model="trial.sourceUrl" placeholder="本地共享盘或受控链接" />
            </el-form-item>
          </el-col>
          <el-col :xs="12" :sm="6">
            <el-form-item label="时长（秒）">
              <el-input-number v-model="trial.durationSeconds" :min="0" :precision="2" class="!w-full" />
            </el-form-item>
          </el-col>
          <el-col :xs="12" :sm="6">
            <el-form-item label="帧率">
              <el-input-number v-model="trial.frameRate" :min="1" :max="240" class="!w-full" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="mb-8px flex items-center justify-between">
          <span class="font-bold">运动学指标</span>
          <el-button size="small" @click="addMetric(trial)">新增指标</el-button>
        </div>
        <el-table :data="trial.metrics" border>
          <el-table-column label="指标" min-width="180">
            <template #default="{ row }">
              <el-input v-model="row.name" placeholder="例如：knee_flexion" />
            </template>
          </el-table-column>
          <el-table-column label="侧别" width="110">
            <template #default="{ row }">
              <el-select v-model="row.side">
                <el-option label="无" value="none" />
                <el-option label="左" value="left" />
                <el-option label="右" value="right" />
                <el-option label="双侧" value="bilateral" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column v-for="field in metricFields" :key="field.key" :label="field.label" min-width="120">
            <template #default="{ row }">
              <el-input-number v-model="row[field.key]" :precision="2" controls-position="right" class="!w-full" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="100">
            <template #default="{ row }">
              <el-input v-model="row.unit" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button type="danger" link @click="removeMetric(trial, $index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-form-item label="质量标记" class="mt-12px">
          <el-checkbox-group v-model="trial.qualityFlags">
            <el-checkbox label="marker_loss">关键点丢失</el-checkbox>
            <el-checkbox label="camera_sync">相机同步问题</el-checkbox>
            <el-checkbox label="model_fit">模型拟合偏差</el-checkbox>
            <el-checkbox label="partial_trial">动作不完整</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="Trial 备注">
          <el-input v-model="trial.note" type="textarea" :rows="2" />
        </el-form-item>
      </el-card>

      <el-divider content-position="left">人工汇总</el-divider>
      <el-form-item label="主要结论">
        <el-input v-model="localData.summary.conclusion" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="数据局限">
        <el-input v-model="localData.summary.limitation" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="处理建议">
        <el-input v-model="localData.summary.recommendation" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script lang="ts" setup>
import {
  buildDefaultOpenCapFormData,
  buildDefaultOpenCapMetric,
  buildDefaultOpenCapTrial,
  mergeStructuredAssessmentData
} from '../../config/structuredAssessmentConfig'

const props = defineProps<{ modelValue?: Record<string, any> }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', value: Record<string, any>): void
}>()

const metricFields = [
  { key: 'minimum', label: '最小值' },
  { key: 'maximum', label: '最大值' },
  { key: 'rangeOfMotion', label: 'ROM' }
]

const localData = reactive<Record<string, any>>(buildDefaultOpenCapFormData())

const addTrial = () => localData.trials.push(buildDefaultOpenCapTrial())
const removeTrial = (index: number) => localData.trials.splice(index, 1)
const addMetric = (trial: Record<string, any>) => trial.metrics.push(buildDefaultOpenCapMetric())
const removeMetric = (trial: Record<string, any>, index: number) => trial.metrics.splice(index, 1)

const resetLocalData = (value?: Record<string, any>) => {
  const next = mergeStructuredAssessmentData(buildDefaultOpenCapFormData(), value)
  if (JSON.stringify(localData) === JSON.stringify(next)) {
    return
  }
  Object.keys(localData).forEach((key) => delete localData[key])
  Object.assign(localData, next)
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
  const incompleteTrials = localData.trials.filter(
    (trial: Record<string, any>) => !trial.name || !trial.movement
  ).length
  if (incompleteTrials) {
    ElMessage.warning(`OpenCap 有 ${incompleteTrials} 个 Trial 未填写名称或动作，仍可保存草稿`)
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

.trial-card {
  margin-bottom: 12px;
}
</style>
