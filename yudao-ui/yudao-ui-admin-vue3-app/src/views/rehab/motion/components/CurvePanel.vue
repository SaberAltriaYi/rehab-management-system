<template>
  <div>
    <el-empty v-if="!trialNames.length" description="暂无曲线（需完成分析）" />
    <template v-else>
      <div class="toolbar">
        <el-select v-model="trialName" style="width: 220px" @change="loadSeries">
          <el-option v-for="n in trialNames" :key="n" :label="n" :value="n" />
        </el-select>
        <el-select v-model="selected" multiple collapse-tags :max-collapse-tags="3" filterable style="width: 460px" placeholder="选择坐标">
          <el-option v-for="c in coordinates" :key="c" :label="c + (units[c] ? ' (' + units[c] + ')' : '')" :value="c" />
        </el-select>
        <span class="muted">{{ data?.note }}{{ data?.decimation > 1 ? `（每 ${data.decimation} 帧取 1）` : '' }}</span>
      </div>
      <LineChart v-if="data" :time="data.time_s || []" :series="chartSeries" :bands="bands" />
      <div v-if="trial" class="muted">
        分期检测：{{ phaseInfo }}
      </div>
    </template>

    <el-divider content-position="left">视频（仅授权后可见）</el-divider>
    <div v-if="!assessment.videoConsent" class="muted">未取得视频授权：系统不上传、不保存、不展示视频。</div>
    <template v-else>
      <div class="toolbar">
        <el-select v-model="videoId" placeholder="选择视频" clearable style="width: 460px" @change="playVideo">
          <el-option v-for="v in videos" :key="v.id" :label="v.relativePath" :value="v.id" />
        </el-select>
      </div>
      <video v-if="videoUrl" :src="videoUrl" controls class="video"></video>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { MotionApi, MotionAssessmentVO, MotionFileVO } from '@/api/rehab/motion'
import LineChart from './LineChart.vue'

const props = defineProps<{ assessment: MotionAssessmentVO; result: any }>()
const trialNames = ref<string[]>([])
const trialName = ref<string>()
const data = ref<any>()
const selected = ref<string[]>([])
const videos = ref<MotionFileVO[]>([])
const videoId = ref<number>()
const videoUrl = ref<string>()

const coordinates = computed(() => Object.keys(data.value?.coordinates || {}))
const units = computed<Record<string, string>>(() => data.value?.units || {})
const chartSeries = computed(() =>
  selected.value.map((c) => ({ name: c, unit: units.value[c], values: data.value?.coordinates?.[c] || [] }))
)
const trial = computed(() => (props.result?.trials || []).find((t: any) => t.opencapTrialName === trialName.value))
const parse = (s: any) => {
  try {
    return typeof s === 'string' ? JSON.parse(s) : s
  } catch {
    return null
  }
}
const bands = computed(() =>
  (parse(trial.value?.phasesJson) || [])
    .filter((p: any) => typeof p.start_s === 'number' && typeof p.end_s === 'number')
    .map((p: any) => ({ code: p.code, start: p.start_s, end: p.end_s === p.start_s ? p.end_s + 0.02 : p.end_s, rep: p.rep }))
)
const phaseInfo = computed(() => {
  const d = parse(trial.value?.phaseDetectionJson) || {}
  return [d.strategy, d.driver ? '驱动信号 ' + d.driver : '', d.repetitions !== undefined ? '重复 ' + d.repetitions : '']
    .filter(Boolean)
    .join('，')
})

const DEFAULTS = ['knee_angle_r', 'knee_angle_l', 'hip_flexion_r', 'hip_flexion_l', 'pelvis_ty']
const loadSeries = async () => {
  if (!trialName.value) return
  data.value = await MotionApi.series(props.assessment.id, trialName.value)
  const cs = coordinates.value
  selected.value = DEFAULTS.filter((c) => cs.includes(c)).slice(0, 4)
  if (!selected.value.length) selected.value = cs.slice(0, 3)
}

const playVideo = async () => {
  if (videoUrl.value) URL.revokeObjectURL(videoUrl.value)
  videoUrl.value = undefined
  if (!videoId.value) return
  const blob: any = await MotionApi.downloadFile(videoId.value)
  videoUrl.value = URL.createObjectURL(blob)
}

onMounted(async () => {
  const r: any = await MotionApi.series(props.assessment.id)
  trialNames.value = r?.trials || []
  trialName.value = trialNames.value[0]
  await loadSeries()
  if (props.assessment.videoConsent) {
    videos.value = ((await MotionApi.listFiles(props.assessment.id)) || []).filter((f) => f.fileKind === 'video')
  }
})
onBeforeUnmount(() => videoUrl.value && URL.revokeObjectURL(videoUrl.value))
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.video {
  max-width: 100%;
  max-height: 420px;
  margin-top: 8px;
}
</style>
