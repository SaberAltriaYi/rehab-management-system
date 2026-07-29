<template>
  <el-card shadow="never" class="assessment-form-shell">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">SFMA 评估表单</span>
        <el-tag size="small" type="success">原书版流程</el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-12px"
      title="流程：完成全部 Top Tier → DN 优先 → FP 第二阶段 → DP 最后阶段 → 分解评估与汇总"
      description="分解流程依据原书第 7、8 章及附录 3；FN 不分解，FP/DP 谨慎进行，测试出现疼痛即终止该路径。"
    />

    <SfmaCervicalFlexionTopTierForm
      v-model="localData.cervical_flexion_top_tier"
      @change="handleCervicalTopTierChange"
      @breakout-action="handleCervicalBreakoutAction"
    />

    <el-card
      v-if="
        legacyBreakoutsEnabled &&
        (localData.cervical_flexion_top_tier.needs_breakout_suggestion ||
        localData.cervical_flexion_breakout.breakout_status !== 'not_started'
        )
      "
      ref="cervicalFlexionBreakoutCardRef"
      shadow="never"
      class="mb-12px"
    >
      <template #header>颈椎屈曲 Breakout（专项）</template>
      <SfmaCervicalFlexionBreakoutForm v-model="localData.cervical_flexion_breakout" @change="handleCervicalBreakoutChange" />
    </el-card>

    <SfmaCervicalExtensionTopTierForm
      v-model="localData.cervical_extension_top_tier"
      @change="handleCervicalExtensionTopTierChange"
      @breakout-action="handleCervicalExtensionBreakoutAction"
    />

    <el-card
      v-if="
        legacyBreakoutsEnabled &&
        (localData.cervical_extension_top_tier.needs_breakout_suggestion ||
        localData.cervical_extension_breakout.breakout_status !== 'not_started'
        )
      "
      ref="cervicalExtensionBreakoutCardRef"
      shadow="never"
      class="mb-12px"
    >
      <template #header>颈椎伸展 Breakout（专项）</template>
      <SfmaCervicalExtensionBreakoutForm
        v-model="localData.cervical_extension_breakout"
        @change="handleCervicalExtensionBreakoutChange"
      />
    </el-card>

    <SfmaCervicalRotationTopTierForm
      v-model="localData.cervical_rotation_top_tier"
      @change="handleCervicalRotationTopTierChange"
      @breakout-action="handleCervicalRotationBreakoutAction"
    />

    <el-card
      v-if="
        legacyBreakoutsEnabled &&
        (localData.cervical_rotation_top_tier.left.needs_breakout_suggestion ||
        localData.cervical_rotation_top_tier.right.needs_breakout_suggestion ||
        localData.cervical_rotation_breakout.left.breakout_status !== 'not_started' ||
        localData.cervical_rotation_breakout.right.breakout_status !== 'not_started'
        )
      "
      ref="cervicalRotationBreakoutCardRef"
      shadow="never"
      class="mb-12px"
    >
      <template #header>颈椎旋转 Breakout（专项）</template>
      <SfmaCervicalRotationBreakoutForm
        v-model="localData.cervical_rotation_breakout"
        @change="handleCervicalRotationBreakoutChange"
      />
    </el-card>

    <SfmaUpperExtremityPattern1TopTierForm
      v-model="localData.upper_extremity_pattern1_top_tier"
      @change="handleUpperExtremityPattern1TopTierChange"
      @breakout-action="handleUpperExtremityPattern1BreakoutAction"
    />

    <el-card
      v-if="
        legacyBreakoutsEnabled &&
        (localData.upper_extremity_pattern1_top_tier.left.needs_breakout_suggestion ||
        localData.upper_extremity_pattern1_top_tier.right.needs_breakout_suggestion ||
        localData.upper_extremity_pattern1_breakout.left.breakout_status !== 'not_started' ||
        localData.upper_extremity_pattern1_breakout.right.breakout_status !== 'not_started'
        )
      "
      ref="upperExtremityPattern1BreakoutCardRef"
      shadow="never"
      class="mb-12px"
    >
      <template #header>上肢模式1 Breakout（专项）</template>
      <SfmaUpperExtremityPattern1BreakoutForm
        v-model="localData.upper_extremity_pattern1_breakout"
        @change="handleUpperExtremityPattern1BreakoutChange"
      />
    </el-card>

    <SfmaUpperExtremityPattern2TopTierForm
      v-model="localData.upper_extremity_pattern2_top_tier"
      @change="handleUpperExtremityPattern2TopTierChange"
      @breakout-action="handleUpperExtremityPattern2BreakoutAction"
    />

    <el-card
      v-if="
        legacyBreakoutsEnabled &&
        (localData.upper_extremity_pattern2_top_tier.left.needs_breakout_suggestion ||
        localData.upper_extremity_pattern2_top_tier.right.needs_breakout_suggestion ||
        localData.upper_extremity_pattern2_breakout.left.breakout_status !== 'not_started' ||
        localData.upper_extremity_pattern2_breakout.right.breakout_status !== 'not_started'
        )
      "
      ref="upperExtremityPattern2BreakoutCardRef"
      shadow="never"
      class="mb-12px"
    >
      <template #header>上肢模式2 Breakout（专项）</template>
      <SfmaUpperExtremityPattern2BreakoutForm
        v-model="localData.upper_extremity_pattern2_breakout"
        @change="handleUpperExtremityPattern2BreakoutChange"
      />
    </el-card>

    <SfmaMsfTopTierForm
      :model-value="localData.top_tier.multi_segmental_flexion"
      @update:model-value="(value) => updateTopTierRow('multi_segmental_flexion', value)"
      @change="handleMsfTopTierChange"
      @breakout-action="handleMsfBreakoutAction"
    />

    <el-card
      v-if="
        legacyBreakoutsEnabled &&
        (localData.top_tier.multi_segmental_flexion.needs_breakout_suggestion ||
        localData.msf_breakout.breakout_status !== 'not_started'
        )
      "
      ref="msfBreakoutCardRef"
      shadow="never"
      class="mb-12px"
    >
      <template #header>多节段屈曲 Breakout（MSF）</template>
      <SfmaMsfBreakoutForm v-model="localData.msf_breakout" @change="handleMsfBreakoutChange" />
    </el-card>

    <SfmaMseTopTierForm
      :model-value="localData.top_tier.multi_segmental_extension"
      @update:model-value="(value) => updateTopTierRow('multi_segmental_extension', value)"
      @change="handleMseTopTierChange"
      @breakout-action="handleMseBreakoutAction"
    />

    <el-card
      v-if="
        legacyBreakoutsEnabled &&
        (localData.top_tier.multi_segmental_extension.needs_breakout_suggestion ||
        localData.mse_breakout.breakout_status !== 'not_started'
        )
      "
      ref="mseBreakoutCardRef"
      shadow="never"
      class="mb-12px"
    >
      <template #header>多节段伸展 Breakout（MSE）</template>
      <SfmaMseBreakoutForm v-model="localData.mse_breakout" @change="handleMseBreakoutChange" />
    </el-card>

    <SfmaMsrTopTierForm
      :model-value="{
        left: localData.top_tier.multi_segmental_rotation_left,
        right: localData.top_tier.multi_segmental_rotation_right
      }"
      @update:model-value="updateMsrTopTierRows"
      @change="handleMsrTopTierChange"
      @breakout-action="handleMsrBreakoutAction"
    />

    <el-card
      v-if="
        legacyBreakoutsEnabled &&
        (localData.top_tier.multi_segmental_rotation_left.needs_breakout_suggestion ||
        localData.top_tier.multi_segmental_rotation_right.needs_breakout_suggestion ||
        localData.msr_breakout.left.breakout_status !== 'not_started' ||
        localData.msr_breakout.right.breakout_status !== 'not_started'
        )
      "
      ref="msrBreakoutCardRef"
      shadow="never"
      class="mb-12px"
    >
      <template #header>多节段旋转 Breakout（MSR）</template>
      <el-row :gutter="12">
        <el-col :span="12">
          <SfmaMsrBreakoutForm
            :model-value="localData.msr_breakout.left"
            side="left"
            @update:model-value="(value) => updateMsrBreakoutSide('left', value)"
            @change="handleMsrBreakoutChange('left')"
          />
        </el-col>
        <el-col :span="12">
          <SfmaMsrBreakoutForm
            :model-value="localData.msr_breakout.right"
            side="right"
            @update:model-value="(value) => updateMsrBreakoutSide('right', value)"
            @change="handleMsrBreakoutChange('right')"
          />
        </el-col>
      </el-row>
    </el-card>

    <SfmaSlsTopTierForm
      :model-value="{
        left: localData.top_tier.single_leg_stance_left,
        right: localData.top_tier.single_leg_stance_right
      }"
      @update:model-value="updateSlsTopTierRows"
      @change="handleSlsTopTierChange"
      @breakout-action="handleSlsBreakoutAction"
    />

    <el-card
      v-if="
        legacyBreakoutsEnabled &&
        (localData.top_tier.single_leg_stance_left.needs_breakout_suggestion ||
        localData.top_tier.single_leg_stance_right.needs_breakout_suggestion ||
        localData.breakouts.sls_left.status !== 'not_started' ||
        localData.breakouts.sls_right.status !== 'not_started'
        )
      "
      ref="slsBreakoutCardRef"
      shadow="never"
      class="mb-12px"
    >
      <template #header>单腿站立 Breakout（SLS）</template>
      <el-row :gutter="12">
        <el-col :span="12">
          <SfmaSlsBreakoutForm
            :model-value="localData.breakouts.sls_left"
            side="left"
            @update:model-value="(value) => updateSlsBreakoutSide('left', value)"
            @change="handleSlsBreakoutChange('left')"
          />
        </el-col>
        <el-col :span="12">
          <SfmaSlsBreakoutForm
            :model-value="localData.breakouts.sls_right"
            side="right"
            @update:model-value="(value) => updateSlsBreakoutSide('right', value)"
            @change="handleSlsBreakoutChange('right')"
          />
        </el-col>
      </el-row>
    </el-card>

    <SfmaTopTierForm
      v-model="localData.top_tier"
      :exclude-test-codes="[
        'cervical_flexion',
        'cervical_extension',
        'cervical_rotation_left',
        'cervical_rotation_right',
        'upper_extremity_pattern1_left',
        'upper_extremity_pattern1_right',
        'upper_extremity_pattern2_left',
        'upper_extremity_pattern2_right',
        'multi_segmental_flexion',
        'multi_segmental_extension',
        'multi_segmental_rotation_left',
        'multi_segmental_rotation_right',
        'single_leg_stance_left',
        'single_leg_stance_right'
      ]"
      @change="handleTopTierChange"
    />

    <SfmaBookProtocolForm
      ref="bookProtocolCardRef"
      v-model="localData.book_protocol"
      :top-tier="localData.top_tier"
    />

    <SfmaBreakoutRecommendationPanel
      v-if="legacyBreakoutsEnabled"
      v-model="localData.breakout_recommendations"
      :disabled="!isTopTierComplete"
      :exclude-test-codes="[
        'cervical_flexion',
        'cervical_extension',
        'cervical_rotation_left',
        'cervical_rotation_right',
        'upper_extremity_pattern1_left',
        'upper_extremity_pattern1_right',
        'upper_extremity_pattern2_left',
        'upper_extremity_pattern2_right',
        'multi_segmental_flexion',
        'multi_segmental_extension',
        'multi_segmental_rotation_left',
        'multi_segmental_rotation_right',
        'single_leg_stance_left',
        'single_leg_stance_right'
      ]"
      @change="handleRecommendationChange"
    />

    <el-card v-if="legacyBreakoutsEnabled" shadow="never" class="mb-12px">
      <template #header>
        <div class="flex items-center justify-between">
          <span>Breakout 分解评估</span>
          <el-tag :type="acceptedGeneralRecommendations.length ? 'success' : 'info'" size="small">
            {{ acceptedGeneralRecommendations.length ? `已选择 ${acceptedGeneralRecommendations.length} 项` : '未选择分解项' }}
          </el-tag>
        </div>
      </template>

      <el-empty
        v-if="!isTopTierComplete"
        description="请先完成 Top Tier 初筛，系统会生成建议分解列表。"
      />
      <el-empty
        v-else-if="!acceptedGeneralRecommendations.length"
        description="当前没有“进入分解评估”的项目，可在建议列表中选择 accepted。"
      />

      <el-collapse v-else>
        <el-collapse-item
          v-for="item in acceptedGeneralRecommendations"
          :key="`breakout-${item.breakout_key}-${item.test_code}`"
          :name="item.breakout_key"
          :title="breakoutLabel(item.breakout_key)"
        >
          <component
            :is="resolveBreakoutComponent(item.breakout_key)"
            :model-value="resolveBreakoutModel(item)"
            :side="resolveSide(item)"
            @update:model-value="updateBreakoutModel(item, $event)"
            @change="handleBreakoutChange(item)"
          />
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <SfmaSummaryPanel :summary="localData.summary" :risk-precheck="localData.risk_precheck || {}" />
  </el-card>
</template>

<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { isEqual } from 'lodash-es'
import {
  applyCervicalExtensionTopTierRules,
  applyCervicalFlexionTopTierRules,
  applyCervicalRotationTopTierRules,
  applyUpperExtremityPattern1TopTierRules,
  applyUpperExtremityPattern2TopTierRules,
  buildDefaultCervicalExtensionBreakout,
  buildDefaultCervicalFlexionBreakout,
  buildDefaultCervicalRotationBreakout,
  buildDefaultArmsDownSquatBreakout,
  buildDefaultUpperExtremityPattern1Breakout,
  buildDefaultUpperExtremityPattern2Breakout,
  buildDefaultMsfBreakout,
  buildDefaultMseBreakout,
  buildDefaultMsrBreakout,
  buildDefaultSfmaBreakoutRecord,
  buildDefaultSfmaFormData,
  buildDefaultSfmaTopTierRecord,
  SFMA_BREAKOUT_LABELS,
  SFMA_BREAKOUT_KEYS,
  SFMA_TOP_TIER_DEFINITIONS,
  SfmaBreakoutRecord,
  SfmaBreakoutRecommendation,
  SfmaCervicalExtensionBreakout,
  SfmaCervicalExtensionTopTier,
  SfmaCervicalFlexionBreakout,
  SfmaCervicalFlexionTopTier,
  SfmaCervicalRotationBreakout,
  SfmaCervicalRotationTopTier,
  SfmaArmsDownSquatBreakout,
  SfmaUpperExtremityPattern1Breakout,
  SfmaUpperExtremityPattern1TopTier,
  SfmaUpperExtremityPattern2Breakout,
  SfmaUpperExtremityPattern2TopTier,
  SfmaMsfBreakout,
  SfmaMseBreakout,
  SfmaMsrBreakout,
  SfmaTopTierRecord,
  SfmaFormData
} from '@/views/rehab/assessment/config/sfmaConfig'
import SfmaTopTierForm from './sfma/SfmaTopTierForm.vue'
import SfmaBreakoutRecommendationPanel from './sfma/SfmaBreakoutRecommendationPanel.vue'
import SfmaCervicalBreakoutForm from './sfma/SfmaCervicalBreakoutForm.vue'
import SfmaUpperExtremityBreakoutForm from './sfma/SfmaUpperExtremityBreakoutForm.vue'
import SfmaMsfBreakoutForm from './sfma/SfmaMsfBreakoutForm.vue'
import SfmaMseBreakoutForm from './sfma/SfmaMseBreakoutForm.vue'
import SfmaMsrBreakoutForm from './sfma/SfmaMsrBreakoutForm.vue'
import SfmaSlsBreakoutForm from './sfma/SfmaSlsBreakoutForm.vue'
import SfmaDeepSquatBreakoutForm from './sfma/SfmaDeepSquatBreakoutForm.vue'
import SfmaSummaryPanel from './sfma/SfmaSummaryPanel.vue'
import SfmaCervicalFlexionTopTierForm from './sfma/SfmaCervicalFlexionTopTierForm.vue'
import SfmaCervicalFlexionBreakoutForm from './sfma/SfmaCervicalFlexionBreakoutForm.vue'
import SfmaCervicalExtensionTopTierForm from './sfma/SfmaCervicalExtensionTopTierForm.vue'
import SfmaCervicalExtensionBreakoutForm from './sfma/SfmaCervicalExtensionBreakoutForm.vue'
import SfmaCervicalRotationTopTierForm from './sfma/SfmaCervicalRotationTopTierForm.vue'
import SfmaCervicalRotationBreakoutForm from './sfma/SfmaCervicalRotationBreakoutForm.vue'
import SfmaUpperExtremityPattern1TopTierForm from './sfma/SfmaUpperExtremityPattern1TopTierForm.vue'
import SfmaUpperExtremityPattern1BreakoutForm from './sfma/SfmaUpperExtremityPattern1BreakoutForm.vue'
import SfmaUpperExtremityPattern2TopTierForm from './sfma/SfmaUpperExtremityPattern2TopTierForm.vue'
import SfmaUpperExtremityPattern2BreakoutForm from './sfma/SfmaUpperExtremityPattern2BreakoutForm.vue'
import SfmaMsfTopTierForm from './sfma/SfmaMsfTopTierForm.vue'
import SfmaMseTopTierForm from './sfma/SfmaMseTopTierForm.vue'
import SfmaMsrTopTierForm from './sfma/SfmaMsrTopTierForm.vue'
import SfmaSlsTopTierForm from './sfma/SfmaSlsTopTierForm.vue'
import ArmsDownSquatBreakoutForm from './sfma/ArmsDownSquatBreakoutForm.vue'
import SfmaBookProtocolForm from './sfma/SfmaBookProtocolForm.vue'

