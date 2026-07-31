<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">多节段旋转分解评估（{{ sideLabel }}）</span>
        <el-tag :type="statusTagType" size="small">{{ statusText }}</el-tag>
      </div>
    </template>

    <el-form :model="model" label-width="188px">
      <el-collapse accordion>
        <el-collapse-item title="A. Breakout 基础状态" name="base">
          <el-form-item label="Breakout 状态">
            <el-radio-group v-model="model.breakout_status" @change="emitChange">
              <el-radio label="not_started">未开始</el-radio>
              <el-radio label="in_progress">进行中</el-radio>
              <el-radio label="completed">已完成</el-radio>
              <el-radio label="skipped">暂不分解</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="旋转方向">
            <el-input :model-value="sideLabel" readonly />
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

        <el-collapse-item title="B. 主动旋转整体表现" name="active">
          <el-form-item label="整体质量">
            <el-radio-group v-model="model.active_rotation_global_quality" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="轻度受限">轻度受限</el-radio>
              <el-radio label="明显受限">明显受限</el-radio>
              <el-radio label="无法完成">无法完成</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="动作中疼痛">
            <el-switch v-model="model.active_rotation_pain" @change="handlePainChange" />
          </el-form-item>
          <el-form-item label="疼痛区域">
            <el-checkbox-group v-model="model.active_rotation_pain_area" @change="emitChange">
              <el-checkbox label="颈肩" />
              <el-checkbox label="胸背" />
              <el-checkbox label="腰背" />
              <el-checkbox label="髋" />
              <el-checkbox label="骨盆周围" />
              <el-checkbox label="下肢支撑侧" />
              <el-checkbox label="其他" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item v-if="model.active_rotation_pain_area.includes('其他')" label="其他疼痛区域">
            <el-input v-model="model.active_rotation_pain_other_note" @input="emitChange" />
          </el-form-item>
          <el-form-item label="动作质量备注">
            <el-input v-model="model.global_rotation_quality_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
          <el-form-item label="关键旋转量化">
            <el-input-number v-model="model.rotation_range_key" :min="0" :max="180" class="!w-full" @change="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="C. 下肢与支撑基础观察" name="lower-extremity">
          <el-form-item label="站姿稳定观察">
            <el-radio-group v-model="model.stance_stability_observation" @change="emitChange">
              <el-radio label="稳定">稳定</el-radio>
              <el-radio label="轻度不稳">轻度不稳</el-radio>
              <el-radio label="明显不稳">明显不稳</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="足踝支撑影响">
            <el-radio-group v-model="model.ankle_foot_support_influence" @change="emitChange">
              <el-radio label="不明显">不明显</el-radio>
              <el-radio label="疑似有影响">疑似有影响</el-radio>
              <el-radio label="明显有影响">明显有影响</el-radio>
              <el-radio label="未测">未测</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="下肢负重左右差">
            <el-radio-group v-model="model.lower_extremity_loading_asymmetry" @change="emitChange">
              <el-radio label="无明显左右差">无明显左右差</el-radio>
              <el-radio label="左侧支撑更差">左侧支撑更差</el-radio>
              <el-radio label="右侧支撑更差">右侧支撑更差</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="膝控制影响">
            <el-radio-group v-model="model.knee_control_influence" @change="emitChange">
              <el-radio label="不明显">不明显</el-radio>
              <el-radio label="疑似有影响">疑似有影响</el-radio>
              <el-radio label="明显有影响">明显有影响</el-radio>
              <el-radio label="未测">未测</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="下肢备注">
            <el-input v-model="model.lower_extremity_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="D. 髋 / 骨盆参与观察" name="hip-pelvis">
          <el-form-item label="髋旋转参与">
            <el-radio-group v-model="model.hip_rotation_contribution" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="减少">减少</el-radio>
              <el-radio label="明显不足">明显不足</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="骨盆旋转控制">
            <el-radio-group v-model="model.pelvis_rotation_control" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="控制不足">控制不足</el-radio>
              <el-radio label="代偿明显">代偿明显</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="髋骨盆分离能力">
            <el-radio-group v-model="model.hip_pelvis_dissociation" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="疑似差">疑似差</el-radio>
              <el-radio label="明显差">明显差</el-radio>
              <el-radio label="未测">未测</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="髋旋转左右差">
            <el-radio-group v-model="model.left_right_hip_rotation_asymmetry" @change="emitChange">
              <el-radio label="无明显左右差">无明显左右差</el-radio>
              <el-radio label="左侧受限更明显">左侧受限更明显</el-radio>
              <el-radio label="右侧受限更明显">右侧受限更明显</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="髋骨盆备注">
            <el-input v-model="model.hip_pelvis_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="E. 脊柱 / 胸廓旋转参与观察" name="spine-thorax">
          <el-form-item label="胸椎旋转参与">
            <el-radio-group v-model="model.thoracic_rotation_participation" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="减少">减少</el-radio>
              <el-radio label="明显不足">明显不足</el-radio>
              <el-radio label="过度代偿">过度代偿</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="腰椎旋转参与">
            <el-radio-group v-model="model.lumbar_rotation_participation" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="减少">减少</el-radio>
              <el-radio label="过度代偿">过度代偿</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="旋转分布观察">
            <el-radio-group v-model="model.rotation_distribution_observation" @change="emitChange">
              <el-radio label="分布协调">分布协调</el-radio>
              <el-radio label="偏腰椎代偿">偏腰椎代偿</el-radio>
              <el-radio label="偏胸椎不足">偏胸椎不足</el-radio>
              <el-radio label="整体僵硬">整体僵硬</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="胸廓骨盆耦合">
            <el-radio-group v-model="model.thorax_pelvis_coupling_observation" @change="emitChange">
              <el-radio label="协调">协调</el-radio>
              <el-radio label="欠协调">欠协调</el-radio>
              <el-radio label="明显失衡">明显失衡</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="脊柱胸廓备注">
            <el-input v-model="model.spine_thorax_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="F. 肩带 / 上肢参与观察" name="shoulder-upper">
          <el-form-item label="肩带参与">
            <el-radio-group v-model="model.shoulder_girdle_participation" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="减少">减少</el-radio>
              <el-radio label="明显不足">明显不足</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="上肢辅助模式">
            <el-radio-group v-model="model.upper_extremity_assist_pattern" @change="emitChange">
              <el-radio label="自然">自然</el-radio>
              <el-radio label="代偿明显">代偿明显</el-radio>
              <el-radio label="左右不对称">左右不对称</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="肩带胸廓联动">
            <el-radio-group v-model="model.shoulder_thorax_link_observation" @change="emitChange">
              <el-radio label="协调">协调</el-radio>
              <el-radio label="欠协调">欠协调</el-radio>
              <el-radio label="明显失衡">明显失衡</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="肩带上肢备注">
            <el-input v-model="model.shoulder_upper_extremity_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="G. 代偿与疼痛特征" name="compensation-pain">
          <el-form-item label="代偿模式">
            <el-checkbox-group v-model="model.compensation_patterns" @change="emitChange">
              <el-checkbox label="骨盆提前旋转" />
              <el-checkbox label="腰椎代偿旋转" />
              <el-checkbox label="胸廓旋转不足" />
              <el-checkbox label="肩带代偿" />
              <el-checkbox label="下肢支撑偏移" />
              <el-checkbox label="左右偏移" />
              <el-checkbox label="重心转移异常" />
              <el-checkbox label="颈部代偿" />
              <el-checkbox label="其他" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item v-if="model.compensation_patterns.includes('其他')" label="其他代偿说明">
            <el-input v-model="model.compensation_other_note" @input="emitChange" />
          </el-form-item>
          <el-form-item label="疼痛主导模式">
            <el-radio-group v-model="model.pain_dominant_pattern" @change="handlePainChange">
              <el-radio label="否">否</el-radio>
              <el-radio label="疑似是">疑似是</el-radio>
              <el-radio label="明显是">明显是</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="症状激惹程度">
            <el-radio-group v-model="model.symptom_irritability" @change="emitChange">
              <el-radio label="低">低</el-radio>
              <el-radio label="中">中</el-radio>
              <el-radio label="高">高</el-radio>
              <el-radio label="不明确">不明确</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="疼痛控制优先提示">
            <el-radio-group v-model="model.pain_control_priority_hint" @change="handlePainChange">
              <el-radio label="否">否</el-radio>
              <el-radio label="是，建议优先疼痛管理">是，建议优先疼痛管理</el-radio>
              <el-radio label="是，建议优先人工复核">是，建议优先人工复核</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="H. 方向性与左右差判断" name="direction">
          <el-form-item label="初步方向">
            <el-checkbox-group v-model="model.breakout_preliminary_direction" @change="emitChange">
              <el-checkbox label="更偏活动度限制" />
              <el-checkbox label="更偏髋旋转参与不足" />
              <el-checkbox label="更偏骨盆旋转控制差" />
              <el-checkbox label="更偏胸椎旋转不足" />
              <el-checkbox label="更偏腰椎代偿" />
              <el-checkbox label="更偏运动控制问题" />
              <el-checkbox label="更偏疼痛主导" />
              <el-checkbox label="需结合其他模式综合判断" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="主要限制链条">
            <el-checkbox-group v-model="model.primary_restriction_chain" @change="emitChange">
              <el-checkbox label="足踝-下肢支撑链" />
              <el-checkbox label="髋-骨盆旋转链" />
              <el-checkbox label="腰椎-骨盆代偿链" />
              <el-checkbox label="胸椎-胸廓旋转链" />
              <el-checkbox label="肩带-胸廓链" />
              <el-checkbox label="暂不明确" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="主要控制障碍链条">
            <el-checkbox-group v-model="model.primary_control_deficit_chain" @change="emitChange">
              <el-checkbox label="LPHC控制不足" />
              <el-checkbox label="旋转控制不足" />
              <el-checkbox label="左右对称控制不足" />
              <el-checkbox label="躯干控制不足" />
              <el-checkbox label="单侧支撑控制不足" />
              <el-checkbox label="暂不明确" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="方向优先结论">
            <el-radio-group v-model="model.side_specific_priority" @change="emitChange">
              <el-radio label="左旋问题更突出">左旋问题更突出</el-radio>
              <el-radio label="右旋问题更突出">右旋问题更突出</el-radio>
              <el-radio label="双侧均有但模式不同">双侧均有但模式不同</el-radio>
              <el-radio label="无明显方向性差异">无明显方向性差异</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="与对侧比较备注">
            <el-input v-model="model.compare_with_other_side_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="I. Breakout 汇总" name="summary">
          <el-form-item label="Breakout 汇总文本">
            <el-input v-model="model.breakout_summary_text" type="textarea" :rows="3" @input="emitChange" />
          </el-form-item>
          <el-form-item label="功能学意义提示">
            <el-input v-model="model.clinical_meaning_hint" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
          <el-form-item label="训练取向提示">
            <el-input v-model="model.training_direction_hint" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
          <el-form-item label="复评优先级">
            <el-radio-group v-model="model.reassessment_priority" @change="emitChange">
              <el-radio label="low">low</el-radio>
              <el-radio label="medium">medium</el-radio>
              <el-radio label="high">high</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="暂停/转介提示">
            <el-radio-group v-model="model.pause_or_referral_hint" @change="emitChange">
              <el-radio label="无需">无需</el-radio>
              <el-radio label="建议优先人工复核">建议优先人工复核</el-radio>
              <el-radio label="建议结合进一步医学评估">建议结合进一步医学评估</el-radio>
              <el-radio label="建议暂缓推进训练">建议暂缓推进训练</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-collapse-item>
      </el-collapse>
    </el-form>
  </el-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { SfmaMsrBreakoutSide } from '@/views/rehab/assessment/config/sfmaConfig'

