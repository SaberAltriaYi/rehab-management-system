<template>
  <div>
    <el-empty v-if="!points.length" description="暂无评估" :image-size="60" />
    <template v-else>
      <div class="muted mb-8px">{{ data?.note }}</div>
      <div class="toolbar">
        <el-select v-model="series" multiple collapse-tags style="width: 420px" placeholder="选择项目">
          <el-option v-for="k in keys" :key="k" :label="keyLabel(k)" :value="k" />
        </el-select>
      </div>
      <LineChart :time="xs" :series="chartSeries" />
      <div class="muted">横轴为评估序号（1 = 最早）。空心/缺失表示该次无分数。</div>
      <el-table :data="points" size="small" class="mt-8px">
        <el-table-column label="序号" type="index" width="60" />
        <el-table-column label="评估" width="80">
          <template #default="{ row }">#{{ row.assessmentId }}</template>
        </el-table-column>
        <el-table-column label="时间" width="120">
          <template #default="{ row }">{{ row.captureTime ? formatDate(row.captureTime, 'YYYY-MM-DD') : '-' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ visitLabel(row.visitType) }}</template>
        </el-table-column>
        <el-table-column label="已签署" width="70">
          <template #default="{ row }">{{ row.signed ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="FMS 总分" width="90">
          <template #default="{ row }">{{ row.fmsTotal ?? '—' }}</template>
        </el-table-column>
        <el-table-column v-for="k in series" :key="k" :label="keyLabel(k)" min-width="120">
          <template #default="{ row }">
            <span v-if="cell(row, k)">{{ cell(row, k).value ?? '—' }} <span v-if="cell(row, k).source !== 'final'" class="muted">未审</span></span>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { MotionApi } from '@/api/rehab/motion'
import { formatDate } from '@/utils/formatTime'
import { sideLabel, testLabel, visitLabel } from '../constants'
import LineChart from './LineChart.vue'

const props = defineProps<{ patientId: number }>()
const data = ref<any>()
const series = ref<string[]>([])
const points = computed<any[]>(() => data.value?.points || [])
const keyOf = (s: any) => `${s.testCode}|${s.side || 'overall'}`
const keys = computed(() => {
  const set = new Set<string>(['FMS_TOTAL|overall'])
  for (const p of points.value) for (const s of p.scores || []) if (s.family !== 'NASM_CES') set.add(keyOf(s))
  return Array.from(set)
})
const keyLabel = (k: string) => {
  const [t, side] = k.split('|')
  return t === 'FMS_TOTAL' ? 'FMS 总分' : `${testLabel(t)}${side && side !== 'overall' ? ' ' + sideLabel(side) : ''}`
}
const cell = (p: any, k: string): any => {
  if (k === 'FMS_TOTAL|overall') return p.fmsTotal === null || p.fmsTotal === undefined ? null : { value: p.fmsTotal, source: 'final' }
  return (p.scores || []).find((s: any) => keyOf(s) === k)
}
const xs = computed(() => points.value.map((_, i) => i + 1))
const chartSeries = computed(() =>
  series.value.map((k) => ({
    name: keyLabel(k),
    values: points.value.map((p) => {
      const c = cell(p, k)
      return c && c.value !== null && c.value !== undefined ? Number(c.value) : null
    })
  }))
)
const load = async () => {
  data.value = await MotionApi.trend(props.patientId)
  series.value = keys.value.filter((k) => k.startsWith('FMS_TOTAL') || k.startsWith('YBT') || k.startsWith('LESS')).slice(0, 4)
}
watch(() => props.patientId, load)
onMounted(load)
</script>

<style scoped>
.toolbar {
  margin-bottom: 8px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
