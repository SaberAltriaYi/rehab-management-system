<template>
  <el-card shadow="never" class="mb-12px book-protocol-card">
    <template #header>
      <div class="flex items-center justify-between">
        <div>
          <div class="font-bold">原书版 SFMA 分解评估</div>
          <div class="protocol-source">{{ protocolDefinition?.source || localData.source }}</div>
        </div>
        <el-tag type="success" size="small">协议 {{ localData.protocol_version }}</el-tag>
      </div>
    </template>

    <el-alert
      v-if="loading"
      :closable="false"
      type="info"
      title="正在加载 SFMA 原书版协议…"
      class="mb-12px"
    />
    <el-alert
      v-else-if="loadError"
      :closable="false"
      type="error"
      :title="loadError"
      class="mb-12px"
    />
    <template v-else-if="protocolDefinition">
      <el-alert
        :closable="false"
        type="warning"
        title="安全与顺序规则"
        description="先完成全部 Top Tier；分解按 DN → FP → DP 排列。FN 不进入分解，FP/DP 谨慎进行；任一步出现 FP 或 DP，系统将终止该流程的后续测试。"
        class="mb-12px"
      />

      <el-alert
        v-if="!topTierComplete"
        :closable="false"
        type="info"
        title="请先完成全部 15 项 Top Tier 分类，再开始分解评估。"
        class="mb-12px"
      />
      <el-result
        v-else-if="!activeWorkflows.length"
        icon="success"
        title="Top Tier 均为 FN"
        sub-title="按照原书流程，无需进入分解评估。"
      />

      <el-collapse v-else v-model="expandedWorkflows">
        <el-collapse-item
          v-for="item in activeWorkflows"
          :key="item.definition.code"
          :name="item.definition.code"
        >
          <template #title>
            <div class="workflow-title">
              <span>{{ item.definition.name }}</span>
              <el-tag :type="stageTagType(item.stage)" size="small">{{ stageLabel(item.stage) }}</el-tag>
              <el-tag :type="workflowStatusType(item.result.status)" size="small">
                {{ workflowStatusLabel(item.result.status) }}
              </el-tag>
            </div>
          </template>

          <el-alert
            v-if="item.stage !== 'DN'"
            :closable="false"
            type="warning"
            :title="`${item.stage} 路径：谨慎进行；若当前疼痛易激惹，可直接停止并转入疼痛处理。`"
            class="mb-12px"
          />

          <div class="trigger-line">
            Top Tier 触发：
            <el-tag
              v-for="trigger in item.result.trigger_classifications"
              :key="trigger.test_code"
              size="small"
              class="mr-6px"
              :type="classificationTagType(trigger.classification)"
            >
              {{ topTierName(trigger.test_code) }} {{ trigger.classification }}
            </el-tag>
          </div>

          <el-table :data="item.definition.steps" border class="mt-10px">
            <el-table-column type="index" label="顺序" width="58" />
            <el-table-column label="原书测试" min-width="220">
              <template #default="{ row }">
                <div class="font-500">{{ row.test_name_zh }}</div>
                <div v-if="row.criterion" class="step-hint">标准：{{ row.criterion }}</div>
                <div v-if="row.condition" class="step-condition">分支：{{ row.condition }}</div>
              </template>
            </el-table-column>
            <el-table-column label="执行状态" width="150">
              <template #default="{ row, $index }">
                <el-select
                  v-model="stepResult(item.result, row).status"
                  :disabled="isStoppedStep(item.result, $index)"
                  @change="handleStatusChange(item.result, row)"
                >
                  <el-option label="待评估" value="pending" />
                  <el-option label="按分支跳过" value="skipped" />
                  <el-option label="不适用" value="not_applicable" />
                  <el-option label="已完成" value="completed" disabled />
                  <el-option label="疼痛终止" value="stopped_due_to_pain" disabled />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="SFMA 分类" min-width="215">
              <template #default="{ row, $index }">
                <template v-if="row.side === 'bilateral'">
                  <div class="bilateral-result">
                    <el-select
                      v-model="stepResult(item.result, row).left_classification"
                      placeholder="左侧"
                      clearable
                      :disabled="isResultDisabled(item.result, row, $index)"
                      @change="handleResultChange(item.result)"
                    >
                      <el-option v-for="option in classificationOptions" :key="option" :label="`左 ${option}`" :value="option" />
                    </el-select>
                    <el-select
                      v-model="stepResult(item.result, row).right_classification"
                      placeholder="右侧"
                      clearable
                      :disabled="isResultDisabled(item.result, row, $index)"
                      @change="handleResultChange(item.result)"
                    >
                      <el-option v-for="option in classificationOptions" :key="option" :label="`右 ${option}`" :value="option" />
                    </el-select>
                  </div>
                </template>
                <el-select
                  v-else
                  v-model="stepResult(item.result, row).classification"
                  placeholder="请选择"
                  clearable
                  :disabled="isResultDisabled(item.result, row, $index)"
                  @change="handleResultChange(item.result)"
                >
                  <el-option v-for="option in classificationOptions" :key="option" :label="option" :value="option" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="关键值" min-width="190">
              <template #default="{ row, $index }">
                <div v-if="row.side === 'bilateral'" class="bilateral-result">
                  <el-input
                    v-model="stepResult(item.result, row).left_value"
                    placeholder="左侧值"
                    :disabled="isResultDisabled(item.result, row, $index)"
                  />
                  <el-input
                    v-model="stepResult(item.result, row).right_value"
                    placeholder="右侧值"
                    :disabled="isResultDisabled(item.result, row, $index)"
                  />
                </div>
                <el-input
                  v-else
                  v-model="stepResult(item.result, row).value"
                  placeholder="角度/次数/表现"
                  :disabled="isResultDisabled(item.result, row, $index)"
                />
              </template>
            </el-table-column>
            <el-table-column label="记录" min-width="180">
              <template #default="{ row, $index }">
                <el-input
                  v-model="stepResult(item.result, row).note"
                  placeholder="疼痛、代偿或临床备注"
                  :disabled="isStoppedStep(item.result, $index)"
                />
              </template>
            </el-table-column>
          </el-table>

          <el-form-item label="流程备注" class="mt-12px">
            <el-input v-model="item.result.note" type="textarea" :rows="2" placeholder="记录分支选择、停止原因或人工复核事项" />
          </el-form-item>
        </el-collapse-item>
      </el-collapse>
    </template>
  </el-card>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { isEqual } from 'lodash-es'
