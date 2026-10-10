<template>
  <div>
    <div class="toolbar">
      <span>对比基线：</span>
      <el-select v-model="baseId" placeholder="选择同一患者的其他评估" style="width: 360px" @change="load">
        <el-option v-for="o in options" :key="o.id" :label="optionLabel(o)" :value="o.id" />
      </el-select>
      <span class="muted">仅可对比同一患者；未审核的分数会标注来源。</span>
    </div>
    <template v-if="data">
      <el-alert v-if="data.versionWarning" type="warning" :closable="false" :title="data.versionWarning" class="mt-8px" />
      <div class="muted mt-8px">测量误差：{{ data.measurement_error }}。{{ data.note }}</div>
      <h4>评分对比</h4>
      <el-table :data="data.scores || []" size="small">
        <el-table-column label="项目" min-width="160">
          <template #default="{ row }">{{ testLabel(row.test_code) }} {{ sideLabel(row.side) }}</template>
        </el-table-column>
        <el-table-column label="计分方案" prop="scheme" width="180" />
        <el-table-column label="基线" width="140">
          <template #default="{ row }">{{ fmt(row.baseline) }} <span class="muted">{{ SRC[row.baseline_source] || row.baseline_source }}</span></template>
        </el-table-column>
        <el-table-column label="本次" width="140">
          <template #default="{ row }">{{ fmt(row.current) }} <span class="muted">{{ SRC[row.current_source] || row.current_source }}</span></template>
        </el-table-column>
        <el-table-column label="变化" width="90">
          <template #default="{ row }">{{ delta(row.delta) }}</template>
        </el-table-column>
      </el-table>
      <h4>指标对比（每侧/分期取均值）</h4>
      <el-table :data="data.metrics || []" size="small" max-height="420">
        <el-table-column label="测试" width="140">
          <template #default="{ row }">{{ testLabel(row.test_code) }}</template>
        </el-table-column>
        <el-table-column label="指标" prop="code" min-width="180" show-overflow-tooltip />
        <el-table-column label="侧别" width="70">
          <template #default="{ row }">{{ sideLabel(row.side) }}</template>
        </el-table-column>
        <el-table-column label="分期" prop="phase" width="90" />
        <el-table-column label="基线" width="90">
          <template #default="{ row }">{{ fmt(row.baseline) }}</template>
        </el-table-column>
        <el-table-column label="本次" width="90">
          <template #default="{ row }">{{ fmt(row.current) }}</template>
        </el-table-column>
        <el-table-column label="变化" width="110">
          <template #default="{ row }">{{ delta(row.delta) }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="超出测量误差" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.beyond_measurement_error === true" size="small" type="warning">是</el-tag>
            <span v-else-if="row.beyond_measurement_error === false" class="muted">否</span>
            <span v-else class="muted">未知</span>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { MotionApi, MotionAssessmentVO } from '@/api/rehab/motion'
import { formatDate } from '@/utils/formatTime'
import { sideLabel, testLabel, visitLabel } from '../constants'

const props = defineProps<{ assessment: MotionAssessmentVO }>()
const SRC: Record<string, string> = { final: '最终', system_unreviewed: '系统(未审)', system: '系统' }
const options = ref<any[]>([])
const baseId = ref<number>()
const data = ref<any>()

const fmt = (v: any) => (v === null || v === undefined ? '—' : Number(v).toString())
const delta = (v: any) => (v === null || v === undefined ? '—' : (Number(v) > 0 ? '+' : '') + Number(v).toString())
const optionLabel = (o: any) =>
  `#${o.id} ${visitLabel(o.visitType)} ${o.captureTime ? formatDate(o.captureTime, 'YYYY-MM-DD') : ''} ${o.status === 'COMPLETED' ? '已签署' : ''}`
const load = async () => {
  if (!baseId.value) return
  data.value = await MotionApi.compare(baseId.value, props.assessment.id)
}
onMounted(async () => {
  const all: any[] = (await MotionApi.listByPatient(props.assessment.patientId)) || []
  options.value = all.filter((x) => x.id !== props.assessment.id)
  baseId.value = props.assessment.baselineId && options.value.some((o) => o.id === props.assessment.baselineId) ? props.assessment.baselineId : undefined
  await load()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}
h4 {
  margin: 16px 0 8px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