const props = defineProps<{
  modelValue?: Record<string, any>
  assessmentBaseInfo?: Record<string, any>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', value: Record<string, any>): void
}>()

const legacyBreakoutsEnabled = false
const bookProtocolCardRef = ref<any>()
const cervicalFlexionBreakoutCardRef = ref<any>()
const cervicalExtensionBreakoutCardRef = ref<any>()
const cervicalRotationBreakoutCardRef = ref<any>()
const upperExtremityPattern1BreakoutCardRef = ref<any>()
const upperExtremityPattern2BreakoutCardRef = ref<any>()
const msfBreakoutCardRef = ref<any>()
const mseBreakoutCardRef = ref<any>()
const msrBreakoutCardRef = ref<any>()
const slsBreakoutCardRef = ref<any>()

const BREAKOUT_COMPONENT_MAP: Record<string, any> = {
  cervical_flexion_breakout: SfmaCervicalFlexionBreakoutForm,
  cervical_extension_breakout: SfmaCervicalExtensionBreakoutForm,
  cervical_rotation_breakout: SfmaCervicalRotationBreakoutForm,
  upper_extremity_pattern1_breakout: SfmaUpperExtremityPattern1BreakoutForm,
  upper_extremity_pattern2_breakout: SfmaUpperExtremityPattern2BreakoutForm,
  cervical_pattern: SfmaCervicalBreakoutForm,
  upper_extremity_pattern_left: SfmaUpperExtremityBreakoutForm,
  upper_extremity_pattern_right: SfmaUpperExtremityBreakoutForm,
  msf_breakout: SfmaMsfBreakoutForm,
  mse_breakout: SfmaMseBreakoutForm,
  msr_breakout: SfmaMsrBreakoutForm,
  msf: SfmaMsfBreakoutForm,
  mse: SfmaMseBreakoutForm,
  msr_left: SfmaMsrBreakoutForm,
  msr_right: SfmaMsrBreakoutForm,
  sls_left: SfmaSlsBreakoutForm,
  sls_right: SfmaSlsBreakoutForm,
  arms_down_squat_breakout: ArmsDownSquatBreakoutForm,
  deep_squat: SfmaDeepSquatBreakoutForm
}

const deepClone = (value: any) => JSON.parse(JSON.stringify(value || {}))

