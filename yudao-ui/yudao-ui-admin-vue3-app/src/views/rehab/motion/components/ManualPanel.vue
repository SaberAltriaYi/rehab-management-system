<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="mb-12px">
      <template #title>
        人工录入是规则计算的输入（疼痛、有效性、肩部灵活性拳距、清除测试、YBT 伸够距离、TJA/LESS 评定、NASM 观察）。
        保存后输入版本递增，需在“处理任务”中重新计算。已分析过的评估修改时必须填写原因，所有修改留痕。
        疼痛未记录的 FMS 项目不会给出建议分；动作或清除测试疼痛 → 该项 0 分。
        FMS 官方原则：每侧最多 3 次取最好一次；当存在疑问时给予低分，边界值取较低等级。
      </template>
    </el-alert>
    <el-form-item label="修改原因" v-if="assessment.analyzedRevision">
      <el-input v-model="reason" maxlength="200" placeholder="已分析后修改输入必须填写原因（写入留痕）" style="width: 480px" />
    </el-form-item>

    <h4>Trial 人工信息</h4>
    <el-empty v-if="!trials.length" description="请先在“Trial 映射”中建立 Trial" />
    <el-collapse v-else v-model="openTrials">
      <el-collapse-item v-for="t in trials" :key="t.id" :name="t.id">
        <template #title>
          <b>{{ t.trialKey }}</b>&nbsp;{{ testLabel(t.testCode) }} {{ sideLabel(t.side) }} 第{{ t.attemptNo }}次
          <el-tag v-if="t.pain === null || t.pain === undefined" size="small" type="danger" class="ml-8px">疼痛未记录</el-tag>
          <el-tag v-if="t.valid === false" size="small" type="warning" class="ml-4px">无效</el-tag>
        </template>
        <el-form label-width="120px" size="small">
          <el-form-item label="疼痛">
            <el-radio-group v-model="edit[t.id].pain">
              <el-radio :value="false">无痛</el-radio>
              <el-radio :value="true">疼痛</el-radio>
              <el-radio value="">未记录</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="有效尝试">
            <el-switch v-model="edit[t.id].valid" />
            <el-input
              v-if="!edit[t.id].valid"
              v-model="edit[t.id].invalidReason"
              maxlength="200"
              placeholder="无效原因（必填，例如：未按口令完成、遮挡）"
              style="width: 360px; margin-left: 12px"
            />
            <span class="muted">无效尝试不计为 1 分，只记录原因</span>
          </el-form-item>
          <el-form-item v-if="t.testCode === 'FMS_SHOULDER_MOBILITY'" label="拳距 / 手长">
            拳距 <el-input-number v-model="edit[t.id].values.fist_distance_cm" :min="0" :max="150" :precision="1" /> cm
            手长 <el-input-number v-model="edit[t.id].values.hand_length_cm" :min="5" :max="40" :precision="1" /> cm
            <span class="muted">
              比值 &lt;1.0 → 3；1.0–&lt;1.5 → 2；≥1.5 → 1（恰等于边界取低分）。手长 = 腕横纹至中指尖；评分侧 = 过肩（上方）手一侧
            </span>
          </el-form-item>
          <el-form-item v-if="t.testCode === 'FMS_TSPU'" label="手位（官方）">
            <span class="muted">
              男性：3 分拇指与额头（上缘）对齐，2 分与下颌对齐；女性：3 分与下颌对齐，2 分与锁骨对齐。高位未完成才降至低位（condition=low）
            </span>
          </el-form-item>
          <el-form-item v-if="criteriaRules(t).length" label="标准判定">
            <el-table :data="criteriaRules(t)" size="small" style="width: 100%">
              <el-table-column label="规则" prop="ruleId" width="90" />
              <el-table-column label="内容" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.label }}
                  <el-tag v-if="(row.note || '').includes('不参与分级')" size="small" type="info">仅证据·不参与分级</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="系统结果" width="140">
                <template #default="{ row }">{{ row.autoOutcome || row.outcome }}</template>
              </el-table-column>
              <el-table-column label="治疗师判定" width="220">
                <template #default="{ row }">
                  <el-radio-group v-model="edit[t.id].criteria[row.ruleId]" size="small">
                    <el-radio-button :value="true">满足</el-radio-button>
                    <el-radio-button :value="false">不满足</el-radio-button>
                    <el-radio-button :value="undefined">未判定</el-radio-button>
                  </el-radio-group>
                </template>
              </el-table-column>
            </el-table>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" v-hasPermi="['rehab:motion:review']" :disabled="locked" @click="saveTrial(t)">保存该 Trial</el-button>
          </el-form-item>
        </el-form>
      </el-collapse-item>
    </el-collapse>

    <template v-if="families.has('FMS')">
      <h4>FMS 清除测试</h4>
      <el-table :data="CLEARING_TESTS" size="small" style="max-width: 760px">
        <el-table-column label="清除测试" prop="label" />
        <el-table-column label="结果" width="280">
          <template #default="{ row }">
            <el-radio-group v-model="clearing[row.code]">
              <el-radio :value="false">无痛</el-radio>
              <el-radio :value="true">疼痛</el-radio>
              <el-radio value="">未做/未记录</el-radio>
            </el-radio-group>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <template v-if="families.has('YBT_LQ')">
      <h4>Y-Balance 伸够距离（cm）</h4>
      <div class="muted">
        腿长：左 {{ assessment.limbLengthLeftCm ?? '未填' }} / 右 {{ assessment.limbLengthRightCm ?? '未填' }}（在编辑评估中填写）。
        归一化 = 最大伸够 ÷ 腿长 × 100；综合 = 三方向之和 ÷ (3 × 腿长) × 100；4 cm 与 94% 仅作提示。
      </div>
      <el-table :data="ybt" size="small" style="max-width: 900px">
        <el-table-column label="支撑侧" width="110">
          <template #default="{ row }">
            <el-select v-model="row.stance_side" size="small"><el-option label="左" value="left" /><el-option label="右" value="right" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="110">
          <template #default="{ row }">
            <el-select v-model="row.direction" size="small">
              <el-option label="前 ANT" value="ANT" /><el-option label="后内 PM" value="PM" /><el-option label="后外 PL" value="PL" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="次序" width="110">
          <template #default="{ row }"><el-input-number v-model="row.attempt_no" size="small" :min="1" :max="20" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="距离" width="140">
          <template #default="{ row }"><el-input-number v-model="row.reach_cm" size="small" :min="0" :max="300" :precision="1" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="有效" width="200">
          <template #default="{ row }">
            <el-switch v-model="row.valid" size="small" />
            <el-input v-if="!row.valid" v-model="row.invalid_reason" size="small" placeholder="原因" style="width: 120px; margin-left: 6px" />
          </template>
        </el-table-column>
        <el-table-column width="60">
          <template #default="{ $index }"><el-button link type="danger" @click="ybt.splice($index, 1)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <el-button size="small" class="mt-8px" @click="addYbt">新增一行</el-button>
      <el-button size="small" class="mt-8px" @click="fillYbtGrid">生成 左右×3 方向×3 次</el-button>
    </template>

    <template v-if="families.has('TUCK_JUMP')">
      <h4>团身跳 TJA（{{ tjaVariant.label }}）</h4>
      <div class="muted">系统候选仅供参考；“落地声音过大”只能人工评定。每项取值 0–{{ tjaVariant.max }}。</div>
      <el-table :data="TJA_ITEMS" size="small" style="max-width: 760px">
        <el-table-column label="项目" prop="code" width="80" />
        <el-table-column label="内容" prop="label" />
        <el-table-column label="评定" width="240">
          <template #default="{ row }">
            <el-radio-group v-model="tja[row.code]" size="small">
              <el-radio-button v-for="n in tjaVariant.max + 1" :key="n" :value="n - 1">{{ n - 1 }}</el-radio-button>
              <el-radio-button value="">未评</el-radio-button>
            </el-radio-group>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <template v-if="families.has('LESS')">
      <h4>LESS（每次 Trial 17 项，取 3 次平均）</h4>
      <div class="muted">系统候选错误仅供参考；每次 Trial 需治疗师逐项确认。无效 Trial 需填写原因。</div>
      <el-tabs v-model="lessTab" type="card">
        <el-tab-pane v-for="lt in less" :key="lt.trial_key" :label="lt.trial_key" :name="lt.trial_key">
          <el-switch v-model="lt.valid" active-text="有效" inactive-text="无效" />
          <el-input v-if="!lt.valid" v-model="lt.invalid_reason" size="small" placeholder="无效原因" style="width: 260px; margin-left: 8px" />
          <el-table :data="LESS_ITEMS" size="small" class="mt-8px" style="max-width: 820px">
            <el-table-column label="项目" prop="code" width="90" />
            <el-table-column label="内容" prop="label" />
            <el-table-column label="系统候选" width="90">
              <template #default="{ row }">{{ lessCandidate(lt.trial_key, row.code) }}</template>
            </el-table-column>
            <el-table-column label="评定" width="210">
              <template #default="{ row }">
                <el-radio-group v-model="lt.item_ratings[row.code]" size="small">
                  <el-radio-button v-for="n in row.max + 1" :key="n" :value="n - 1">{{ n - 1 }}</el-radio-button>
                  <el-radio-button value="">未评</el-radio-button>
                </el-radio-group>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <div v-if="!less.length" class="muted">请先在 Trial 映射中添加 LESS Trial（通常 3 次）</div>
    </template>

    <template v-if="families.has('NASM_CES') && nasmCheckpoints.length">
      <h4>NASM 观察（定性，不评分）</h4>
      <el-table :data="nasmCheckpoints" size="small" style="max-width: 1000px">
        <el-table-column label="测试" width="150">
          <template #default="{ row }">{{ testLabel(row.testCode) }}</template>
        </el-table-column>
        <el-table-column label="检查点" prop="label" min-width="200" show-overflow-tooltip />
        <el-table-column label="证据等级" prop="thresholdStatus" width="140" />
        <el-table-column label="是否出现" width="230">
          <template #default="{ row }">
            <el-radio-group v-model="nasm[row.key].present" size="small">
              <el-radio-button :value="true">出现</el-radio-button>
              <el-radio-button :value="false">未出现</el-radio-button>
              <el-radio-button value="">未评</el-radio-button>
            </el-radio-group>
          </template>
        </el-table-column>
        <el-table-column label="备注" width="220">
          <template #default="{ row }"><el-input v-model="nasm[row.key].note" size="small" maxlength="500" /></template>
        </el-table-column>
      </el-table>
    </template>

    <div class="mt-12px">
      <el-button type="primary" v-hasPermi="['rehab:motion:review']" :disabled="locked" :loading="saving" @click="saveInputs">
        保存清除测试 / YBT / TJA / LESS / NASM 录入
      </el-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { MotionApi, MotionAssessmentVO } from '@/api/rehab/motion'
