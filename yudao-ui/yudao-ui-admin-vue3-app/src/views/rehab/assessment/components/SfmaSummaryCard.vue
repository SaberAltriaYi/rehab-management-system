<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>SFMA 评估结果汇总</template>

    <el-alert
      v-if="!hasSummary"
      :closable="false"
      type="info"
      title="当前 SFMA 记录暂未生成结构化汇总，建议重新保存评估数据后刷新。"
      class="mb-12px"
    />

    <template v-else>
      <el-alert
        v-if="bookProtocolSummary.protocol_version"
        :closable="false"
        type="success"
        class="mb-12px"
        :title="`原书版 SFMA 协议 ${bookProtocolSummary.protocol_version}`"
        :description="`已记录 ${bookProtocolSummary.recorded_step_count || 0} 个分解步骤；完成 ${bookProtocolSummary.completed_workflow_count || 0} 条流程；疼痛终止 ${bookProtocolSummary.stopped_due_to_pain_count || 0} 条流程。`"
      />
      <el-card shadow="never" class="mb-12px">
        <template #header>颈椎屈曲专项（Top Tier + Breakout）</template>
        <el-alert
          v-if="!hasCervicalSummary"
          :closable="false"
          type="info"
          title="当前尚未生成颈椎屈曲专项汇总。"
        />
        <el-descriptions v-else :column="2" border>
          <el-descriptions-item label="Top Tier 分类">
            <el-tag :type="classificationTagType(cervicalTopTierSummary.classification)">
              {{ cervicalTopTierSummary.classification || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Top Tier 疼痛">
            {{ cervicalTopTierSummary.painPresent ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="是否建议 Breakout">
            {{ cervicalTopTierSummary.breakoutSuggested ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="Breakout 状态">
            <el-tag :type="breakoutTagType(cervicalBreakoutSummary.breakoutStatus)">
              {{ mapBreakoutStatus(cervicalBreakoutSummary.breakoutStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="分解方向" :span="2">
            {{ cervicalBreakoutSummary.preliminaryDirection.join('、') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="需人工复核" :span="2">
            {{ cervicalBreakoutSummary.needsManualReview ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="专项摘要" :span="2">
            {{ cervicalSummaryText }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="mb-12px">
        <template #header>颈椎伸展专项（Top Tier + Breakout）</template>
        <el-alert
          v-if="!hasCervicalExtensionSummary"
          :closable="false"
          type="info"
          title="当前尚未生成颈椎伸展专项汇总。"
        />
        <el-descriptions v-else :column="2" border>
          <el-descriptions-item label="Top Tier 分类">
            <el-tag :type="classificationTagType(cervicalExtensionTopTierSummary.classification)">
              {{ cervicalExtensionTopTierSummary.classification || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Top Tier 疼痛">
            {{ cervicalExtensionTopTierSummary.painPresent ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="是否建议 Breakout">
            {{ cervicalExtensionTopTierSummary.breakoutSuggested ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="Breakout 状态">
            <el-tag :type="breakoutTagType(cervicalExtensionBreakoutSummary.breakoutStatus)">
              {{ mapBreakoutStatus(cervicalExtensionBreakoutSummary.breakoutStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="分解方向" :span="2">
            {{ cervicalExtensionBreakoutSummary.preliminaryDirection.join('、') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="需人工复核" :span="2">
            {{ cervicalExtensionBreakoutSummary.needsManualReview ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="专项摘要" :span="2">
            {{ cervicalExtensionSummaryText }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="mb-12px">
        <template #header>颈椎旋转专项（左/右）</template>
        <el-alert
          v-if="!hasCervicalRotationSummary"
          :closable="false"
          type="info"
          title="当前尚未生成颈椎旋转专项汇总。"
        />
        <el-descriptions v-else :column="2" border>
          <el-descriptions-item label="左侧 Top Tier 分类">
            <el-tag :type="classificationTagType(cervicalRotationLeftTopTierSummary.classification)">
              {{ cervicalRotationLeftTopTierSummary.classification || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="左侧 Breakout 状态">
            <el-tag :type="breakoutTagType(cervicalRotationLeftBreakoutSummary.breakoutStatus)">
              {{ mapBreakoutStatus(cervicalRotationLeftBreakoutSummary.breakoutStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="右侧 Top Tier 分类">
            <el-tag :type="classificationTagType(cervicalRotationRightTopTierSummary.classification)">
              {{ cervicalRotationRightTopTierSummary.classification || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="右侧 Breakout 状态">
            <el-tag :type="breakoutTagType(cervicalRotationRightBreakoutSummary.breakoutStatus)">
              {{ mapBreakoutStatus(cervicalRotationRightBreakoutSummary.breakoutStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="左右差重点" :span="2">
            {{ cervicalRotationAsymmetryFocus || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="旋转专项摘要" :span="2">
            {{ cervicalRotationSummaryText }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="mb-12px">
        <template #header>上肢模式1专项（左/右）</template>
        <el-alert
          v-if="!hasUpperExtremityPattern1Summary"
          :closable="false"
          type="info"
          title="当前尚未生成上肢模式1专项汇总。"
        />
        <el-descriptions v-else :column="2" border>
          <el-descriptions-item label="左侧 Top Tier 分类">
            <el-tag :type="classificationTagType(upperExtremityPattern1LeftTopTierSummary.classification)">
              {{ upperExtremityPattern1LeftTopTierSummary.classification || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="左侧 Breakout 状态">
            <el-tag :type="breakoutTagType(upperExtremityPattern1LeftBreakoutSummary.breakoutStatus)">
              {{ mapBreakoutStatus(upperExtremityPattern1LeftBreakoutSummary.breakoutStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="右侧 Top Tier 分类">
            <el-tag :type="classificationTagType(upperExtremityPattern1RightTopTierSummary.classification)">
              {{ upperExtremityPattern1RightTopTierSummary.classification || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="右侧 Breakout 状态">
            <el-tag :type="breakoutTagType(upperExtremityPattern1RightBreakoutSummary.breakoutStatus)">
              {{ mapBreakoutStatus(upperExtremityPattern1RightBreakoutSummary.breakoutStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="左右差重点" :span="2">
            {{ upperExtremityPattern1AsymmetryFocus || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="专项摘要" :span="2">
            {{ upperExtremityPattern1SummaryText }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="mb-12px">
        <template #header>上肢模式2专项（左/右）</template>
        <el-alert
          v-if="!hasUpperExtremityPattern2Summary"
          :closable="false"
          type="info"
          title="当前尚未生成上肢模式2专项汇总。"
        />
        <el-descriptions v-else :column="2" border>
          <el-descriptions-item label="左侧 Top Tier 分类">
            <el-tag :type="classificationTagType(upperExtremityPattern2LeftTopTierSummary.classification)">
              {{ upperExtremityPattern2LeftTopTierSummary.classification || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="左侧 Breakout 状态">
            <el-tag :type="breakoutTagType(upperExtremityPattern2LeftBreakoutSummary.breakoutStatus)">
              {{ mapBreakoutStatus(upperExtremityPattern2LeftBreakoutSummary.breakoutStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="右侧 Top Tier 分类">
            <el-tag :type="classificationTagType(upperExtremityPattern2RightTopTierSummary.classification)">
              {{ upperExtremityPattern2RightTopTierSummary.classification || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="右侧 Breakout 状态">
            <el-tag :type="breakoutTagType(upperExtremityPattern2RightBreakoutSummary.breakoutStatus)">
              {{ mapBreakoutStatus(upperExtremityPattern2RightBreakoutSummary.breakoutStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="左右差重点" :span="2">
            {{ upperExtremityPattern2AsymmetryFocus || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="专项摘要" :span="2">
            {{ upperExtremityPattern2SummaryText }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="mb-12px">
        <template #header>多节段伸展专项（MSE Breakout）</template>
        <el-alert
          v-if="!hasMseSummary"
          :closable="false"
          type="info"
          title="当前尚未生成多节段伸展专项汇总。"
        />
        <el-descriptions v-else :column="2" border>
          <el-descriptions-item label="Top Tier 分类">
            <el-tag :type="classificationTagType(mseTopTierSummary.classification)">
              {{ mseTopTierSummary.classification || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Top Tier 疼痛">
            {{ mseTopTierSummary.painPresent ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="是否建议 Breakout">
            {{ mseTopTierSummary.breakoutSuggested ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="Breakout 状态">
            <el-tag :type="breakoutTagType(mseBreakoutSummary.breakoutStatus)">
              {{ mapBreakoutStatus(mseBreakoutSummary.breakoutStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="分解方向" :span="2">
            {{ mseBreakoutSummary.preliminaryDirection.join('、') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="主要限制链条" :span="2">
            {{ mseBreakoutSummary.primaryRestrictionChain.join('、') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="主要控制障碍链条" :span="2">
            {{ mseBreakoutSummary.primaryControlDeficitChain.join('、') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="左右差重点" :span="2">
            {{ mseBreakoutSummary.leftRightAsymmetryFocus || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="需人工复核" :span="2">
            {{ mseBreakoutSummary.needsManualReview ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="专项摘要" :span="2">
            {{ mseSummaryText }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-row :gutter="12" class="mb-12px">
        <el-col :span="12">
          <el-card shadow="never" class="summary-card">
            <template #header>Top Tier 总表</template>
            <div class="summary-meta">完成项：{{ topTierRows.length }}</div>
            <el-table :data="topTierRows" size="small" border>
              <el-table-column label="模式项" min-width="160" prop="testNameZh" />
              <el-table-column label="分类" width="100">
                <template #default="scope">
                  <el-tag :type="classificationTagType(scope.row.classification)">{{ scope.row.classification }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="疼痛" width="70">
                <template #default="scope">{{ scope.row.painPresent ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="Breakout" min-width="120" prop="breakoutStatusText" />
            </el-table>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" class="summary-card">
            <template #header>Breakout 总表</template>
            <el-table :data="breakoutRows" size="small" border>
              <el-table-column label="分解项" min-width="160" prop="breakoutNameZh" />
              <el-table-column label="状态" width="110">
                <template #default="scope">
                  <el-tag :type="breakoutTagType(scope.row.status)">{{ scope.row.statusText }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="活动度线索" min-width="160" prop="mobilitySigns" />
              <el-table-column label="控制线索" min-width="160" prop="controlSigns" />
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" class="mb-12px">
        <template #header>分类与训练取向</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="主分类">{{ summary.primaryClassification || '-' }}</el-descriptions-item>
          <el-descriptions-item label="次分类">{{ summary.secondaryClassification.join('、') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="临床意义" :span="2">{{ summary.clinicalMeaning || '-' }}</el-descriptions-item>
          <el-descriptions-item label="训练取向" :span="2">{{ summary.trainingDirection || '-' }}</el-descriptions-item>
          <el-descriptions-item label="优先级排序" :span="2">{{ summary.priorities || '-' }}</el-descriptions-item>
          <el-descriptions-item label="主要限制链条" :span="2">
            {{ summary.majorLimitationChains.join('、') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="主要控制障碍链条" :span="2">
            {{ summary.majorControlChains.join('、') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="左右差重点" :span="2">{{ summary.asymmetry.join('；') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="人工复核提示" :span="2">{{ summary.manualReviewHint || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="mb-12px">
        <template #header>风险等级初筛（功能性）</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="总体等级">
            <el-tag :type="riskLevelTagType(riskPrecheck.overallRiskLevel)">{{ riskPrecheck.overallRiskLevel }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="标签">
            <el-tag v-for="tag in riskPrecheck.riskTags" :key="tag" class="mr-4px">{{ tag }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="说明" :span="2">{{ riskPrecheck.reasonText || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never">
        <template #header>报告映射预览（SFMA）</template>
        <pre class="module-json">{{ sfmaReportMappingText }}</pre>
      </el-card>
    </template>
  </el-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'

const props = defineProps<{
  moduleDataJson?: string | Record<string, any> | null
}>()

const parseJsonObject = (raw: any) => {
  if (!raw) return {}
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw)
    } catch {
      return {}
    }
  }
  return typeof raw === 'object' ? raw : {}
}

const source = computed(() => {
  const parsed = parseJsonObject(props.moduleDataJson)
  if (parsed?.sfma && typeof parsed.sfma === 'object') {
    return parsed.sfma
  }
  return parsed
})
const summaryRaw = computed(() => source.value?.summary || {})
const hasSummary = computed(() => Object.keys(summaryRaw.value || {}).length > 0)
const bookProtocolSummary = computed(() => summaryRaw.value?.book_protocol_summary || {})

const topTierRows = computed(() => {
  const rows = summaryRaw.value?.top_tier_table
  if (!Array.isArray(rows)) return []
  return rows.map((item: any) => ({
    testNameZh: item?.test_name_zh || '-',
    classification: item?.classification || '-',
    painPresent: Boolean(item?.pain_present),
    breakoutStatusText: item?.breakout_status || '-'
  }))
})

const breakoutRows = computed(() => {
  const rows = summaryRaw.value?.breakout_table
  if (!Array.isArray(rows)) return []
  return rows.map((item: any) => ({
    breakoutNameZh: item?.breakout_name_zh || item?.breakout_key || '-',
    status: item?.status || 'not_started',
    statusText: mapBreakoutStatus(item?.status),
    mobilitySigns: item?.mobility_restriction_signs || '-',
    controlSigns: item?.motor_control_signs || '-'
  }))
})

const summary = computed(() => ({
  primaryClassification: summaryRaw.value?.primary_classification || '-',
  secondaryClassification: Array.isArray(summaryRaw.value?.secondary_classification)
    ? summaryRaw.value.secondary_classification
    : [],
  clinicalMeaning: summaryRaw.value?.clinical_meaning || '-',
  trainingDirection: summaryRaw.value?.training_direction || '-',
  priorities: [summaryRaw.value?.priority_1, summaryRaw.value?.priority_2, summaryRaw.value?.priority_3]
    .filter(Boolean)
    .join(' | '),
  majorLimitationChains: Array.isArray(summaryRaw.value?.major_limitation_chains)
    ? summaryRaw.value.major_limitation_chains
    : [],
  majorControlChains: Array.isArray(summaryRaw.value?.major_control_deficit_chains)
    ? summaryRaw.value.major_control_deficit_chains
    : [],
  asymmetry: Array.isArray(summaryRaw.value?.left_right_key_asymmetry) ? summaryRaw.value.left_right_key_asymmetry : [],
  manualReviewHint: summaryRaw.value?.manual_review_or_referral_hint || '-'
}))

const cervicalTopTierSummary = computed(() => {
  const topTierSummary = summaryRaw.value?.top_tier_summary_item?.cervical_flexion || {}
  return {
    classification: topTierSummary?.classification || '-',
    painPresent: Boolean(topTierSummary?.pain_present),
    breakoutSuggested: Boolean(topTierSummary?.breakout_suggested),
    breakoutCompleted: Boolean(topTierSummary?.breakout_completed),
    reviewPriority: topTierSummary?.review_priority || '-',
    summaryText: topTierSummary?.summary_text || ''
  }
})

const cervicalBreakoutSummary = computed(() => {
  const breakoutSummary = summaryRaw.value?.breakout_summary_item?.cervical_flexion || {}
  return {
    breakoutStatus: breakoutSummary?.breakout_status || 'not_started',
    primaryFindings: Array.isArray(breakoutSummary?.primary_findings) ? breakoutSummary.primary_findings : [],
    preliminaryDirection: Array.isArray(breakoutSummary?.preliminary_direction)
      ? breakoutSummary.preliminary_direction
      : [],
    needsManualReview: Boolean(breakoutSummary?.needs_manual_review),
    summaryText: breakoutSummary?.summary_text || ''
  }
})

const cervicalSummaryText = computed(() => {
  if (cervicalBreakoutSummary.value.summaryText) {
    return cervicalBreakoutSummary.value.summaryText
  }
  if (cervicalTopTierSummary.value.summaryText) {
    return cervicalTopTierSummary.value.summaryText
  }
  return '-'
})

const hasCervicalSummary = computed(() => {
  return (
    Object.keys(summaryRaw.value?.top_tier_summary_item?.cervical_flexion || {}).length > 0 ||
    Object.keys(summaryRaw.value?.breakout_summary_item?.cervical_flexion || {}).length > 0
  )
})

const cervicalExtensionTopTierSummary = computed(() => {
  const topTierSummary = summaryRaw.value?.top_tier_summary_item?.cervical_extension || {}
  return {
    classification: topTierSummary?.classification || '-',
    painPresent: Boolean(topTierSummary?.pain_present),
    breakoutSuggested: Boolean(topTierSummary?.breakout_suggested),
    breakoutCompleted: Boolean(topTierSummary?.breakout_completed),
    reviewPriority: topTierSummary?.review_priority || '-',
    summaryText: topTierSummary?.summary_text || ''
  }
})

const cervicalExtensionBreakoutSummary = computed(() => {
  const breakoutSummary = summaryRaw.value?.breakout_summary_item?.cervical_extension || {}
  return {
    breakoutStatus: breakoutSummary?.breakout_status || 'not_started',
    primaryFindings: Array.isArray(breakoutSummary?.primary_findings) ? breakoutSummary.primary_findings : [],
    preliminaryDirection: Array.isArray(breakoutSummary?.preliminary_direction)
      ? breakoutSummary.preliminary_direction
      : [],
    needsManualReview: Boolean(breakoutSummary?.needs_manual_review),
    summaryText: breakoutSummary?.summary_text || ''
  }
})

const cervicalExtensionSummaryText = computed(() => {
  if (cervicalExtensionBreakoutSummary.value.summaryText) {
    return cervicalExtensionBreakoutSummary.value.summaryText
  }
  if (cervicalExtensionTopTierSummary.value.summaryText) {
    return cervicalExtensionTopTierSummary.value.summaryText
  }
  return '-'
})

const hasCervicalExtensionSummary = computed(() => {
  return (
    Object.keys(summaryRaw.value?.top_tier_summary_item?.cervical_extension || {}).length > 0 ||
    Object.keys(summaryRaw.value?.breakout_summary_item?.cervical_extension || {}).length > 0
  )
})

const cervicalRotationLeftTopTierSummary = computed(() => {
  const topTierSummary = summaryRaw.value?.top_tier_summary_item?.cervical_rotation_left || {}
  return {
    classification: topTierSummary?.classification || '-',
    painPresent: Boolean(topTierSummary?.pain_present),
    breakoutSuggested: Boolean(topTierSummary?.breakout_suggested),
    breakoutCompleted: Boolean(topTierSummary?.breakout_completed),
    reviewPriority: topTierSummary?.review_priority || '-',
    summaryText: topTierSummary?.summary_text || ''
  }
})

const cervicalRotationRightTopTierSummary = computed(() => {
  const topTierSummary = summaryRaw.value?.top_tier_summary_item?.cervical_rotation_right || {}
  return {
    classification: topTierSummary?.classification || '-',
    painPresent: Boolean(topTierSummary?.pain_present),
    breakoutSuggested: Boolean(topTierSummary?.breakout_suggested),
    breakoutCompleted: Boolean(topTierSummary?.breakout_completed),
    reviewPriority: topTierSummary?.review_priority || '-',
    summaryText: topTierSummary?.summary_text || ''
  }
})

const cervicalRotationLeftBreakoutSummary = computed(() => {
  const breakoutSummary = summaryRaw.value?.breakout_summary_item?.cervical_rotation_left || {}
  return {
    breakoutStatus: breakoutSummary?.breakout_status || 'not_started',
    summaryText: breakoutSummary?.summary_text || ''
  }
})

const cervicalRotationRightBreakoutSummary = computed(() => {
  const breakoutSummary = summaryRaw.value?.breakout_summary_item?.cervical_rotation_right || {}
  return {
    breakoutStatus: breakoutSummary?.breakout_status || 'not_started',
    summaryText: breakoutSummary?.summary_text || ''
  }
})

const cervicalRotationAsymmetryFocus = computed(() => {
  const mappingAsymmetry = source.value?.report_mapping?.sfma?.cervical_rotation?.rotation_asymmetry_focus
  if (Array.isArray(mappingAsymmetry) && mappingAsymmetry.length) {
    return mappingAsymmetry.join('；')
  }
  const summaryAsymmetry = summaryRaw.value?.left_right_key_asymmetry
  if (Array.isArray(summaryAsymmetry)) {
    const hits = summaryAsymmetry.filter((item: string) => String(item).includes('颈椎旋转'))
    if (hits.length) {
      return hits.join('；')
    }
  }
  return ''
})

const cervicalRotationSummaryText = computed(() => {
  const leftText = cervicalRotationLeftBreakoutSummary.value.summaryText || cervicalRotationLeftTopTierSummary.value.summaryText
  const rightText = cervicalRotationRightBreakoutSummary.value.summaryText || cervicalRotationRightTopTierSummary.value.summaryText
  return [leftText, rightText].filter(Boolean).join('；') || '-'
})

const hasCervicalRotationSummary = computed(() => {
  return (
    Object.keys(summaryRaw.value?.top_tier_summary_item?.cervical_rotation_left || {}).length > 0 ||
    Object.keys(summaryRaw.value?.top_tier_summary_item?.cervical_rotation_right || {}).length > 0 ||
    Object.keys(summaryRaw.value?.breakout_summary_item?.cervical_rotation_left || {}).length > 0 ||
    Object.keys(summaryRaw.value?.breakout_summary_item?.cervical_rotation_right || {}).length > 0
  )
})

const upperExtremityPattern1LeftTopTierSummary = computed(() => {
  const topTierSummary = summaryRaw.value?.top_tier_summary_item?.upper_extremity_pattern1_left || {}
  return {
    classification: topTierSummary?.classification || '-',
    painPresent: Boolean(topTierSummary?.pain_present),
    breakoutSuggested: Boolean(topTierSummary?.breakout_suggested),
    breakoutCompleted: Boolean(topTierSummary?.breakout_completed),
    reviewPriority: topTierSummary?.review_priority || '-',
    summaryText: topTierSummary?.summary_text || ''
  }
})

const upperExtremityPattern1RightTopTierSummary = computed(() => {
  const topTierSummary = summaryRaw.value?.top_tier_summary_item?.upper_extremity_pattern1_right || {}
  return {
    classification: topTierSummary?.classification || '-',
    painPresent: Boolean(topTierSummary?.pain_present),
    breakoutSuggested: Boolean(topTierSummary?.breakout_suggested),
    breakoutCompleted: Boolean(topTierSummary?.breakout_completed),
    reviewPriority: topTierSummary?.review_priority || '-',
    summaryText: topTierSummary?.summary_text || ''
  }
})

const upperExtremityPattern1LeftBreakoutSummary = computed(() => {
  const breakoutSummary = summaryRaw.value?.breakout_summary_item?.upper_extremity_pattern1_left || {}
  return {
    breakoutStatus: breakoutSummary?.breakout_status || 'not_started',
    summaryText: breakoutSummary?.summary_text || ''
  }
})

const upperExtremityPattern1RightBreakoutSummary = computed(() => {
  const breakoutSummary = summaryRaw.value?.breakout_summary_item?.upper_extremity_pattern1_right || {}
  return {
    breakoutStatus: breakoutSummary?.breakout_status || 'not_started',
    summaryText: breakoutSummary?.summary_text || ''
  }
})

const upperExtremityPattern1AsymmetryFocus = computed(() => {
  const mappingAsymmetry = source.value?.report_mapping?.sfma?.upper_extremity_pattern1?.asymmetry_focus
  if (Array.isArray(mappingAsymmetry) && mappingAsymmetry.length) {
    return mappingAsymmetry.join('；')
  }
  const summaryAsymmetry = summaryRaw.value?.left_right_key_asymmetry
  if (Array.isArray(summaryAsymmetry)) {
    const hits = summaryAsymmetry.filter((item: string) => String(item).includes('上肢模式1'))
    if (hits.length) {
      return hits.join('；')
    }
  }
  return ''
})

const upperExtremityPattern1SummaryText = computed(() => {
  const leftText =
    upperExtremityPattern1LeftBreakoutSummary.value.summaryText || upperExtremityPattern1LeftTopTierSummary.value.summaryText
  const rightText =
    upperExtremityPattern1RightBreakoutSummary.value.summaryText || upperExtremityPattern1RightTopTierSummary.value.summaryText
  return [leftText, rightText].filter(Boolean).join('；') || '-'
})

const hasUpperExtremityPattern1Summary = computed(() => {
  return (
    Object.keys(summaryRaw.value?.top_tier_summary_item?.upper_extremity_pattern1_left || {}).length > 0 ||
    Object.keys(summaryRaw.value?.top_tier_summary_item?.upper_extremity_pattern1_right || {}).length > 0 ||
    Object.keys(summaryRaw.value?.breakout_summary_item?.upper_extremity_pattern1_left || {}).length > 0 ||
    Object.keys(summaryRaw.value?.breakout_summary_item?.upper_extremity_pattern1_right || {}).length > 0
  )
})

const upperExtremityPattern2LeftTopTierSummary = computed(() => {
  const topTierSummary = summaryRaw.value?.top_tier_summary_item?.upper_extremity_pattern2_left || {}
  return {
    classification: topTierSummary?.classification || '-',
    painPresent: Boolean(topTierSummary?.pain_present),
    breakoutSuggested: Boolean(topTierSummary?.breakout_suggested),
    breakoutCompleted: Boolean(topTierSummary?.breakout_completed),
    reviewPriority: topTierSummary?.review_priority || '-',
    summaryText: topTierSummary?.summary_text || ''
  }
})

const upperExtremityPattern2RightTopTierSummary = computed(() => {
  const topTierSummary = summaryRaw.value?.top_tier_summary_item?.upper_extremity_pattern2_right || {}
  return {
    classification: topTierSummary?.classification || '-',
    painPresent: Boolean(topTierSummary?.pain_present),
    breakoutSuggested: Boolean(topTierSummary?.breakout_suggested),
    breakoutCompleted: Boolean(topTierSummary?.breakout_completed),
    reviewPriority: topTierSummary?.review_priority || '-',
    summaryText: topTierSummary?.summary_text || ''
  }
})

const upperExtremityPattern2LeftBreakoutSummary = computed(() => {
  const breakoutSummary = summaryRaw.value?.breakout_summary_item?.upper_extremity_pattern2_left || {}
  return {
    breakoutStatus: breakoutSummary?.breakout_status || 'not_started',
    summaryText: breakoutSummary?.summary_text || ''
  }
})

const upperExtremityPattern2RightBreakoutSummary = computed(() => {
  const breakoutSummary = summaryRaw.value?.breakout_summary_item?.upper_extremity_pattern2_right || {}
  return {
    breakoutStatus: breakoutSummary?.breakout_status || 'not_started',
    summaryText: breakoutSummary?.summary_text || ''
  }
})

const upperExtremityPattern2AsymmetryFocus = computed(() => {
  const mappingAsymmetry = source.value?.report_mapping?.sfma?.upper_extremity_pattern2?.asymmetry_focus
  if (Array.isArray(mappingAsymmetry) && mappingAsymmetry.length) {
    return mappingAsymmetry.join('；')
  }
  const summaryAsymmetry = summaryRaw.value?.left_right_key_asymmetry
  if (Array.isArray(summaryAsymmetry)) {
    const hits = summaryAsymmetry.filter((item: string) => String(item).includes('上肢模式2'))
    if (hits.length) {
      return hits.join('；')
    }
  }
  return ''
})

const upperExtremityPattern2SummaryText = computed(() => {
  const leftText =
    upperExtremityPattern2LeftBreakoutSummary.value.summaryText || upperExtremityPattern2LeftTopTierSummary.value.summaryText
  const rightText =
    upperExtremityPattern2RightBreakoutSummary.value.summaryText || upperExtremityPattern2RightTopTierSummary.value.summaryText
  return [leftText, rightText].filter(Boolean).join('；') || '-'
})

const hasUpperExtremityPattern2Summary = computed(() => {
  return (
    Object.keys(summaryRaw.value?.top_tier_summary_item?.upper_extremity_pattern2_left || {}).length > 0 ||
    Object.keys(summaryRaw.value?.top_tier_summary_item?.upper_extremity_pattern2_right || {}).length > 0 ||
    Object.keys(summaryRaw.value?.breakout_summary_item?.upper_extremity_pattern2_left || {}).length > 0 ||
    Object.keys(summaryRaw.value?.breakout_summary_item?.upper_extremity_pattern2_right || {}).length > 0
  )
})

const mseTopTierSummary = computed(() => {
  const topTierSummary = summaryRaw.value?.top_tier_summary_item?.multi_segmental_extension || {}
  return {
    classification: topTierSummary?.classification || '-',
    painPresent: Boolean(topTierSummary?.pain_present),
    breakoutSuggested: Boolean(topTierSummary?.breakout_suggested),
    summaryText: topTierSummary?.summary_text || ''
  }
})

const mseBreakoutSummary = computed(() => {
  const breakoutSummary = summaryRaw.value?.breakout_summary_item?.multi_segmental_extension || {}
  return {
    breakoutStatus: breakoutSummary?.breakout_status || 'not_started',
    preliminaryDirection: Array.isArray(breakoutSummary?.preliminary_direction)
      ? breakoutSummary.preliminary_direction
      : [],
    primaryRestrictionChain: Array.isArray(breakoutSummary?.primary_restriction_chain)
      ? breakoutSummary.primary_restriction_chain
      : [],
    primaryControlDeficitChain: Array.isArray(breakoutSummary?.primary_control_deficit_chain)
      ? breakoutSummary.primary_control_deficit_chain
      : [],
    leftRightAsymmetryFocus: breakoutSummary?.left_right_asymmetry_focus || '',
    needsManualReview: Boolean(breakoutSummary?.needs_manual_review),
    summaryText: breakoutSummary?.summary_text || ''
  }
})

const mseSummaryText = computed(() => {
  const mapped = source.value?.report_mapping?.sfma?.multi_segmental_extension?.summary_text
  return mapped || mseBreakoutSummary.value.summaryText || mseTopTierSummary.value.summaryText || '-'
})

const hasMseSummary = computed(() => {
  return (
    Object.keys(summaryRaw.value?.top_tier_summary_item?.multi_segmental_extension || {}).length > 0 ||
    Object.keys(summaryRaw.value?.breakout_summary_item?.multi_segmental_extension || {}).length > 0
  )
})

const riskPrecheck = computed(() => {
  const raw = source.value?.risk_precheck || {}
  return {
    overallRiskLevel: raw?.overall_risk_level || '-',
    riskTags: Array.isArray(raw?.risk_tags) ? raw.risk_tags : [],
    reasonText: raw?.reason_text || '-'
  }
})

const sfmaReportMappingText = computed(() => {
  const mapping = source.value?.report_mapping?.sfma
  if (!mapping) return '未提供/数据不足'
  try {
    return JSON.stringify(mapping, null, 2)
  } catch {
    return String(mapping)
  }
})

const mapBreakoutStatus = (status?: string) => {
  if (status === 'partial') status = 'in_progress'
  if (status === 'stopped_due_to_pain') status = 'completed'
  switch (status) {
    case 'completed':
      return '已完成'
    case 'in_progress':
      return '进行中'
    case 'skipped':
      return '暂不分解'
    default:
      return '未开始'
  }
}

const classificationTagType = (classification: string) => {
  if (classification === 'FP' || classification === 'DP') return 'danger'
  if (classification === 'DN') return 'warning'
  if (classification === 'FN') return 'success'
  return 'info'
}

const breakoutTagType = (status: string) => {
  if (status === 'partial') status = 'in_progress'
  if (status === 'stopped_due_to_pain') status = 'completed'
  if (status === 'completed') return 'success'
  if (status === 'in_progress') return 'warning'
  return 'info'
}

const riskLevelTagType = (level: string) => {
  if (level === 'high') return 'danger'
  if (level === 'medium') return 'warning'
  if (level === 'low') return 'success'
  return 'info'
}
</script>

<style scoped>
.summary-card {
  min-height: 280px;
}

.summary-meta {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.module-json {
  max-height: 180px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f6f8fa;
  border-radius: 4px;
  padding: 8px;
}
</style>
