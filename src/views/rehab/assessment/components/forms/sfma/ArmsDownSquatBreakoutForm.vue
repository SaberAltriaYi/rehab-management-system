<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">垂臂下蹲分解评估（Arms-Down Squat Breakout）</span>
        <el-tag :type="statusTagType" size="small">{{ statusText }}</el-tag>
      </div>
    </template>

    <el-form :model="model" label-width="200px">
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
          <el-form-item label="初筛触发原因">
            <el-input v-model="model.breakout_reason_from_screening" type="textarea" :rows="2" readonly />
          </el-form-item>
          <el-form-item label="Breakout 备注">
            <el-input v-model="model.breakout_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
          <el-form-item label="需人工复核">
            <el-switch v-model="model.needs_manual_review" @change="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="B. 整体动作表现" name="global">
          <el-form-item label="整体质量">
            <el-radio-group v-model="model.squat_global_quality" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="轻度异常">轻度异常</el-radio>
              <el-radio label="中度异常">中度异常</el-radio>
              <el-radio label="明显异常">明显异常</el-radio>
              <el-radio label="无法完成">无法完成</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="深度水平">
            <el-radio-group v-model="model.squat_depth_level" @change="emitChange">
              <el-radio label="深度充分">深度充分</el-radio>
              <el-radio label="接近平行">接近平行</el-radio>
              <el-radio label="未达平行">未达平行</el-radio>
              <el-radio label="明显受限">明显受限</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="下蹲控制">
            <el-radio-group v-model="model.descent_control" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="轻度控制不足">轻度控制不足</el-radio>
              <el-radio label="明显控制不足">明显控制不足</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="起立控制">
            <el-radio-group v-model="model.ascent_control" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="轻度控制不足">轻度控制不足</el-radio>
              <el-radio label="明显控制不足">明显控制不足</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="动作节奏观察">
            <el-radio-group v-model="model.squat_rhythm_observation" @change="emitChange">
              <el-radio label="节奏流畅">节奏流畅</el-radio>
              <el-radio label="轻度中断">轻度中断</el-radio>
              <el-radio label="明显中断/犹豫">明显中断/犹豫</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="动作质量备注">
            <el-input v-model="model.movement_quality_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="C. 足踝 / 支撑基础观察" name="foot-ankle">
          <el-form-item label="踝背屈受限疑似">
            <el-radio-group v-model="model.ankle_dorsiflexion_limitation_suspected" @change="emitChange">
              <el-radio label="否">否</el-radio>
              <el-radio label="左侧疑似">左侧疑似</el-radio>
              <el-radio label="右侧疑似">右侧疑似</el-radio>
              <el-radio label="双侧疑似">双侧疑似</el-radio>
              <el-radio label="明显双侧受限">明显双侧受限</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="Heel Rise 模式">
            <el-radio-group v-model="model.heel_rise_pattern" @change="emitChange">
              <el-radio label="无">无</el-radio>
              <el-radio label="左侧明显">左侧明显</el-radio>
              <el-radio label="右侧明显">右侧明显</el-radio>
              <el-radio label="双侧明显">双侧明显</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="足旋前控制">
            <el-radio-group v-model="model.foot_pronation_control" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="左侧塌陷更明显">左侧塌陷更明显</el-radio>
              <el-radio label="右侧塌陷更明显">右侧塌陷更明显</el-radio>
              <el-radio label="双侧塌陷">双侧塌陷</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="后足稳定性">
            <el-radio-group v-model="model.rearfoot_stability" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="左侧不稳">左侧不稳</el-radio>
              <el-radio label="右侧不稳">右侧不稳</el-radio>
              <el-radio label="双侧不稳">双侧不稳</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="足底压力策略">
            <el-radio-group v-model="model.foot_tripod_or_pressure_strategy" @change="emitChange">
              <el-radio label="支撑均衡">支撑均衡</el-radio>
              <el-radio label="前足偏重">前足偏重</el-radio>
              <el-radio label="后足偏重">后足偏重</el-radio>
              <el-radio label="内侧偏重">内侧偏重</el-radio>
              <el-radio label="外侧偏重">外侧偏重</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="足踝备注">
            <el-input v-model="model.foot_ankle_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="D. 膝控制观察" name="knee">
          <el-form-item label="膝内扣控制">
            <el-radio-group v-model="model.knee_valgus_control" @change="emitChange">
              <el-radio label="无">无</el-radio>
              <el-radio label="左侧明显">左侧明显</el-radio>
              <el-radio label="右侧明显">右侧明显</el-radio>
              <el-radio label="双侧明显">双侧明显</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="膝外移/外翻模式">
            <el-radio-group v-model="model.knee_varus_or_outward_shift" @change="emitChange">
              <el-radio label="无">无</el-radio>
              <el-radio label="左侧明显">左侧明显</el-radio>
              <el-radio label="右侧明显">右侧明显</el-radio>
              <el-radio label="双侧明显">双侧明显</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="膝前移模式">
            <el-radio-group v-model="model.knee_forward_translation_pattern" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="偏少">偏少</el-radio>
              <el-radio label="偏多">偏多</el-radio>
              <el-radio label="左右不一致">左右不一致</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="膝不稳/抖动">
            <el-radio-group v-model="model.knee_wobble_or_instability" @change="emitChange">
              <el-radio label="无">无</el-radio>
              <el-radio label="左侧轻度">左侧轻度</el-radio>
              <el-radio label="右侧轻度">右侧轻度</el-radio>
              <el-radio label="左侧明显">左侧明显</el-radio>
              <el-radio label="右侧明显">右侧明显</el-radio>
              <el-radio label="双侧明显">双侧明显</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="膝控制备注">
            <el-input v-model="model.knee_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="E. 髋 / 骨盆参与观察" name="hip-pelvis">
          <el-form-item label="髋屈参与">
            <el-radio-group v-model="model.hip_flexion_contribution" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="偏少">偏少</el-radio>
              <el-radio label="明显不足">明显不足</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="髋控制左右差">
            <el-radio-group v-model="model.hip_control_asymmetry" @change="emitChange">
              <el-radio label="无明显左右差">无明显左右差</el-radio>
              <el-radio label="左侧控制较差">左侧控制较差</el-radio>
              <el-radio label="右侧控制较差">右侧控制较差</el-radio>
              <el-radio label="双侧均差">双侧均差</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="骨盆偏移模式">
            <el-radio-group v-model="model.pelvic_shift_pattern" @change="emitChange">
              <el-radio label="无明显偏移">无明显偏移</el-radio>
              <el-radio label="向左偏移">向左偏移</el-radio>
              <el-radio label="向右偏移">向右偏移</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="骨盆旋转疑似">
            <el-radio-group v-model="model.pelvic_rotation_suspected" @change="emitChange">
              <el-radio label="否">否</el-radio>
              <el-radio label="疑似">疑似</el-radio>
              <el-radio label="明显">明显</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="髋骨盆分离能力">
            <el-radio-group v-model="model.hip_pelvis_dissociation" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="欠协调">欠协调</el-radio>
              <el-radio label="明显失衡">明显失衡</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="髋骨盆备注">
            <el-input v-model="model.hip_pelvis_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="F. 躯干 / LPHC 参与观察" name="trunk-lphc">
          <el-form-item label="躯干前倾代偿">
            <el-radio-group v-model="model.excessive_forward_lean" @change="emitChange">
              <el-radio label="无">无</el-radio>
              <el-radio label="轻度">轻度</el-radio>
              <el-radio label="明显">明显</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="腰椎屈曲代偿">
            <el-radio-group v-model="model.lumbar_rounding" @change="emitChange">
              <el-radio label="无">无</el-radio>
              <el-radio label="轻度">轻度</el-radio>
              <el-radio label="明显">明显</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="腰椎过伸代偿">
            <el-radio-group v-model="model.lumbar_extension_or_arching" @change="emitChange">
              <el-radio label="无">无</el-radio>
              <el-radio label="轻度">轻度</el-radio>
              <el-radio label="明显">明显</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="LPHC 控制观察">
            <el-radio-group v-model="model.lphc_control_observation" @change="emitChange">
              <el-radio label="正常">正常</el-radio>
              <el-radio label="轻度不足">轻度不足</el-radio>
              <el-radio label="明显不足">明显不足</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="躯干偏移/旋转">
            <el-radio-group v-model="model.trunk_shift_or_rotation" @change="emitChange">
              <el-radio label="无明显异常">无明显异常</el-radio>
              <el-radio label="左偏/左旋更明显">左偏/左旋更明显</el-radio>
              <el-radio label="右偏/右旋更明显">右偏/右旋更明显</el-radio>
              <el-radio label="双向异常">双向异常</el-radio>
              <el-radio label="难以判断">难以判断</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="躯干/LPHC 备注">
            <el-input v-model="model.trunk_lphc_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="G. 左右差与代偿链观察" name="asymmetry-compensation">
          <el-form-item label="全局左右差">
            <el-radio-group v-model="model.left_right_asymmetry_global" @change="emitChange">
              <el-radio label="无明显左右差">无明显左右差</el-radio>
              <el-radio label="左侧问题更突出">左侧问题更突出</el-radio>
              <el-radio label="右侧问题更突出">右侧问题更突出</el-radio>
              <el-radio label="双侧均差但模式不同">双侧均差但模式不同</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="代偿模式">
            <el-checkbox-group v-model="model.compensation_patterns" @change="emitChange">
              <el-checkbox label="足外八代偿" />
              <el-checkbox label="足过度旋前代偿" />
              <el-checkbox label="膝内扣代偿" />
              <el-checkbox label="髋控制不足代偿" />
              <el-checkbox label="骨盆侧移代偿" />
              <el-checkbox label="躯干前倾代偿" />
              <el-checkbox label="躯干旋转/偏移代偿" />
              <el-checkbox label="heel rise 代偿" />
              <el-checkbox label="其他" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item v-if="model.compensation_patterns.includes('其他')" label="其他代偿说明">
            <el-input v-model="model.compensation_other_note" @input="emitChange" />
          </el-form-item>
          <el-form-item label="主要代偿链条备注">
            <el-input v-model="model.primary_compensation_chain_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="H. 疼痛与症状特征" name="pain-symptom">
          <el-form-item label="是否疼痛">
            <el-switch v-model="model.pain_present" @change="handlePainChange" />
          </el-form-item>
          <el-form-item label="疼痛评分 VAS">
            <el-input-number v-model="model.pain_vas" :min="0" :max="10" :step="1" class="!w-full" @change="handlePainChange" />
          </el-form-item>
          <el-form-item label="疼痛部位">
            <el-checkbox-group v-model="model.pain_area" @change="emitChange">
              <el-checkbox label="足踝" />
              <el-checkbox label="膝" />
              <el-checkbox label="髋" />
              <el-checkbox label="腰背" />
              <el-checkbox label="其他" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item v-if="model.pain_area.includes('其他')" label="其他疼痛部位">
            <el-input v-model="model.pain_area_other_note" @input="emitChange" />
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

        <el-collapse-item title="I. 初步方向判断" name="direction">
          <el-form-item label="初步方向">
            <el-checkbox-group v-model="model.breakout_preliminary_direction" @change="emitChange">
              <el-checkbox label="更偏踝活动度限制" />
              <el-checkbox label="更偏足踝稳定不足" />
              <el-checkbox label="更偏膝对线/控制问题" />
              <el-checkbox label="更偏髋控制问题" />
              <el-checkbox label="更偏髋活动度限制" />
              <el-checkbox label="更偏骨盆/LPHC控制问题" />
              <el-checkbox label="更偏躯干控制问题" />
              <el-checkbox label="更偏左右不对称" />
              <el-checkbox label="更偏疼痛主导" />
              <el-checkbox label="需结合其他动作综合判断" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="主要限制链条">
            <el-checkbox-group v-model="model.primary_restriction_chain" @change="emitChange">
              <el-checkbox label="足踝-支撑链" />
              <el-checkbox label="膝-下肢对线链" />
              <el-checkbox label="髋-骨盆链" />
              <el-checkbox label="LPHC-躯干控制链" />
              <el-checkbox label="左右不对称链" />
              <el-checkbox label="暂不明确" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="主要控制障碍链条">
            <el-checkbox-group v-model="model.primary_control_deficit_chain" @change="emitChange">
              <el-checkbox label="LPHC控制不足" />
              <el-checkbox label="髋主导不足" />
              <el-checkbox label="深蹲模式控制不足" />
              <el-checkbox label="左右对称控制不足" />
              <el-checkbox label="单侧支撑控制不足" />
              <el-checkbox label="暂不明确" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="风险初筛等级">
            <el-radio-group v-model="model.risk_precheck_level" @change="emitChange">
              <el-radio label="low">low</el-radio>
              <el-radio label="medium">medium</el-radio>
              <el-radio label="high">high</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="风险标签">
            <el-checkbox-group v-model="model.risk_tags" @change="emitChange">
              <el-checkbox label="lower_extremity_alignment_attention" />
              <el-checkbox label="lphc_stability_attention" />
              <el-checkbox label="asymmetry_attention" />
              <el-checkbox label="squat_pattern_attention" />
              <el-checkbox label="pain_attention" />
              <el-checkbox label="reassessment_attention" />
            </el-checkbox-group>
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="J. Breakout 汇总" name="summary">
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
import { SfmaArmsDownSquatBreakout, buildDefaultArmsDownSquatBreakout } from '@/views/rehab/assessment/config/sfmaConfig'