import { CLEARING_TESTS, LESS_ITEMS, TJA_ITEMS, TJA_VARIANTS, sideLabel, testLabel } from '../constants'

const props = defineProps<{ assessment: MotionAssessmentVO; result: any }>()
const emit = defineEmits(['changed'])
const message = useMessage()

const locked = computed(() => props.assessment.status === 'COMPLETED')
const families = computed(() => new Set(props.assessment.protocolFamilies || []))
const tjaVariant = computed(() => TJA_VARIANTS.find((v) => v.value === (props.assessment.tjaVariant || 'TJA_MODIFIED_0_2'))!)
const reason = ref('')
const saving = ref(false)
const trials = ref<any[]>([])
const openTrials = ref<number[]>([])
const edit = reactive<Record<number, { pain: boolean | ''; valid: boolean; invalidReason: string; values: Record<string, any>; criteria: Record<string, any> }>>({})
const clearing = reactive<Record<string, boolean | ''>>({})
const ybt = ref<any[]>([])
const tja = reactive<Record<string, number | ''>>({})
const less = ref<Array<{ trial_key: string; valid: boolean; invalid_reason?: string; item_ratings: Record<string, number | ''> }>>([])
const lessTab = ref('')
const nasm = reactive<Record<string, { test_code: string; checkpoint: string; present: boolean | ''; note: string }>>({})