import { getRehabSfmaBookProtocol } from '@/api/rehab/assessment'
import {
  buildDefaultSfmaBookProtocolData,
  buildDefaultSfmaBookStepResult,
  SfmaBookProtocolData,
  SfmaBookProtocolDefinition,
  SfmaBookStepDefinition,
  SfmaBookWorkflowDefinition,
  SfmaBookWorkflowResult
} from '@/views/rehab/assessment/config/sfmaBookProtocol'

const props = defineProps<{
  modelValue?: SfmaBookProtocolData
  topTier: Record<string, any>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: SfmaBookProtocolData): void
  (e: 'change', value: SfmaBookProtocolData): void
}>()

const loading = ref(true)
const loadError = ref('')
const protocolDefinition = ref<SfmaBookProtocolDefinition>()
const expandedWorkflows = ref<string[]>([])
const localData = reactive<SfmaBookProtocolData>(buildDefaultSfmaBookProtocolData())
let syncingFromProps = false

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value))
}

const resetFromProps = (value?: SfmaBookProtocolData) => {
  const next = {
    ...buildDefaultSfmaBookProtocolData(),
    ...(value ? clone(value) : {}),
    workflows: value?.workflows ? clone(value.workflows) : {}
  }
  if (isEqual(next, clone(localData))) return
  syncingFromProps = true
  Object.assign(localData, next)
  queueMicrotask(() => {
    syncingFromProps = false
  })
}

watch(() => props.modelValue, resetFromProps, { deep: true, immediate: true })

watch(
  localData,
  () => {
    if (syncingFromProps) return
    const payload = clone(localData)
    emit('update:modelValue', payload)
    emit('change', payload)
  },
  { deep: true }
)

