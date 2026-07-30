<template>
  <el-card shadow="never" class="assessment-form-shell">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">YBT 动态平衡测试</span>
        <el-tag size="small" type="success">自动标准化</el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-12px"
      title="录入肢体长度和各方向最佳触及距离，系统自动计算综合分与最大左右差。"
    />

    <el-card
      v-for="region in regionEntries"
      :key="region.key"
      shadow="never"
      class="mb-12px"
    >
      <template #header>
        <div class="flex items-center justify-between">
          <span class="font-bold">{{ region.label }}</span>
          <el-switch
            v-model="region.data.enabled"
            active-text="纳入本次评估"
            :disabled="region.key === 'lowerQuarter'"
          />
        </div>
      </template>

      <template v-if="region.data.enabled">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="左侧肢长（cm）">
              <el-input-number
                v-model="region.data.limbLength.left"
                :min="10"
                :max="150"
                :precision="1"
                class="!w-full"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="右侧肢长（cm）">
              <el-input-number
                v-model="region.data.limbLength.right"
                :min="10"
                :max="150"
                :precision="1"
                class="!w-full"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-table :data="region.data.directions" border>
          <el-table-column prop="name" label="方向" min-width="150" />
          <el-table-column label="左侧最佳距离（cm）" min-width="190">
            <template #default="{ row }">
              <el-input-number
                v-model="row.left"
                :min="0"
                :max="200"
                :precision="1"
                class="!w-full"
              />
            </template>
          </el-table-column>
          <el-table-column label="右侧最佳距离（cm）" min-width="190">
            <template #default="{ row }">
              <el-input-number
                v-model="row.right"
                :min="0"
                :max="200"
                :precision="1"
                class="!w-full"
              />
            </template>
          </el-table-column>
          <el-table-column label="左右差（cm）" width="130" align="center">
            <template #default="{ row }">
              {{ directionAsymmetry(row) }}
            </template>
          </el-table-column>
        </el-table>

        <el-descriptions :column="3" border class="mt-12px">
          <el-descriptions-item label="左侧综合分">
            {{ formatPercent(region.data.result.leftCompositePercent) }}
          </el-descriptions-item>
          <el-descriptions-item label="右侧综合分">
            {{ formatPercent(region.data.result.rightCompositePercent) }}
          </el-descriptions-item>
          <el-descriptions-item label="最大左右差">
            {{ formatDistance(region.data.result.maxAsymmetryCm) }}
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="region.data.result.riskFlag"
          :closable="false"
          type="warning"
          class="mt-12px"
          title="存在综合分偏低或明显左右差，请结合年龄、项目和症状人工判断。"
        />
        <el-form-item label="区域备注" class="mt-12px">
          <el-input v-model="region.data.note" type="textarea" :rows="2" />
        </el-form-item>
      </template>
    </el-card>

    <el-form :model="localData" label-width="110px">
      <el-form-item label="综合结论">
        <el-input v-model="localData.summary.conclusion" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="训练建议">
        <el-input v-model="localData.summary.recommendation" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script lang="ts" setup>
import {
  buildDefaultYbtFormData,
  mergeStructuredAssessmentData
} from '../../config/structuredAssessmentConfig'

const props = defineProps<{ modelValue?: Record<string, any> }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', value: Record<string, any>): void
}>()

const localData = reactive<Record<string, any>>(buildDefaultYbtFormData())

const regionEntries = computed(() => [
  { key: 'lowerQuarter', label: '下肢 Y-Balance（LQ）', data: localData.lowerQuarter },
  { key: 'upperQuarter', label: '上肢 Y-Balance（UQ）', data: localData.upperQuarter }
])

const directionAsymmetry = (direction: Record<string, any>) => {
  if (direction.left == null || direction.right == null) {
    return '-'
  }
  return Math.abs(Number(direction.left) - Number(direction.right)).toFixed(1)
}

const calculateComposite = (directions: Record<string, any>[], side: 'left' | 'right', length: any) => {
  if (!length || directions.some((direction) => direction[side] == null)) {
    return undefined
  }
  const reachTotal = directions.reduce(
    (sum, direction) => sum + Number(direction[side] || 0),
    0
  )
  return Number(((reachTotal / (3 * Number(length))) * 100).toFixed(1))
}

const syncRegionResult = (region: Record<string, any>) => {
  if (!region.enabled) {
    Object.assign(region.result, {
      leftCompositePercent: undefined,
      rightCompositePercent: undefined,
      maxAsymmetryCm: undefined,
      riskFlag: false
    })
    return
  }
  const leftCompositePercent = calculateComposite(
    region.directions,
    'left',
    region.limbLength.left
  )
  const rightCompositePercent = calculateComposite(
    region.directions,
    'right',
    region.limbLength.right
  )
  const differences = region.directions
    .filter((direction: Record<string, any>) => direction.left != null && direction.right != null)
    .map((direction: Record<string, any>) =>
      Math.abs(Number(direction.left) - Number(direction.right))
    )
  const maxAsymmetryCm = differences.length ? Math.max(...differences) : undefined
  Object.assign(region.result, {
    leftCompositePercent,
    rightCompositePercent,
    maxAsymmetryCm,
    riskFlag:
      (leftCompositePercent != null && leftCompositePercent < 94) ||
      (rightCompositePercent != null && rightCompositePercent < 94) ||
      (maxAsymmetryCm != null && maxAsymmetryCm > 4)
  })
}

const syncDerivedValues = () => {
  syncRegionResult(localData.lowerQuarter)
  syncRegionResult(localData.upperQuarter)
}

const formatPercent = (value: any) => (value == null ? '-' : `${value}%`)
const formatDistance = (value: any) => (value == null ? '-' : `${value.toFixed(1)} cm`)

const resetLocalData = (value?: Record<string, any>) => {
  const next = mergeStructuredAssessmentData(buildDefaultYbtFormData(), value)
  if (JSON.stringify(localData) === JSON.stringify(next)) {
    syncDerivedValues()
    return
  }
  Object.keys(localData).forEach((key) => delete localData[key])
  Object.assign(localData, next)
  syncDerivedValues()
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
    syncDerivedValues()
    const payload = getFormData()
    emit('update:modelValue', payload)
    emit('change', payload)
  },
  { deep: true }
)

const validate = async () => {
  const incomplete = regionEntries.value.some(
    ({ data }) =>
      data.enabled &&
      (!data.limbLength.left ||
        !data.limbLength.right ||
        data.directions.some((item: Record<string, any>) => item.left == null || item.right == null))
  )
  if (incomplete) {
    ElMessage.warning('YBT 存在未完成的肢长或方向距离，仍可保存草稿')
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