const parse = (s: any) => {
  try {
    return typeof s === 'string' ? JSON.parse(s) : s || {}
  } catch {
    return {}
  }
}
const rules = computed<any[]>(() => props.result?.rules || [])
const criteriaRules = (t: any) => rules.value.filter((r) => r.family === 'FMS' && r.trialId === t.id)
const nasmCheckpoints = computed(() =>
  rules.value.filter((r) => r.family === 'NASM_CES').map((r) => ({ key: r.ruleKey, testCode: r.testCode, checkpoint: r.ruleId, label: r.label, thresholdStatus: r.thresholdStatus }))
)
const lessCandidate = (trialKey: string, code: string) => {
  const r = rules.value.find((x) => x.ruleKey === `l:${trialKey}:${code}`)
  return r?.autoOutcome ?? '—'
}

const init = async () => {
  trials.value = (await MotionApi.listTrials(props.assessment.id)) || []
  for (const t of trials.value) {
    const values = parse(t.manualValuesJson)
    edit[t.id] = {
      pain: typeof t.pain === 'boolean' ? t.pain : '',
      valid: t.valid !== false,
      invalidReason: t.invalidReason || '',
      values: { fist_distance_cm: values.fist_distance_cm, hand_length_cm: values.hand_length_cm, ...values },
      criteria: { ...parse(t.manualCriteriaJson) }
    }
  }
  const mi = props.result?.manualInputs || {}
  for (const c of CLEARING_TESTS) clearing[c.code] = ''
  for (const c of mi.clearing_tests || []) clearing[c.code] = typeof c.pain === 'boolean' ? c.pain : ''
  ybt.value = (mi.ybt_trials || []).map((y: any) => ({ valid: true, ...y }))
  for (const i of TJA_ITEMS) tja[i.code] = ''
  for (const [k, v] of Object.entries(mi.tuck_jump_ratings || {})) if (typeof v === 'number') tja[k] = v
  const saved = new Map<string, any>((mi.less_trials || []).map((l: any) => [l.trial_key, l]))
  less.value = trials.value
    .filter((t) => t.testCode === 'LESS')
    .map((t) => {
      const s = saved.get(t.trialKey) || {}
      const ratings: Record<string, number | ''> = {}
      for (const i of LESS_ITEMS) ratings[i.code] = typeof s.item_ratings?.[i.code] === 'number' ? s.item_ratings[i.code] : ''
      return { trial_key: t.trialKey, valid: s.valid !== false, invalid_reason: s.invalid_reason, item_ratings: ratings }
    })
  lessTab.value = less.value[0]?.trial_key || ''
  const obs = new Map<string, any>((mi.nasm_observations || []).map((o: any) => [`n:${o.test_code}:${o.checkpoint}`, o]))
  for (const cp of nasmCheckpoints.value) {
    const o = obs.get(cp.key) || {}
    nasm[cp.key] = { test_code: cp.testCode, checkpoint: cp.checkpoint, present: typeof o.present === 'boolean' ? o.present : '', note: o.note || '' }
  }
}