const initializeWorkflows = () => {
  if (!protocolDefinition.value) return
  localData.protocol_id = protocolDefinition.value.protocol_id
  localData.protocol_version = protocolDefinition.value.protocol_version
  localData.source = protocolDefinition.value.source
  protocolDefinition.value.workflows.forEach((definition) => {
    const existing = localData.workflows[definition.code]
    const existingStepMap = new Map(
      (existing?.steps || []).filter((step) => !!step.test_code).map((step) => [step.test_code, step])
    )
    localData.workflows[definition.code] = {
      workflow_code: definition.code,
      workflow_name_zh: definition.name,
      status: existing?.status || 'not_started',
      trigger_classifications: existing?.trigger_classifications || [],
      steps: definition.steps.map((step) => ({
        ...buildDefaultSfmaBookStepResult(step.test_code),
        ...(existingStepMap.get(step.test_code) || {})
      })),
      note: existing?.note || ''
    }
  })
  syncTriggersAndStatuses()
}

onMounted(async () => {
  loading.value = true
  try {
    protocolDefinition.value = await getRehabSfmaBookProtocol()
    initializeWorkflows()
  } catch (error: any) {
    loadError.value = error?.message || 'SFMA 原书版协议加载失败，请检查后端服务。'
  } finally {
    loading.value = false
  }
})

const allTriggerCodes = computed(() => {
  const codes = new Set<string>()
  protocolDefinition.value?.workflows.forEach((workflow) => workflow.trigger_test_codes.forEach((code) => codes.add(code)))
  return Array.from(codes)
})

const topTierComplete = computed(
  () => allTriggerCodes.value.length === 15 && allTriggerCodes.value.every((code) => !!props.topTier?.[code]?.classification)
)

const classificationOptions = computed(() => protocolDefinition.value?.classification_options || ['FN', 'FP', 'DN', 'DP'])

const triggerClassifications = (definition: SfmaBookWorkflowDefinition) =>
  definition.trigger_test_codes
    .map((testCode) => ({ test_code: testCode, classification: String(props.topTier?.[testCode]?.classification || '') }))
    .filter((item) => item.classification && item.classification !== 'FN')

const workflowStage = (definition: SfmaBookWorkflowDefinition) => {
  const classes = triggerClassifications(definition).map((item) => item.classification)
  if (classes.includes('DN')) return 'DN'
  if (classes.includes('FP')) return 'FP'
  return 'DP'
}

const stageWeight = (stage: string) => (stage === 'DN' ? 1 : stage === 'FP' ? 2 : 3)

const activeWorkflows = computed(() => {
  if (!topTierComplete.value || !protocolDefinition.value) return []
  return protocolDefinition.value.workflows
    .filter((definition) => triggerClassifications(definition).length > 0)
    .map((definition) => ({
      definition,
      result: localData.workflows[definition.code],
      stage: workflowStage(definition)
    }))
    .filter((item) => !!item.result)
    .sort((a, b) => stageWeight(a.stage) - stageWeight(b.stage) || a.definition.order - b.definition.order)
})

const stepResult = (workflow: SfmaBookWorkflowResult, definition: SfmaBookStepDefinition) => {
  const testCode = definition?.test_code
  if (!testCode) {
    // Element Plus 表格初始化时可能短暂传入空 row；此时只返回临时值，禁止污染提交数据。
    return buildDefaultSfmaBookStepResult('')
  }
  let result = workflow.steps.find((step) => step.test_code === testCode)
  if (!result) {
    result = buildDefaultSfmaBookStepResult(testCode)
    workflow.steps.push(result)
  }
  return result
}

const stepHasPain = (step: ReturnType<typeof buildDefaultSfmaBookStepResult>) =>
  [step.classification, step.left_classification, step.right_classification].some(
    (value) => value === 'FP' || value === 'DP'
  )

const firstPainIndex = (workflow: SfmaBookWorkflowResult) => workflow.steps.findIndex(stepHasPain)

const isStoppedStep = (workflow: SfmaBookWorkflowResult, index: number) => {
  const painIndex = firstPainIndex(workflow)
  return painIndex >= 0 && index > painIndex
}

const isResultDisabled = (
  workflow: SfmaBookWorkflowResult,
  definition: SfmaBookStepDefinition,
  index: number
) => {
  const status = stepResult(workflow, definition).status
  return isStoppedStep(workflow, index) || status === 'skipped' || status === 'not_applicable'
}

