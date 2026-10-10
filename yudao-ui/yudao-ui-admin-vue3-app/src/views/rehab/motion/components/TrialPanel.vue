<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="mb-12px">
      <template #title>
        每个 Trial 对应一次动作尝试：选择测试项目、侧别、条件（如深蹲足跟垫高）与第几次尝试，并关联 OpenCap Trial 名称。
        FMS 每项最多取 3 次有效尝试中的最好一次；双侧项目分别录入左右。系统给出的“建议”仅按文件名推测，需人工确认。
      </template>
    </el-alert>
    <div class="toolbar">
      <el-button :disabled="locked" @click="addRow()">新增 Trial</el-button>
      <el-button v-if="!isOpencap" :disabled="locked || !motTrials.length" @click="fromFiles">
        按已上传 .mot 生成（{{ motTrials.length }}）
      </el-button>
      <template v-else>
        <el-button
          v-hasPermi="['rehab:motion:process']"
          :disabled="locked || !assessment.opencapSessionId"
          :loading="ocLoading"
          @click="loadOpencap"
        >
          读取 OpenCap 会话 Trial
        </el-button>
        <el-button :disabled="locked || !ocDynamic.length" @click="fromOpencap">按 OpenCap Trial 生成（{{ ocDynamic.length }}）</el-button>
      </template>
      <el-button type="primary" v-hasPermi="['rehab:motion:create']" :disabled="locked" :loading="saving" @click="save">
        保存映射
      </el-button>
    </div>
    <el-table :data="rows" size="small" class="mt-8px">
      <el-table-column label="Trial Key" width="170">
        <template #default="{ row }"><el-input v-model="row.trialKey" size="small" :disabled="locked" /></template>
      </el-table-column>
      <el-table-column label="测试项目" min-width="190">
        <template #default="{ row }">
          <el-select
            v-model="row.testCode"
            size="small"
            filterable
            :disabled="locked"
            placeholder="请选择"
            @change="row.side = normalizeSide(row.testCode, row.side)"
          >
            <el-option-group v-for="g in groups" :key="g.family" :label="g.family">
              <el-option v-for="t in g.items" :key="t.value" :label="t.label" :value="t.value" />
            </el-option-group>
          </el-select>
          <div v-if="row._suggested" class="muted">建议：按文件名推测，请确认</div>
        </template>
      </el-table-column>
      <el-table-column label="侧别" width="110">
        <template #default="{ row }">
          <el-select v-model="row.side" size="small" :disabled="locked" placeholder="必选">
            <el-option v-for="s in sideOptionsFor(row.testCode)" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="条件" width="150">
        <template #default="{ row }">
          <el-select
            v-if="conditionOptions(row.testCode).length"
            v-model="row.conditionCode"
            size="small"
            :disabled="locked"
            placeholder="必选"
          >
            <el-option v-for="c in conditionOptions(row.testCode)" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="第几次" width="100">
        <template #default="{ row }">
          <el-input-number v-model="row.attemptNo" size="small" :min="1" :max="20" :disabled="locked" controls-position="right" />
        </template>
      </el-table-column>
      <el-table-column label="OpenCap Trial 名称" min-width="170">
        <template #default="{ row }">
          <el-select
            v-if="isOpencap"
            v-model="row.opencapTrialId"
            size="small"
            filterable
            clearable
            :disabled="locked"
            placeholder="先读取会话"
            @change="(v: string) => pickOpencap(row, v)"
          >
            <el-option v-for="t in ocDynamic" :key="t.trial_id" :label="ocLabel(t)" :value="t.trial_id" />
            <el-option
              v-if="row.opencapTrialId && !ocDynamic.some((t) => t.trial_id === row.opencapTrialId)"
              :label="row.opencapTrialName || row.opencapTrialId"
              :value="row.opencapTrialId"
            />
          </el-select>
          <el-select v-else v-model="row.opencapTrialName" size="small" filterable allow-create clearable :disabled="locked">
            <el-option v-for="n in motTrials" :key="n" :label="n" :value="n" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="QC" width="80">
        <template #default="{ row }">{{ row.id ? QC_LABEL[row.qcStatus] || '-' : '' }}</template>
      </el-table-column>
      <el-table-column label="" width="60">
        <template #default="{ $index }">
          <el-button link type="danger" :disabled="locked" @click="rows.splice($index, 1)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script lang="ts" setup>
