<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>CES 评估结果汇总</template>

    <el-alert
      v-if="!hasCesSummary"
      type="info"
      :closable="false"
      title="当前 NASM-CES 记录暂未生成结构化汇总，建议重新保存评估数据以触发汇总。"
      class="mb-12px"
    />

    <template v-else>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-card shadow="never" class="summary-card">
            <template #header>过渡动作评估汇总</template>
            <div class="summary-text">{{ transitionSummary.summaryText }}</div>
            <div class="summary-meta">
              已完成：{{ transitionSummary.completedItems }}，未完成：{{ transitionSummary.missingItems }}
            </div>
            <ul class="summary-list">
              <li
                v-for="(item, index) in transitionSummary.abnormalFindings.slice(0, 6)"
                :key="`transition-abnormal-${index}`"
              >
                {{ item.actionNameZh }} / {{ item.findingText }}
              </li>
            </ul>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" class="summary-card">
            <template #header>动态动作评估汇总</template>
            <div class="summary-text">{{ dynamicSummary.summaryText }}</div>
            <div class="summary-meta">
              已完成：{{ dynamicSummary.completedItems }}，未完成：{{ dynamicSummary.missingItems }}
            </div>
            <ul class="summary-list">
              <li
                v-for="(item, index) in dynamicSummary.abnormalFindings.slice(0, 6)"
                :key="`dynamic-abnormal-${index}`"
              >
                {{ item.actionNameZh }} / {{ item.findingText }}
              </li>
            </ul>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="12" class="mt-12px">
        <el-col :span="12">
          <el-card shadow="never" class="summary-card">
            <template #header>戴维斯测试汇总</template>
            <div class="summary-text">{{ daviesSummary.summaryText }}</div>
            <div class="summary-meta">有效 trial：{{ daviesSummary.trialCount }}</div>
            <ul class="summary-list">
              <li v-for="(item, index) in daviesSummary.results.slice(0, 6)" :key="`davies-${index}`">
                Trial {{ item.trialNo || '-' }}：{{ item.repetitionCount ?? '-' }} 次 / {{ item.durationSec ?? '-' }} 秒
              </li>
            </ul>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" class="summary-card">
            <template #header>LESS 测试汇总</template>
            <div class="summary-text">{{ lessSummary.summaryText }}</div>
            <div class="summary-meta">
              已填写：{{ lessSummary.filledItemCount }} 项，LESS 总分：{{ lessSummary.lessTotalScore ?? '-' }}
            </div>
            <ul class="summary-list">
              <li v-for="(item, index) in lessSummary.keyFindings.slice(0, 6)" :key="`less-${index}`">{{ item }}</li>
            </ul>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" class="mt-12px summary-card">
        <template #header>总体 CES 汇总</template>
        <div class="summary-text">{{ overallSummary.summaryText }}</div>
        <div class="summary-meta">重点区域：{{ overallSummary.priorityRegions.join('、') || '未提取' }}</div>
        <div class="summary-meta">动作模式标记：{{ overallSummary.movementPatternFlags.join('、') || '未提取' }}</div>
        <ul class="summary-list">
          <li v-for="(item, index) in overallSummary.keyFindings.slice(0, 10)" :key="`overall-${index}`">{{ item }}</li>
        </ul>
      </el-card>

      <el-card shadow="never" class="mt-12px">
        <template #header>风险等级初筛（功能性/动作学）</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="总体风险等级">
            <el-tag :type="riskLevelTagType(riskPrecheck.overallRiskLevel)">{{ riskPrecheck.overallRiskLevel }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="优先关注区域">
            {{ riskPrecheck.priorityRegions.join('、') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="风险标签" :span="2">
            <el-tag v-for="tag in riskPrecheck.riskTags" :key="`risk-tag-${tag}`" class="mr-4px mb-4px">{{ tag }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="初筛说明" :span="2">
            {{ riskPrecheck.reasonText || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="mt-12px">
        <template #header>动作级汇总</template>
        <el-table :data="actionSummaries" size="small" border>
          <el-table-column label="动作" min-width="180">
            <template #default="scope">{{ scope.row.actionNameZh }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="scope">
              <el-tag
                :type="scope.row.status === 'completed' ? 'success' : scope.row.status === 'partial' ? 'warning' : 'info'"
                size="small"
              >
                {{ scope.row.status === 'completed' ? '已评估' : scope.row.status === 'partial' ? '部分评估' : '未评估' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="风险初筛" min-width="220">
            <template #default="scope">
              <el-tag :type="riskLevelTagType(scope.row.riskLevel)">{{ scope.row.riskLevel }}</el-tag>
              <span class="ml-8px">{{ scope.row.riskTags.join('、') || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="关键发现" min-width="360">
            <template #default="scope">
              <span>{{ scope.row.keyFindings.join('；') || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="汇总描述" min-width="320">
            <template #default="scope">{{ scope.row.summaryText || '-' }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="mt-12px">
        <template #header>报告字段映射预览</template>
        <el-descriptions :column="1" border class="mb-10px">
          <el-descriptions-item label="动态功能评估总结">
            {{ reportMapping.dynamicFunctionSummaryText || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="整体主要风险指向">
            {{ reportMapping.overallRiskDirectionText || '-' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="reportMapping.actionBlocks" size="small" border class="mb-10px">
          <el-table-column label="动作" min-width="160">
            <template #default="scope">{{ scope.row.actionNameZh }}</template>
          </el-table-column>
          <el-table-column label="观测" min-width="260">
            <template #default="scope">{{ scope.row.observation }}</template>
          </el-table-column>
          <el-table-column label="分析" min-width="260">
            <template #default="scope">{{ scope.row.analysis }}</template>
          </el-table-column>
          <el-table-column label="风险" min-width="180">
            <template #default="scope">{{ scope.row.risk }}</template>
          </el-table-column>
          <el-table-column label="建议" min-width="220">
            <template #default="scope">{{ scope.row.suggestion }}</template>
          </el-table-column>
        </el-table>
        <div class="summary-meta">优先干预草案：{{ reportMapping.priorityDraftText }}</div>
      </el-card>
    </template>
  </el-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'

interface Props {
  moduleDataJson?: string | Record<string, any> | null
}

const props = defineProps<Props>()

interface FindingItem {
  actionNameZh: string
  findingText: string
}

interface DaviesResultItem {
  trialNo?: number | null
  durationSec?: number | null
  repetitionCount?: number | null
}

interface ActionSummaryItem {
  actionCode: string
  actionNameZh: string
  status: string
  keyFindings: string[]
  summaryText: string
  riskLevel: string
  riskTags: string[]
}

const parseJsonObject = (raw: any) => {
  if (!raw) return {}
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw)
    } catch {
      return {}
    }
  }
  if (typeof raw === 'object') return raw
  return {}
}

const source = computed(() => parseJsonObject(props.moduleDataJson))
const cesSummaryRaw = computed(() => source.value?.ces_summary || {})
const hasCesSummary = computed(() => Object.keys(cesSummaryRaw.value || {}).length > 0)

const mapFindingItem = (item: any): FindingItem => ({
  actionNameZh: item?.action_name_zh || '-',
  findingText: item?.finding_text || `${item?.observation_item || '-'}：${item?.value || '-'}`
})

const transitionSummary = computed(() => {
  const raw = cesSummaryRaw.value?.transition_assessments_summary || {}
  return {
    completedItems: Number(raw?.completed_items || 0),
    missingItems: Number(raw?.missing_items || 0),
    abnormalFindings: Array.isArray(raw?.abnormal_findings) ? raw.abnormal_findings.map(mapFindingItem) : [],
    summaryText: raw?.summary_text || '过渡动作评估暂未录入完整评估结果。'
  }
})

const dynamicSummary = computed(() => {
  const raw = cesSummaryRaw.value?.dynamic_assessments_summary || {}
  return {
    completedItems: Number(raw?.completed_items || 0),
    missingItems: Number(raw?.missing_items || 0),
    abnormalFindings: Array.isArray(raw?.abnormal_findings) ? raw.abnormal_findings.map(mapFindingItem) : [],
    summaryText: raw?.summary_text || '动态动作评估暂未录入完整评估结果。'
  }
})

const daviesSummary = computed(() => {
  const raw = cesSummaryRaw.value?.davies_test_summary || {}
  const results = Array.isArray(raw?.results)
    ? raw.results.map((item: any) => ({
        trialNo: item?.trial_no ?? null,
        durationSec: item?.duration_sec ?? null,
        repetitionCount: item?.repetition_count ?? null
      }))
    : []
  return {
    trialCount: Number(raw?.trial_count || 0),
    results: results as DaviesResultItem[],
    summaryText: raw?.summary_text || '上肢戴维斯测试暂未录入有效测试结果。'
  }
})

const lessSummary = computed(() => {
  const raw = cesSummaryRaw.value?.less_test_summary || {}
  return {
    filledItemCount: Number(raw?.filled_item_count || 0),
    lessTotalScore: raw?.less_total_score ?? null,
    keyFindings: Array.isArray(raw?.key_findings) ? raw.key_findings : [],
    summaryText: raw?.summary_text || 'LESS 测试暂未录入完整评分结果。'
  }
})

const overallSummary = computed(() => {
  const raw = cesSummaryRaw.value?.overall_summary || {}
  return {
    keyFindings: Array.isArray(raw?.key_findings) ? raw.key_findings : [],
    priorityRegions: Array.isArray(raw?.priority_regions) ? raw.priority_regions : [],
    movementPatternFlags: Array.isArray(raw?.movement_pattern_flags) ? raw.movement_pattern_flags : [],
    summaryText: raw?.summary_text || 'CES 评估显示当前录入信息有限，证据不足，需结合人工复核。'
  }
})

const riskPrecheck = computed(() => {
  const raw = source.value?.risk_precheck || {}
  return {
    overallRiskLevel: raw?.overall_risk_level || raw?.risk_level || 'low',
    riskTags: Array.isArray(raw?.risk_tags) ? raw.risk_tags : [],
    reasonText: raw?.reason_text || '证据不足，需结合人工复核。',
    priorityRegions: Array.isArray(raw?.priority_regions) ? raw.priority_regions : []
  }
})

const actionSummaries = computed<ActionSummaryItem[]>(() => {
  const rows = Array.isArray(cesSummaryRaw.value?.action_summaries)
    ? cesSummaryRaw.value.action_summaries
    : Array.isArray(source.value?.action_summaries)
      ? source.value.action_summaries
      : []
  return rows.map((item: any) => ({
    actionCode: item?.action_code || '',
    actionNameZh: item?.action_name_zh || '-',
    status: item?.status || 'not_assessed',
    keyFindings: Array.isArray(item?.key_findings) ? item.key_findings : [],
    summaryText: item?.summary_text || '',
    riskLevel: item?.risk_precheck?.risk_level || 'low',
    riskTags: Array.isArray(item?.risk_precheck?.risk_tags) ? item.risk_precheck.risk_tags : []
  }))
})

const reportMapping = computed(() => {
  const raw = source.value?.report_mapping || {}
  const actionBlocks = Array.isArray(raw?.nasm_ces_action_blocks)
    ? raw.nasm_ces_action_blocks.map((item: any) => ({
        actionNameZh: item?.action_name_zh || '-',
        observation: item?.observation || '-',
        analysis: item?.analysis || '-',
        risk: item?.risk || '-',
        suggestion: item?.suggestion || '-'
      }))
    : []
  const priorityDraft = Array.isArray(raw?.priority_intervention_draft) ? raw.priority_intervention_draft : []
  return {
    dynamicFunctionSummaryText: raw?.dynamic_function_summary_text || '-',
    overallRiskDirectionText: raw?.overall_risk_direction_text || '-',
    actionBlocks,
    priorityDraftText: priorityDraft
      .map((item: any) => `${item?.priority_rank || '-'}-${item?.region || '-'}：${item?.focus || '-'}`)
      .join('；')
  }
})

const riskLevelTagType = (level: string) => {
  if (level === 'high') return 'danger'
  if (level === 'medium') return 'warning'
  return 'success'
}
</script>

<style scoped>
.summary-card {
  min-height: 220px;
}

.summary-text {
  font-size: 14px;
  color: var(--el-text-color-primary);
  line-height: 22px;
  margin-bottom: 8px;
}

.summary-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.summary-list {
  margin: 0;
  padding-left: 18px;
  max-height: 170px;
  overflow: auto;
}
</style>
