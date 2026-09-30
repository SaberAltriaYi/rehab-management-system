<template>
  <div>
    <el-descriptions :column="2" border size="small">
      <el-descriptions-item label="计分方案">{{ score.scoringScheme }}</el-descriptions-item>
      <el-descriptions-item label="系统状态">{{ SYSTEM_STATUS_LABEL[score.systemStatus] || score.systemStatus }}</el-descriptions-item>
      <el-descriptions-item label="系统分">{{ fmt(score.systemScore) }}</el-descriptions-item>
      <el-descriptions-item label="最终分">{{ fmt(score.finalScore) }}（{{ FINAL_STATUS_LABEL[score.finalStatus] || score.finalStatus }}）</el-descriptions-item>
      <el-descriptions-item v-if="score.changeReason" label="修改原因" :span="2">{{ score.changeReason }}</el-descriptions-item>
      <el-descriptions-item label="关联 Trial" :span="2">{{ (score.evidence?.trial_keys || []).join('、') || '-' }}</el-descriptions-item>
    </el-descriptions>

    <template v-if="sides.length">
      <h4>分侧判定</h4>
      <el-table :data="sides" size="small">
        <el-table-column label="侧别" prop="side" width="90" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">{{ SYSTEM_STATUS_LABEL[row.status] || row.status }}</template>
        </el-table-column>
        <el-table-column label="系统分" width="70">
          <template #default="{ row }">{{ fmt(row.system_score) }}</template>
        </el-table-column>
        <el-table-column label="采用 Trial" width="120">
          <template #default="{ row }">{{ (row.used_trial_keys || []).join('、') }}</template>
        </el-table-column>
        <el-table-column label="原因 / 无效 Trial" min-width="220">
          <template #default="{ row }">
            {{ (row.reasons || []).join('；') }}
            <span v-for="t in row.invalid_trials || []" :key="t.trial_key || t" class="muted">[{{ t.trial_key || t }} {{ t.reason || '' }}]</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="score.detail?.lower_side_rule" class="muted">双侧项目取较低侧（FMS 规则）</div>
    </template>

    <h4>规则结果（{{ rules.length }}）</h4>
    <el-table :data="rules" size="small" row-key="ruleKey">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="pad">
            <div v-if="row.criterion"><b>判定标准：</b>{{ row.criterion }}</div>
            <div v-if="row.source"><b>来源：</b>{{ row.source }}</div>
            <div v-if="row.note"><b>说明：</b>{{ row.note }}</div>
            <el-table :data="metricsOf(row)" size="small" class="mt-8px">
              <el-table-column label="指标" min-width="180">
                <template #default="{ row: m }">{{ m.label || m.code }}<div class="muted">{{ m.metricKey }}</div></template>
              </el-table-column>
              <el-table-column label="值" width="110">
                <template #default="{ row: m }">
                  <span v-if="m.valueNum !== null && m.valueNum !== undefined">{{ Number(m.valueNum).toFixed(2) }} {{ m.unit }}</span>
                  <span v-else class="err">不可用：{{ m.unavailableReason || '缺数据' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="证据等级" width="130">
                <template #default="{ row: m }">{{ CLASSIFICATION_LABEL[m.classification] || m.classification }}</template>
              </el-table-column>
              <el-table-column label="验证状态" prop="validationStatus" width="170" />
              <el-table-column label="方法" prop="method" min-width="160" show-overflow-tooltip />
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="规则" prop="ruleId" width="90" />
      <el-table-column label="内容" prop="label" min-width="200" show-overflow-tooltip />
      <el-table-column label="侧别" width="70">
        <template #default="{ row }">{{ sideLabel(row.side) }}</template>
      </el-table-column>
      <el-table-column label="结果" width="130">
        <template #default="{ row }">
          <el-tag size="small" :type="outcomeTag(row.outcome)">{{ row.outcome }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="判定方" width="110">
        <template #default="{ row }">{{ DECIDED[row.decidedBy] || row.decidedBy || '—' }}</template>
      </el-table-column>
      <el-table-column label="阈值状态" prop="thresholdStatus" width="160" show-overflow-tooltip />
    </el-table>
  </div>
</template>

<script lang="ts" setup>
import { CLASSIFICATION_LABEL, FINAL_STATUS_LABEL, SYSTEM_STATUS_LABEL, sideLabel } from '../constants'

const props = defineProps<{ score: any; result: any }>()
const DECIDED: Record<string, string> = { auto: '系统（已验证）', auto_candidate: '系统候选', manual: '治疗师' }

const fmt = (v: any) => (v === null || v === undefined ? '—' : Number(v).toString())
const outcomeTag = (o?: string): any =>
  !o ? 'info' : o === 'met' || o.startsWith('rated_0') ? 'success' : o === 'not_met' || o === 'present' ? 'danger' : o.startsWith('candidate') ? 'warning' : 'info'

const sides = computed(() =>
  Object.entries(props.score.detail?.sides || {}).map(([side, v]: [string, any]) => ({ side: sideLabel(side) || side, ...v }))
)
const rules = computed(() => {
  const keys = new Set<string>(props.score.evidence?.rule_ids || [])
  return (props.result.rules || []).filter((r: any) => keys.has(r.ruleKey))
})
const metricByKey = computed(() => {
  const m = new Map<string, any>()
  for (const x of props.result.metrics || []) m.set(x.metricKey, x)
  return m
})
const collect = (node: any, out: Set<string>) => {
  if (Array.isArray(node)) node.forEach((n) => collect(n, out))
  else if (node && typeof node === 'object') {
    for (const [k, v] of Object.entries(node)) {
      if (k === 'metric_id' && typeof v === 'string') out.add(v)
      else if (k === 'metric_ids' && Array.isArray(v)) v.forEach((x) => typeof x === 'string' && out.add(x))
      else collect(v, out)
    }
  }
}
const metricsOf = (rule: any) => {
  const ids = new Set<string>()
  try {
    collect(JSON.parse(rule.evidenceJson || '[]'), ids)
  } catch {
    /* 无证据 */
  }
  return Array.from(ids).map((id) => metricByKey.value.get(id) || { metricKey: id, code: id, unavailableReason: '指标未入库' })
}
</script>

<style scoped>
h4 {
  margin: 16px 0 8px;
}
.pad {
  padding: 8px 16px;
  font-size: 13px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.err {
  color: var(--el-color-danger);
}
</style>