import { MotionApi, MotionAssessmentVO, MotionTrialItem } from '@/api/rehab/motion'
import { QC_LABEL, TEST_OPTIONS, conditionOptions, normalizeSide, sideOptionsFor } from '../constants'

const props = defineProps<{ assessment: MotionAssessmentVO }>()
const emit = defineEmits(['changed'])
const message = useMessage()

type Row = MotionTrialItem & { qcStatus?: string; _suggested?: boolean }
const rows = ref<Row[]>([])
const motTrials = ref<string[]>([])
const saving = ref(false)
const locked = computed(() => props.assessment.status === 'COMPLETED')
const groups = computed(() => {
  const fam = new Set(
    (props.assessment.protocolFamilies || []).map((f) => (f === 'NASM_CES' ? 'NASM' : f === 'YBT_LQ' ? 'YBT' : f === 'TUCK_JUMP' ? 'TJA' : f))
  )
  const out: Array<{ family: string; items: typeof TEST_OPTIONS }> = []
  for (const t of TEST_OPTIONS) {
    if (fam.size && !fam.has(t.family)) continue
    let g = out.find((x) => x.family === t.family)
    if (!g) out.push((g = { family: t.family, items: [] }))
    g.items.push(t)
  }
  return out
})

const KEY_RE = /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$/
/** 仅做提示性推测，不自动保存 */
const guess = (name: string): { testCode?: string; side: string } => {
  const n = name.toLowerCase()
  const hinted = /(^|[_-])(l|left)$|left/.test(n) ? 'left' : /(^|[_-])(r|right)$|right/.test(n) ? 'right' : undefined
  const rules: Array<[RegExp, string]> = [
    [/ybalance|ybt/, 'YBT_LQ'],
    [/tuck/, 'TUCK_JUMP'],
    // LESS 为双脚跳箱落地；单腿落地（1leg/single）不推测为 LESS
    [/^(?!.*(1leg|single)).*(droplanding|drop_jump|less)/, 'LESS'],
    [/hurdle/, 'FMS_HURDLE_STEP'],
    [/lunge/, 'FMS_INLINE_LUNGE'],
    [/aslr|legraise/, 'FMS_ASLR'],
    [/rotary/, 'FMS_ROTARY_STABILITY'],
    [/1legsquat|singlelegsquat|sls/, 'NASM_SLS'],
    [/overhead|ohs/, 'NASM_OHS'],
    [/gait|walk/, 'NASM_GAIT'],
    [/deepsquat|^squat/, 'FMS_DEEP_SQUAT']
  ]
  const hit = rules.find(([re]) => re.test(n))
  const testCode = hit ? hit[1] : undefined
  return { testCode, side: testCode ? normalizeSide(testCode, hinted) : hinted || '' }
}

/** OpenCap 会话来源：Trial 必须关联 OpenCap Trial ID，处理任务据此让引擎下载结果 */
type OcTrial = { trial_id: string; name: string; file_stem?: string; status?: string; ready?: boolean; is_dynamic?: boolean }
const isOpencap = computed(() => props.assessment.dataSource === 'opencap')
const ocTrials = ref<OcTrial[]>([])
const ocLoading = ref(false)
const ocDynamic = computed(() => ocTrials.value.filter((t) => t.is_dynamic))
const ocLabel = (t: OcTrial) => `${t.name}${t.ready ? '' : `（${t.status || '未就绪'}）`}`
const loadOpencap = async () => {
  ocLoading.value = true
  try {
    ocTrials.value = ((await MotionApi.opencapTrials(props.assessment.id, props.assessment.opencapSessionId!)) || []) as OcTrial[]
    if (!ocDynamic.value.length) message.warning('该会话没有可用的动态 Trial（neutral/calibration 已排除）')
  } finally {
    ocLoading.value = false
  }
}
const pickOpencap = (row: Row, id?: string) => {
  const t = ocTrials.value.find((x) => x.trial_id === id)
  row.opencapTrialName = t ? t.file_stem || t.name : undefined
}
const fromOpencap = () => {
  const existing = new Set(rows.value.map((r) => r.opencapTrialId))
  let added = 0
  for (const t of ocDynamic.value) {
    if (existing.has(t.trial_id) || /^(alignment|static)/i.test(t.name)) continue
    const g = guess(t.name)
    addRow({
      trialKey: (t.file_stem || t.name).replace(/[^A-Za-z0-9_.:-]/g, '_').slice(0, 64),
      testCode: g.testCode || '',
      side: g.side,
      opencapTrialId: t.trial_id,
      opencapTrialName: t.file_stem || t.name,
      _suggested: !!g.testCode
    })
    added++
  }
  message.info(added ? `已生成 ${added} 行，请逐行确认测试项目与侧别` : '没有新的 OpenCap Trial')
}

