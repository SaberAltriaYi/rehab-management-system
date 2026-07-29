<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">上肢模式2 Breakout（左/右分解）</span>
        <el-tag :type="allCompleted ? 'success' : 'warning'" size="small">
          {{ allCompleted ? '左右已完成' : '进行中/待完善' }}
        </el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-10px"
      title="上肢动作模式受限时按教材流程解析：俯卧位主动动作模式 → 俯卧位被动动作模式 → 仰卧位上肢交互动作模式。"
    />

    <el-row :gutter="12">
      <el-col v-for="side in sides" :key="side" :span="12">
        <el-card shadow="never">
          <template #header>上肢模式2 Breakout（{{ sideText(side) }}）</template>
          <el-form :model="model[side]" label-width="154px">
            <el-form-item label="状态">
              <el-select v-model="model[side].breakout_status" class="!w-full" @change="emitChange(side)">
                <el-option label="未开始" value="not_started" />
                <el-option label="进行中" value="in_progress" />
                <el-option label="已完成" value="completed" />
                <el-option label="暂不分解" value="skipped" />
              </el-select>
            </el-form-item>
            <el-form-item label="分解备注">
              <el-input v-model="model[side].breakout_note" @input="emitChange(side)" />
            </el-form-item>

            <el-divider content-position="left">俯卧位上肢主动动作模式</el-divider>
            <el-form-item label="主动测试结果">
              <el-radio-group v-model="model[side].prone_active_result" @change="emitChange(side)">
                <el-radio v-for="item in classOptions" :key="`ue2-active-${side}-${item}`" :label="item">{{ item }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="主动疼痛VAS">
              <el-input-number
                v-model="model[side].prone_active_pain_vas"
                :min="0"
                :max="10"
                class="!w-full"
                @change="emitChange(side)"
              />
            </el-form-item>
            <el-form-item label="主动测试备注">
              <el-input v-model="model[side].prone_active_note" type="textarea" :rows="2" @input="emitChange(side)" />
            </el-form-item>

            <el-divider content-position="left">俯卧位上肢被动动作模式</el-divider>
            <el-alert
              v-if="!needsPassiveTest(model[side])"
              type="success"
              :closable="false"
              class="mb-10px"
              title="主动测试为 FN 时通常无需继续被动分解，可重点考虑姿势性与肩带稳定控制因素。"
            />
            <template v-else>
              <el-form-item label="被动测试结果">
                <el-radio-group v-model="model[side].prone_passive_result" @change="emitChange(side)">
                  <el-radio v-for="item in classOptions" :key="`ue2-passive-${side}-${item}`" :label="item">{{ item }}</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="被动疼痛VAS">
                <el-input-number
                  v-model="model[side].prone_passive_pain_vas"
                  :min="0"
                  :max="10"
                  class="!w-full"
                  @change="emitChange(side)"
                />
              </el-form-item>
              <el-form-item label="被动测试备注">
                <el-input v-model="model[side].prone_passive_note" type="textarea" :rows="2" @input="emitChange(side)" />
              </el-form-item>
            </template>

            <el-divider content-position="left">仰卧位上肢交互动作模式</el-divider>
            <el-alert
              v-if="!needsSupineTest(model[side])"
              type="warning"
              :closable="false"
              class="mb-10px"
              title="仅当“俯卧位被动动作模式 = FN”时建议继续做仰卧位交互动作模式。"
            />
            <template v-else>
              <el-form-item label="交互测试结果">
                <el-radio-group v-model="model[side].supine_interactive_result" @change="emitChange(side)">
                  <el-radio v-for="item in classOptions" :key="`ue2-supine-${side}-${item}`" :label="item">{{ item }}</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="交互疼痛VAS">
                <el-input-number
                  v-model="model[side].supine_interactive_pain_vas"
                  :min="0"
                  :max="10"
                  class="!w-full"
                  @change="emitChange(side)"
                />
              </el-form-item>
              <el-form-item label="交互测试备注">
                <el-input
                  v-model="model[side].supine_interactive_note"
                  type="textarea"
                  :rows="2"
                  @input="emitChange(side)"
                />
              </el-form-item>
            </template>

            <el-alert
              :closable="false"
              type="info"
              class="mb-10px"
              :title="flowHint(model[side])"
            />

            <el-divider content-position="left">初步方向与兼容汇总</el-divider>
            <el-form-item label="分解方向">
              <el-checkbox-group v-model="model[side].breakout_preliminary_direction" @change="emitChange(side)">
                <el-checkbox label="更偏活动度限制" />
                <el-checkbox label="更偏疼痛主导" />
                <el-checkbox label="更偏运动控制问题" />
                <el-checkbox label="需进一步肩带/胸椎解析" />
                <el-checkbox label="需结合其他模式综合判断" />
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="流程建议">
              <el-input v-model="model[side].flow_recommendation_text" type="textarea" :rows="2" @input="emitChange(side)" />
            </el-form-item>
            <el-form-item label="需局部生物力学测试">
              <el-switch v-model="model[side].local_biomechanics_needed" @change="emitChange(side)" />
            </el-form-item>
            <el-form-item label="疼痛停止测试">
              <el-switch v-model="model[side].stop_and_treat" @change="emitChange(side)" />
            </el-form-item>
            <el-form-item label="总结">
              <el-input v-model="model[side].breakout_summary_text" type="textarea" :rows="2" @input="emitChange(side)" />
            </el-form-item>
            <el-form-item label="需人工复核">
              <el-switch v-model="model[side].needs_manual_review" @change="emitChange(side)" />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-divider />
    <el-form :model="model" label-width="138px">
      <el-form-item label="左右差重点">
        <el-input
          v-model="model.asymmetry_focus"
          type="textarea"
          :rows="2"
          placeholder="例如：右侧模式2在被动与交互阶段均明显差于左侧。"
          @input="emitChange()"
        />
      </el-form-item>
      <el-form-item label="专项总备注">
        <el-input v-model="model.overall_note" type="textarea" :rows="2" @input="emitChange()" />
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import {
  buildDefaultUpperExtremityPattern2Breakout,
  SfmaClassification,
  SfmaUpperExtremityPattern2Breakout,
  SfmaUpperExtremityPattern2BreakoutSide
} from '@/views/rehab/assessment/config/sfmaConfig'

type SideKey = 'left' | 'right'

const sides: SideKey[] = ['left', 'right']
const classOptions: SfmaClassification[] = ['FN', 'FP', 'DN', 'DP']

const props = defineProps<{
  modelValue?: SfmaUpperExtremityPattern2Breakout
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: SfmaUpperExtremityPattern2Breakout): void
  (e: 'change', value: SfmaUpperExtremityPattern2Breakout): void
}>()

