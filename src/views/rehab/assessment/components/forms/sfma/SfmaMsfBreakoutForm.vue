<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">多节段屈曲分解评估（MSF Breakout）</span>
        <el-tag :type="statusTagType" size="small">{{ statusText }}</el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-10px"
      title="教材流程：单腿站立体前屈 → 长坐位触摸足趾 →（按分支）滚动 / ASLR / PSLR / 俯卧位向后摆动 / 仰卧位双膝触胸。"
    />

    <el-alert
      v-if="flowSnapshot.nextStepLabel"
      :closable="false"
      :type="flowAlertType"
      class="mb-10px"
      :title="`当前流程建议：${flowSnapshot.nextStepLabel}`"
    />

    <el-descriptions border :column="2" size="small" class="mb-10px">
      <el-descriptions-item label="当前流程节点">{{ flowSnapshot.activeNodeLabel }}</el-descriptions-item>
      <el-descriptions-item label="是否疼痛中止">{{ flowSnapshot.stopAndTreatPain ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="下一步流转">{{ flowSnapshot.nextStepLabel }}</el-descriptions-item>
      <el-descriptions-item label="综合判断">{{ flowSnapshot.summaryHint }}</el-descriptions-item>
    </el-descriptions>

    <el-form :model="model" label-width="180px">
      <el-collapse v-model="activePanels">
        <el-collapse-item title="A. Breakout 基础状态" name="base">
          <el-form-item label="Breakout 状态">
            <el-radio-group v-model="model.breakout_status" @change="emitChange">
              <el-radio label="not_started">未开始</el-radio>
              <el-radio label="in_progress">进行中</el-radio>
              <el-radio label="completed">已完成</el-radio>
              <el-radio label="skipped">暂不分解</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="Top Tier 建议原因">
            <el-input v-model="model.breakout_reason_from_top_tier" type="textarea" :rows="2" readonly />
          </el-form-item>
          <el-form-item label="Breakout 备注">
            <el-input v-model="model.breakout_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
          <el-form-item label="需人工复核">
            <el-switch v-model="model.needs_manual_review" @change="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="B. 单腿站立体前屈" name="single-leg-forward-flexion">
          <el-alert
            :closable="false"
            type="info"
            class="mb-10px"
            :title="`目的：${nodeMeta.single_leg_stance_forward_bend.purpose}`"
            :description="`说明：${nodeMeta.single_leg_stance_forward_bend.instructions}；附加信息：${nodeMeta.single_leg_stance_forward_bend.clinical_notes}；下一步规则：${nodeMeta.single_leg_stance_forward_bend.next_step_rules}`"
          />
          <el-form-item label="测试结果">
            <el-radio-group v-model="model.single_leg_standing_forward_flexion_result" @change="emitChange">
              <el-radio label="双侧功能正常且无痛">双侧功能正常且无痛</el-radio>
              <el-radio label="双侧功能障碍或疼痛">双侧功能障碍或疼痛</el-radio>
              <el-radio label="单侧功能障碍或疼痛">单侧功能障碍或疼痛</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="对称性观察">
            <el-radio-group v-model="model.single_leg_standing_forward_flexion_asymmetry" @change="emitChange">
              <el-radio label="无明显左右差">无明显左右差</el-radio>
              <el-radio label="左侧更差">左侧更差</el-radio>
              <el-radio label="右侧更差">右侧更差</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="测试备注">
            <el-input
              v-model="model.single_leg_standing_forward_flexion_note"
              type="textarea"
              :rows="2"
              @input="emitChange"
            />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="C. 长坐位触摸足趾（骶骨角判断）" name="long-sit-toe-touch">
          <fieldset
            class="flow-node-fieldset"
            :class="{ 'flow-node-disabled': !isNodeEnabled('long_sit_toe_touch') }"
            :disabled="!isNodeEnabled('long_sit_toe_touch')"
          >
          <el-alert
            :closable="false"
            type="info"
            class="mb-10px"
            :title="`目的：${nodeMeta.long_sit_toe_touch.purpose}`"
            :description="`说明：${nodeMeta.long_sit_toe_touch.instructions}；附加信息：${nodeMeta.long_sit_toe_touch.clinical_notes}；下一步规则：${nodeMeta.long_sit_toe_touch.next_step_rules}`"
          />
          <el-form-item label="长坐触趾结果">
            <el-radio-group v-model="model.long_sit_toe_touch_result" @change="emitChange">
              <el-radio label="FN">FN</el-radio>
              <el-radio label="FP">FP</el-radio>
              <el-radio label="DN">DN</el-radio>
              <el-radio label="DP">DP</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="是否触及足趾">
            <el-radio-group v-model="model.long_sit_toe_touch_reach_status" @change="emitChange">
              <el-radio label="可触及足趾">可触及足趾</el-radio>
              <el-radio label="未触及足趾">未触及足趾</el-radio>
              <el-radio label="未测">未测</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item :label="`骶骨角(°) 阈值≥${model.sacral_angle_threshold_ref}`">
                <el-input-number
                  v-model="model.long_sit_sacral_angle_deg"
                  :min="0"
                  :max="120"
                  :step="1"
                  class="!w-full"
                  @change="emitChange"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="骶骨角状态">
                <el-radio-group v-model="model.long_sit_sacral_angle_status" @change="emitChange">
                  <el-radio label="正常(≥80°)">正常(≥80°)</el-radio>
                  <el-radio label="受限(<80°)">受限(&lt;80°)</el-radio>
                  <el-radio label="未测">未测</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="测试备注">
            <el-input v-model="model.long_sit_toe_touch_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
          </fieldset>
        </el-collapse-item>

        <el-collapse-item title="D. 主动直腿抬高（ASLR）" name="aslr">
          <fieldset
            class="flow-node-fieldset"
            :class="{ 'flow-node-disabled': !isNodeEnabled('active_straight_leg_raise') }"
            :disabled="!isNodeEnabled('active_straight_leg_raise')"
          >
            <el-alert
              :closable="false"
              type="info"
              class="mb-10px"
              :title="`目的：${nodeMeta.active_straight_leg_raise.purpose}`"
              :description="`说明：${nodeMeta.active_straight_leg_raise.instructions}；附加信息：${nodeMeta.active_straight_leg_raise.clinical_notes}；下一步规则：${nodeMeta.active_straight_leg_raise.next_step_rules}`"
            />
            <el-form-item label="ASLR 结果">
              <el-radio-group v-model="model.aslr_result" @change="emitChange">
                <el-radio label="FN">FN</el-radio>
                <el-radio label="FP">FP</el-radio>
                <el-radio label="DN">DN</el-radio>
                <el-radio label="DP">DP</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-row :gutter="8">
              <el-col :span="12">
                <el-form-item :label="`左侧角度(°) 阈值>${model.aslr_threshold_ref}`">
                  <el-input-number v-model="model.aslr_left_deg" :min="0" :max="130" :step="1" class="!w-full" @change="emitChange" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="右侧角度(°)">
                  <el-input-number
                    v-model="model.aslr_right_deg"
                    :min="0"
                    :max="130"
                    :step="1"
                    class="!w-full"
                    @change="emitChange"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="ASLR 备注">
              <el-input v-model="model.aslr_note" type="textarea" :rows="2" @input="emitChange" />
            </el-form-item>
          </fieldset>
        </el-collapse-item>

        <el-collapse-item title="E. 被动直腿抬高（PSLR）" name="pslr">
          <fieldset
            class="flow-node-fieldset"
            :class="{ 'flow-node-disabled': !isNodeEnabled('passive_straight_leg_raise') }"
            :disabled="!isNodeEnabled('passive_straight_leg_raise')"
          >
            <el-alert
              :closable="false"
              type="info"
              class="mb-10px"
              :title="`目的：${nodeMeta.passive_straight_leg_raise.purpose}`"
              :description="`说明：${nodeMeta.passive_straight_leg_raise.instructions}；附加信息：${nodeMeta.passive_straight_leg_raise.clinical_notes}；下一步规则：${nodeMeta.passive_straight_leg_raise.next_step_rules}`"
            />
            <el-form-item label="PSLR 结果">
              <el-radio-group v-model="model.pslr_result" @change="emitChange">
                <el-radio label="FN">FN</el-radio>
                <el-radio label="FP">FP</el-radio>
                <el-radio label="DN">DN</el-radio>
                <el-radio label="DP">DP</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-row :gutter="8">
              <el-col :span="12">
                <el-form-item :label="`左侧角度(°) 阈值>${model.pslr_threshold_ref}`">
                  <el-input-number v-model="model.pslr_left_deg" :min="0" :max="130" :step="1" class="!w-full" @change="emitChange" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="右侧角度(°)">
                  <el-input-number
                    v-model="model.pslr_right_deg"
                    :min="0"
                    :max="130"
                    :step="1"
                    class="!w-full"
                    @change="emitChange"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="PSLR 对比 ASLR">
              <el-radio-group v-model="model.pslr_vs_aslr_interpretation" @change="emitChange">
                <el-radio label="PSLR>80°">PSLR&gt;80°</el-radio>
                <el-radio label="PSLR<80°且比ASLR大10°以上">PSLR&lt;80°且比ASLR大10°以上</el-radio>
                <el-radio label="PSLR≤ASLR">PSLR≤ASLR</el-radio>
                <el-radio label="未判断">未判断</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="PSLR 备注">
              <el-input v-model="model.pslr_note" type="textarea" :rows="2" @input="emitChange" />
            </el-form-item>
          </fieldset>
        </el-collapse-item>

        <el-collapse-item title="F. 俯卧位向后摆动" name="prone-rock-back">
          <fieldset
            class="flow-node-fieldset"
            :class="{ 'flow-node-disabled': !isNodeEnabled('prone_backward_rocking') }"
            :disabled="!isNodeEnabled('prone_backward_rocking')"
          >
            <el-alert
              :closable="false"
              type="info"
              class="mb-10px"
              :title="`目的：${nodeMeta.prone_backward_rocking.purpose}`"
              :description="`说明：${nodeMeta.prone_backward_rocking.instructions}；附加信息：${nodeMeta.prone_backward_rocking.clinical_notes}；下一步规则：${nodeMeta.prone_backward_rocking.next_step_rules}`"
            />
            <el-form-item label="向后摆动结果">
              <el-radio-group v-model="model.prone_rock_back_result" @change="emitChange">
                <el-radio label="FN">FN</el-radio>
                <el-radio label="FP">FP</el-radio>
                <el-radio label="DN">DN</el-radio>
                <el-radio label="DP">DP</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="测试备注">
              <el-input v-model="model.prone_rock_back_note" type="textarea" :rows="2" @input="emitChange" />
            </el-form-item>
          </fieldset>
        </el-collapse-item>

        <el-collapse-item title="G. 仰卧位双膝触胸" name="supine-knees-to-chest">
          <fieldset
            class="flow-node-fieldset"
            :class="{ 'flow-node-disabled': !isNodeEnabled('supine_double_knees_to_chest') }"
            :disabled="!isNodeEnabled('supine_double_knees_to_chest')"
          >
            <el-alert
              :closable="false"
              type="info"
              class="mb-10px"
              :title="`目的：${nodeMeta.supine_double_knees_to_chest.purpose}`"
              :description="`说明：${nodeMeta.supine_double_knees_to_chest.instructions}；附加信息：${nodeMeta.supine_double_knees_to_chest.clinical_notes}；下一步规则：${nodeMeta.supine_double_knees_to_chest.next_step_rules}`"
            />
            <el-form-item label="双膝触胸结果">
              <el-radio-group v-model="model.supine_knees_to_chest_result" @change="emitChange">
                <el-radio label="FN">FN</el-radio>
                <el-radio label="FP">FP</el-radio>
                <el-radio label="DN">DN</el-radio>
                <el-radio label="DP">DP</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="测试备注">
              <el-input v-model="model.supine_knees_to_chest_note" type="textarea" :rows="2" @input="emitChange" />
            </el-form-item>
          </fieldset>
        </el-collapse-item>

        <el-collapse-item title="H. 滚动解析测试" name="rolling">
          <fieldset
            class="flow-node-fieldset"
            :class="{ 'flow-node-disabled': !isNodeEnabled('rolling_analysis_result') }"
            :disabled="!isNodeEnabled('rolling_analysis_result')"
          >
          <el-alert
            :closable="false"
            type="info"
            class="mb-10px"
            :title="`目的：${nodeMeta.rolling_analysis_result.purpose}`"
            :description="`说明：${nodeMeta.rolling_analysis_result.instructions}；附加信息：${nodeMeta.rolling_analysis_result.clinical_notes}；下一步规则：${nodeMeta.rolling_analysis_result.next_step_rules}`"
          />
          <el-form-item label="滚动结果">
            <el-radio-group v-model="model.rolling_result" @change="emitChange">
              <el-radio label="FN">FN</el-radio>
              <el-radio label="FP">FP</el-radio>
              <el-radio label="DN">DN</el-radio>
              <el-radio label="DP">DP</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="滚动备注">
            <el-input v-model="model.rolling_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
          </fieldset>
        </el-collapse-item>

        <el-collapse-item title="I. 自动判断方向（系统生成）" name="direction-auto">
          <el-alert
            :closable="false"
            type="info"
            class="mb-10px"
            title="该区块由流程节点结果自动生成。请先按流程录入 B~G 节点，再查看自动判断方向。"
          />
          <el-descriptions border :column="1" size="small">
            <el-descriptions-item label="主要区域">
              {{ currentFlowState.analysis.summary.primary_region || '待生成' }}
            </el-descriptions-item>
            <el-descriptions-item label="判断方向">
              <template v-if="currentFlowState.analysis.summary.likely_pattern?.length">
                <el-tag
                  v-for="item in currentFlowState.analysis.summary.likely_pattern"
                  :key="item"
                  class="mr-6px mb-6px"
                  size="small"
                >
                  {{ item }}
                </el-tag>
              </template>
              <span v-else>待生成</span>
            </el-descriptions-item>
            <el-descriptions-item label="进入旋转动作解析">
              {{ currentFlowState.analysis.summary.rotation_flow_needed ? '是' : '否' }}
            </el-descriptions-item>
            <el-descriptions-item label="是否优先疼痛处理">
              {{ currentFlowState.analysis.summary.stop_and_treat_pain ? '是' : '否' }}
            </el-descriptions-item>
            <el-descriptions-item label="是否需人工复核">
              {{ currentFlowState.analysis.summary.manual_review_required ? '是' : '否' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-collapse-item>

        <el-collapse-item title="J. Breakout 汇总（人工补充）" name="summary">
          <el-form-item label="流程算法说明">
            <el-input
              v-model="model.flow_algorithm_note"
              type="textarea"
              :rows="2"
              placeholder="记录本次分支判断依据（例如：长坐触趾FP且骶骨角受限，转入ASLR/PSLR）"
              @input="emitChange"
            />
          </el-form-item>
          <el-form-item label="系统汇总文本（可编辑补充）">
            <el-input v-model="model.breakout_summary_text" type="textarea" :rows="3" @input="emitChange" />
          </el-form-item>
          <el-form-item label="功能学意义（可选）">
            <el-input v-model="model.clinical_meaning_hint" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
          <el-form-item label="训练取向（可选）">
            <el-input v-model="model.training_direction_hint" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="复评优先级">
                <el-radio-group v-model="model.reassessment_priority" @change="emitChange">
                  <el-radio label="low">low</el-radio>
                  <el-radio label="medium">medium</el-radio>
                  <el-radio label="high">high</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="暂停/转介提示">
                <el-radio-group v-model="model.pause_or_referral_hint" @change="emitChange">
                  <el-radio label="无需">无需</el-radio>
                  <el-radio label="建议优先人工复核">建议优先人工复核</el-radio>
                  <el-radio label="建议结合进一步医学评估">建议结合进一步医学评估</el-radio>
                  <el-radio label="建议暂缓推进训练">建议暂缓推进训练</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
          <div class="mt-8px">
            <el-button type="success" @click="markCompleted">标记为已完成</el-button>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-form>
  </el-card>
</template>

<script lang="ts" setup>
import { computed, reactive, ref, watch } from 'vue'
import {
  buildDefaultMsfAnalysis,
  buildDefaultMsfBreakout,
  SfmaMsfBreakout
} from '@/views/rehab/assessment/config/sfmaConfig'
import {
  hasMsfNodeValue,
  MsfFlowNodeCode,
  MSF_FLOW_NODE_ORDER,
  MSF_NODE_PANEL_MAP,
  runMsfAnalysisFlowEngine
} from './msfAnalysisFlowEngine'

const props = defineProps<{ modelValue?: SfmaMsfBreakout }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: SfmaMsfBreakout): void
  (e: 'change', value: SfmaMsfBreakout): void
}>()

const deepClone = (value: any) => JSON.parse(JSON.stringify(value || {}))
const nodeMeta = buildDefaultMsfAnalysis().flow_nodes
const model = reactive<SfmaMsfBreakout>(buildDefaultMsfBreakout())
const activePanels = ref<string[]>([
  'base',
  'single-leg-forward-flexion',
  'long-sit-toe-touch',
  'aslr',
  'pslr',
  'prone-rock-back',
  'supine-knees-to-chest',
  'rolling',
  'direction-auto',
  'summary'
])

const syncFromProps = (value?: SfmaMsfBreakout) => {
  const merged = {
    ...buildDefaultMsfBreakout(),
    ...(value || {})
  }
  Object.assign(model, normalizeBeforeEmit(merged))
}

const statusText = computed(() => {
  const statusMap: Record<string, string> = {
    not_started: '未开始',
    in_progress: '进行中',
    completed: '已完成',
    skipped: '暂不分解'
  }
  return statusMap[model.breakout_status] || '未开始'
})

const statusTagType = computed(() => {
  if (model.breakout_status === 'completed') return 'success'
  if (model.breakout_status === 'in_progress') return 'warning'
  if (model.breakout_status === 'skipped') return 'info'
  return 'info'
})

const flowAlertType = computed(() => {
  if (flowSnapshot.value.nextStepLabel === '停止并优先处理疼痛') return 'error'
  if (flowSnapshot.value.nextStepLabel === '需结合人工复核判断下一步') return 'warning'
  if (flowSnapshot.value.nextStepLabel === '流程已完成') return 'success'
  return 'info'
})

const hasAnyFlowInput = (payload: SfmaMsfBreakout) => {
  return Boolean(
    payload.single_leg_standing_forward_flexion_result ||
      payload.long_sit_toe_touch_result ||
      payload.rolling_result ||
      payload.aslr_result ||
      payload.pslr_result ||
      payload.prone_rock_back_result ||
      payload.supine_knees_to_chest_result ||
      payload.breakout_summary_text ||
      payload.flow_algorithm_note
  )
}

const hasPainResult = (payload: SfmaMsfBreakout) => {
  const anyResultWithPain = [
    payload.long_sit_toe_touch_result,
    payload.rolling_result,
    payload.aslr_result,
    payload.pslr_result,
    payload.prone_rock_back_result,
    payload.supine_knees_to_chest_result
  ].some((item) => item === 'FP' || item === 'DP')
  return (
    anyResultWithPain ||
    payload.active_flexion_pain ||
    payload.pain_dominant_pattern === '疑似是' ||
    payload.pain_dominant_pattern === '明显是' ||
    payload.pain_control_priority_hint === '是，建议优先人工复核'
  )
}

const buildFlowState = (payload: SfmaMsfBreakout) => runMsfAnalysisFlowEngine(payload)

const currentFlowState = computed(() => buildFlowState(model))
const isNodeEnabled = (node: MsfFlowNodeCode) => currentFlowState.value.enabledNodes.has(node)

const isNodeCompletedInState = (node: MsfFlowNodeCode) => {
  const flowNode = currentFlowState.value.analysis.flow_nodes[node]
  return Boolean(String(flowNode?.result_code || flowNode?.result_type || '').trim())
}

const getPendingNode = () =>
  MSF_FLOW_NODE_ORDER.find((item) => currentFlowState.value.enabledNodes.has(item.code) && !isNodeCompletedInState(item.code))

const buildActivePanels = () => {
  const panels = new Set<string>([
    'base',
    'single-leg-forward-flexion',
    'long-sit-toe-touch',
    'aslr',
    'pslr',
    'prone-rock-back',
    'supine-knees-to-chest',
    'rolling',
    'direction-auto',
    'summary'
  ])
  const pending = getPendingNode()
  if (pending) {
    panels.add(MSF_NODE_PANEL_MAP[pending.code])
  }
  return Array.from(panels)
}

const flowSnapshot = computed(() => {
  const state = currentFlowState.value
  const pending = getPendingNode()
  const hasAnyInput = MSF_FLOW_NODE_ORDER.some((item) => hasMsfNodeValue(model, item.code))
  const activeNodeLabel =
    pending?.label ||
    (state.nextStep === '继续进入旋转动作解析'
      ? '当前分支已完成，建议进入旋转动作解析'
      : hasAnyInput
        ? '流程已完成'
        : '单腿站立体前屈')
  return {
    activeNodeLabel,
    stopAndTreatPain: state.stopAndTreatPain,
    nextStepLabel: state.nextStep || '待录入',
    summaryHint: state.analysis.summary.summary_text || '待生成'
  }
})

watch(
  [currentFlowState, () => model.breakout_status],
  () => {
    activePanels.value = buildActivePanels()
  },
  { immediate: true, deep: true }
)

function normalizeBeforeEmit(value: SfmaMsfBreakout): SfmaMsfBreakout {
  const payload: SfmaMsfBreakout = {
    ...buildDefaultMsfBreakout(),
    ...deepClone(value)
  }
  const state = buildFlowState(payload)
  payload.msf_analysis = state.analysis
  payload.flow_next_step = state.nextStep as SfmaMsfBreakout['flow_next_step']
  payload.flow_algorithm_note = state.analysis.summary.summary_text
  if (!payload.breakout_summary_text) {
    payload.breakout_summary_text = state.analysis.summary.summary_text
  }
  if (payload.breakout_status === 'not_started' && hasAnyFlowInput(payload)) {
    payload.breakout_status = 'in_progress'
  }
  if (hasPainResult(payload) || state.stopAndTreatPain || state.analysis.summary.manual_review_required) {
    payload.needs_manual_review = true
  }
  return payload
}

const emitModel = () => {
  const payload = normalizeBeforeEmit(model)
  Object.assign(model, payload)
  emit('update:modelValue', payload)
  emit('change', payload)
}

const emitChange = () => {
  emitModel()
}

const markCompleted = () => {
  const next = deepClone(model)
  next.breakout_status = 'completed'
  Object.assign(model, normalizeBeforeEmit(next))
  emitModel()
}

watch(
  () => props.modelValue,
  (value) => syncFromProps(value),
  { immediate: true }
)

const validate = async () => true
const getFormData = () => deepClone(model)
const reset = () => {
  Object.assign(model, buildDefaultMsfBreakout())
  emitModel()
}

defineExpose({ validate, getFormData, reset })
</script>

<style scoped>
.flow-node-fieldset {
  border: 0;
  margin: 0;
  min-inline-size: 0;
  padding: 0;
}

.flow-node-disabled {
  opacity: 0.55;
}
</style>
