<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>SFMA 自动汇总分析（保存后自动刷新）</template>

    <el-alert
      v-if="!hasSummary"
      :closable="false"
      type="info"
      title="当前尚未生成 SFMA 汇总。完成 Top Tier 并保存后，系统会自动生成结构化汇总与报告映射。"
    />

    <template v-else>
      <el-alert
        v-if="bookProtocolSummary.protocol_version"
        :closable="false"
        type="success"
        class="mb-10px"
        :title="`原书版 SFMA 协议 ${bookProtocolSummary.protocol_version}`"
        :description="`已记录 ${bookProtocolSummary.recorded_step_count || 0} 个分解步骤；完成 ${bookProtocolSummary.completed_workflow_count || 0} 条流程；因疼痛终止 ${bookProtocolSummary.stopped_due_to_pain_count || 0} 条流程。`"
      />
      <el-row :gutter="12" class="mb-10px">
        <el-col :span="8">
          <el-card shadow="never" class="summary-small-card">
            <template #header>主分类</template>
            <div class="summary-text">
              {{ summary.primary_classification || '-' }}
              <span class="text-12px text-[var(--el-text-color-secondary)] ml-6px">
                (置信度 {{ summary.classification_confidence || '-' }})
              </span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="summary-small-card">
            <template #header>次分类</template>
            <div class="summary-text">{{ (summary.secondary_classification || []).join('、') || '-' }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="summary-small-card">
            <template #header>训练取向</template>
            <div class="summary-text">{{ summary.training_direction || '-' }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="临床意义">
          {{ summary.clinical_meaning || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="优先级排序">
          {{ [summary.priority_1, summary.priority_2, summary.priority_3].filter(Boolean).join(' | ') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="主要限制链条">
          {{ (summary.major_limitation_chains || []).join('、') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="主要控制障碍链条">
          {{ (summary.major_control_deficit_chains || []).join('、') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="左右差重点">
          {{ (summary.left_right_key_asymmetry || []).join('；') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="人工复核提示">
          {{ summary.manual_review_or_referral_hint || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-card shadow="never" class="mt-10px">
        <template #header>功能关注标签（初筛）</template>
        <el-tag
          v-for="tag in riskTags"
          :key="tag"
          class="mr-6px mb-6px"
          :type="riskTagType"
        >
          {{ tag }}
        </el-tag>
        <div class="text-12px text-[var(--el-text-color-secondary)] mt-6px">{{ riskReason || '—' }}</div>
      </el-card>
    </template>
  </el-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    summary?: Record<string, any>
    riskPrecheck?: Record<string, any>
  }>(),
  {
    summary: () => ({}),
    riskPrecheck: () => ({})
  }
)

const hasSummary = computed(() => Object.keys(props.summary || {}).length > 0)
const summary = computed(() => props.summary || {})
const bookProtocolSummary = computed(() => props.summary?.book_protocol_summary || {})
const riskTags = computed<string[]>(() => {
  const tags = props.riskPrecheck?.risk_tags
  return Array.isArray(tags) ? tags : []
})
const riskReason = computed(() => props.riskPrecheck?.reason_text || '')
const riskLevel = computed(() => String(props.riskPrecheck?.overall_risk_level || '').toLowerCase())

const riskTagType = computed(() => {
  if (riskLevel.value === 'high') {
    return 'danger'
  }
  if (riskLevel.value === 'medium') {
    return 'warning'
  }
  if (riskLevel.value === 'low') {
    return 'success'
  }
  return 'info'
})
</script>

<style scoped>
.summary-small-card {
  min-height: 110px;
}

.summary-text {
  line-height: 20px;
}
</style>