const needReason = () => {
  if (props.assessment.analyzedRevision && !reason.value.trim()) {
    message.warning('已分析的评估修改输入必须填写原因')
    return true
  }
  return false
}

const saveTrial = async (t: any) => {
  if (needReason()) return
  const e = edit[t.id]
  if (!e.valid && !e.invalidReason.trim()) return message.warning('无效尝试必须填写原因')
  const values: Record<string, number> = {}
  for (const [k, v] of Object.entries(e.values)) if (typeof v === 'number') values[k] = v
  const criteria: Record<string, boolean> = {}
  for (const [k, v] of Object.entries(e.criteria)) if (typeof v === 'boolean') criteria[k] = v
  await MotionApi.updateTrialManual({
    trialId: t.id,
    valid: e.valid,
    invalidReason: e.valid ? undefined : e.invalidReason,
    pain: typeof e.pain === 'boolean' ? e.pain : null,
    manualCriteria: Object.keys(criteria).length ? criteria : undefined,
    manualValues: Object.keys(values).length ? values : undefined,
    reason: reason.value || undefined
  })
  message.success('已保存，需重新计算')
  emit('changed')
  await init()
}

const addYbt = () => ybt.value.push({ stance_side: 'left', direction: 'ANT', attempt_no: 1, reach_cm: null, valid: true })
const fillYbtGrid = () => {
  if (ybt.value.length && !window.confirm('将替换当前 YBT 行，继续？')) return
  const rows: any[] = []
  for (const s of ['left', 'right']) for (const d of ['ANT', 'PM', 'PL']) for (let n = 1; n <= 3; n++) rows.push({ stance_side: s, direction: d, attempt_no: n, reach_cm: null, valid: true })
  ybt.value = rows
}