const props = withDefaults(
  defineProps<{
    modelValue?: SfmaMsrBreakoutSide
    side?: 'left' | 'right'
  }>(),
  {
    side: 'left'
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: SfmaMsrBreakoutSide): void
  (e: 'change', value: SfmaMsrBreakoutSide): void
}>()

const deepClone = (value: any) => JSON.parse(JSON.stringify(value || {}))

const buildDefaultSide = (side: 'left' | 'right'): SfmaMsrBreakoutSide => ({
  breakout_status: 'not_started',
  rotation_side: side,
  breakout_reason_from_top_tier: '',
  breakout_note: '',
  needs_manual_review: false,
  active_rotation_global_quality: '',
  active_rotation_pain: false,
  active_rotation_pain_area: [],
  active_rotation_pain_other_note: '',
  global_rotation_quality_note: '',
  rotation_range_key: null,
  stance_stability_observation: '',
  ankle_foot_support_influence: '',
  lower_extremity_loading_asymmetry: '',
  knee_control_influence: '',
  lower_extremity_note: '',
  hip_rotation_contribution: '',
  pelvis_rotation_control: '',
  hip_pelvis_dissociation: '',
  left_right_hip_rotation_asymmetry: '',
  hip_pelvis_note: '',
  thoracic_rotation_participation: '',
  lumbar_rotation_participation: '',
  rotation_distribution_observation: '',
  thorax_pelvis_coupling_observation: '',
  spine_thorax_note: '',
  shoulder_girdle_participation: '',
  upper_extremity_assist_pattern: '',
  shoulder_thorax_link_observation: '',
  shoulder_upper_extremity_note: '',
  compensation_patterns: [],
  compensation_other_note: '',
  pain_dominant_pattern: '',
  symptom_irritability: '',
  pain_control_priority_hint: '',
  breakout_preliminary_direction: [],
  primary_restriction_chain: [],
  primary_control_deficit_chain: [],
  side_specific_priority: '',
  compare_with_other_side_note: '',
  breakout_summary_text: '',
  clinical_meaning_hint: '',
  training_direction_hint: '',
  reassessment_priority: 'medium',
  pause_or_referral_hint: ''
})