const deepClone = (value: any) => JSON.parse(JSON.stringify(value || {}))

const mergeWithDefaults = (value?: SfmaUpperExtremityPattern2Breakout): SfmaUpperExtremityPattern2Breakout => {
  const defaults = buildDefaultUpperExtremityPattern2Breakout()
  const input = value || ({} as SfmaUpperExtremityPattern2Breakout)
  return {
    ...defaults,
    ...input,
    left: {
      ...defaults.left,
      ...(input.left || {})
    },
    right: {
      ...defaults.right,
      ...(input.right || {})
    }
  }
}

const model = computed<SfmaUpperExtremityPattern2Breakout>({
  get: () => mergeWithDefaults(props.modelValue),
  set: (value) => {
    const payload = mergeWithDefaults(deepClone(value || {}))
    emit('update:modelValue', payload)
    emit('change', payload)
  }
})

const allCompleted = computed(() => {
  return model.value.left.breakout_status === 'completed' && model.value.right.breakout_status === 'completed'
})

const sideText = (side: SideKey) => (side === 'left' ? '左' : '右')

const isPainClass = (value?: string) => value === 'FP' || value === 'DP'
const isAbnormalClass = (value?: string) => value === 'DN' || value === 'DP' || value === 'FP'

const needsPassiveTest = (sideData: SfmaUpperExtremityPattern2BreakoutSide) => {
  return isAbnormalClass(sideData.prone_active_result)
}

const needsSupineTest = (sideData: SfmaUpperExtremityPattern2BreakoutSide) => {
  return needsPassiveTest(sideData) && sideData.prone_passive_result === 'FN'
}

const mapActiveQuality = (classification: SfmaClassification): '' | '正常' | '受限' | '明显受限' | '无法完成' => {
  if (classification === 'FN') return '正常'
  if (classification === 'DN') return '受限'
  if (classification === 'FP') return '受限'
  if (classification === 'DP') return '明显受限'
  return ''
}