const load = async () => {
  const [trials, files] = await Promise.all([MotionApi.listTrials(props.assessment.id), MotionApi.listFiles(props.assessment.id)])
  rows.value = (trials || []).map((t: any) => ({
    ...t,
    conditionCode: t.conditionCode || (t.testCode === 'FMS_DEEP_SQUAT' ? 'standard' : '')
  }))
  motTrials.value = Array.from(new Set((files || []).filter((f) => f.fileKind === 'mot' && f.trialName).map((f) => f.trialName!)))
}

const addRow = (init: Partial<Row> = {}) =>
  rows.value.push({ trialKey: '', testCode: '', side: '', conditionCode: '', attemptNo: 1, ...init })

const fromFiles = () => {
  const existing = new Set(rows.value.map((r) => r.opencapTrialName))
  let added = 0
  for (const name of motTrials.value) {
    if (existing.has(name) || /^(neutral|alignment|static|calibration)/i.test(name)) continue
    const g = guess(name)
    addRow({
      trialKey: name.replace(/[^A-Za-z0-9_.:-]/g, '_').slice(0, 64),
      testCode: g.testCode || '',
      side: g.side,
      opencapTrialName: name,
      _suggested: !!g.testCode
    })
    added++
  }
  message.info(added ? `已生成 ${added} 行，请逐行确认测试项目与侧别` : '没有新的 Trial（静态/标定 Trial 已忽略）')
}

const save = async () => {
  const keys = new Set<string>()
  for (const r of rows.value) {
    if (!KEY_RE.test(r.trialKey)) return message.error(`Trial Key 不合法：${r.trialKey || '(空)'}`)
    if (keys.has(r.trialKey)) return message.error(`Trial Key 重复：${r.trialKey}`)
    if (!r.testCode) return message.error(`请为 ${r.trialKey} 选择测试项目`)
    if (!sideOptionsFor(r.testCode).some((o) => o.value === r.side)) {
      const labels = sideOptionsFor(r.testCode).map((o) => o.label).join(' / ')
      return message.error(`${r.trialKey}：请选择侧别（${labels}${r.testCode === 'YBT_LQ' ? '，YBT 为支撑腿' : ''}）`)
    }
    if (isOpencap.value && !r.opencapTrialId) return message.error(`${r.trialKey}：请选择对应的 OpenCap Trial`)
    const opts = conditionOptions(r.testCode)
    if (!opts.length) {
      r.conditionCode = ''
    } else if (!opts.some((o) => o.value === r.conditionCode)) {
      if (r.testCode === 'FMS_DEEP_SQUAT' && !r.conditionCode) r.conditionCode = 'standard'
      else return message.error(`${r.trialKey}：请选择条件（${opts.map((o) => o.label).join(' / ')}）`)
    }
    keys.add(r.trialKey)
  }
  saving.value = true
  try {
    await MotionApi.saveTrials(
      props.assessment.id,
      rows.value.map((r, i) => ({
        id: r.id,
        trialKey: r.trialKey,
        testCode: r.testCode,
        side: r.side,
        conditionCode: r.conditionCode || undefined,
        attemptNo: r.attemptNo,
        opencapTrialName: r.opencapTrialName || undefined,
        opencapTrialId: r.opencapTrialId,
        sortNo: i
      }))
    )
    message.success('已保存；输入已变更，请重新计算')
    await load()
    emit('changed')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await load()
  if (isOpencap.value && props.assessment.opencapSessionId && !locked.value) {
    await loadOpencap().catch(() => undefined)
  }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
}
.muted {
  color: var(--el-color-warning);
  font-size: 12px;
}
</style>
