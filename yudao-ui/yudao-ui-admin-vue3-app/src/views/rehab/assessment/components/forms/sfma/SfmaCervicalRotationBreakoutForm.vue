<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">颈椎旋转 Breakout（左/右分解）</span>
        <el-tag :type="allCompleted ? 'success' : 'warning'" size="small">
          {{ allCompleted ? '左右已完成' : '进行中/待完善' }}
        </el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-10px"
      title="按教材流程记录左右旋转：主动旋转 → 被动旋转 → 上位颈观察。支持左右分别进入、跳过和补录。"
    />

    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>左侧旋转 Breakout</template>
          <el-form :model="model.left" label-width="148px">
            <el-form-item label="状态">
              <el-select v-model="model.left.breakout_status" class="!w-full" @change="emitChange">
                <el-option label="未开始" value="not_started" />
                <el-option label="进行中" value="in_progress" />
                <el-option label="已完成" value="completed" />
                <el-option label="暂不分解" value="skipped" />
              </el-select>
            </el-form-item>
            <el-form-item label="分解备注">
              <el-input v-model="model.left.breakout_note" @input="emitChange" />
            </el-form-item>
            <el-form-item label="主动旋转质量">
              <el-radio-group v-model="model.left.active_cervical_rotation_quality" @change="emitChange">
                <el-radio label="正常">正常</el-radio>
                <el-radio label="受限">受限</el-radio>
                <el-radio label="明显受限">明显受限</el-radio>
                <el-radio label="无法完成">无法完成</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="主动疼痛">
              <el-switch v-model="model.left.active_cervical_rotation_pain" @change="handlePainAutoReview" />
            </el-form-item>
            <el-form-item label="主动ROM(°)">
              <el-input-number
                v-model="model.left.active_cervical_rotation_rom_key"
                :min="0"
                :max="180"
                class="!w-full"
                @change="emitChange"
              />
            </el-form-item>
            <el-form-item label="被动旋转质量">
              <el-radio-group v-model="model.left.passive_cervical_rotation_quality" @change="emitChange">
                <el-radio label="正常">正常</el-radio>
                <el-radio label="受限">受限</el-radio>
                <el-radio label="明显受限">明显受限</el-radio>
                <el-radio label="未测">未测</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="被动疼痛">
              <el-switch v-model="model.left.passive_cervical_rotation_pain" @change="handlePainAutoReview" />
            </el-form-item>
            <el-form-item label="被动ROM(°)">
              <el-input-number
                v-model="model.left.passive_cervical_rotation_rom_key"
                :min="0"
                :max="180"
                class="!w-full"
                @change="emitChange"
              />
            </el-form-item>
            <el-form-item label="主动/被动差异">
              <el-select v-model="model.left.passive_vs_active_difference" class="!w-full" @change="emitChange">
                <el-option label="被动优于主动" value="被动优于主动" />
                <el-option label="主动与被动接近" value="主动与被动接近" />
                <el-option label="被动也受限" value="被动也受限" />
                <el-option label="未判断" value="未判断" />
              </el-select>
            </el-form-item>
            <el-form-item label="上位颈观察">
              <el-radio-group v-model="model.left.upper_cervical_rotation_observation" @change="emitChange">
                <el-radio label="正常">正常</el-radio>
                <el-radio label="疑似受限">疑似受限</el-radio>
                <el-radio label="疑似代偿">疑似代偿</el-radio>
                <el-radio label="未测">未测</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="上位颈备注">
              <el-input v-model="model.left.upper_cervical_note" @input="emitChange" />
            </el-form-item>
            <el-form-item label="代偿模式">
              <el-checkbox-group v-model="model.left.compensation_patterns" @change="emitChange">
                <el-checkbox label="胸椎旋转代偿" />
                <el-checkbox label="肩胛上提" />
                <el-checkbox label="躯干侧倾代偿" />
                <el-checkbox label="下巴前引" />
                <el-checkbox label="肩部前伸" />
                <el-checkbox label="其他" />
              </el-checkbox-group>
            </el-form-item>
            <el-form-item v-if="model.left.compensation_patterns.includes('其他')" label="其他代偿">
              <el-input v-model="model.left.compensation_other_note" @input="emitChange" />
            </el-form-item>
            <el-form-item label="相关区域影响">
              <el-checkbox-group v-model="model.left.related_region_influence" @change="emitChange">
                <el-checkbox label="胸椎旋转受限影响" />
                <el-checkbox label="肩带紧张影响" />
                <el-checkbox label="软组织长度问题疑似参与" />
                <el-checkbox label="疼痛抑制影响" />
                <el-checkbox label="控制障碍疑似参与" />
                <el-checkbox label="暂不明确" />
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="分解方向">
              <el-checkbox-group v-model="model.left.breakout_preliminary_direction" @change="emitChange">
                <el-checkbox label="更偏活动度限制" />
                <el-checkbox label="更偏疼痛主导" />
                <el-checkbox label="更偏运动控制问题" />
                <el-checkbox label="需进一步颈椎解析" />
                <el-checkbox label="需结合其他模式综合判断" />
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="总结">
              <el-input v-model="model.left.breakout_summary_text" type="textarea" :rows="2" @input="emitChange" />
            </el-form-item>
            <el-form-item label="需人工复核">
              <el-switch v-model="model.left.needs_manual_review" @change="emitChange" />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header>右侧旋转 Breakout</template>
          <el-form :model="model.right" label-width="148px">
            <el-form-item label="状态">
              <el-select v-model="model.right.breakout_status" class="!w-full" @change="emitChange">
                <el-option label="未开始" value="not_started" />
                <el-option label="进行中" value="in_progress" />
                <el-option label="已完成" value="completed" />
                <el-option label="暂不分解" value="skipped" />
              </el-select>
            </el-form-item>
            <el-form-item label="分解备注">
              <el-input v-model="model.right.breakout_note" @input="emitChange" />
            </el-form-item>
            <el-form-item label="主动旋转质量">
              <el-radio-group v-model="model.right.active_cervical_rotation_quality" @change="emitChange">
                <el-radio label="正常">正常</el-radio>
                <el-radio label="受限">受限</el-radio>
                <el-radio label="明显受限">明显受限</el-radio>
                <el-radio label="无法完成">无法完成</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="主动疼痛">
              <el-switch v-model="model.right.active_cervical_rotation_pain" @change="handlePainAutoReview" />
            </el-form-item>
            <el-form-item label="主动ROM(°)">
              <el-input-number
                v-model="model.right.active_cervical_rotation_rom_key"
                :min="0"
                :max="180"
                class="!w-full"
                @change="emitChange"
              />
            </el-form-item>
            <el-form-item label="被动旋转质量">
              <el-radio-group v-model="model.right.passive_cervical_rotation_quality" @change="emitChange">
                <el-radio label="正常">正常</el-radio>
                <el-radio label="受限">受限</el-radio>
                <el-radio label="明显受限">明显受限</el-radio>
                <el-radio label="未测">未测</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="被动疼痛">
              <el-switch v-model="model.right.passive_cervical_rotation_pain" @change="handlePainAutoReview" />
            </el-form-item>
            <el-form-item label="被动ROM(°)">
              <el-input-number
                v-model="model.right.passive_cervical_rotation_rom_key"
                :min="0"
                :max="180"
                class="!w-full"
                @change="emitChange"
              />
            </el-form-item>
            <el-form-item label="主动/被动差异">
              <el-select v-model="model.right.passive_vs_active_difference" class="!w-full" @change="emitChange">
                <el-option label="被动优于主动" value="被动优于主动" />
                <el-option label="主动与被动接近" value="主动与被动接近" />
                <el-option label="被动也受限" value="被动也受限" />
                <el-option label="未判断" value="未判断" />
              </el-select>
            </el-form-item>
            <el-form-item label="上位颈观察">
              <el-radio-group v-model="model.right.upper_cervical_rotation_observation" @change="emitChange">
                <el-radio label="正常">正常</el-radio>
                <el-radio label="疑似受限">疑似受限</el-radio>
                <el-radio label="疑似代偿">疑似代偿</el-radio>
                <el-radio label="未测">未测</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="上位颈备注">
              <el-input v-model="model.right.upper_cervical_note" @input="emitChange" />
            </el-form-item>
            <el-form-item label="代偿模式">
              <el-checkbox-group v-model="model.right.compensation_patterns" @change="emitChange">
                <el-checkbox label="胸椎旋转代偿" />
                <el-checkbox label="肩胛上提" />
                <el-checkbox label="躯干侧倾代偿" />
                <el-checkbox label="下巴前引" />
                <el-checkbox label="肩部前伸" />
                <el-checkbox label="其他" />
              </el-checkbox-group>
            </el-form-item>
            <el-form-item v-if="model.right.compensation_patterns.includes('其他')" label="其他代偿">
              <el-input v-model="model.right.compensation_other_note" @input="emitChange" />
            </el-form-item>
            <el-form-item label="相关区域影响">
              <el-checkbox-group v-model="model.right.related_region_influence" @change="emitChange">
                <el-checkbox label="胸椎旋转受限影响" />
                <el-checkbox label="肩带紧张影响" />
                <el-checkbox label="软组织长度问题疑似参与" />
                <el-checkbox label="疼痛抑制影响" />
                <el-checkbox label="控制障碍疑似参与" />
                <el-checkbox label="暂不明确" />
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="分解方向">
              <el-checkbox-group v-model="model.right.breakout_preliminary_direction" @change="emitChange">
                <el-checkbox label="更偏活动度限制" />
                <el-checkbox label="更偏疼痛主导" />
                <el-checkbox label="更偏运动控制问题" />
                <el-checkbox label="需进一步颈椎解析" />
                <el-checkbox label="需结合其他模式综合判断" />
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="总结">
              <el-input v-model="model.right.breakout_summary_text" type="textarea" :rows="2" @input="emitChange" />
            </el-form-item>
            <el-form-item label="需人工复核">
              <el-switch v-model="model.right.needs_manual_review" @change="emitChange" />
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
          placeholder="例如：左侧主动旋转明显差于右侧，伴代偿。"
          @input="emitChange"
        />
      </el-form-item>
      <el-form-item label="专项总备注">
        <el-input v-model="model.overall_note" type="textarea" :rows="2" @input="emitChange" />
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { buildDefaultCervicalRotationBreakout, SfmaCervicalRotationBreakout } from '@/views/rehab/assessment/config/sfmaConfig'