const scrollToBreakoutCard = async (cardRef: any) => {
  await nextTick()
  const el = (cardRef?.value?.$el || cardRef?.value) as HTMLElement | undefined
  if (!el || typeof el.scrollIntoView !== 'function') {
    return
  }
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

type SfmaDedicatedBreakoutStatus = 'not_started' | 'in_progress' | 'completed' | 'skipped'

const normalizeBreakoutStatus4 = (
  status?: string,
  mapStoppedToCompleted = true
): SfmaDedicatedBreakoutStatus => {
  if (status === 'in_progress' || status === 'partial') return 'in_progress'
  if (status === 'completed') return 'completed'
  if (status === 'skipped') return 'skipped'
  if (status === 'stopped_due_to_pain') return mapStoppedToCompleted ? 'completed' : 'in_progress'
  return 'not_started'
}

const breakoutToCervical = (legacy?: Record<string, any>): SfmaCervicalFlexionBreakout => {
  const base = buildDefaultCervicalFlexionBreakout()
  if (!legacy || typeof legacy !== 'object') {
    return base
  }
  return {
    ...base,
    breakout_status: normalizeBreakoutStatus4(legacy.status),
    breakout_note: legacy.clinician_note || '',
    active_cervical_flexion_quality: '',
    active_cervical_flexion_pain: !!legacy.pain_present,
    active_cervical_flexion_rom_key: null,
    active_cervical_flexion_end_feel_note: '',
    passive_cervical_flexion_quality: '',
    passive_cervical_flexion_pain: false,
    passive_cervical_flexion_rom_key: null,
    passive_vs_active_difference: '',
    upper_cervical_flexion_observation: '',
    upper_cervical_note: '',
    compensation_patterns: [],
    compensation_other_note: '',
    related_region_influence: [],
    breakout_preliminary_direction: [],
    breakout_summary_text: legacy.findings || '',
    needs_manual_review: !!legacy.stop_due_to_pain
  }
}

const cervicalToLegacyBreakout = (cervical: SfmaCervicalFlexionBreakout) => {
  const row = buildDefaultSfmaBreakoutRecord()
  row.status = cervical.breakout_status
  row.findings = cervical.breakout_summary_text || cervical.breakout_note || ''
  row.rom_key_values = [
    cervical.active_cervical_flexion_rom_key != null ? `主动ROM:${cervical.active_cervical_flexion_rom_key}` : '',
    cervical.passive_cervical_flexion_rom_key != null ? `被动ROM:${cervical.passive_cervical_flexion_rom_key}` : ''
  ]
    .filter(Boolean)
    .join(' | ')
  row.pain_present = cervical.active_cervical_flexion_pain || cervical.passive_cervical_flexion_pain
  row.mobility_restriction_signs = cervical.breakout_preliminary_direction.includes('更偏活动度限制')
    ? '更偏活动度限制'
    : ''
  row.motor_control_signs = cervical.breakout_preliminary_direction.includes('更偏运动控制问题')
    ? '更偏运动控制问题'
    : ''
  row.asymmetry_signs = ''
  row.stop_due_to_pain = !!cervical.needs_manual_review
  row.stop_reason = cervical.breakout_note || ''
  row.clinician_note = cervical.breakout_note || ''
  return row
}

const breakoutToCervicalExtension = (legacy?: Record<string, any>): SfmaCervicalExtensionBreakout => {
  const base = buildDefaultCervicalExtensionBreakout()
  if (!legacy || typeof legacy !== 'object') {
    return base
  }
  return {
    ...base,
    breakout_status: normalizeBreakoutStatus4(legacy.status),
    breakout_note: legacy.clinician_note || '',
    active_cervical_extension_quality: '',
    active_cervical_extension_pain: !!legacy.pain_present,
    active_cervical_extension_rom_key: null,
    active_cervical_extension_end_feel_note: '',
    passive_cervical_extension_quality: '',
    passive_cervical_extension_pain: false,
    passive_cervical_extension_rom_key: null,
    passive_vs_active_difference: '',
    upper_cervical_extension_observation: '',
    upper_cervical_note: '',
    compensation_patterns: [],
    compensation_other_note: '',
    related_region_influence: [],
    breakout_preliminary_direction: [],
    breakout_summary_text: legacy.findings || '',
    needs_manual_review: !!legacy.stop_due_to_pain
  }
}

const cervicalExtensionToLegacyBreakout = (cervical: SfmaCervicalExtensionBreakout) => {
  const row = buildDefaultSfmaBreakoutRecord()
  row.status = cervical.breakout_status
  row.findings = cervical.breakout_summary_text || cervical.breakout_note || ''
  row.rom_key_values = [
    cervical.active_cervical_extension_rom_key != null ? `主动ROM:${cervical.active_cervical_extension_rom_key}` : '',
    cervical.passive_cervical_extension_rom_key != null ? `被动ROM:${cervical.passive_cervical_extension_rom_key}` : ''
  ]
    .filter(Boolean)
    .join(' | ')
  row.pain_present = cervical.active_cervical_extension_pain || cervical.passive_cervical_extension_pain
  row.mobility_restriction_signs = cervical.breakout_preliminary_direction.includes('更偏活动度限制')
    ? '更偏活动度限制'
    : ''
  row.motor_control_signs = cervical.breakout_preliminary_direction.includes('更偏运动控制问题')
    ? '更偏运动控制问题'
    : ''
  row.asymmetry_signs = ''
  row.stop_due_to_pain = !!cervical.needs_manual_review
  row.stop_reason = cervical.breakout_note || ''
  row.clinician_note = cervical.breakout_note || ''
  return row
}

const breakoutToCervicalRotation = (legacy?: Record<string, any>): SfmaCervicalRotationBreakout => {
  const base = buildDefaultCervicalRotationBreakout()
  if (!legacy || typeof legacy !== 'object') {
    return base
  }
  const sideFromLegacy = () => ({
    ...base.left,
    breakout_status: normalizeBreakoutStatus4(legacy.status),
    breakout_note: legacy.clinician_note || '',
    active_cervical_rotation_pain: !!legacy.pain_present,
    breakout_summary_text: legacy.findings || '',
    needs_manual_review: !!legacy.stop_due_to_pain
  })
  return {
    ...base,
    left: sideFromLegacy(),
    right: sideFromLegacy(),
    asymmetry_focus: legacy.asymmetry_signs || '',
    overall_note: legacy.clinician_note || ''
  }
}

const mergeRotationStatus = (left: string, right: string) => {
  const all = [left, right]
  if (left === 'completed' && right === 'completed') return 'completed'
  if (left === 'skipped' && right === 'skipped') return 'skipped'
  if (all.includes('in_progress') || all.includes('completed')) return 'in_progress'
  if (all.includes('skipped')) return 'skipped'
  return 'not_started'
}

const cervicalRotationToLegacyBreakout = (rotation: SfmaCervicalRotationBreakout) => {
  const row = buildDefaultSfmaBreakoutRecord()
  const left = rotation.left
  const right = rotation.right
  row.status = mergeRotationStatus(left.breakout_status, right.breakout_status) as any
  row.findings = [left.breakout_summary_text, right.breakout_summary_text, rotation.overall_note].filter(Boolean).join('；')
  row.rom_key_values = [
    left.active_cervical_rotation_rom_key != null ? `左主动ROM:${left.active_cervical_rotation_rom_key}` : '',
    left.passive_cervical_rotation_rom_key != null ? `左被动ROM:${left.passive_cervical_rotation_rom_key}` : '',
    right.active_cervical_rotation_rom_key != null ? `右主动ROM:${right.active_cervical_rotation_rom_key}` : '',
    right.passive_cervical_rotation_rom_key != null ? `右被动ROM:${right.passive_cervical_rotation_rom_key}` : ''
  ]
    .filter(Boolean)
    .join(' | ')
  row.pain_present =
    left.active_cervical_rotation_pain ||
    left.passive_cervical_rotation_pain ||
    right.active_cervical_rotation_pain ||
    right.passive_cervical_rotation_pain
  row.mobility_restriction_signs =
    left.breakout_preliminary_direction.includes('更偏活动度限制') ||
    right.breakout_preliminary_direction.includes('更偏活动度限制')
      ? '更偏活动度限制'
      : ''
  row.motor_control_signs =
    left.breakout_preliminary_direction.includes('更偏运动控制问题') ||
    right.breakout_preliminary_direction.includes('更偏运动控制问题')
      ? '更偏运动控制问题'
      : ''
  row.asymmetry_signs = rotation.asymmetry_focus || ''
  row.stop_due_to_pain = !!left.needs_manual_review || !!right.needs_manual_review
  row.stop_reason = [left.breakout_note, right.breakout_note].filter(Boolean).join('；')
  row.clinician_note = rotation.overall_note || row.stop_reason
  return row
}

const breakoutToUpperExtremityPattern1 = (
  legacyLeft?: Record<string, any>,
  legacyRight?: Record<string, any>
): SfmaUpperExtremityPattern1Breakout => {
  const base = buildDefaultUpperExtremityPattern1Breakout()
  const toSide = (legacy?: Record<string, any>) => ({
    ...buildDefaultUpperExtremityPattern1Breakout().left,
    breakout_status: normalizeBreakoutStatus4(legacy?.status),
    breakout_note: legacy?.clinician_note || '',
    active_ue_pattern1_pain: !!legacy?.pain_present,
    breakout_summary_text: legacy?.findings || '',
    needs_manual_review: !!legacy?.stop_due_to_pain
  })
  return {
    ...base,
    left: toSide(legacyLeft),
    right: toSide(legacyRight),
    asymmetry_focus:
      legacyLeft?.asymmetry_signs || legacyRight?.asymmetry_signs || '',
    overall_note: legacyLeft?.clinician_note || legacyRight?.clinician_note || ''
  }
}

const mergeUe1Status = (left: string, right: string) => {
  const all = [left, right]
  if (left === 'completed' && right === 'completed') return 'completed'
  if (left === 'skipped' && right === 'skipped') return 'skipped'
  if (all.includes('in_progress') || all.includes('completed')) return 'in_progress'
  if (all.includes('skipped')) return 'skipped'
  return 'not_started'
}

const upperExtremityPattern1ToLegacyBreakout = (pattern: SfmaUpperExtremityPattern1Breakout, side: 'left' | 'right') => {
  const row = buildDefaultSfmaBreakoutRecord()
  const left = pattern.left
  const right = pattern.right
  const target = side === 'left' ? left : right
  row.status = target.breakout_status
  row.findings = [target.breakout_summary_text, target.breakout_note].filter(Boolean).join('；')
  row.rom_key_values = target.active_ue_pattern1_rom_key != null ? `关键ROM:${target.active_ue_pattern1_rom_key}` : ''
  row.pain_present = !!target.active_ue_pattern1_pain
  row.mobility_restriction_signs = target.breakout_preliminary_direction.includes('更偏活动度限制')
    ? '更偏活动度限制'
    : ''
  row.motor_control_signs = target.breakout_preliminary_direction.includes('更偏运动控制问题')
    ? '更偏运动控制问题'
    : ''
  row.asymmetry_signs = pattern.asymmetry_focus || ''
  row.stop_due_to_pain = !!target.needs_manual_review
  row.stop_reason = target.breakout_note || ''
  row.clinician_note = target.breakout_note || pattern.overall_note || ''

  const merged = buildDefaultSfmaBreakoutRecord()
  merged.status = mergeUe1Status(left.breakout_status, right.breakout_status) as any
  merged.findings = [
    left.breakout_summary_text ? `左:${left.breakout_summary_text}` : '',
    right.breakout_summary_text ? `右:${right.breakout_summary_text}` : '',
    pattern.overall_note
  ]
    .filter(Boolean)
    .join('；')
  merged.rom_key_values = [
    left.active_ue_pattern1_rom_key != null ? `左关键ROM:${left.active_ue_pattern1_rom_key}` : '',
    right.active_ue_pattern1_rom_key != null ? `右关键ROM:${right.active_ue_pattern1_rom_key}` : ''
  ]
    .filter(Boolean)
    .join(' | ')
  merged.pain_present = !!left.active_ue_pattern1_pain || !!right.active_ue_pattern1_pain
  merged.mobility_restriction_signs =
    left.breakout_preliminary_direction.includes('更偏活动度限制') ||
    right.breakout_preliminary_direction.includes('更偏活动度限制')
      ? '更偏活动度限制'
      : ''
  merged.motor_control_signs =
    left.breakout_preliminary_direction.includes('更偏运动控制问题') ||
    right.breakout_preliminary_direction.includes('更偏运动控制问题')
      ? '更偏运动控制问题'
      : ''
  merged.asymmetry_signs = pattern.asymmetry_focus || ''
  merged.stop_due_to_pain = !!left.needs_manual_review || !!right.needs_manual_review
  merged.stop_reason = [left.breakout_note, right.breakout_note].filter(Boolean).join('；')
  merged.clinician_note = pattern.overall_note || merged.stop_reason
  return { sideLegacy: row, mergedLegacy: merged }
}

const breakoutToUpperExtremityPattern2 = (
  legacyLeft?: Record<string, any>,
  legacyRight?: Record<string, any>
): SfmaUpperExtremityPattern2Breakout => {
  const base = buildDefaultUpperExtremityPattern2Breakout()
  const toSide = (legacy?: Record<string, any>) => ({
    ...buildDefaultUpperExtremityPattern2Breakout().left,
    breakout_status: normalizeBreakoutStatus4(legacy?.status),
    breakout_note: legacy?.clinician_note || '',
    active_ue_pattern2_pain: !!legacy?.pain_present,
    breakout_summary_text: legacy?.findings || '',
    needs_manual_review: !!legacy?.stop_due_to_pain
  })
  return {
    ...base,
    left: toSide(legacyLeft),
    right: toSide(legacyRight),
    asymmetry_focus: legacyLeft?.asymmetry_signs || legacyRight?.asymmetry_signs || '',
    overall_note: legacyLeft?.clinician_note || legacyRight?.clinician_note || ''
  }
}

const mergeUe2Status = (left: string, right: string) => {
  const all = [left, right]
  if (left === 'completed' && right === 'completed') return 'completed'
  if (left === 'skipped' && right === 'skipped') return 'skipped'
  if (all.includes('in_progress') || all.includes('completed')) return 'in_progress'
  if (all.includes('skipped')) return 'skipped'
  return 'not_started'
}

const upperExtremityPattern2ToLegacyBreakout = (pattern: SfmaUpperExtremityPattern2Breakout, side: 'left' | 'right') => {
  const row = buildDefaultSfmaBreakoutRecord()
  const left = pattern.left
  const right = pattern.right
  const target = side === 'left' ? left : right
  row.status = target.breakout_status
  row.findings = [target.breakout_summary_text, target.breakout_note].filter(Boolean).join('；')
  row.rom_key_values = target.active_ue_pattern2_rom_key != null ? `关键ROM:${target.active_ue_pattern2_rom_key}` : ''
  row.pain_present = !!target.active_ue_pattern2_pain
  row.mobility_restriction_signs = target.breakout_preliminary_direction.includes('更偏活动度限制')
    ? '更偏活动度限制'
    : ''
  row.motor_control_signs = target.breakout_preliminary_direction.includes('更偏运动控制问题')
    ? '更偏运动控制问题'
    : ''
  row.asymmetry_signs = pattern.asymmetry_focus || ''
  row.stop_due_to_pain = !!target.needs_manual_review
  row.stop_reason = target.breakout_note || ''
  row.clinician_note = target.breakout_note || pattern.overall_note || ''

  const merged = buildDefaultSfmaBreakoutRecord()
  merged.status = mergeUe2Status(left.breakout_status, right.breakout_status) as any
  merged.findings = [
    left.breakout_summary_text ? `左:${left.breakout_summary_text}` : '',
    right.breakout_summary_text ? `右:${right.breakout_summary_text}` : '',
    pattern.overall_note
  ]
    .filter(Boolean)
    .join('；')
  merged.rom_key_values = [
    left.active_ue_pattern2_rom_key != null ? `左关键ROM:${left.active_ue_pattern2_rom_key}` : '',
    right.active_ue_pattern2_rom_key != null ? `右关键ROM:${right.active_ue_pattern2_rom_key}` : ''
  ]
    .filter(Boolean)
    .join(' | ')
  merged.pain_present = !!left.active_ue_pattern2_pain || !!right.active_ue_pattern2_pain
  merged.mobility_restriction_signs =
    left.breakout_preliminary_direction.includes('更偏活动度限制') ||
    right.breakout_preliminary_direction.includes('更偏活动度限制')
      ? '更偏活动度限制'
      : ''
  merged.motor_control_signs =
    left.breakout_preliminary_direction.includes('更偏运动控制问题') ||
    right.breakout_preliminary_direction.includes('更偏运动控制问题')
      ? '更偏运动控制问题'
      : ''
  merged.asymmetry_signs = pattern.asymmetry_focus || ''
  merged.stop_due_to_pain = !!left.needs_manual_review || !!right.needs_manual_review
  merged.stop_reason = [left.breakout_note, right.breakout_note].filter(Boolean).join('；')
  merged.clinician_note = pattern.overall_note || merged.stop_reason
  return { sideLegacy: row, mergedLegacy: merged }
}

const msrStatusToLegacy = (status: SfmaMsrBreakout['left']['breakout_status']): SfmaBreakoutRecord['status'] => {
  return status as SfmaBreakoutRecord['status']
}

const msrStatusFromLegacy = (status?: string): SfmaMsrBreakout['left']['breakout_status'] => {
  return normalizeBreakoutStatus4(status)
}

const breakoutToMsrBreakout = (
  legacyLeft?: Record<string, any>,
  legacyRight?: Record<string, any>,
  legacyMerged?: Record<string, any>
): SfmaMsrBreakout => {
  const base = buildDefaultMsrBreakout()
  const defaults = buildDefaultMsrBreakout()
  const fromLegacy = (legacy: Record<string, any> | undefined, side: 'left' | 'right') => ({
    ...(side === 'left' ? defaults.left : defaults.right),
    breakout_status: msrStatusFromLegacy(legacy?.status),
    breakout_reason_from_top_tier: '',
    breakout_note: legacy?.clinician_note || '',
    needs_manual_review: !!legacy?.stop_due_to_pain,
    active_rotation_pain: !!legacy?.pain_present,
    breakout_summary_text: legacy?.findings || ''
  })
  return {
    ...base,
    left: fromLegacy(legacyLeft, 'left'),
    right: fromLegacy(legacyRight, 'right'),
    asymmetry_focus:
      legacyMerged?.asymmetry_signs || legacyLeft?.asymmetry_signs || legacyRight?.asymmetry_signs || '',
    overall_note: legacyMerged?.clinician_note || ''
  }
}

const msrBreakoutSideToLegacy = (
  side: SfmaMsrBreakout['left'],
  fallbackAsymmetryFocus = '',
  overallNote = ''
) => {
  const row = buildDefaultSfmaBreakoutRecord()
  row.status = msrStatusToLegacy(side.breakout_status)
  row.findings = [side.breakout_summary_text, side.breakout_note].filter(Boolean).join('；')
  row.rom_key_values = side.rotation_range_key != null ? `关键旋转范围:${side.rotation_range_key}` : ''
  row.pain_present = !!side.active_rotation_pain
  row.pain_vas = null
  row.mobility_restriction_signs = side.breakout_preliminary_direction
    .filter((item) => ['更偏活动度限制', '更偏髋旋转参与不足', '更偏胸椎旋转不足'].includes(item))
    .join('、')
  row.motor_control_signs = side.breakout_preliminary_direction
    .filter((item) => ['更偏骨盆旋转控制差', '更偏腰椎代偿', '更偏运动控制问题'].includes(item))
    .join('、')
  row.asymmetry_signs = side.side_specific_priority || fallbackAsymmetryFocus || ''
  row.stop_due_to_pain =
    side.pain_control_priority_hint === '是，建议优先人工复核' || side.pain_dominant_pattern === '明显是'
  row.stop_reason = row.stop_due_to_pain ? side.breakout_note || side.pain_control_priority_hint : ''
  row.clinician_note = side.breakout_note || overallNote || ''
  return row
}

const mergeMsrBreakoutStatus = (
  left: SfmaMsrBreakout['left']['breakout_status'],
  right: SfmaMsrBreakout['right']['breakout_status']
) => {
  const all = [left, right]
  if (left === 'completed' && right === 'completed') return 'completed'
  if (left === 'skipped' && right === 'skipped') return 'skipped'
  if (all.includes('in_progress') || all.includes('completed')) return 'in_progress'
  if (all.includes('skipped')) return 'skipped'
  return 'not_started'
}

const msrBreakoutToLegacy = (msr: SfmaMsrBreakout) => {
  const leftLegacy = msrBreakoutSideToLegacy(msr.left, msr.asymmetry_focus, msr.overall_note)
  const rightLegacy = msrBreakoutSideToLegacy(msr.right, msr.asymmetry_focus, msr.overall_note)
  const merged = buildDefaultSfmaBreakoutRecord()
  merged.status = mergeMsrBreakoutStatus(msr.left.breakout_status, msr.right.breakout_status) as any
  merged.findings = [
    msr.left.breakout_summary_text ? `左旋:${msr.left.breakout_summary_text}` : '',
    msr.right.breakout_summary_text ? `右旋:${msr.right.breakout_summary_text}` : '',
    msr.overall_note
  ]
    .filter(Boolean)
    .join('；')
  merged.rom_key_values = [
    msr.left.rotation_range_key != null ? `左关键旋转:${msr.left.rotation_range_key}` : '',
    msr.right.rotation_range_key != null ? `右关键旋转:${msr.right.rotation_range_key}` : ''
  ]
    .filter(Boolean)
    .join(' | ')
  merged.pain_present = !!msr.left.active_rotation_pain || !!msr.right.active_rotation_pain
  merged.pain_vas = null
  merged.mobility_restriction_signs = [leftLegacy.mobility_restriction_signs, rightLegacy.mobility_restriction_signs]
    .filter(Boolean)
    .join('、')
  merged.motor_control_signs = [leftLegacy.motor_control_signs, rightLegacy.motor_control_signs]
    .filter(Boolean)
    .join('、')
  merged.asymmetry_signs = msr.asymmetry_focus || msr.left.side_specific_priority || msr.right.side_specific_priority || ''
  merged.stop_due_to_pain = leftLegacy.stop_due_to_pain || rightLegacy.stop_due_to_pain
  merged.stop_reason = [leftLegacy.stop_reason, rightLegacy.stop_reason].filter(Boolean).join('；')
  merged.clinician_note = msr.overall_note || merged.stop_reason
  return { leftLegacy, rightLegacy, mergedLegacy: merged }
}

const msfStatusToLegacy = (status: SfmaMsfBreakout['breakout_status']): SfmaBreakoutRecord['status'] => {
  return status as SfmaBreakoutRecord['status']
}

const msfStatusFromLegacy = (status?: string): SfmaMsfBreakout['breakout_status'] => {
  return normalizeBreakoutStatus4(status)
}

const breakoutToMsfBreakout = (
  legacy?: Record<string, any>,
  topTierReason = ''
): SfmaMsfBreakout => {
  const base = buildDefaultMsfBreakout()
  if (!legacy || typeof legacy !== 'object') {
    return { ...base, breakout_reason_from_top_tier: topTierReason || '' }
  }
  return {
    ...base,
    breakout_status: msfStatusFromLegacy(legacy.status),
    breakout_reason_from_top_tier: topTierReason || '',
    breakout_note: legacy.clinician_note || '',
    needs_manual_review: !!legacy.stop_due_to_pain,
    flow_algorithm_note: legacy.findings || '',
    breakout_summary_text: legacy.findings || '',
    clinical_meaning_hint: '',
    training_direction_hint: '',
    reassessment_priority: 'medium',
    msf_analysis: {
      ...base.msf_analysis,
      summary: {
        ...base.msf_analysis.summary,
        stop_and_treat_pain: !!legacy.stop_due_to_pain,
        manual_review_required: !!legacy.stop_due_to_pain,
        summary_text: legacy.findings || ''
      }
    }
  }
}

const msfBreakoutToLegacy = (msf: SfmaMsfBreakout): SfmaBreakoutRecord => {
  const row = buildDefaultSfmaBreakoutRecord()
  const analysisSummary = msf.msf_analysis?.summary || {}
  const analysisSummaryText = (analysisSummary.summary_text as string) || ''
  row.status = msfStatusToLegacy(msf.breakout_status)
  row.findings = [
    msf.breakout_summary_text,
    msf.flow_algorithm_note,
    analysisSummaryText,
    msf.breakout_note
  ]
    .filter(Boolean)
    .join('；')
  row.rom_key_values = [
    msf.fingertips_to_floor_distance_cm != null ? `指尖距地:${msf.fingertips_to_floor_distance_cm}cm` : '',
    msf.long_sit_sacral_angle_deg != null ? `长坐骶骨角:${msf.long_sit_sacral_angle_deg}°` : '',
    msf.aslr_left_deg != null ? `ASLR左:${msf.aslr_left_deg}°` : '',
    msf.aslr_right_deg != null ? `ASLR右:${msf.aslr_right_deg}°` : '',
    msf.pslr_left_deg != null ? `PSLR左:${msf.pslr_left_deg}°` : '',
    msf.pslr_right_deg != null ? `PSLR右:${msf.pslr_right_deg}°` : ''
  ]
    .filter(Boolean)
    .join(' | ')
  row.pain_present =
    !!msf.active_flexion_pain ||
    ['FP', 'DP'].includes(msf.long_sit_toe_touch_result) ||
    ['FP', 'DP'].includes(msf.rolling_result) ||
    ['FP', 'DP'].includes(msf.aslr_result) ||
    ['FP', 'DP'].includes(msf.pslr_result) ||
    ['FP', 'DP'].includes(msf.prone_rock_back_result) ||
    ['FP', 'DP'].includes(msf.supine_knees_to_chest_result)
  row.pain_vas = null
  row.mobility_restriction_signs = [
    msf.breakout_preliminary_direction.includes('更偏活动度限制') ? '更偏活动度限制' : '',
    msf.breakout_preliminary_direction.includes('更偏后侧链张力限制') ? '后侧链张力限制' : '',
    msf.breakout_preliminary_direction.includes('更偏髋/骨盆参与不足') ? '髋/骨盆参与不足' : '',
    msf.long_sit_sacral_angle_status === '受限(<80°)' ? '骶骨角受限' : '',
    ['DN', 'FP', 'DP'].includes(msf.aslr_result) ? 'ASLR异常' : '',
    ['DN', 'FP', 'DP'].includes(msf.pslr_result) ? 'PSLR异常' : ''
  ]
    .filter(Boolean)
    .join('、')
  row.motor_control_signs = [
    msf.breakout_preliminary_direction.includes('更偏运动控制问题') ? '更偏运动控制问题' : '',
    msf.single_leg_standing_forward_flexion_result === '单侧功能障碍或疼痛' ? '单侧控制异常' : '',
    msf.pslr_vs_aslr_interpretation === 'PSLR<80°且比ASLR大10°以上' ? '主动控制不足线索' : ''
  ]
    .filter(Boolean)
    .join('、')
  row.asymmetry_signs =
    msf.left_right_asymmetry_focus && msf.left_right_asymmetry_focus !== '无明显左右差'
      ? msf.left_right_asymmetry_focus
      : msf.single_leg_standing_forward_flexion_asymmetry &&
          msf.single_leg_standing_forward_flexion_asymmetry !== '无明显左右差'
        ? msf.single_leg_standing_forward_flexion_asymmetry
      : ''
  row.stop_due_to_pain =
    msf.flow_next_step === '停止并优先处理疼痛' ||
    !!analysisSummary.stop_and_treat_pain ||
    msf.pain_control_priority_hint === '是，建议优先人工复核' ||
    ['FP', 'DP'].includes(msf.prone_rock_back_result) ||
    ['FP', 'DP'].includes(msf.supine_knees_to_chest_result)
  row.stop_reason = row.stop_due_to_pain ? msf.flow_next_step || msf.pain_control_priority_hint || '' : ''
  row.clinician_note = msf.breakout_note || ''
  return row
}

const mseStatusToLegacy = (status: SfmaMseBreakout['breakout_status']): SfmaBreakoutRecord['status'] => {
  return status as SfmaBreakoutRecord['status']
}

const mseStatusFromLegacy = (status?: string): SfmaMseBreakout['breakout_status'] => {
  return normalizeBreakoutStatus4(status)
}

const breakoutToMseBreakout = (
  legacy?: Record<string, any>,
  topTierReason = ''
): SfmaMseBreakout => {
  const base = buildDefaultMseBreakout()
  if (!legacy || typeof legacy !== 'object') {
    return { ...base, breakout_reason_from_top_tier: topTierReason || '' }
  }
  return {
    ...base,
    breakout_status: mseStatusFromLegacy(legacy.status),
    breakout_reason_from_top_tier: topTierReason || '',
    breakout_note: legacy.clinician_note || '',
    needs_manual_review: !!legacy.stop_due_to_pain,
    breakout_summary_text: legacy.findings || '',
    clinical_meaning_hint: '',
    training_direction_hint: '',
    reassessment_priority: 'medium'
  }
}

const mseBreakoutToLegacy = (mse: SfmaMseBreakout): SfmaBreakoutRecord => {
  const row = buildDefaultSfmaBreakoutRecord()
  row.status = mseStatusToLegacy(mse.breakout_status)
  row.findings = mse.breakout_summary_text || mse.breakout_note || ''
  row.rom_key_values = ''
  row.pain_present = !!mse.active_extension_pain
  row.pain_vas = null
  row.mobility_restriction_signs = mse.breakout_preliminary_direction
    .filter((item) =>
      ['更偏活动度限制', '更偏髋伸展不足', '更偏胸椎伸展不足', '更偏肩带/上肢参与不足'].includes(item)
    )
    .join('、')
  row.motor_control_signs = mse.breakout_preliminary_direction
    .filter((item) => ['更偏腰盆控制问题', '更偏运动控制问题'].includes(item))
    .join('、')
  row.asymmetry_signs =
    mse.left_right_asymmetry_focus && mse.left_right_asymmetry_focus !== '无明显左右差'
      ? mse.left_right_asymmetry_focus
      : ''
  row.stop_due_to_pain =
    mse.pain_control_priority_hint === '是，建议优先人工复核' || mse.pain_dominant_pattern === '明显是'
  row.stop_reason = mse.pain_control_priority_hint || ''
  row.clinician_note = mse.breakout_note || ''
  return row
}

const armsDownSquatStatusToLegacy = (
  status: SfmaArmsDownSquatBreakout['breakout_status']
): SfmaBreakoutRecord['status'] => {
  return status as SfmaBreakoutRecord['status']
}

const armsDownSquatStatusFromLegacy = (
  status?: string
): SfmaArmsDownSquatBreakout['breakout_status'] => {
  return normalizeBreakoutStatus4(status)
}

const breakoutToArmsDownSquatBreakout = (
  legacy?: Record<string, any>,
  topTierReason = ''
): SfmaArmsDownSquatBreakout => {
  const base = buildDefaultArmsDownSquatBreakout()
  if (!legacy || typeof legacy !== 'object') {
    return { ...base, breakout_reason_from_screening: topTierReason || '' }
  }
  return {
    ...base,
    breakout_status: armsDownSquatStatusFromLegacy(legacy.status),
    breakout_reason_from_screening: topTierReason || '',
    breakout_note: legacy.clinician_note || '',
    needs_manual_review: !!legacy.stop_due_to_pain,
    breakout_summary_text: legacy.findings || ''
  }
}

const armsDownSquatBreakoutToLegacy = (breakout: SfmaArmsDownSquatBreakout): SfmaBreakoutRecord => {
  const row = buildDefaultSfmaBreakoutRecord()
  row.status = armsDownSquatStatusToLegacy(breakout.breakout_status)
  row.findings = breakout.breakout_summary_text || breakout.breakout_note || ''
  row.rom_key_values = ''
  row.pain_present = !!breakout.pain_present
  row.pain_vas = breakout.pain_vas ?? null
  row.mobility_restriction_signs = breakout.breakout_preliminary_direction
    .filter((item) => ['更偏踝活动度限制', '更偏髋活动度限制'].includes(item))
    .join('、')
  row.motor_control_signs = breakout.breakout_preliminary_direction
    .filter((item) =>
      ['更偏足踝稳定不足', '更偏膝对线/控制问题', '更偏髋控制问题', '更偏骨盆/LPHC控制问题', '更偏躯干控制问题'].includes(item)
    )
    .join('、')
  row.asymmetry_signs =
    breakout.left_right_asymmetry_global && breakout.left_right_asymmetry_global !== '无明显左右差'
      ? breakout.left_right_asymmetry_global
      : ''
  row.stop_due_to_pain =
    breakout.pain_control_priority_hint === '是，建议优先人工复核' || breakout.pain_dominant_pattern === '明显是'
  row.stop_reason = row.stop_due_to_pain ? breakout.breakout_note || breakout.pain_control_priority_hint : ''
  row.clinician_note = breakout.breakout_note || ''
  return row
}

const mergeDefault = (value?: Record<string, any>): SfmaFormData => {
  const base = buildDefaultSfmaFormData()
  if (!value || typeof value !== 'object') {
    return base
  }
  const nestedSfma = (value as any).sfma
  if (nestedSfma && typeof nestedSfma === 'object') {
    value = nestedSfma as Record<string, any>
  }
  const merged = deepClone(base)
  if (value.basic_info && typeof value.basic_info === 'object') {
    Object.assign(merged.basic_info, value.basic_info)
  }
  if (value.top_tier && typeof value.top_tier === 'object') {
    const sourceTopTier = value.top_tier
    Object.keys(merged.top_tier).forEach((key) => {
      merged.top_tier[key] = {
        ...buildDefaultSfmaTopTierRecord(
          SFMA_TOP_TIER_DEFINITIONS.find((item) => item.test_code === key) || SFMA_TOP_TIER_DEFINITIONS[0]
        ),
        ...(sourceTopTier[key] || {})
      }
    })
  }
  if (Array.isArray(value.breakout_recommendations)) {
    merged.breakout_recommendations = deepClone(value.breakout_recommendations)
  }
  if (value.breakouts && typeof value.breakouts === 'object') {
    const sourceBreakouts = value.breakouts
    Object.keys(merged.breakouts).forEach((key) => {
      merged.breakouts[key] = {
        ...buildDefaultSfmaBreakoutRecord(),
        ...(sourceBreakouts[key] || {})
      }
    })
  }
  if (value.book_protocol && typeof value.book_protocol === 'object') {
    merged.book_protocol = deepClone(value.book_protocol)
  }
  const legacyCervicalTopTier = value.top_tier?.cervical_flexion
  const legacyCervicalExtensionTopTier = value.top_tier?.cervical_extension
  merged.cervical_flexion_top_tier = applyCervicalFlexionTopTierRules(
    value.cervical_flexion_top_tier || {
      classification: legacyCervicalTopTier?.classification || '',
      pain_present: !!legacyCervicalTopTier?.pain_present,
      pain_vas: legacyCervicalTopTier?.pain_vas ?? null,
      top_tier_note: legacyCervicalTopTier?.clinician_note || '',
      needs_breakout_suggestion: !!legacyCervicalTopTier?.needs_breakout_suggestion,
      breakout_target: legacyCervicalTopTier?.needs_breakout_suggestion ? 'cervical_flexion_breakout' : '',
      breakout_reason_text: legacyCervicalTopTier?.breakout_reason_text || '',
      review_priority:
        legacyCervicalTopTier?.review_priority === 'high'
          ? 'high'
          : legacyCervicalTopTier?.review_priority === 'low'
            ? 'low'
            : 'medium'
    }
  )
  merged.cervical_flexion_breakout = {
    ...buildDefaultCervicalFlexionBreakout(),
    ...(value.cervical_flexion_breakout || breakoutToCervical(value.breakouts?.cervical_flexion_breakout))
  }
  merged.cervical_extension_top_tier = applyCervicalExtensionTopTierRules(
    value.cervical_extension_top_tier || {
      classification: legacyCervicalExtensionTopTier?.classification || '',
      pain_present: !!legacyCervicalExtensionTopTier?.pain_present,
      pain_vas: legacyCervicalExtensionTopTier?.pain_vas ?? null,
      top_tier_note: legacyCervicalExtensionTopTier?.clinician_note || '',
      needs_breakout_suggestion: !!legacyCervicalExtensionTopTier?.needs_breakout_suggestion,
      breakout_target: legacyCervicalExtensionTopTier?.needs_breakout_suggestion ? 'cervical_extension_breakout' : '',
      breakout_reason_text: legacyCervicalExtensionTopTier?.breakout_reason_text || '',
      review_priority:
        legacyCervicalExtensionTopTier?.review_priority === 'high'
          ? 'high'
          : legacyCervicalExtensionTopTier?.review_priority === 'low'
            ? 'low'
            : 'medium'
    }
  )
  merged.cervical_extension_breakout = {
    ...buildDefaultCervicalExtensionBreakout(),
    ...(value.cervical_extension_breakout || breakoutToCervicalExtension(value.breakouts?.cervical_extension_breakout))
  }
  const legacyRotationTopLeft = value.top_tier?.cervical_rotation_left
  const legacyRotationTopRight = value.top_tier?.cervical_rotation_right
  merged.cervical_rotation_top_tier = applyCervicalRotationTopTierRules(
    value.cervical_rotation_top_tier || {
      left: {
        classification: legacyRotationTopLeft?.classification || '',
        pain_present: !!legacyRotationTopLeft?.pain_present,
        pain_vas: legacyRotationTopLeft?.pain_vas ?? null,
        top_tier_note: legacyRotationTopLeft?.clinician_note || '',
        breakout_reason_text: legacyRotationTopLeft?.breakout_reason_text || '',
        review_priority:
          legacyRotationTopLeft?.review_priority === 'high'
            ? 'high'
            : legacyRotationTopLeft?.review_priority === 'low'
              ? 'low'
              : 'medium'
      },
      right: {
        classification: legacyRotationTopRight?.classification || '',
        pain_present: !!legacyRotationTopRight?.pain_present,
        pain_vas: legacyRotationTopRight?.pain_vas ?? null,
        top_tier_note: legacyRotationTopRight?.clinician_note || '',
        breakout_reason_text: legacyRotationTopRight?.breakout_reason_text || '',
        review_priority:
          legacyRotationTopRight?.review_priority === 'high'
            ? 'high'
            : legacyRotationTopRight?.review_priority === 'low'
              ? 'low'
              : 'medium'
      }
    }
  )
  merged.cervical_rotation_breakout = {
    ...buildDefaultCervicalRotationBreakout(),
    ...(value.cervical_rotation_breakout ||
      breakoutToCervicalRotation(value.breakouts?.cervical_rotation_breakout || value.breakouts?.cervical_pattern))
  }
  const legacyUe1LeftTop = value.top_tier?.upper_extremity_pattern1_left
  const legacyUe1RightTop = value.top_tier?.upper_extremity_pattern1_right
  merged.upper_extremity_pattern1_top_tier = applyUpperExtremityPattern1TopTierRules(
    value.upper_extremity_pattern1_top_tier || {
      left: {
        classification: legacyUe1LeftTop?.classification || '',
        pain_present: !!legacyUe1LeftTop?.pain_present,
        pain_vas: legacyUe1LeftTop?.pain_vas ?? null,
        top_tier_note: legacyUe1LeftTop?.clinician_note || '',
        breakout_reason_text: legacyUe1LeftTop?.breakout_reason_text || '',
        review_priority:
          legacyUe1LeftTop?.review_priority === 'high'
            ? 'high'
            : legacyUe1LeftTop?.review_priority === 'low'
              ? 'low'
              : 'medium'
      },
      right: {
        classification: legacyUe1RightTop?.classification || '',
        pain_present: !!legacyUe1RightTop?.pain_present,
        pain_vas: legacyUe1RightTop?.pain_vas ?? null,
        top_tier_note: legacyUe1RightTop?.clinician_note || '',
        breakout_reason_text: legacyUe1RightTop?.breakout_reason_text || '',
        review_priority:
          legacyUe1RightTop?.review_priority === 'high'
            ? 'high'
            : legacyUe1RightTop?.review_priority === 'low'
              ? 'low'
              : 'medium'
      }
    }
  )
  const legacyUe1CombinedBreakout = value.breakouts?.upper_extremity_pattern1_breakout
  merged.upper_extremity_pattern1_breakout = {
    ...buildDefaultUpperExtremityPattern1Breakout(),
    ...(value.upper_extremity_pattern1_breakout ||
      breakoutToUpperExtremityPattern1(
        value.breakouts?.upper_extremity_pattern_left || legacyUe1CombinedBreakout,
        value.breakouts?.upper_extremity_pattern_right || legacyUe1CombinedBreakout
      ))
  }
  const legacyUe2LeftTop = value.top_tier?.upper_extremity_pattern2_left
  const legacyUe2RightTop = value.top_tier?.upper_extremity_pattern2_right
  merged.upper_extremity_pattern2_top_tier = applyUpperExtremityPattern2TopTierRules(
    value.upper_extremity_pattern2_top_tier || {
      left: {
        classification: legacyUe2LeftTop?.classification || '',
        pain_present: !!legacyUe2LeftTop?.pain_present,
        pain_vas: legacyUe2LeftTop?.pain_vas ?? null,
        top_tier_note: legacyUe2LeftTop?.clinician_note || '',
        breakout_reason_text: legacyUe2LeftTop?.breakout_reason_text || '',
        review_priority:
          legacyUe2LeftTop?.review_priority === 'high'
            ? 'high'
            : legacyUe2LeftTop?.review_priority === 'low'
              ? 'low'
              : 'medium'
      },
      right: {
        classification: legacyUe2RightTop?.classification || '',
        pain_present: !!legacyUe2RightTop?.pain_present,
        pain_vas: legacyUe2RightTop?.pain_vas ?? null,
        top_tier_note: legacyUe2RightTop?.clinician_note || '',
        breakout_reason_text: legacyUe2RightTop?.breakout_reason_text || '',
        review_priority:
          legacyUe2RightTop?.review_priority === 'high'
            ? 'high'
            : legacyUe2RightTop?.review_priority === 'low'
              ? 'low'
              : 'medium'
      }
    }
  )
  const legacyUe2CombinedBreakout = value.breakouts?.upper_extremity_pattern2_breakout
  merged.upper_extremity_pattern2_breakout = {
    ...buildDefaultUpperExtremityPattern2Breakout(),
    ...(value.upper_extremity_pattern2_breakout ||
      breakoutToUpperExtremityPattern2(
        value.breakouts?.upper_extremity_pattern_left || legacyUe2CombinedBreakout,
        value.breakouts?.upper_extremity_pattern_right || legacyUe2CombinedBreakout
      ))
  }
  const legacyMsfTop = value.top_tier?.multi_segmental_flexion
  merged.msf_breakout = {
    ...buildDefaultMsfBreakout(),
    ...(value.msf_breakout || breakoutToMsfBreakout(value.breakouts?.msf_breakout || value.breakouts?.msf, legacyMsfTop?.breakout_reason_text || ''))
  }
  const legacyMseTop = value.top_tier?.multi_segmental_extension
  merged.mse_breakout = {
    ...buildDefaultMseBreakout(),
    ...(value.mse_breakout ||
      breakoutToMseBreakout(value.breakouts?.mse_breakout || value.breakouts?.mse, legacyMseTop?.breakout_reason_text || ''))
  }
  const legacyArmsDownTop = value.top_tier?.arms_down_deep_squat
  merged.arms_down_squat_breakout = {
    ...buildDefaultArmsDownSquatBreakout(),
    ...(value.arms_down_squat_breakout ||
      breakoutToArmsDownSquatBreakout(
        value.breakouts?.arms_down_squat_breakout || value.breakouts?.deep_squat,
        legacyArmsDownTop?.breakout_reason_text || ''
      ))
  }
  merged.msr_breakout = {
    ...buildDefaultMsrBreakout(),
    ...(value.msr_breakout ||
      breakoutToMsrBreakout(
        value.breakouts?.msr_left,
        value.breakouts?.msr_right,
        value.breakouts?.msr_breakout
      ))
  }
  const defaultAnalysisFlows = deepClone(base.analysis_flows || {})
  const incomingAnalysisFlows =
    value.analysis_flows && typeof value.analysis_flows === 'object' ? deepClone(value.analysis_flows) : {}
  merged.analysis_flows = {
    ...defaultAnalysisFlows,
    ...incomingAnalysisFlows,
    msf_analysis: deepClone(
      incomingAnalysisFlows.msf_analysis || value.msf_breakout?.msf_analysis || merged.msf_breakout?.msf_analysis || defaultAnalysisFlows.msf_analysis
    ),
    mse_analysis: deepClone(
      incomingAnalysisFlows.mse_analysis || value.mse_breakout?.mse_analysis || merged.mse_breakout?.mse_analysis || defaultAnalysisFlows.mse_analysis
    )
  }
  if (value.summary && typeof value.summary === 'object') {
    merged.summary = deepClone(value.summary)
  }
  if (value.risk_precheck && typeof value.risk_precheck === 'object') {
    merged.risk_precheck = deepClone(value.risk_precheck)
  }
  if (value.report_mapping && typeof value.report_mapping === 'object') {
    merged.report_mapping = deepClone(value.report_mapping)
  }
  return merged
}

const localData = reactive<SfmaFormData>(buildDefaultSfmaFormData())
const syncingFromChildEmitUntil = ref(0)
let syncWindowTimer: ReturnType<typeof setTimeout> | undefined
let lastEmittedModelValue: Record<string, any> | undefined

const isInEmitSyncWindow = () => Date.now() < syncingFromChildEmitUntil.value
const markEmitSyncWindow = (durationMs = 180) => {
  syncingFromChildEmitUntil.value = Date.now() + durationMs
  if (syncWindowTimer) {
    clearTimeout(syncWindowTimer)
  }
  syncWindowTimer = setTimeout(() => {
    syncingFromChildEmitUntil.value = 0
  }, durationMs)
}

onBeforeUnmount(() => {
  if (syncWindowTimer) {
    clearTimeout(syncWindowTimer)
  }
})

const injectAssessmentBaseInfo = () => {
  const baseInfo = props.assessmentBaseInfo || {}
  if (!localData.basic_info.name && baseInfo.name) {
    localData.basic_info.name = baseInfo.name
  }
  if (!localData.basic_info.age && baseInfo.age != null) {
    localData.basic_info.age = Number(baseInfo.age)
  }
  if (!localData.basic_info.assessment_date && baseInfo.assessment_date) {
    localData.basic_info.assessment_date = String(baseInfo.assessment_date)
  }
  if (!localData.basic_info.assessor && baseInfo.assessor) {
    localData.basic_info.assessor = String(baseInfo.assessor)
  }
}

const syncCervicalToLegacy = () => {
  const cervicalTop = applyCervicalFlexionTopTierRules(localData.cervical_flexion_top_tier)
  localData.cervical_flexion_top_tier = cervicalTop

  const legacyTop = localData.top_tier.cervical_flexion || buildDefaultSfmaTopTierRecord(
    SFMA_TOP_TIER_DEFINITIONS.find((item) => item.test_code === 'cervical_flexion') || SFMA_TOP_TIER_DEFINITIONS[0]
  )
  legacyTop.classification = cervicalTop.classification
  legacyTop.pain_present = cervicalTop.pain_present
  legacyTop.pain_vas = cervicalTop.pain_vas
  legacyTop.needs_breakout_suggestion = cervicalTop.needs_breakout_suggestion
  legacyTop.breakout_reason_text = cervicalTop.breakout_reason_text
  legacyTop.clinician_note = cervicalTop.top_tier_note
  legacyTop.review_priority =
    cervicalTop.review_priority === 'medium' ? 'normal' : cervicalTop.review_priority
  legacyTop.caution_text =
    cervicalTop.classification === 'FP' || cervicalTop.classification === 'DP'
      ? '优先疼痛管理/谨慎继续分解'
      : ''
  localData.top_tier.cervical_flexion = legacyTop

  const cervicalBreakout = {
    ...buildDefaultCervicalFlexionBreakout(),
    ...localData.cervical_flexion_breakout
  }
  localData.cervical_flexion_breakout = cervicalBreakout
  const legacyBreakout = cervicalToLegacyBreakout(cervicalBreakout)
  localData.breakouts.cervical_flexion_breakout = legacyBreakout

  const cervicalExtensionTop = applyCervicalExtensionTopTierRules(localData.cervical_extension_top_tier)
  localData.cervical_extension_top_tier = cervicalExtensionTop

  const legacyExtensionTop = localData.top_tier.cervical_extension || buildDefaultSfmaTopTierRecord(
    SFMA_TOP_TIER_DEFINITIONS.find((item) => item.test_code === 'cervical_extension') || SFMA_TOP_TIER_DEFINITIONS[0]
  )
  legacyExtensionTop.classification = cervicalExtensionTop.classification
  legacyExtensionTop.pain_present = cervicalExtensionTop.pain_present
  legacyExtensionTop.pain_vas = cervicalExtensionTop.pain_vas
  legacyExtensionTop.needs_breakout_suggestion = cervicalExtensionTop.needs_breakout_suggestion
  legacyExtensionTop.breakout_reason_text = cervicalExtensionTop.breakout_reason_text
  legacyExtensionTop.clinician_note = cervicalExtensionTop.top_tier_note
  legacyExtensionTop.review_priority =
    cervicalExtensionTop.review_priority === 'medium' ? 'normal' : cervicalExtensionTop.review_priority
  legacyExtensionTop.caution_text =
    cervicalExtensionTop.classification === 'FP' || cervicalExtensionTop.classification === 'DP'
      ? '优先疼痛管理/谨慎继续分解'
      : ''
  localData.top_tier.cervical_extension = legacyExtensionTop

  const cervicalExtensionBreakout = {
    ...buildDefaultCervicalExtensionBreakout(),
    ...localData.cervical_extension_breakout
  }
  localData.cervical_extension_breakout = cervicalExtensionBreakout
  const legacyExtensionBreakout = cervicalExtensionToLegacyBreakout(cervicalExtensionBreakout)
  localData.breakouts.cervical_extension_breakout = legacyExtensionBreakout

  const cervicalRotationTop = applyCervicalRotationTopTierRules(localData.cervical_rotation_top_tier)
  localData.cervical_rotation_top_tier = cervicalRotationTop

  const legacyRotationLeft = localData.top_tier.cervical_rotation_left || buildDefaultSfmaTopTierRecord(
    SFMA_TOP_TIER_DEFINITIONS.find((item) => item.test_code === 'cervical_rotation_left') || SFMA_TOP_TIER_DEFINITIONS[0]
  )
  legacyRotationLeft.classification = cervicalRotationTop.left.classification
  legacyRotationLeft.pain_present = cervicalRotationTop.left.pain_present
  legacyRotationLeft.pain_vas = cervicalRotationTop.left.pain_vas
  legacyRotationLeft.needs_breakout_suggestion = cervicalRotationTop.left.needs_breakout_suggestion
  legacyRotationLeft.breakout_reason_text = cervicalRotationTop.left.breakout_reason_text
  legacyRotationLeft.clinician_note = cervicalRotationTop.left.top_tier_note
  legacyRotationLeft.review_priority =
    cervicalRotationTop.left.review_priority === 'medium' ? 'normal' : cervicalRotationTop.left.review_priority
  legacyRotationLeft.caution_text =
    cervicalRotationTop.left.classification === 'FP' || cervicalRotationTop.left.classification === 'DP'
      ? '优先疼痛管理/谨慎继续分解'
      : ''
  localData.top_tier.cervical_rotation_left = legacyRotationLeft

  const legacyRotationRight = localData.top_tier.cervical_rotation_right || buildDefaultSfmaTopTierRecord(
    SFMA_TOP_TIER_DEFINITIONS.find((item) => item.test_code === 'cervical_rotation_right') || SFMA_TOP_TIER_DEFINITIONS[0]
  )
  legacyRotationRight.classification = cervicalRotationTop.right.classification
  legacyRotationRight.pain_present = cervicalRotationTop.right.pain_present
  legacyRotationRight.pain_vas = cervicalRotationTop.right.pain_vas
  legacyRotationRight.needs_breakout_suggestion = cervicalRotationTop.right.needs_breakout_suggestion
  legacyRotationRight.breakout_reason_text = cervicalRotationTop.right.breakout_reason_text
  legacyRotationRight.clinician_note = cervicalRotationTop.right.top_tier_note
  legacyRotationRight.review_priority =
    cervicalRotationTop.right.review_priority === 'medium' ? 'normal' : cervicalRotationTop.right.review_priority
  legacyRotationRight.caution_text =
    cervicalRotationTop.right.classification === 'FP' || cervicalRotationTop.right.classification === 'DP'
      ? '优先疼痛管理/谨慎继续分解'
      : ''
  localData.top_tier.cervical_rotation_right = legacyRotationRight

  const cervicalRotationBreakout = {
    ...buildDefaultCervicalRotationBreakout(),
    ...localData.cervical_rotation_breakout
  }
  localData.cervical_rotation_breakout = cervicalRotationBreakout
  const legacyRotationBreakout = cervicalRotationToLegacyBreakout(cervicalRotationBreakout)
  localData.breakouts.cervical_rotation_breakout = legacyRotationBreakout
  // 兼容历史数据：旋转专项聚合镜像到旧 cervical_pattern
  localData.breakouts.cervical_pattern = { ...buildDefaultSfmaBreakoutRecord(), ...legacyRotationBreakout }

  const upperExtremityPattern1Top = applyUpperExtremityPattern1TopTierRules(localData.upper_extremity_pattern1_top_tier)
  localData.upper_extremity_pattern1_top_tier = upperExtremityPattern1Top
  const legacyUe1Left = localData.top_tier.upper_extremity_pattern1_left || buildDefaultSfmaTopTierRecord(
    SFMA_TOP_TIER_DEFINITIONS.find((item) => item.test_code === 'upper_extremity_pattern1_left') || SFMA_TOP_TIER_DEFINITIONS[0]
  )
  legacyUe1Left.classification = upperExtremityPattern1Top.left.classification
  legacyUe1Left.pain_present = upperExtremityPattern1Top.left.pain_present
  legacyUe1Left.pain_vas = upperExtremityPattern1Top.left.pain_vas
  legacyUe1Left.needs_breakout_suggestion = upperExtremityPattern1Top.left.needs_breakout_suggestion
  legacyUe1Left.breakout_reason_text = upperExtremityPattern1Top.left.breakout_reason_text
  legacyUe1Left.clinician_note = upperExtremityPattern1Top.left.top_tier_note
  legacyUe1Left.review_priority =
    upperExtremityPattern1Top.left.review_priority === 'medium' ? 'normal' : upperExtremityPattern1Top.left.review_priority
  legacyUe1Left.caution_text =
    upperExtremityPattern1Top.left.classification === 'FP' || upperExtremityPattern1Top.left.classification === 'DP'
      ? '优先疼痛管理/谨慎继续分解'
      : ''
  localData.top_tier.upper_extremity_pattern1_left = legacyUe1Left

  const legacyUe1Right = localData.top_tier.upper_extremity_pattern1_right || buildDefaultSfmaTopTierRecord(
    SFMA_TOP_TIER_DEFINITIONS.find((item) => item.test_code === 'upper_extremity_pattern1_right') || SFMA_TOP_TIER_DEFINITIONS[0]
  )
  legacyUe1Right.classification = upperExtremityPattern1Top.right.classification
  legacyUe1Right.pain_present = upperExtremityPattern1Top.right.pain_present
  legacyUe1Right.pain_vas = upperExtremityPattern1Top.right.pain_vas
  legacyUe1Right.needs_breakout_suggestion = upperExtremityPattern1Top.right.needs_breakout_suggestion
  legacyUe1Right.breakout_reason_text = upperExtremityPattern1Top.right.breakout_reason_text
  legacyUe1Right.clinician_note = upperExtremityPattern1Top.right.top_tier_note
  legacyUe1Right.review_priority =
    upperExtremityPattern1Top.right.review_priority === 'medium' ? 'normal' : upperExtremityPattern1Top.right.review_priority
  legacyUe1Right.caution_text =
    upperExtremityPattern1Top.right.classification === 'FP' || upperExtremityPattern1Top.right.classification === 'DP'
      ? '优先疼痛管理/谨慎继续分解'
      : ''
  localData.top_tier.upper_extremity_pattern1_right = legacyUe1Right

  const upperExtremityPattern1Breakout = {
    ...buildDefaultUpperExtremityPattern1Breakout(),
    ...localData.upper_extremity_pattern1_breakout
  }
  localData.upper_extremity_pattern1_breakout = upperExtremityPattern1Breakout
  const mergedLegacy = upperExtremityPattern1ToLegacyBreakout(upperExtremityPattern1Breakout, 'left').mergedLegacy
  localData.breakouts.upper_extremity_pattern1_breakout = mergedLegacy

  const upperExtremityPattern2Top = applyUpperExtremityPattern2TopTierRules(localData.upper_extremity_pattern2_top_tier)
  localData.upper_extremity_pattern2_top_tier = upperExtremityPattern2Top
  const legacyUe2Left = localData.top_tier.upper_extremity_pattern2_left || buildDefaultSfmaTopTierRecord(
    SFMA_TOP_TIER_DEFINITIONS.find((item) => item.test_code === 'upper_extremity_pattern2_left') || SFMA_TOP_TIER_DEFINITIONS[0]
  )
  legacyUe2Left.classification = upperExtremityPattern2Top.left.classification
  legacyUe2Left.pain_present = upperExtremityPattern2Top.left.pain_present
  legacyUe2Left.pain_vas = upperExtremityPattern2Top.left.pain_vas
  legacyUe2Left.needs_breakout_suggestion = upperExtremityPattern2Top.left.needs_breakout_suggestion
  legacyUe2Left.breakout_reason_text = upperExtremityPattern2Top.left.breakout_reason_text
  legacyUe2Left.clinician_note = upperExtremityPattern2Top.left.top_tier_note
  legacyUe2Left.review_priority =
    upperExtremityPattern2Top.left.review_priority === 'medium' ? 'normal' : upperExtremityPattern2Top.left.review_priority
  legacyUe2Left.caution_text =
    upperExtremityPattern2Top.left.classification === 'FP' || upperExtremityPattern2Top.left.classification === 'DP'
      ? '优先疼痛管理/谨慎继续分解'
      : ''
  localData.top_tier.upper_extremity_pattern2_left = legacyUe2Left

  const legacyUe2Right = localData.top_tier.upper_extremity_pattern2_right || buildDefaultSfmaTopTierRecord(
    SFMA_TOP_TIER_DEFINITIONS.find((item) => item.test_code === 'upper_extremity_pattern2_right') || SFMA_TOP_TIER_DEFINITIONS[0]
  )
  legacyUe2Right.classification = upperExtremityPattern2Top.right.classification
  legacyUe2Right.pain_present = upperExtremityPattern2Top.right.pain_present
  legacyUe2Right.pain_vas = upperExtremityPattern2Top.right.pain_vas
  legacyUe2Right.needs_breakout_suggestion = upperExtremityPattern2Top.right.needs_breakout_suggestion
  legacyUe2Right.breakout_reason_text = upperExtremityPattern2Top.right.breakout_reason_text
  legacyUe2Right.clinician_note = upperExtremityPattern2Top.right.top_tier_note
  legacyUe2Right.review_priority =
    upperExtremityPattern2Top.right.review_priority === 'medium' ? 'normal' : upperExtremityPattern2Top.right.review_priority
  legacyUe2Right.caution_text =
    upperExtremityPattern2Top.right.classification === 'FP' || upperExtremityPattern2Top.right.classification === 'DP'
      ? '优先疼痛管理/谨慎继续分解'
      : ''
  localData.top_tier.upper_extremity_pattern2_right = legacyUe2Right

  const upperExtremityPattern2Breakout = {
    ...buildDefaultUpperExtremityPattern2Breakout(),
    ...localData.upper_extremity_pattern2_breakout
  }
  localData.upper_extremity_pattern2_breakout = upperExtremityPattern2Breakout
  const ue2LeftResult = upperExtremityPattern2ToLegacyBreakout(upperExtremityPattern2Breakout, 'left')
  const ue2RightResult = upperExtremityPattern2ToLegacyBreakout(upperExtremityPattern2Breakout, 'right')
  const ue2LeftLegacy = ue2LeftResult.sideLegacy
  const ue2RightLegacy = ue2RightResult.sideLegacy
  const ue2MergedLegacy = ue2LeftResult.mergedLegacy
  localData.breakouts.upper_extremity_pattern_left = ue2LeftLegacy
  localData.breakouts.upper_extremity_pattern_right = ue2RightLegacy
  localData.breakouts.upper_extremity_pattern2_breakout = ue2MergedLegacy

  const msfTop = localData.top_tier.multi_segmental_flexion
  const msfBreakout = {
    ...buildDefaultMsfBreakout(),
    ...localData.msf_breakout,
    breakout_reason_from_top_tier: msfTop?.breakout_reason_text || localData.msf_breakout.breakout_reason_from_top_tier || ''
  }
  localData.msf_breakout = msfBreakout
  const msfLegacy = msfBreakoutToLegacy(msfBreakout)
  localData.breakouts.msf_breakout = msfLegacy
  localData.breakouts.msf = { ...buildDefaultSfmaBreakoutRecord(), ...msfLegacy }

  const mseTop = localData.top_tier.multi_segmental_extension
  const mseBreakout = {
    ...buildDefaultMseBreakout(),
    ...localData.mse_breakout,
    breakout_reason_from_top_tier: mseTop?.breakout_reason_text || localData.mse_breakout.breakout_reason_from_top_tier || ''
  }
  localData.mse_breakout = mseBreakout
  const mseLegacy = mseBreakoutToLegacy(mseBreakout)
  localData.breakouts.mse_breakout = mseLegacy
  localData.breakouts.mse = { ...buildDefaultSfmaBreakoutRecord(), ...mseLegacy }

  const armsDownTop = localData.top_tier.arms_down_deep_squat
  const armsDownBreakout = {
    ...buildDefaultArmsDownSquatBreakout(),
    ...localData.arms_down_squat_breakout,
    breakout_reason_from_screening:
      armsDownTop?.breakout_reason_text || localData.arms_down_squat_breakout.breakout_reason_from_screening || ''
  }
  localData.arms_down_squat_breakout = armsDownBreakout
  const armsDownLegacy = armsDownSquatBreakoutToLegacy(armsDownBreakout)
  localData.breakouts.arms_down_squat_breakout = armsDownLegacy
  localData.breakouts.deep_squat = { ...buildDefaultSfmaBreakoutRecord(), ...armsDownLegacy }

  const legacyMsrLeftTop = localData.top_tier.multi_segmental_rotation_left
  const legacyMsrRightTop = localData.top_tier.multi_segmental_rotation_right
  const msrBreakout: SfmaMsrBreakout = {
    ...buildDefaultMsrBreakout(),
    ...localData.msr_breakout,
    left: {
      ...buildDefaultMsrBreakout().left,
      ...localData.msr_breakout.left,
      rotation_side: 'left' as const,
      breakout_reason_from_top_tier:
        legacyMsrLeftTop?.breakout_reason_text || localData.msr_breakout.left.breakout_reason_from_top_tier || ''
    },
    right: {
      ...buildDefaultMsrBreakout().right,
      ...localData.msr_breakout.right,
      rotation_side: 'right' as const,
      breakout_reason_from_top_tier:
        legacyMsrRightTop?.breakout_reason_text || localData.msr_breakout.right.breakout_reason_from_top_tier || ''
    }
  }
  localData.msr_breakout = msrBreakout
  const msrLegacy = msrBreakoutToLegacy(msrBreakout)
  localData.breakouts.msr_left = msrLegacy.leftLegacy
  localData.breakouts.msr_right = msrLegacy.rightLegacy
  localData.breakouts.msr_breakout = msrLegacy.mergedLegacy

  const defaultAnalysisFlows = buildDefaultSfmaFormData().analysis_flows
  localData.analysis_flows = {
    ...defaultAnalysisFlows,
    ...(localData.analysis_flows || {}),
    msf_analysis: deepClone(localData.msf_breakout?.msf_analysis || defaultAnalysisFlows.msf_analysis),
    mse_analysis: deepClone(localData.mse_breakout?.mse_analysis || defaultAnalysisFlows.mse_analysis),
    msr_analysis: deepClone(localData.analysis_flows?.msr_analysis || {}),
    sls_analysis: deepClone(localData.analysis_flows?.sls_analysis || {}),
    cervical_analysis: deepClone(localData.analysis_flows?.cervical_analysis || {}),
    upper_extremity_analysis: deepClone(localData.analysis_flows?.upper_extremity_analysis || {}),
    arms_down_squat_analysis: deepClone(localData.analysis_flows?.arms_down_squat_analysis || {})
  }
}

const resetLocalData = (value?: Record<string, any>) => {
  const next = mergeDefault(value)
  Object.keys(localData).forEach((key) => delete (localData as any)[key])
  Object.assign(localData, next)
  syncCervicalToLegacy()
  injectAssessmentBaseInfo()
}

const isSameSfmaPayload = (value?: Record<string, any>) => {
  const incomingState = mergeDefault(value)
  const currentState = deepClone(localData)
  return isEqual(incomingState, currentState)
}

watch(
  () => props.modelValue,
  (value) => {
    // 子组件 emit 后，父层会立即把同一份数据再传回。
    // 只跳过刚刚 emit 的同一份回声；后端异步加载的不同数据必须正常回填。
    if (isInEmitSyncWindow() && lastEmittedModelValue && isEqual(value, lastEmittedModelValue)) {
      return
    }
    if (isSameSfmaPayload(value)) {
      return
    }
    resetLocalData(value)
  },
  { immediate: true }
)

watch(
  () => props.assessmentBaseInfo,
  () => injectAssessmentBaseInfo(),
  { deep: true }
)

watch(
  localData,
  () => {
    const sfmaPayload = deepClone(localData)
    const payload = {
      ...sfmaPayload,
      sfma: sfmaPayload
    }
    lastEmittedModelValue = deepClone(payload)
    markEmitSyncWindow()
    emit('update:modelValue', payload)
    emit('change', payload)
  },
  { deep: true }
)

const isTopTierComplete = computed(() => {
  return SFMA_TOP_TIER_DEFINITIONS.every((item) => {
    const row = localData.top_tier[item.test_code]
    return !!row?.classification
  })
})

const acceptedGeneralRecommendations = computed(() => {
  return (localData.breakout_recommendations || []).filter(
    (item) =>
      item.recommendation_status === 'accepted' &&
      item.test_code !== 'cervical_flexion' &&
      item.test_code !== 'cervical_extension' &&
      item.test_code !== 'cervical_rotation_left' &&
      item.test_code !== 'cervical_rotation_right' &&
      item.test_code !== 'upper_extremity_pattern1_left' &&
      item.test_code !== 'upper_extremity_pattern1_right' &&
      item.test_code !== 'upper_extremity_pattern2_left' &&
      item.test_code !== 'upper_extremity_pattern2_right' &&
      item.test_code !== 'multi_segmental_flexion' &&
      item.test_code !== 'multi_segmental_extension' &&
      item.test_code !== 'multi_segmental_rotation_left' &&
      item.test_code !== 'multi_segmental_rotation_right' &&
      item.test_code !== 'single_leg_stance_left' &&
      item.test_code !== 'single_leg_stance_right'
  )
})

const breakoutLabel = (key: string) => SFMA_BREAKOUT_LABELS[key] || key
const resolveBreakoutComponent = (key: string) => BREAKOUT_COMPONENT_MAP[key] || SfmaCervicalBreakoutForm
const resolveBreakoutModel = (item: SfmaBreakoutRecommendation | string) => {
  const key = typeof item === 'string' ? item : item.breakout_key
  const side = resolveSide(item)
  if (key === 'arms_down_squat_breakout') return localData.arms_down_squat_breakout
  if (key === 'msf_breakout') return localData.msf_breakout
  if (key === 'mse_breakout') return localData.mse_breakout
  if (key === 'msr_breakout') {
    return side === 'right' ? localData.msr_breakout.right : localData.msr_breakout.left
  }
  return localData.breakouts[key]
}
const updateBreakoutModel = (item: SfmaBreakoutRecommendation | string, value: any) => {
  const key = typeof item === 'string' ? item : item.breakout_key
  const side = resolveSide(item)
  if (key === 'arms_down_squat_breakout') {
    localData.arms_down_squat_breakout = {
      ...buildDefaultArmsDownSquatBreakout(),
      ...(value || {})
    }
    return
  }
  if (key === 'msf_breakout') {
    localData.msf_breakout = {
      ...buildDefaultMsfBreakout(),
      ...(value || {})
    }
    return
  }
  if (key === 'mse_breakout') {
    localData.mse_breakout = {
      ...buildDefaultMseBreakout(),
      ...(value || {})
    }
    return
  }
  if (key === 'msr_breakout') {
    const next = {
      ...buildDefaultMsrBreakout(),
      ...localData.msr_breakout
    }
    if (side === 'right') {
      next.right = {
        ...buildDefaultMsrBreakout().right,
        ...localData.msr_breakout.right,
        ...(value || {}),
        rotation_side: 'right'
      }
    } else {
      next.left = {
        ...buildDefaultMsrBreakout().left,
        ...localData.msr_breakout.left,
        ...(value || {}),
        rotation_side: 'left'
      }
    }
    localData.msr_breakout = next
    return
  }
  localData.breakouts[key] = {
    ...buildDefaultSfmaBreakoutRecord(),
    ...(value || {})
  }
}

const resolveSide = (item: SfmaBreakoutRecommendation | string): 'left' | 'right' | undefined => {
  const key = typeof item === 'string' ? item : item.breakout_key
  const testCode = typeof item === 'string' ? '' : item.test_code
  if (testCode.endsWith('_left')) {
    return 'left'
  }
  if (testCode.endsWith('_right')) {
    return 'right'
  }
  if (key.endsWith('_left')) {
    return 'left'
  }
  if (key.endsWith('_right')) {
    return 'right'
  }
  return undefined
}

const updateTopTierRow = (testCode: string, value: SfmaTopTierRecord) => {
  const definition = SFMA_TOP_TIER_DEFINITIONS.find((item) => item.test_code === testCode)
  if (!definition) {
    return
  }
  localData.top_tier[testCode] = {
    ...buildDefaultSfmaTopTierRecord(definition),
    ...(value || {})
  }
}

const updateMsrTopTierRows = (value: { left: SfmaTopTierRecord; right: SfmaTopTierRecord }) => {
  updateTopTierRow('multi_segmental_rotation_left', value?.left)
  updateTopTierRow('multi_segmental_rotation_right', value?.right)
}

const updateSlsTopTierRows = (value: { left: SfmaTopTierRecord; right: SfmaTopTierRecord }) => {
  updateTopTierRow('single_leg_stance_left', value?.left)
  updateTopTierRow('single_leg_stance_right', value?.right)
}

const updateMsrBreakoutSide = (side: 'left' | 'right', value: SfmaMsrBreakout['left']) => {
  const next = {
    ...buildDefaultMsrBreakout(),
    ...localData.msr_breakout
  }
  next[side] = {
    ...buildDefaultMsrBreakout()[side],
    ...localData.msr_breakout[side],
    ...(value || {}),
    rotation_side: side
  }
  localData.msr_breakout = next
}

const updateSlsBreakoutSide = (side: 'left' | 'right', value: SfmaBreakoutRecord) => {
  const key = side === 'left' ? 'sls_left' : 'sls_right'
  localData.breakouts[key] = {
    ...buildDefaultSfmaBreakoutRecord(),
    ...(value || {})
  }
}

const buildRecommendationReason = (classification: string, motion: 'flexion' | 'extension') => {
  const actionZh = motion === 'flexion' ? '颈椎屈曲' : '颈椎伸展'
  if (classification === 'FP') {
    return `${actionZh}为疼痛性功能模式，建议谨慎进入${actionZh}分解评估。`
  }
  if (classification === 'DN') {
    return `${actionZh}存在功能异常，建议进入${actionZh}分解评估。`
  }
  if (classification === 'DP') {
    return `${actionZh}存在功能异常并伴疼痛，建议优先人工复核并谨慎进入${actionZh}分解评估。`
  }
  return ''
}

const breakoutHierarchyOrder = (breakoutKey: string) => {
  const map: Record<string, number> = {
    cervical_flexion_breakout: 10,
    cervical_extension_breakout: 11,
    cervical_rotation_breakout: 12,
    upper_extremity_pattern1_breakout: 22,
    upper_extremity_pattern2_breakout: 23,
    cervical_pattern: 12,
    upper_extremity_pattern_left: 20,
    upper_extremity_pattern_right: 21,
    msf_breakout: 30,
    mse_breakout: 31,
    msf: 30,
    mse: 31,
    msr_left: 40,
    msr_right: 41,
    sls_left: 50,
    sls_right: 51,
    arms_down_squat_breakout: 60,
    deep_squat: 60
  }
  return map[breakoutKey] || 999
}

const recommendationStage = (classification: string): SfmaBreakoutRecommendation['recommendation_stage'] => {
  if (classification === 'DN') return 'dn_first'
  if (classification === 'FP') return 'fp_second'
  return 'dp_last'
}

const recommendationStageWeight = (classification: string) => {
  if (classification === 'DN') return 1
  if (classification === 'FP') return 2
  if (classification === 'DP') return 3
  return 9
}

const normalizeRecommendationStatusByBreakout = (
  status?: string
): SfmaBreakoutRecommendation['recommendation_status'] => {
  if (status === 'in_progress' || status === 'completed') {
    return 'accepted'
  }
  if (status === 'skipped') {
    return 'skipped'
  }
  return 'suggested'
}

const getRecommendationStatusFromBreakout = (definition: { test_code: string; breakout_key: string }) => {
  const key = definition.breakout_key
  if (key === 'msf_breakout') {
    return normalizeRecommendationStatusByBreakout(localData.msf_breakout.breakout_status)
  }
  if (key === 'mse_breakout') {
    return normalizeRecommendationStatusByBreakout(localData.mse_breakout.breakout_status)
  }
  if (key === 'arms_down_squat_breakout' || key === 'deep_squat') {
    return normalizeRecommendationStatusByBreakout(localData.arms_down_squat_breakout.breakout_status)
  }
  if (key === 'msr_breakout') {
    const sideStatus =
      definition.test_code === 'multi_segmental_rotation_right'
        ? localData.msr_breakout.right.breakout_status
        : localData.msr_breakout.left.breakout_status
    return normalizeRecommendationStatusByBreakout(sideStatus)
  }
  if (key === 'sls_left' || key === 'sls_right') {
    return normalizeRecommendationStatusByBreakout(localData.breakouts[key]?.status)
  }
  return normalizeRecommendationStatusByBreakout(localData.breakouts[key]?.status)
}

const upsertDedicatedCervicalRecommendation = (motion: 'flexion' | 'extension') => {
  const isFlexion = motion === 'flexion'
  const testCode = isFlexion ? 'cervical_flexion' : 'cervical_extension'
  const testNameZh = isFlexion ? '颈椎屈曲' : '颈椎伸展'
  const breakoutKey = isFlexion ? 'cervical_flexion_breakout' : 'cervical_extension_breakout'
  const top = isFlexion ? localData.cervical_flexion_top_tier : localData.cervical_extension_top_tier
  const breakout = isFlexion ? localData.cervical_flexion_breakout : localData.cervical_extension_breakout

  const list = [...(localData.breakout_recommendations || [])].filter((item) => item.test_code !== testCode)
  const classification = top.classification || ''
  const needsSuggestion = top.needs_breakout_suggestion && !!classification
  if (!needsSuggestion) {
    localData.breakout_recommendations = list
    return
  }
  const stage = recommendationStage(classification)
  list.push({
    recommendation_id: `rec_${testCode}`,
    test_code: testCode,
    test_name_zh: testNameZh,
    classification,
    breakout_key: breakoutKey,
      recommendation_status:
        breakout.breakout_status === 'skipped'
          ? 'skipped'
        : breakout.breakout_status === 'in_progress' ||
            breakout.breakout_status === 'completed'
          ? 'accepted'
          : 'suggested',
    recommendation_stage: stage,
    recommendation_order: recommendationStageWeight(classification) * 100 + breakoutHierarchyOrder(breakoutKey),
    recommendation_reason: top.breakout_reason_text || buildRecommendationReason(classification, motion),
    recommendation_note: top.top_tier_note || '',
    review_priority: top.review_priority === 'medium' ? 'normal' : top.review_priority,
    caution_text: classification === 'FP' || classification === 'DP' ? '优先疼痛管理/谨慎继续分解' : ''
  })
  localData.breakout_recommendations = list.sort((a, b) => {
    const aOrder = typeof a.recommendation_order === 'number' ? a.recommendation_order : 9999
    const bOrder = typeof b.recommendation_order === 'number' ? b.recommendation_order : 9999
    return aOrder - bOrder
  })
}

const upsertDedicatedCervicalRotationRecommendations = () => {
  const list = [...(localData.breakout_recommendations || [])].filter(
    (item) => item.test_code !== 'cervical_rotation_left' && item.test_code !== 'cervical_rotation_right'
  )
  ;(['left', 'right'] as const).forEach((side) => {
    const top = localData.cervical_rotation_top_tier[side]
    const breakout = localData.cervical_rotation_breakout[side]
    const testCode = side === 'left' ? 'cervical_rotation_left' : 'cervical_rotation_right'
    const testNameZh = side === 'left' ? '颈椎旋转（左）' : '颈椎旋转（右）'
    const classification = top.classification || ''
    const needsSuggestion = top.needs_breakout_suggestion && !!classification
    if (!needsSuggestion) {
      return
    }
    const stage = recommendationStage(classification)
    list.push({
      recommendation_id: `rec_${testCode}`,
      test_code: testCode,
      test_name_zh: testNameZh,
      classification,
      breakout_key: 'cervical_rotation_breakout',
      recommendation_status:
        breakout.breakout_status === 'skipped'
          ? 'skipped'
          : breakout.breakout_status === 'in_progress' ||
              breakout.breakout_status === 'completed'
            ? 'accepted'
            : 'suggested',
      recommendation_stage: stage,
      recommendation_order: recommendationStageWeight(classification) * 100 + breakoutHierarchyOrder('cervical_rotation_breakout'),
      recommendation_reason: top.breakout_reason_text || '',
      recommendation_note: top.top_tier_note || '',
      review_priority: top.review_priority === 'medium' ? 'normal' : top.review_priority,
      caution_text: classification === 'FP' || classification === 'DP' ? '优先疼痛管理/谨慎继续分解' : ''
    })
  })
  localData.breakout_recommendations = list.sort((a, b) => {
    const aOrder = typeof a.recommendation_order === 'number' ? a.recommendation_order : 9999
    const bOrder = typeof b.recommendation_order === 'number' ? b.recommendation_order : 9999
    if (aOrder !== bOrder) return aOrder - bOrder
    return a.test_name_zh.localeCompare(b.test_name_zh, 'zh-CN')
  })
}

const upsertDedicatedUpperExtremityPattern1Recommendations = () => {
  const list = [...(localData.breakout_recommendations || [])].filter(
    (item) => item.test_code !== 'upper_extremity_pattern1_left' && item.test_code !== 'upper_extremity_pattern1_right'
  )
  ;(['left', 'right'] as const).forEach((side) => {
    const top = localData.upper_extremity_pattern1_top_tier[side]
    const breakout = localData.upper_extremity_pattern1_breakout[side]
    const testCode = side === 'left' ? 'upper_extremity_pattern1_left' : 'upper_extremity_pattern1_right'
    const testNameZh = side === 'left' ? '上肢模式1（左）' : '上肢模式1（右）'
    const classification = top.classification || ''
    const needsSuggestion = top.needs_breakout_suggestion && !!classification
    if (!needsSuggestion) {
      return
    }
    const stage = recommendationStage(classification)
    list.push({
      recommendation_id: `rec_${testCode}`,
      test_code: testCode,
      test_name_zh: testNameZh,
      classification,
      breakout_key: 'upper_extremity_pattern1_breakout',
      recommendation_status:
        breakout.breakout_status === 'skipped'
          ? 'skipped'
          : breakout.breakout_status === 'in_progress' ||
              breakout.breakout_status === 'completed'
            ? 'accepted'
            : 'suggested',
      recommendation_stage: stage,
      recommendation_order: recommendationStageWeight(classification) * 100 + breakoutHierarchyOrder('upper_extremity_pattern1_breakout'),
      recommendation_reason: top.breakout_reason_text || '',
      recommendation_note: top.top_tier_note || '',
      review_priority: top.review_priority === 'medium' ? 'normal' : top.review_priority,
      caution_text: classification === 'FP' || classification === 'DP' ? '优先疼痛管理/谨慎继续分解' : ''
    })
  })
  localData.breakout_recommendations = list.sort((a, b) => {
    const aOrder = typeof a.recommendation_order === 'number' ? a.recommendation_order : 9999
    const bOrder = typeof b.recommendation_order === 'number' ? b.recommendation_order : 9999
    if (aOrder !== bOrder) return aOrder - bOrder
    return a.test_name_zh.localeCompare(b.test_name_zh, 'zh-CN')
  })
}

const upsertDedicatedUpperExtremityPattern2Recommendations = () => {
  const list = [...(localData.breakout_recommendations || [])].filter(
    (item) => item.test_code !== 'upper_extremity_pattern2_left' && item.test_code !== 'upper_extremity_pattern2_right'
  )
  ;(['left', 'right'] as const).forEach((side) => {
    const top = localData.upper_extremity_pattern2_top_tier[side]
    const breakout = localData.upper_extremity_pattern2_breakout[side]
    const testCode = side === 'left' ? 'upper_extremity_pattern2_left' : 'upper_extremity_pattern2_right'
    const testNameZh = side === 'left' ? '上肢模式2（左）' : '上肢模式2（右）'
    const classification = top.classification || ''
    const needsSuggestion = top.needs_breakout_suggestion && !!classification
    if (!needsSuggestion) {
      return
    }
    const stage = recommendationStage(classification)
    list.push({
      recommendation_id: `rec_${testCode}`,
      test_code: testCode,
      test_name_zh: testNameZh,
      classification,
      breakout_key: 'upper_extremity_pattern2_breakout',
      recommendation_status:
        breakout.breakout_status === 'skipped'
          ? 'skipped'
          : breakout.breakout_status === 'in_progress' ||
              breakout.breakout_status === 'completed'
            ? 'accepted'
            : 'suggested',
      recommendation_stage: stage,
      recommendation_order: recommendationStageWeight(classification) * 100 + breakoutHierarchyOrder('upper_extremity_pattern2_breakout'),
      recommendation_reason: top.breakout_reason_text || '',
      recommendation_note: top.top_tier_note || '',
      review_priority: top.review_priority === 'medium' ? 'normal' : top.review_priority,
      caution_text: classification === 'FP' || classification === 'DP' ? '优先疼痛管理/谨慎继续分解' : ''
    })
  })
  localData.breakout_recommendations = list.sort((a, b) => {
    const aOrder = typeof a.recommendation_order === 'number' ? a.recommendation_order : 9999
    const bOrder = typeof b.recommendation_order === 'number' ? b.recommendation_order : 9999
    if (aOrder !== bOrder) return aOrder - bOrder
    return a.test_name_zh.localeCompare(b.test_name_zh, 'zh-CN')
  })
}

const handleCervicalTopTierChange = (value?: SfmaCervicalFlexionTopTier) => {
  if (value) {
    localData.cervical_flexion_top_tier = deepClone(value)
  }
  localData.cervical_flexion_top_tier = applyCervicalFlexionTopTierRules(localData.cervical_flexion_top_tier)
  if (!localData.cervical_flexion_top_tier.needs_breakout_suggestion) {
    localData.cervical_flexion_breakout.breakout_status = 'not_started'
  }
  syncCervicalToLegacy()
  upsertDedicatedCervicalRecommendation('flexion')
}

const handleCervicalBreakoutAction = (action: 'enter' | 'skip') => {
  if (action === 'enter') {
    if (localData.cervical_flexion_breakout.breakout_status !== 'completed') {
      localData.cervical_flexion_breakout.breakout_status = 'in_progress'
    }
    void scrollToBreakoutCard(bookProtocolCardRef)
  } else {
    localData.cervical_flexion_breakout.breakout_status = 'skipped'
  }
  syncCervicalToLegacy()
  upsertDedicatedCervicalRecommendation('flexion')
}

const handleCervicalBreakoutChange = () => {
  syncCervicalToLegacy()
  upsertDedicatedCervicalRecommendation('flexion')
}

const handleCervicalExtensionTopTierChange = (value?: SfmaCervicalExtensionTopTier) => {
  if (value) {
    localData.cervical_extension_top_tier = deepClone(value)
  }
  localData.cervical_extension_top_tier = applyCervicalExtensionTopTierRules(localData.cervical_extension_top_tier)
  if (!localData.cervical_extension_top_tier.needs_breakout_suggestion) {
    localData.cervical_extension_breakout.breakout_status = 'not_started'
  }
  syncCervicalToLegacy()
  upsertDedicatedCervicalRecommendation('extension')
}

const handleCervicalExtensionBreakoutAction = (action: 'enter' | 'skip') => {
  if (action === 'enter') {
    if (localData.cervical_extension_breakout.breakout_status !== 'completed') {
      localData.cervical_extension_breakout.breakout_status = 'in_progress'
    }
    void scrollToBreakoutCard(bookProtocolCardRef)
  } else {
    localData.cervical_extension_breakout.breakout_status = 'skipped'
  }
  syncCervicalToLegacy()
  upsertDedicatedCervicalRecommendation('extension')
}

const handleCervicalExtensionBreakoutChange = () => {
  syncCervicalToLegacy()
  upsertDedicatedCervicalRecommendation('extension')
}

const handleCervicalRotationTopTierChange = (value?: SfmaCervicalRotationTopTier) => {
  if (value) {
    localData.cervical_rotation_top_tier = deepClone(value)
  }
  localData.cervical_rotation_top_tier = applyCervicalRotationTopTierRules(localData.cervical_rotation_top_tier)
  ;(['left', 'right'] as const).forEach((side) => {
    if (!localData.cervical_rotation_top_tier[side].needs_breakout_suggestion) {
      localData.cervical_rotation_breakout[side].breakout_status = 'not_started'
    }
  })
  syncCervicalToLegacy()
  upsertDedicatedCervicalRotationRecommendations()
}

const handleCervicalRotationBreakoutAction = ({
  side,
  action
}: {
  side: 'left' | 'right'
  action: 'enter' | 'skip'
}) => {
  if (action === 'enter') {
    if (localData.cervical_rotation_breakout[side].breakout_status !== 'completed') {
      localData.cervical_rotation_breakout[side].breakout_status = 'in_progress'
    }
    void scrollToBreakoutCard(bookProtocolCardRef)
  } else {
    localData.cervical_rotation_breakout[side].breakout_status = 'skipped'
  }
  syncCervicalToLegacy()
  upsertDedicatedCervicalRotationRecommendations()
}

const handleCervicalRotationBreakoutChange = () => {
  syncCervicalToLegacy()
  upsertDedicatedCervicalRotationRecommendations()
}

const handleUpperExtremityPattern1TopTierChange = (value?: SfmaUpperExtremityPattern1TopTier) => {
  if (value) {
    localData.upper_extremity_pattern1_top_tier = deepClone(value)
  }
  localData.upper_extremity_pattern1_top_tier = applyUpperExtremityPattern1TopTierRules(localData.upper_extremity_pattern1_top_tier)
  ;(['left', 'right'] as const).forEach((side) => {
    if (!localData.upper_extremity_pattern1_top_tier[side].needs_breakout_suggestion) {
      localData.upper_extremity_pattern1_breakout[side].breakout_status = 'not_started'
    }
  })
  syncCervicalToLegacy()
  upsertDedicatedUpperExtremityPattern1Recommendations()
}

const handleUpperExtremityPattern1BreakoutAction = ({
  side,
  action
}: {
  side: 'left' | 'right'
  action: 'enter' | 'skip'
}) => {
  if (action === 'enter') {
    if (localData.upper_extremity_pattern1_breakout[side].breakout_status !== 'completed') {
      localData.upper_extremity_pattern1_breakout[side].breakout_status = 'in_progress'
    }
    void scrollToBreakoutCard(bookProtocolCardRef)
  } else {
    localData.upper_extremity_pattern1_breakout[side].breakout_status = 'skipped'
  }
  syncCervicalToLegacy()
  upsertDedicatedUpperExtremityPattern1Recommendations()
}

const handleUpperExtremityPattern1BreakoutChange = () => {
  syncCervicalToLegacy()
  upsertDedicatedUpperExtremityPattern1Recommendations()
}

const handleUpperExtremityPattern2TopTierChange = (value?: SfmaUpperExtremityPattern2TopTier) => {
  if (value) {
    localData.upper_extremity_pattern2_top_tier = deepClone(value)
  }
  localData.upper_extremity_pattern2_top_tier = applyUpperExtremityPattern2TopTierRules(localData.upper_extremity_pattern2_top_tier)
  ;(['left', 'right'] as const).forEach((side) => {
    if (!localData.upper_extremity_pattern2_top_tier[side].needs_breakout_suggestion) {
      localData.upper_extremity_pattern2_breakout[side].breakout_status = 'not_started'
    }
  })
  syncCervicalToLegacy()
  upsertDedicatedUpperExtremityPattern2Recommendations()
}

const handleUpperExtremityPattern2BreakoutAction = ({
  side,
  action
}: {
  side: 'left' | 'right'
  action: 'enter' | 'skip'
}) => {
  if (action === 'enter') {
    if (localData.upper_extremity_pattern2_breakout[side].breakout_status !== 'completed') {
      localData.upper_extremity_pattern2_breakout[side].breakout_status = 'in_progress'
    }
    void scrollToBreakoutCard(bookProtocolCardRef)
  } else {
    localData.upper_extremity_pattern2_breakout[side].breakout_status = 'skipped'
  }
  syncCervicalToLegacy()
  upsertDedicatedUpperExtremityPattern2Recommendations()
}

const handleUpperExtremityPattern2BreakoutChange = () => {
  syncCervicalToLegacy()
  upsertDedicatedUpperExtremityPattern2Recommendations()
}

const handleMsfTopTierChange = (value?: SfmaTopTierRecord) => {
  if (value) {
    updateTopTierRow('multi_segmental_flexion', value)
  }
  handleTopTierChange()
}

const handleMseTopTierChange = (value?: SfmaTopTierRecord) => {
  if (value) {
    updateTopTierRow('multi_segmental_extension', value)
  }
  handleTopTierChange()
}

const handleMsrTopTierChange = (value?: { left: SfmaTopTierRecord; right: SfmaTopTierRecord }) => {
  if (value) {
    updateMsrTopTierRows(value)
  }
  handleTopTierChange()
}

const handleSlsTopTierChange = (value?: { left: SfmaTopTierRecord; right: SfmaTopTierRecord }) => {
  if (value) {
    updateSlsTopTierRows(value)
  }
  handleTopTierChange()
}

const handleMsfBreakoutAction = (action: 'enter' | 'skip') => {
  if (action === 'enter') {
    if (localData.msf_breakout.breakout_status !== 'completed') {
      localData.msf_breakout.breakout_status = 'in_progress'
    }
    void scrollToBreakoutCard(bookProtocolCardRef)
  }
  if (action === 'skip') {
    localData.msf_breakout.breakout_status = 'skipped'
  }
  syncCervicalToLegacy()
  handleTopTierChange()
  handleBreakoutChange('msf_breakout')
}

const handleMseBreakoutAction = (action: 'enter' | 'skip') => {
  if (action === 'enter') {
    if (localData.mse_breakout.breakout_status !== 'completed') {
      localData.mse_breakout.breakout_status = 'in_progress'
    }
    void scrollToBreakoutCard(bookProtocolCardRef)
  }
  if (action === 'skip') {
    localData.mse_breakout.breakout_status = 'skipped'
  }
  syncCervicalToLegacy()
  handleTopTierChange()
  handleBreakoutChange('mse_breakout')
}

const handleMsrBreakoutAction = ({ side, action }: { side: 'left' | 'right'; action: 'enter' | 'skip' }) => {
  const target = side === 'right' ? localData.msr_breakout.right : localData.msr_breakout.left
  if (action === 'enter') {
    if (target.breakout_status !== 'completed') {
      target.breakout_status = 'in_progress'
    }
    void scrollToBreakoutCard(bookProtocolCardRef)
  }
  if (action === 'skip') {
    target.breakout_status = 'skipped'
  }
  syncCervicalToLegacy()
  handleTopTierChange()
  handleBreakoutChange({
    breakout_key: 'msr_breakout',
    test_code: side === 'left' ? 'multi_segmental_rotation_left' : 'multi_segmental_rotation_right'
  } as SfmaBreakoutRecommendation)
}

const handleSlsBreakoutAction = ({ side, action }: { side: 'left' | 'right'; action: 'enter' | 'skip' }) => {
  const key = side === 'left' ? 'sls_left' : 'sls_right'
  const target = localData.breakouts[key] || buildDefaultSfmaBreakoutRecord()
  if (action === 'enter') {
    if (target.status !== 'completed') {
      target.status = 'in_progress'
    }
    void scrollToBreakoutCard(bookProtocolCardRef)
  }
  if (action === 'skip') {
    target.status = 'skipped'
  }
  localData.breakouts[key] = target
  syncCervicalToLegacy()
  handleTopTierChange()
  handleBreakoutChange(key)
}

const handleMsfBreakoutChange = () => {
  syncCervicalToLegacy()
  handleTopTierChange()
  handleBreakoutChange('msf_breakout')
}

const handleMseBreakoutChange = () => {
  syncCervicalToLegacy()
  handleTopTierChange()
  handleBreakoutChange('mse_breakout')
}

const handleMsrBreakoutChange = (side: 'left' | 'right') => {
  syncCervicalToLegacy()
  handleTopTierChange()
  handleBreakoutChange({
    breakout_key: 'msr_breakout',
    test_code: side === 'left' ? 'multi_segmental_rotation_left' : 'multi_segmental_rotation_right'
  } as SfmaBreakoutRecommendation)
}

const handleSlsBreakoutChange = (side: 'left' | 'right') => {
  syncCervicalToLegacy()
  handleTopTierChange()
  handleBreakoutChange(side === 'left' ? 'sls_left' : 'sls_right')
}

const handleTopTierChange = () => {
  if (!isTopTierComplete.value) {
    const dedicatedTestCodes = new Set([
      'cervical_flexion',
      'cervical_extension',
      'cervical_rotation_left',
      'cervical_rotation_right',
      'upper_extremity_pattern1_left',
      'upper_extremity_pattern1_right',
      'upper_extremity_pattern2_left',
      'upper_extremity_pattern2_right'
    ])
    localData.breakout_recommendations = (localData.breakout_recommendations || []).filter((item) =>
      dedicatedTestCodes.has(item.test_code)
    )
    return
  }
  const existingMap = new Map<string, SfmaBreakoutRecommendation>()
  ;(localData.breakout_recommendations || []).forEach((item) => {
    existingMap.set(item.test_code, deepClone(item))
  })

  const suggestions: SfmaBreakoutRecommendation[] = []
  SFMA_TOP_TIER_DEFINITIONS.forEach((def) => {
    if (
      def.test_code === 'cervical_flexion' ||
      def.test_code === 'cervical_extension' ||
      def.test_code === 'cervical_rotation_left' ||
      def.test_code === 'cervical_rotation_right' ||
      def.test_code === 'upper_extremity_pattern1_left' ||
      def.test_code === 'upper_extremity_pattern1_right' ||
      def.test_code === 'upper_extremity_pattern2_left' ||
      def.test_code === 'upper_extremity_pattern2_right'
    ) {
      return
    }
    const row = localData.top_tier[def.test_code]
    if (!row) {
      return
    }
    const classification = row.classification || ''
    const needsSuggestion = classification === 'DN' || classification === 'DP' || classification === 'FP'
    row.needs_breakout_suggestion = needsSuggestion
    row.breakout_reason_text = needsSuggestion
      ? classification === 'DN'
        ? '存在非疼痛性功能障碍（DN），建议进入 Breakout 分解。'
        : classification === 'FP'
          ? '功能存在疼痛（FP），建议在疼痛管理前提下进入 Breakout。'
          : '功能障碍并伴疼痛（DP），建议疼痛管理优先，DP 分解建议放在最后阶段。'
      : ''
    if (classification === 'FP' || classification === 'DP') {
      row.pain_present = true
      row.review_priority = 'high'
      row.caution_text = '优先疼痛管理/谨慎继续分解'
    }
    if (!needsSuggestion) return

    const existed = existingMap.get(def.test_code)
    const stage = recommendationStage(classification)
    const order = recommendationStageWeight(classification) * 100 + breakoutHierarchyOrder(def.breakout_key)
    suggestions.push({
      recommendation_id: existed?.recommendation_id || `rec_${def.test_code}`,
      test_code: def.test_code,
      test_name_zh: def.test_name_zh,
      classification,
      breakout_key: def.breakout_key,
      recommendation_status: existed?.recommendation_status || 'suggested',
      recommendation_stage: existed?.recommendation_stage || stage,
      recommendation_order: typeof existed?.recommendation_order === 'number' ? existed.recommendation_order : order,
      recommendation_reason: row.breakout_reason_text || '',
      recommendation_note: existed?.recommendation_note || '',
      review_priority: row.review_priority || 'normal',
      caution_text: row.caution_text || ''
    })
    suggestions[suggestions.length - 1].recommendation_status =
      existed?.recommendation_status || getRecommendationStatusFromBreakout(def)
  })

  const cervical = existingMap.get('cervical_flexion')
  if (cervical) {
    suggestions.push(cervical)
  }
  const cervicalExtension = existingMap.get('cervical_extension')
  if (cervicalExtension) {
    suggestions.push(cervicalExtension)
  }
  const cervicalRotationLeft = existingMap.get('cervical_rotation_left')
  if (cervicalRotationLeft) {
    suggestions.push(cervicalRotationLeft)
  }
  const cervicalRotationRight = existingMap.get('cervical_rotation_right')
  if (cervicalRotationRight) {
    suggestions.push(cervicalRotationRight)
  }
  const ue1Left = existingMap.get('upper_extremity_pattern1_left')
  if (ue1Left) {
    suggestions.push(ue1Left)
  }
  const ue1Right = existingMap.get('upper_extremity_pattern1_right')
  if (ue1Right) {
    suggestions.push(ue1Right)
  }
  const ue2Left = existingMap.get('upper_extremity_pattern2_left')
  if (ue2Left) {
    suggestions.push(ue2Left)
  }
  const ue2Right = existingMap.get('upper_extremity_pattern2_right')
  if (ue2Right) {
    suggestions.push(ue2Right)
  }

  localData.breakout_recommendations = suggestions.sort((a, b) => {
    const aOrder = typeof a.recommendation_order === 'number' ? a.recommendation_order : 9999
    const bOrder = typeof b.recommendation_order === 'number' ? b.recommendation_order : 9999
    if (aOrder !== bOrder) return aOrder - bOrder
    return a.test_name_zh.localeCompare(b.test_name_zh, 'zh-CN')
  })
}

const handleRecommendationChange = (recommendations: SfmaBreakoutRecommendation[]) => {
  recommendations.forEach((item) => {
    if (item.breakout_key === 'arms_down_squat_breakout') {
      if (item.recommendation_status === 'accepted') {
        if (localData.arms_down_squat_breakout.breakout_status !== 'completed') {
          localData.arms_down_squat_breakout.breakout_status = 'in_progress'
        }
      }
      if (item.recommendation_status === 'skipped') {
        localData.arms_down_squat_breakout.breakout_status = 'skipped'
      }
      return
    }
    if (item.breakout_key === 'msf_breakout') {
      if (item.recommendation_status === 'accepted') {
        if (localData.msf_breakout.breakout_status !== 'completed') {
          localData.msf_breakout.breakout_status = 'in_progress'
        }
      }
      if (item.recommendation_status === 'skipped') {
        localData.msf_breakout.breakout_status = 'skipped'
      }
      return
    }
    if (item.breakout_key === 'mse_breakout') {
      if (item.recommendation_status === 'accepted') {
        if (localData.mse_breakout.breakout_status !== 'completed') {
          localData.mse_breakout.breakout_status = 'in_progress'
        }
      }
      if (item.recommendation_status === 'skipped') {
        localData.mse_breakout.breakout_status = 'skipped'
      }
      return
    }
    if (item.breakout_key === 'msr_breakout') {
      const side = resolveSide(item)
      const target = side === 'right' ? localData.msr_breakout.right : localData.msr_breakout.left
      if (item.recommendation_status === 'accepted') {
        if (target.breakout_status !== 'completed') {
          target.breakout_status = 'in_progress'
        }
      }
      if (item.recommendation_status === 'skipped') {
        target.breakout_status = 'skipped'
      }
      return
    }
    if (!SFMA_BREAKOUT_KEYS.includes(item.breakout_key as any)) {
      return
    }
    const breakout = localData.breakouts[item.breakout_key] || buildDefaultSfmaBreakoutRecord()
    if (item.recommendation_status === 'accepted') {
      if (breakout.status !== 'completed') {
        breakout.status = 'in_progress'
      }
    }
    if (item.recommendation_status === 'skipped') {
      breakout.status = 'skipped'
    }
    localData.breakouts[item.breakout_key] = breakout
  })
}

const handleBreakoutChange = (itemOrBreakoutKey: SfmaBreakoutRecommendation | string) => {
  const breakoutKey = typeof itemOrBreakoutKey === 'string' ? itemOrBreakoutKey : itemOrBreakoutKey.breakout_key
  const side = resolveSide(itemOrBreakoutKey)
  if (breakoutKey === 'arms_down_squat_breakout') {
    const matchedArmsDown = localData.breakout_recommendations.find((item) => item.breakout_key === breakoutKey)
    if (!matchedArmsDown) return
    if (
      localData.arms_down_squat_breakout.breakout_status === 'completed' ||
      localData.arms_down_squat_breakout.breakout_status === 'in_progress'
    ) {
      matchedArmsDown.recommendation_status = 'accepted'
    }
    if (localData.arms_down_squat_breakout.breakout_status === 'skipped') {
      matchedArmsDown.recommendation_status = 'skipped'
    }
    return
  }
  if (breakoutKey === 'msf_breakout') {
    const matchedMsf = localData.breakout_recommendations.find((item) => item.breakout_key === breakoutKey)
    if (!matchedMsf) {
      return
    }
    if (
      localData.msf_breakout.breakout_status === 'completed' ||
      localData.msf_breakout.breakout_status === 'in_progress'
    ) {
      matchedMsf.recommendation_status = 'accepted'
    }
    if (localData.msf_breakout.breakout_status === 'skipped') {
      matchedMsf.recommendation_status = 'skipped'
    }
    return
  }
  if (breakoutKey === 'mse_breakout') {
    const matchedMse = localData.breakout_recommendations.find((item) => item.breakout_key === breakoutKey)
    if (!matchedMse) return
    if (
      localData.mse_breakout.breakout_status === 'completed' ||
      localData.mse_breakout.breakout_status === 'in_progress'
    ) {
      matchedMse.recommendation_status = 'accepted'
    }
    if (localData.mse_breakout.breakout_status === 'skipped') {
      matchedMse.recommendation_status = 'skipped'
    }
    return
  }
  if (breakoutKey === 'msr_breakout') {
    const matchedMsr = localData.breakout_recommendations.find((item) => {
      if (item.breakout_key !== breakoutKey) return false
      const recommendationSide = resolveSide(item)
      return recommendationSide === side
    })
    if (!matchedMsr) return
    const sideBreakout = side === 'right' ? localData.msr_breakout.right : localData.msr_breakout.left
    if (sideBreakout.breakout_status === 'completed' || sideBreakout.breakout_status === 'in_progress') {
      matchedMsr.recommendation_status = 'accepted'
    }
    if (sideBreakout.breakout_status === 'skipped') {
      matchedMsr.recommendation_status = 'skipped'
    }
    return
  }
  const matched = localData.breakout_recommendations.find((item) => item.breakout_key === breakoutKey)
  if (!matched) {
    return
  }
  const breakout = localData.breakouts[breakoutKey]
  if (!breakout) {
    return
  }
  if (breakout.status === 'completed' || breakout.status === 'in_progress') {
    matched.recommendation_status = 'accepted'
  }
}

const validate = async () => true
const getFormData = () => {
  const sfmaPayload = deepClone(localData)
  return {
    ...sfmaPayload,
    sfma: sfmaPayload
  }
}
const reset = () => {
  resetLocalData(buildDefaultSfmaFormData())
}

defineExpose({ validate, getFormData, reset })
</script>

<style scoped>
.assessment-form-shell {
  border: 1px solid var(--el-border-color-light);
}
</style>
