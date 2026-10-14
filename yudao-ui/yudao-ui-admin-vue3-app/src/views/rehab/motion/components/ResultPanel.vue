<template>
  <div>
    <el-empty v-if="!result || !result.analyzedRevision" description="尚未完成分析，请先上传数据并在“处理任务”中开始处理" />
    <template v-else>
      <el-alert v-if="result.stale" type="warning" :closable="false" show-icon title="输入已修改，以下结果基于旧输入，需重新计算后才能审核签署" class="mb-8px" />
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="会话质控">
          <el-tag :type="qcTag(result.sessionQualityStatus)">{{ QC_LABEL[result.sessionQualityStatus] || result.sessionQualityStatus || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="引擎 / 规则">{{ result.engineVersion }} / {{ result.ruleVersion }}</el-descriptions-item>
        <el-descriptions-item label="分析时间">{{ result.analyzedTime ? formatDate(result.analyzedTime) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="协议版本" :span="3">
          <span v-for="(p, k) in result.protocolVersions || {}" :key="k" class="mr-12px">
            {{ k }}：{{ (p as any)?.protocol_version || p }}
          </span>
        </el-descriptions-item>
      </el-descriptions>

      <h4>Trial 质控</h4>
      <el-table :data="result.trials || []" size="small">
        <el-table-column label="Trial" prop="trialKey" width="140" />
        <el-table-column label="测试" min-width="150">
          <template #default="{ row }">{{ testLabel(row.testCode) }} {{ sideLabel(row.side) }} {{ row.conditionCode ? '[' + conditionLabel(row.testCode, row.conditionCode) + ']' : '' }}</template>
        </el-table-column>
        <el-table-column label="第几次" prop="attemptNo" width="70" />
        <el-table-column label="质控" width="90">
          <template #default="{ row }"><el-tag size="small" :type="qcTag(row.qcStatus)">{{ QC_LABEL[row.qcStatus] || row.qcStatus || '-' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="重复次数" prop="repCount" width="80" />
        <el-table-column label="有效" width="70">
          <template #default="{ row }">{{ row.valid === false ? '无效' : '有效' }}</template>
        </el-table-column>
        <el-table-column label="疼痛" width="80">
          <template #default="{ row }">
            <span :class="{ err: row.pain === null || row.pain === undefined }">{{ row.pain === true ? '是' : row.pain === false ? '否' : '未记录' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="质控问题" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ issues(row).join('；') || (row.invalidReason ? '无效原因：' + row.invalidReason : '') }}</template>
        </el-table-column>
      </el-table>

      <h4>评分（系统分 → 治疗师最终分）</h4>
      <el-table :data="result.scores || []" size="small">
        <el-table-column label="项目" min-width="170">
          <template #default="{ row }">{{ row.testLabel || testLabel(row.testCode) }} {{ sideLabel(row.side) }}</template>
        </el-table-column>
        <el-table-column label="计分方案" prop="scoringScheme" width="190" />
        <el-table-column label="系统状态" width="150">
          <template #default="{ row }">{{ SYSTEM_STATUS_LABEL[row.systemStatus] || row.systemStatus }}</template>
        </el-table-column>
        <el-table-column label="系统分" width="80">
          <template #default="{ row }">{{ fmt(row.systemScore) }}</template>
        </el-table-column>
        <el-table-column label="参考分" width="80">
          <template #default="{ row }">
            <el-tooltip content="含候选/待验证判定，仅供治疗师参考，不作为系统分"><span class="muted">{{ fmt(row.provisionalScore) }}</span></el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="最终分" width="80">
          <template #default="{ row }"><b>{{ fmt(row.finalScore) }}</b></template>
        </el-table-column>
        <el-table-column label="审核" width="150">
          <template #default="{ row }">
            <el-tag size="small" :type="row.finalStatus === 'needs_recheck' ? 'danger' : row.finalStatus === 'pending' ? 'info' : 'success'">
              {{ FINAL_STATUS_LABEL[row.finalStatus] || row.finalStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="证据" width="80">
          <template #default="{ row }"><el-button link type="primary" @click="openEvidence(row)">查看</el-button></template>
        </el-table-column>
      </el-table>
      <div class="total">
        FMS 总分：
        <b v-if="result.fmsTotal !== null && result.fmsTotal !== undefined">{{ result.fmsTotal }} / 21</b>
        <span v-else class="muted">未显示</span>
        <span class="muted">（{{ result.fmsTotalNote }}）</span>
      </div>

      <h4>局限性</h4>
      <ul class="lim">
        <li v-for="(l, i) in result.limitations || []" :key="i">{{ l }}</li>
      </ul>
    </template>

    <el-drawer v-model="evidenceVisible" :title="evidenceTitle" size="55%">
      <EvidenceView v-if="evidenceScore" :score="evidenceScore" :result="result" />
    </el-drawer>
  </div>
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import { FINAL_STATUS_LABEL, QC_LABEL, SYSTEM_STATUS_LABEL, conditionLabel, sideLabel, testLabel } from '../constants'
import EvidenceView from './EvidenceView.vue'

defineProps<{ result: any }>()

const evidenceVisible = ref(false)
const evidenceScore = ref<any>()
const evidenceTitle = computed(() =>
  evidenceScore.value ? `证据链：${evidenceScore.value.testLabel || evidenceScore.value.testCode} ${sideLabel(evidenceScore.value.side)}` : ''
)

const fmt = (v: any) => (v === null || v === undefined ? '—' : Number(v).toString())
const qcTag = (s?: string): any => (s === 'pass' ? 'success' : s === 'warn' ? 'warning' : s === 'fail' ? 'danger' : 'info')
const issues = (t: any): string[] => {
  try {
    const arr = typeof t.qcIssuesJson === 'string' ? JSON.parse(t.qcIssuesJson) : t.qcIssuesJson || []
    return arr.map((i: any) => (typeof i === 'string' ? i : `${i.code || ''}${i.message ? ' ' + i.message : ''}`))
  } catch {
    return []
  }
}
const openEvidence = (row: any) => {
  evidenceScore.value = row
  evidenceVisible.value = true
}
</script>

<style scoped>
h4 {
  margin: 16px 0 8px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.err {
  color: var(--el-color-danger);
}
.total {
  margin-top: 8px;
}
.lim {
  padding-left: 18px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
</style>