const props = defineProps<{ modelValue: SfmaArmsDownSquatBreakout }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: SfmaArmsDownSquatBreakout): void
  (e: 'change', value: SfmaArmsDownSquatBreakout): void
}>()

const model = computed({
  get: () => ({
    ...buildDefaultArmsDownSquatBreakout(),
    ...(props.modelValue || {})
  }),
  set: (value: SfmaArmsDownSquatBreakout) => {
    emit('update:modelValue', value)
    emit('change', value)
  }
})

const statusText = computed(() => {
  const map: Record<string, string> = {
    not_started: '未开始',
    in_progress: '进行中',
    completed: '已完成',
    skipped: '暂不分解'
  }
  return map[model.value.breakout_status] || '未开始'
})

const statusTagType = computed((): 'success' | 'warning' | 'info' => {
  if (model.value.breakout_status === 'completed') return 'success'
  if (model.value.breakout_status === 'in_progress') return 'warning'
  return 'info'
})

const emitChange = () => {
  model.value = {
    ...model.value,
    needs_manual_review:
      model.value.needs_manual_review ||
      model.value.pain_present ||
      model.value.pain_dominant_pattern === '明显是' ||
      model.value.pain_control_priority_hint === '是，建议优先人工复核' ||
      model.value.risk_precheck_level === 'high'
  }
}

const handlePainChange = () => {
  const needReviewByPain =
    model.value.pain_present ||
    model.value.pain_dominant_pattern === '明显是' ||
    model.value.pain_control_priority_hint === '是，建议优先人工复核'
  model.value = {
    ...model.value,
    needs_manual_review: needReviewByPain || model.value.needs_manual_review
  }
}
</script>