const flowHint = (sideData: SfmaUpperExtremityPattern2BreakoutSide) => {
  if (!sideData.prone_active_result) return '请先完成“俯卧位上肢主动动作模式”结果判定。'
  if (sideData.prone_active_result === 'FN') {
    return '主动测试为 FN：提示更偏姿势性和/或肩带稳定-运动控制问题，可进入训练控制分析。'
  }
  if (!sideData.prone_passive_result) return '主动测试为 DN/DP/FP：建议继续进行“俯卧位上肢被动动作模式”。'
  if (sideData.prone_passive_result === 'DN') {
    return '被动测试为 DN：提示肩带关节灵活性或组织延展性受限，建议补充局部生物力学测试。'
  }
  if (isPainClass(sideData.prone_passive_result)) {
    return '被动测试为 DP/FP：测试中疼痛，建议优先处理疼痛并进行人工复核。'
  }
  if (!sideData.supine_interactive_result) {
    return '被动测试为 FN：建议继续进行“仰卧位上肢交互动作模式”。'
  }
  if (sideData.supine_interactive_result === 'FN') {
    return '交互测试为 FN：提示中段更偏肩胛/盂肱稳定或运动控制问题。'
  }
  if (sideData.supine_interactive_result === 'DN') {
    return '交互测试为 DN：提示末端稳定与运动控制功能障碍倾向，建议继续功能训练。'
  }
  return '交互测试为 DP/FP：测试伴疼痛，建议停止推进并优先疼痛管理与人工复核。'
}

const syncSideDerived = (sideData: SfmaUpperExtremityPattern2BreakoutSide) => {
  sideData.active_ue_pattern2_quality = mapActiveQuality(sideData.prone_active_result)
  sideData.active_ue_pattern2_pain = isPainClass(sideData.prone_active_result)

  const autoDirections: string[] = []
  if (sideData.prone_passive_result === 'DN') autoDirections.push('更偏活动度限制')
  if (sideData.prone_active_result === 'FN' || sideData.supine_interactive_result === 'DN' || sideData.supine_interactive_result === 'FN') {
    autoDirections.push('更偏运动控制问题')
  }
  if (isPainClass(sideData.prone_active_result) || isPainClass(sideData.prone_passive_result) || isPainClass(sideData.supine_interactive_result)) {
    autoDirections.push('更偏疼痛主导')
  }

  if (!sideData.breakout_preliminary_direction.length && autoDirections.length) {
    sideData.breakout_preliminary_direction = [...new Set(autoDirections)] as any
  }

  const hint = flowHint(sideData)
  if (!sideData.flow_recommendation_text) {
    sideData.flow_recommendation_text = hint
  }

  sideData.local_biomechanics_needed = sideData.prone_passive_result === 'DN' || sideData.local_biomechanics_needed
  sideData.stop_and_treat =
    isPainClass(sideData.prone_passive_result) || isPainClass(sideData.supine_interactive_result) || sideData.stop_and_treat

  const hasPain = sideData.active_ue_pattern2_pain || sideData.stop_and_treat
  if (hasPain) {
    sideData.needs_manual_review = true
  }

  const hasAnyStep =
    !!sideData.prone_active_result || !!sideData.prone_passive_result || !!sideData.supine_interactive_result
  const terminal =
    sideData.prone_active_result === 'FN' ||
    (needsPassiveTest(sideData) &&
      (!!sideData.prone_passive_result &&
        (sideData.prone_passive_result === 'DN' ||
          isPainClass(sideData.prone_passive_result) ||
          (sideData.prone_passive_result === 'FN' && !!sideData.supine_interactive_result))))
  if (sideData.breakout_status !== 'skipped') {
    sideData.breakout_status = terminal ? 'completed' : hasAnyStep ? 'in_progress' : 'not_started'
  }

  if (!sideData.breakout_summary_text && terminal) {
    sideData.breakout_summary_text = hint
  }
}

const emitChange = (side?: SideKey) => {
  const next = deepClone(model.value) as SfmaUpperExtremityPattern2Breakout
  if (side) {
    syncSideDerived(next[side])
  } else {
    syncSideDerived(next.left)
    syncSideDerived(next.right)
  }
  model.value = next
}

const validate = async () => true
const getFormData = () => deepClone(model.value)
const reset = () => {
  model.value = buildDefaultUpperExtremityPattern2Breakout()
}

defineExpose({ validate, getFormData, reset })
</script>