const saveInputs = async () => {
  if (needReason()) return
  for (const y of ybt.value) if (!y.valid && !(y.invalid_reason || '').trim()) return message.warning('YBT 无效尝试必须填写原因')
  for (const l of less.value) if (!l.valid && !(l.invalid_reason || '').trim()) return message.warning(`LESS ${l.trial_key} 无效必须填写原因`)
  const inputs: Record<string, any> = {}
  if (families.value.has('FMS')) {
    const ct = CLEARING_TESTS.filter((c) => typeof clearing[c.code] === 'boolean').map((c) => ({ code: c.code, pain: clearing[c.code] as boolean }))
    if (ct.length) inputs.clearing_tests = ct
  }
  if (families.value.has('YBT_LQ') && ybt.value.length) {
    inputs.ybt_trials = ybt.value.map((y) => ({
      stance_side: y.stance_side,
      direction: y.direction,
      attempt_no: y.attempt_no,
      reach_cm: typeof y.reach_cm === 'number' ? y.reach_cm : null,
      valid: y.valid !== false,
      ...(y.valid === false ? { invalid_reason: y.invalid_reason } : {})
    }))
  }
  if (families.value.has('TUCK_JUMP')) {
    const r: Record<string, number> = {}
    for (const [k, v] of Object.entries(tja)) if (typeof v === 'number') r[k] = v
    if (Object.keys(r).length) inputs.tuck_jump_ratings = r
  }
  if (families.value.has('LESS') && less.value.length) {
    inputs.less_trials = less.value.map((l) => {
      const r: Record<string, number> = {}
      for (const [k, v] of Object.entries(l.item_ratings)) if (typeof v === 'number') r[k] = v
      return { trial_key: l.trial_key, item_ratings: r, valid: l.valid, ...(l.valid ? {} : { invalid_reason: l.invalid_reason }) }
    })
  }
  if (families.value.has('NASM_CES')) {
    const obs = Object.values(nasm)
      .filter((o) => typeof o.present === 'boolean' || o.note)
      .map((o) => ({ test_code: o.test_code, checkpoint: o.checkpoint, present: typeof o.present === 'boolean' ? o.present : null, ...(o.note ? { note: o.note } : {}) }))
    if (obs.length) inputs.nasm_observations = obs
  }
  saving.value = true
  try {
    await MotionApi.saveManualInputs({ assessmentId: props.assessment.id, inputs, reason: reason.value || undefined })
    message.success('已保存，需重新计算')
    emit('changed')
  } finally {
    saving.value = false
  }
}

watch(() => props.result, init)
onMounted(init)
</script>

<style scoped>
h4 {
  margin: 18px 0 8px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: 8px;
}
</style>