const model = computed<SfmaMsrBreakoutSide>({
  get: () => ({
    ...buildDefaultSide(props.side),
    ...(props.modelValue || {}),
    rotation_side: props.side
  }),
  set: (value) => {
    const payload = {
      ...buildDefaultSide(props.side),
      ...(deepClone(value || {}) as SfmaMsrBreakoutSide),
      rotation_side: props.side
    }
    emit('update:modelValue', payload)
    emit('change', payload)
  }
})

const sideLabel = computed(() => (props.side === 'left' ? '左旋方向' : '右旋方向'))

const statusText = computed(() => {
  const statusMap: Record<string, string> = {
    not_started: '未开始',
    in_progress: '进行中',
    completed: '已完成',
    skipped: '暂不分解'
  }
  return statusMap[model.value.breakout_status] || '未开始'
})

const statusTagType = computed<'primary' | 'success' | 'warning' | 'danger' | 'info' | undefined>(() => {
  if (model.value.breakout_status === 'completed') return 'success'
  if (model.value.breakout_status === 'in_progress') return 'warning'
  if (model.value.breakout_status === 'skipped') return 'info'
  return undefined
})

const emitChange = () => {
  model.value = { ...model.value }
}

const handlePainChange = () => {
  const shouldManualReview =
    model.value.active_rotation_pain ||
    model.value.pain_dominant_pattern === '明显是' ||
    model.value.pain_control_priority_hint === '是，建议优先人工复核'
  model.value = {
    ...model.value,
    needs_manual_review: shouldManualReview || model.value.needs_manual_review
  }
}
</script>
