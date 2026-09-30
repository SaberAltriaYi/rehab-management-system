<template>
  <svg :viewBox="`0 0 ${W} ${H}`" class="chart" preserveAspectRatio="none" @mousemove="onMove" @mouseleave="hover = -1">
    <!-- 分期背景 -->
    <g v-for="(b, i) in bands" :key="'b' + i">
      <rect :x="sx(b.start)" :y="PAD_T" :width="Math.max(1, sx(b.end) - sx(b.start))" :height="H - PAD_T - PAD_B" :fill="bandColor(b.code)" opacity="0.18" />
      <text :x="sx(b.start) + 2" :y="PAD_T + 10" font-size="9" fill="#666">{{ b.code }}{{ b.rep ? '#' + b.rep : '' }}</text>
    </g>
    <!-- 坐标轴 -->
    <line :x1="PAD_L" :y1="H - PAD_B" :x2="W - PAD_R" :y2="H - PAD_B" stroke="#bbb" />
    <line :x1="PAD_L" :y1="PAD_T" :x2="PAD_L" :y2="H - PAD_B" stroke="#bbb" />
    <text v-for="t in yTicks" :key="'y' + t" :x="PAD_L - 4" :y="sy(t) + 3" font-size="9" text-anchor="end" fill="#888">{{ t.toFixed(yStepDigits) }}</text>
    <text v-for="t in xTicks" :key="'x' + t" :x="sx(t)" :y="H - PAD_B + 12" font-size="9" text-anchor="middle" fill="#888">{{ t.toFixed(1) }}s</text>
    <!-- 曲线（缺失值断开，不插值） -->
    <path v-for="(s, i) in series" :key="s.name" :d="pathOf(s.values)" fill="none" :stroke="COLORS[i % COLORS.length]" stroke-width="1.3" />
    <line v-if="hover >= 0" :x1="sx(time[hover])" :x2="sx(time[hover])" :y1="PAD_T" :y2="H - PAD_B" stroke="#999" stroke-dasharray="3,3" />
    <!-- 图例 -->
    <g v-for="(s, i) in series" :key="'l' + s.name">
      <rect :x="PAD_L + 6 + i * 150" :y="H - 12" width="10" height="3" :fill="COLORS[i % COLORS.length]" />
      <text :x="PAD_L + 20 + i * 150" :y="H - 8" font-size="9" fill="#444">
        {{ s.name }}{{ s.unit ? ' (' + s.unit + ')' : '' }}{{ hover >= 0 && s.values[hover] !== null && s.values[hover] !== undefined ? ' = ' + Number(s.values[hover]).toFixed(2) : '' }}
      </text>
    </g>
  </svg>
</template>

<script lang="ts" setup>
/** 轻量 SVG 折线图（无外部依赖）。null/NaN 视为缺失并断开曲线。 */
const props = defineProps<{
  time: number[]
  series: Array<{ name: string; unit?: string; values: Array<number | null> }>
  bands?: Array<{ code: string; start: number; end: number; rep?: number }>
}>()
const W = 900
const H = 300
const PAD_L = 48
const PAD_R = 12
const PAD_T = 12
const PAD_B = 34
const COLORS = ['#409eff', '#e6a23c', '#67c23a', '#f56c6c', '#909399', '#8e44ad']
const hover = ref(-1)

const valid = (v: any) => typeof v === 'number' && isFinite(v)
const xMin = computed(() => (props.time.length ? props.time[0] : 0))
const xMax = computed(() => (props.time.length ? props.time[props.time.length - 1] : 1))
const yRange = computed(() => {
  let lo = Infinity
  let hi = -Infinity
  for (const s of props.series) for (const v of s.values) if (valid(v)) (lo = Math.min(lo, v as number)), (hi = Math.max(hi, v as number))
  if (!isFinite(lo)) return [0, 1]
  if (lo === hi) return [lo - 1, hi + 1]
  const pad = (hi - lo) * 0.05
  return [lo - pad, hi + pad]
})
const sx = (t: number) => PAD_L + ((t - xMin.value) / Math.max(1e-9, xMax.value - xMin.value)) * (W - PAD_L - PAD_R)
const sy = (v: number) => H - PAD_B - ((v - yRange.value[0]) / (yRange.value[1] - yRange.value[0])) * (H - PAD_T - PAD_B)
const ticks = (lo: number, hi: number, n: number) => Array.from({ length: n + 1 }, (_, i) => lo + ((hi - lo) * i) / n)
const yTicks = computed(() => ticks(yRange.value[0], yRange.value[1], 4))
const xTicks = computed(() => ticks(xMin.value, xMax.value, 6))
const yStepDigits = computed(() => (yRange.value[1] - yRange.value[0] < 1 ? 3 : 1))
const pathOf = (values: Array<number | null>) => {
  let d = ''
  let pen = false
  values.forEach((v, i) => {
    if (!valid(v) || i >= props.time.length) {
      pen = false
      return
    }
    d += `${pen ? 'L' : 'M'}${sx(props.time[i]).toFixed(1)},${sy(v as number).toFixed(1)}`
    pen = true
  })
  return d
}
const bandColor = (code: string) =>
  ({ descent: '#409eff', bottom: '#f56c6c', ascent: '#67c23a', flight: '#e6a23c', landing: '#f56c6c', stance: '#909399' })[code] ||
  '#c0c4cc'
const onMove = (e: MouseEvent) => {
  const el = e.currentTarget as SVGElement
  const rect = el.getBoundingClientRect()
  const x = ((e.clientX - rect.left) / rect.width) * W
  const t = xMin.value + ((x - PAD_L) / (W - PAD_L - PAD_R)) * (xMax.value - xMin.value)
  let best = -1
  let bestD = Infinity
  props.time.forEach((tt, i) => {
    const d = Math.abs(tt - t)
    if (d < bestD) (bestD = d), (best = i)
  })
  hover.value = best
}
</script>

<style scoped>
.chart {
  width: 100%;
  height: 320px;
  background: #fff;
}
</style>