const props = defineProps<{
  modelValue?: SfmaCervicalRotationBreakout
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: SfmaCervicalRotationBreakout): void
  (e: 'change', value: SfmaCervicalRotationBreakout): void
}>()

const deepClone = (value: any) => JSON.parse(JSON.stringify(value || {}))

const model = computed<SfmaCervicalRotationBreakout>({
  get: () => ({
    ...buildDefaultCervicalRotationBreakout(),
    ...(props.modelValue || {})
  }),
  set: (value) => {
    const payload = {
      ...buildDefaultCervicalRotationBreakout(),
      ...deepClone(value || {})
    }
    emit('update:modelValue', payload)
    emit('change', payload)
  }
})

const allCompleted = computed(() => {
  return model.value.left.breakout_status === 'completed' && model.value.right.breakout_status === 'completed'
})

const emitChange = () => {
  model.value = deepClone(model.value)
}

const handlePainAutoReview = () => {
  const next = deepClone(model.value)
  const leftPain =
    next.left.active_cervical_rotation_pain ||
    next.left.passive_cervical_rotation_pain
  const rightPain =
    next.right.active_cervical_rotation_pain ||
    next.right.passive_cervical_rotation_pain
  if (leftPain) {
    next.left.needs_manual_review = true
  }
  if (rightPain) {
    next.right.needs_manual_review = true
  }
  model.value = next
}

const validate = async () => true
const getFormData = () => deepClone(model.value)
const reset = () => {
  model.value = buildDefaultCervicalRotationBreakout()
}

defineExpose({ validate, getFormData, reset })
</script>