const normalizeWorkflow = (workflow: SfmaBookWorkflowResult) => {
  // 同时清理旧页面渲染阶段可能遗留的空步骤，后端仍保持严格协议校验。
  workflow.steps = workflow.steps.filter((step) => !!step.test_code)
  const painIndex = firstPainIndex(workflow)
  workflow.steps.forEach((step, index) => {
    if (painIndex >= 0 && index > painIndex) {
      step.status = 'stopped_due_to_pain'
      step.classification = ''
      step.left_classification = ''
      step.right_classification = ''
      step.value = ''
      step.left_value = ''
      step.right_value = ''
      return
    }
    if (step.status === 'stopped_due_to_pain') {
      step.status = 'pending'
    }
    const hasResult = !!(step.classification || step.left_classification || step.right_classification)
    if (hasResult) {
      step.status = 'completed'
    } else if (step.status === 'completed') {
      step.status = 'pending'
    }
  })

  if (painIndex >= 0) {
    workflow.status = 'stopped_due_to_pain'
    return
  }
  const terminal = workflow.steps.every((step) =>
    ['completed', 'skipped', 'not_applicable'].includes(step.status)
  )
  const started = workflow.steps.some((step) => step.status !== 'pending') || !!workflow.note
  workflow.status = terminal ? 'completed' : started ? 'in_progress' : 'not_started'
}

const handleResultChange = (workflow: SfmaBookWorkflowResult) => normalizeWorkflow(workflow)

const handleStatusChange = (workflow: SfmaBookWorkflowResult, definition: SfmaBookStepDefinition) => {
  const result = stepResult(workflow, definition)
  if (result.status === 'skipped' || result.status === 'not_applicable' || result.status === 'pending') {
    result.classification = ''
    result.left_classification = ''
    result.right_classification = ''
  }
  normalizeWorkflow(workflow)
}

const syncTriggersAndStatuses = () => {
  protocolDefinition.value?.workflows.forEach((definition) => {
    const workflow = localData.workflows[definition.code]
    if (!workflow) return
    workflow.trigger_classifications = triggerClassifications(definition)
    normalizeWorkflow(workflow)
  })
  const firstActive = activeWorkflows.value[0]?.definition.code
  if (firstActive && !expandedWorkflows.value.length) {
    expandedWorkflows.value = [firstActive]
  }
}

watch(() => props.topTier, syncTriggersAndStatuses, { deep: true })

const topTierNames: Record<string, string> = {
  cervical_flexion: '颈椎屈曲',
  cervical_extension: '颈椎伸展',
  cervical_rotation_left: '颈椎左旋',
  cervical_rotation_right: '颈椎右旋',
  upper_extremity_pattern1_left: '上肢模式1左',
  upper_extremity_pattern1_right: '上肢模式1右',
  upper_extremity_pattern2_left: '上肢模式2左',
  upper_extremity_pattern2_right: '上肢模式2右',
  multi_segmental_flexion: 'MSF',
  multi_segmental_extension: 'MSE',
  multi_segmental_rotation_left: 'MSR左',
  multi_segmental_rotation_right: 'MSR右',
  single_leg_stance_left: 'SLS左',
  single_leg_stance_right: 'SLS右',
  arms_down_deep_squat: '深蹲'
}

const topTierName = (code: string) => topTierNames[code] || code
const stageLabel = (stage: string) => `${stage} ${stage === 'DN' ? '优先' : stage === 'FP' ? '第二阶段' : '最后阶段'}`
const stageTagType = (stage: string) => (stage === 'DN' ? 'success' : stage === 'FP' ? 'warning' : 'danger')
const classificationTagType = (value: string) =>
  value === 'DN' ? 'success' : value === 'FP' ? 'warning' : value === 'DP' ? 'danger' : 'info'
const workflowStatusLabel = (status: string) =>
  ({
    not_started: '未开始',
    in_progress: '进行中',
    completed: '已完成',
    skipped: '已跳过',
    stopped_due_to_pain: '因疼痛终止'
  })[status] || status
const workflowStatusType = (status: string) =>
  status === 'completed' ? 'success' : status === 'stopped_due_to_pain' ? 'danger' : status === 'in_progress' ? 'warning' : 'info'
</script>

<style scoped>
.book-protocol-card {
  border-color: var(--el-color-success-light-5);
}

.protocol-source,
.step-hint,
.step-condition,
.trigger-line {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.step-condition {
  color: var(--el-color-primary);
}

.workflow-title,
.bilateral-result {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-title {
  flex-wrap: wrap;
}

.bilateral-result > * {
  flex: 1;
  min-width: 0;
}
</style>
